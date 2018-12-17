// Copyright (c) 2015 Cloudera, Inc.

package com.cloudera.director.spi.tck;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cloudera.director.spi.tck.util.ClassReference;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PluginMetadataTest {

  private File pluginFile = new File("target/byon-provider-example.jar");

  @Test
  public void testLoadByonMetadata() throws IOException {
    PluginMetadata metadata = PluginMetadata.fromExternalJarFile(new JarFile(pluginFile));

    Set<String> spiVersions = metadata.getSpiVersions();
    assertEquals(1, spiVersions.size());
    assertEquals("v2", spiVersions.iterator().next());

    List<ClassReference> launcherClasses = metadata.getLauncherClasses("v2");
    assertEquals(1, launcherClasses.size());
    assertEquals("BYONLauncher",
                 metadata.getLauncherClasses("v2").iterator().next().getSimpleClassName());

    assertTrue(metadata.getClasses().contains("com.cloudera.director.byon.BYONLauncher"));
    assertTrue(metadata.getFiles()
        .contains("META-INF/maven/com.cloudera.director/byon-provider-example/pom.xml"));
    assertFalse(metadata.getFiles()
        .contains("META-INF/services/com.cloudera.director.spi.v1.provider.Launcher"));
  }
}
