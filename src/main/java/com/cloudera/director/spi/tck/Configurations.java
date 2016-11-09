// Copyright (c) 2015 Cloudera, Inc.

package com.cloudera.director.spi.tck;

/**
 * Constants for important properties and sections in the configuration file.
 *
 * @see <a href="https://github.com/typesafehub/config" />
 */
public final class Configurations {

  private Configurations() {
  }

  /**
   * The path to the configuration directory for plugins.
   */
  public static final String CONFIGURATION_DIRECTORY_PROPERTY = "configurationDirectory";

  /**
   * The HOCON path prefix for provider configuration (includes credentials).
   */
  public static final String CONFIGS_SECTION = "configs";

  /**
   * The HOCON section identifier for resource template configurations.
   */
  public static final String RESOURCE_CONFIGS_SECTION = "resourceConfigs";

  /**
   * The HOCON section identifier for resource template tags.
   */
  public static final String RESOURCE_TAGS_SECTION = "resourceTags";

  /**
   * A TCP port number that should be accepting connections if resource provisioning
   * succeeded for a given resource provider within a cloud environment.
   */
  public static final String EXPECTED_OPEN_PORT_PROPERTY = "expectedOpenPort";

}
