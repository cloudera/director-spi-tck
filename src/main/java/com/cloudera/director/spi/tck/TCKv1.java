// Copyright (c) 2015 Cloudera, Inc.

package com.cloudera.director.spi.tck;

import com.cloudera.director.spi.tck.util.ClassReference;
import com.cloudera.director.spi.tck.util.TCKUtil;
import com.cloudera.director.spi.v1.provider.Launcher;
import com.cloudera.director.spi.v2.adapters.v1.FromV1;
import com.typesafe.config.Config;

import java.io.File;
import java.util.logging.Logger;

/**
 * Validates an implementation of the v1 of the Director SPI.
 */
public class TCKv1 implements TCK {

  private static final String SPI_VERSION = "v1";

  private static final Logger LOG = Logger.getLogger(TCKv1.class.getName());

  private static final TCKUtil TCK_UTIL = new TCKUtil();

  public Summary validate(File pluginFile, PluginMetadata metadata, Config config)
      throws Exception {

    Summary summary = new Summary();

    TCK_UTIL.validateJar(summary, metadata, SPI_VERSION);

    if (summary.hasErrors()) {
      return summary;  // no need to continue if we found some errors already
    }

    ClassLoader classLoader = TCK_UTIL.getClassLoader(pluginFile);

    // Load each launcher and try to create an instance using the default constructor

    for (ClassReference launcherClassRef : metadata.getLauncherClasses(SPI_VERSION)) {
      LOG.info(String.format("Creating an instance of the launcher class: %s",
          launcherClassRef.getCanonicalClassName()));

      Class<?> launcherClass = classLoader.loadClass(
          launcherClassRef.getCanonicalClassName());

      if (!Launcher.class.isAssignableFrom(launcherClass)) {
        summary.addError("%s should implement the SPI Launcher interface",
            launcherClass.getCanonicalName());
        return summary;
      }

      Launcher launcher = (Launcher) launcherClass.newInstance();

      com.cloudera.director.spi.v2.provider.Launcher convertedLauncher = FromV1.fromV1(launcher);
      TCKv2 tckV2 = new TCKv2();
      tckV2.validate(convertedLauncher, config, summary);

      if (summary.hasErrors()) {
        break;  // no need to continue if we found some errors for one launcher
      }
    }

    return summary;
  }
}
