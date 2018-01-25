// Copyright (c) 2015 Cloudera, Inc.

package com.cloudera.director.spi.tck;

import static com.cloudera.director.spi.tck.util.Preconditions.checkNotNull;

import com.cloudera.director.spi.tck.util.ClassReference;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;

/**
 * Plugin metadata extracted from the packaged jar file that contains
 * the cloud provider implementation.
 */
public class PluginMetadata {

  private static final Logger LOG = Logger.getLogger(PluginMetadata.class.getName());

  private static final String CLASS_FILE_EXTENSION = ".class";
  private static final String PROVIDER_CONFIGURATION_START = "META-INF/services/";
  private static final int PROVIDER_CONFIGURATION_START_LEN = PROVIDER_CONFIGURATION_START.length();

  /**
   * Loads the plugin metadata from the JAR.
   *
   * @param jar plugin JAR
   * @return plugin metadata
   * @throws IllegalStateException if an unknown launcher interface is found
   */
  public static PluginMetadata fromExternalJarFile(JarFile jar) throws IOException {

    // Collect all the entries in the jar file and group them:
    // - classes
    // - provider configuration files, which should describe launchers
    // - regular files

    Map<String, List<ClassReference>> launchers = new HashMap<String, List<ClassReference>>();
    List<String> classes = new ArrayList<String>();
    List<String> files = new ArrayList<String>();

    Enumeration<JarEntry> entries = jar.entries();
    while (entries.hasMoreElements()) {
      JarEntry entry = entries.nextElement();
      if (entry.isDirectory()) {
        continue;
      }

      String name = entry.getName();
      if (name.endsWith(CLASS_FILE_EXTENSION)) {
        classes.add(compiledFilePathToClassName(name));

      } else if (name.startsWith(PROVIDER_CONFIGURATION_START)) {
        // file is META-INF/services/binary.name.of.launcher.interface
        // content is list of launcher implementation, one per line, with # comments

        String spiVersion = getSpiVersion(name.substring(PROVIDER_CONFIGURATION_START_LEN));
        if (spiVersion == null) {
          continue;
        }

        List<String> launcherClasses = IOUtils.readLines(jar.getInputStream(entry), "UTF-8");
        List<ClassReference> refs = new ArrayList<ClassReference>();
        for (String launcherClass : launcherClasses) {
          if (launcherClass.startsWith("#")) {
            continue;
          }
          int firstCommentMarker = launcherClass.indexOf("#");
          if (firstCommentMarker != -1) {
            launcherClass = launcherClass.substring(0, firstCommentMarker);
          }
          refs.add(new ClassReference(launcherClass.trim()));
        }

        // the expectation is that there is exactly one launcher interface per
        // SPI version
        launchers.put(spiVersion, refs);

      } else {
        files.add(name);
      }
    }

    return new PluginMetadata(launchers, classes, files);
  }

  private static String compiledFilePathToClassName(String path) {
    return path.replace("/", ".").substring(0, path.length() - CLASS_FILE_EXTENSION.length());
  }

  private static final Map<String, String> LAUNCHER_INTERFACE_VERSIONS;

  static {
    LAUNCHER_INTERFACE_VERSIONS = new HashMap<String, String>();
    LAUNCHER_INTERFACE_VERSIONS.put("com.cloudera.director.spi.v1.provider.Launcher", "v1");
    LAUNCHER_INTERFACE_VERSIONS.put("com.cloudera.director.spi.v2.provider.Launcher", "v2");
    // add future interface versions here
  }

  /**
   * Gets the SPI version for a named launcher interface.
   *
   * @param launcherInterface launcher interface name
   * @return SPI version, or null if this doesn't appear to be a launcher interface
   * @throws IllegalStateException if the launcher interface is unknown
   */
  static String getSpiVersion(String launcherInterface) {
    String spiVersion = LAUNCHER_INTERFACE_VERSIONS.get(launcherInterface);
    if (spiVersion == null) {
      if (!launcherInterface.startsWith("com.cloudera.director.spi")) {
        LOG.warning("Found provider configuration file for " + launcherInterface +
                    ", assuming not an SPI launcher interface");
        return null;  // must be for some other service
      }
      // missing from table!
      throw new IllegalStateException("Unknown launcher interface " + launcherInterface);
    }
    return spiVersion;
  }

  private final Map<String, List<ClassReference>> launchers;
  private final List<String> classes;
  private final List<String> files;

  /**
   * Creates new plugin metadata.
   *
   * @param launchers map of SPI versions to launcher references
   * @param classes list of classes in plugin
   * @param files list of other files in plugin
   * @throws NullPointerException if any argument is null
   */
  public PluginMetadata(Map<String, List<ClassReference>> launchers,
      List<String> classes, List<String> files) {

    Map<String, List<ClassReference>> launchersCopy = new HashMap<String, List<ClassReference>>();
    for (Map.Entry<String, List<ClassReference>> e :
         checkNotNull(launchers, "launchers is null").entrySet()) {
      launchersCopy.put(e.getKey(), Collections.unmodifiableList(new ArrayList(e.getValue())));
    }
    this.launchers = Collections.unmodifiableMap(launchersCopy);

    this.classes = Collections.unmodifiableList(new ArrayList<String>(classes));
    this.files = Collections.unmodifiableList(new ArrayList<String>(files));
  }

  /**
   * Gets the launchers in the plugin.
   *
   * @return launchers, as a map of SPI versions to launcher references
   */
  public Map<String, List<ClassReference>> getLaunchers() {
    return launchers;
  }

  /**
   * Gets the SPI versions supported by the plugin.
   *
   * @return SPI versions
   */
  public Set<String> getSpiVersions() {
    return Collections.unmodifiableSet(launchers.keySet());
  }

  /**
   * Gets references to the launcher classes available for the given SPI
   * version.
   *
   * @param spiVersion SPI version
   * @return list of launcher class references, or null if none available
   */
  public List<ClassReference> getLauncherClasses(String spiVersion) {
    return launchers.get(spiVersion);
  }

  /**
   * Gets the names of classes in the plugin.
   *
   * @return class names
   */
  public List<String> getClasses() {
    return classes;
  }

  /**
   * Gets the names of non-class files in the plugin.
   *
   * @return non-class file names
   */
  public List<String> getFiles() {
    return files;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PluginMetadata that = (PluginMetadata) o;

    if (!launchers.equals(that.launchers)) return false;
    if (!classes.equals(that.classes)) return false;
    if (!files.equals(that.files)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = launchers.hashCode();
    result = 31 * result + classes.hashCode();
    result = 31 * result + files.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "PluginMetadata{" +
        "launchers=" + launchers +
        ", classesCount=" + classes.size() +
        ", filesCount=" + files.size() +
        '}';
  }
}
