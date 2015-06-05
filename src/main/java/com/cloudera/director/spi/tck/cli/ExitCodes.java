// Copyright (c) 2015 Cloudera, Inc.

package com.cloudera.director.spi.tck.cli;

/**
 * Enumeration of exit codes for the TCK command line tool
 */
public final class ExitCodes {

  private ExitCodes() {
  }

  /**
   * Everything worked as expected
   */
  public static final int OK = 0;

  /**
   * Failed to validate the plugin (see stderr output for details)
   */
  public static final int PLUGIN_VALIDATION_FAILED = 5;

  /**
   * Wrong number of arguments for the validation tool
   */
  public static final int WRONG_ARGUMENT_COUNT = 10;

  /**
   * Plugin .jar file not found
   */
  public static final int PLUGIN_FILE_NOT_FOUND = 20;

  /**
   * Plugin .jar file is not a plugin
   */
  public static final int NO_LAUNCHERS_FOUND = 21;

  /**
   * Configuration file not found
   */
  public static final int CONFIG_FILE_NOT_FOUND = 30;

  /**
   * There is no TCK that can check the given SPI version
   */
  public static final int UNSUPPORTED_SPI_VERSION = 40;

}
