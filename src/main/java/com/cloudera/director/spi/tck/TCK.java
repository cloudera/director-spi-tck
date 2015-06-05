// Copyright (c) 2015 Cloudera, Inc.

package com.cloudera.director.spi.tck;

import com.typesafe.config.Config;

import java.io.File;

/**
 * A generic TCK for Director SPI implementations
 */
public interface TCK {

  /**
   * Validate a plugin implementation packaged as a jar file.
   * <p/>
   * Implementations of this method should run various checks and also go through
   * the process of creating a resource for all the available resource providers.
   *
   * @param pluginFile a path to the plugin file
   * @param metadata   the metadata object extracted from the plugin
   *                   file in order to discover the proper TCK
   * @param config     the config file used to drive the validation
   */
  Summary validate(File pluginFile, PluginMetadata metadata, Config config)
      throws Exception;

}
