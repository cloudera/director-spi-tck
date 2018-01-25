// Copyright (c) 2017 Cloudera, Inc.

package com.cloudera.director.spi.tck.util;

import com.cloudera.director.spi.tck.PluginMetadata;
import com.cloudera.director.spi.tck.Summary;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class TCKUtil {

  private static final Logger LOG = Logger.getLogger(TCKUtil.class.getName());

  private static final int DEFAULT_PORT_TIMEOUT_MINUTES = 10;
  private static final int DEFAULT_PORT_WAIT_BETWEEN_ATTEMPTS_SECONDS = 5;

  private static final String JAR_FILE_EXTENSION = ".jar";

  /**
   * Validates the internal file structure of a plugin.
   *
   * @param summary a summary of errors and warnings that will be updated
   * @param metadata the plugin metadata
   * @param version the SPI version
   */
  public void validateJar(Summary summary, PluginMetadata metadata, String version) {
    LOG.info("Validating plugin jar file internal structure (shading of dependencies)");
    validatePackaging(summary, metadata, version);
    validateThereAreNoEmbeddedJarFiles(summary, metadata);
  }

  /**
   * Retrieves a class loader from a plugin JAR file.
   *
   * @param pluginFile the plugin
   * @return a class loader for the plugin
   * @throws IOException
   */
  public ClassLoader getClassLoader(File pluginFile) throws IOException {
    LOG.info("Loading plugin file via a new ClassLoader from: " + pluginFile.getAbsolutePath());

    final URL pluginUrl;
    try {
      pluginUrl = pluginFile.toURI().toURL();
    } catch (MalformedURLException e) {
      throw new IOException("Failed to convert JAR file to URL", e);
    }

    return AccessController.doPrivileged(
        new PrivilegedAction<URLClassLoader>() {
          public URLClassLoader run() {
            return new URLClassLoader(new URL[] { pluginUrl },
                this.getClass().getClassLoader());
          }
        });
  }

  /**
   * Wait until successful connection to the specified port.
   *
   * @param summary a summary of errors and warnings that will be updated
   * @param privateIpAddress the private ip of the machine to check
   * @param port the port number of the machine to check
   * @throws InterruptedException if the operation is interrupted
   * @throws IOException if an error occurs when closing the connection
   */
  public void waitForPort(Summary summary, InetAddress privateIpAddress, int port)
      throws InterruptedException, IOException {

    Stopwatch stopwatch = Stopwatch.createStarted();
    InetSocketAddress address = new InetSocketAddress(privateIpAddress.getHostName(), port);

    while (stopwatch.elapsed(TimeUnit.MINUTES) < DEFAULT_PORT_TIMEOUT_MINUTES) {
      LOG.info("Attempting connection to " + address);
      Socket socket = new Socket();
      try {
        socket.connect(address, 500);
        LOG.info(String.format("Connection successful. Found port %d open as expected", port));
        break;  // connection successful

      } catch (IOException e) {
        TimeUnit.SECONDS.sleep(DEFAULT_PORT_WAIT_BETWEEN_ATTEMPTS_SECONDS);

      } finally {
        socket.close();
      }
    }

    if (stopwatch.elapsed(TimeUnit.MINUTES) >= DEFAULT_PORT_TIMEOUT_MINUTES) {
      summary.addError("Unable to connect on port %s after %s minutes",
          port, DEFAULT_PORT_TIMEOUT_MINUTES);
    }
  }

  /**
   * Checks that everything is shaded properly. The launcher classes should
   * define the root of the Java package hierarchy; every other class in the
   * plugin must be in that package or below it.
   */
  private void validatePackaging(Summary summary, PluginMetadata metadata, String version) {
    // with multiple launcher classes, this only works if the launchers are all
    // in the same package, which is what we want
    for (ClassReference launcherClassRef : metadata.getLauncherClasses(version)) {
      String expectedNamespace = launcherClassRef.getPackageName();
      for (String candidate : metadata.getClasses()) {
        if (!candidate.startsWith(expectedNamespace)) {
          summary.addError("Class '%s' should be relocated under '%s', due to launcher class '%s'",
              candidate, expectedNamespace, launcherClassRef.getCanonicalClassName());
        }
      }
    }
  }

  /**
   * Checks that the packaged plugin doesn't contain any embedded jar files. The
   * shading process should expand dependencies and relocate them as needed.
   */
  private void validateThereAreNoEmbeddedJarFiles(Summary summary, PluginMetadata metadata) {
    for (String candidate : metadata.getFiles()) {
      if (candidate.toLowerCase(Locale.US).endsWith(JAR_FILE_EXTENSION)) {
        summary.addError("Embedded jar files are not allowed: %s", candidate);
      }
    }
  }
}
