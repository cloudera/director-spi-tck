// Copyright (c) 2015 Cloudera, Inc.

package com.cloudera.director.spi.tck;

import static org.junit.Assert.assertFalse;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigSyntax;
import com.typesafe.config.ConfigValueFactory;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.jar.JarFile;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TCKv1Test {

  private static final ConfigParseOptions CONFIG_PARSER_OPTIONS = ConfigParseOptions.defaults()
      .setSyntax(ConfigSyntax.CONF)
      .setAllowMissing(false);

  private File pluginFile = new File("target/byon-provider-example.jar");
  private int localTestPort;

  @Before
  public void setUp() throws IOException {
    localTestPort = setUpLocalServerToAcceptOneConnectionOnRandomPort();
  }

  @Test
  public void testValidateByonProvider() throws Exception {
    TCK tck = new TCKv1();

    Config config = ConfigFactory
        .parseResourcesAnySyntax("test.byon.conf", CONFIG_PARSER_OPTIONS)
        .withValue("byon.compute.expectedOpenPort", ConfigValueFactory.fromAnyRef(localTestPort));

    PluginMetadata metadata = PluginMetadata.fromExternalJarFile(new JarFile(pluginFile));
    Summary summary = tck.validate(pluginFile, metadata, config);

    assertFalse(summary.hasErrors());
    assertFalse(summary.hasWarnings());
  }

  private int setUpLocalServerToAcceptOneConnectionOnRandomPort() throws IOException {
    final ServerSocket serverSocket = new ServerSocket(0);

    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          Socket client = serverSocket.accept();
          client.close();
          serverSocket.close();

        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }).start();

    return serverSocket.getLocalPort();
  }
}
