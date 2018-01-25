// Copyright (c) 2015 Cloudera, Inc.

package com.cloudera.director.spi.tck.cli;

import com.cloudera.director.spi.tck.PluginMetadata;
import com.cloudera.director.spi.tck.Summary;
import com.cloudera.director.spi.tck.TCK;
import com.cloudera.director.spi.tck.TCKv1;
import com.cloudera.director.spi.tck.TCKv2;
import com.cloudera.director.spi.tck.util.SingleLineFormatter;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigSyntax;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.logging.Handler;
import java.util.logging.Logger;


/**
 * Main entry point for the TCK as a command line tool.
 */
public class Runner {

  private static final Logger LOG = Logger.getLogger(Runner.class.getName());

  private static final Map<String, TCK> TCKS;

  static {
    Map<String, TCK> validators = new HashMap<String, TCK>();
    validators.put("v1", new TCKv1());
    validators.put("v2", new TCKv2());

    TCKS = Collections.unmodifiableMap(validators);
  }

  public static void main(String[] args) throws Exception {
    configureRootLogger();
    System.exit(run(args));
  }

  /**
   * Change default JUL configuration to print one entry per line for all handlers.
   * <p/>
   * We do it programmatically because we expect the TCK to also be used as a library
   * and the jar file shouldn't include a logging configuration file
   */
  private static void configureRootLogger() {
    Logger rootLogger = Logger.getLogger("");
    for (Handler handler : rootLogger.getHandlers()) {
      handler.setFormatter(new SingleLineFormatter());
    }
  }

  private static int run(String[] args) throws Exception {
    if (args.length != 2) {
      LOG.severe("Usage: java -jar director-spi-tck-*.jar <plugin-jar> <config-file>");
      return ExitCodes.WRONG_ARGUMENT_COUNT;
    }

    File pluginFile = new File(args[0]);
    File configFile = new File(args[1]);

    if (!pluginFile.isFile()) {
      LOG.severe("Plugin not a file or not found: " + pluginFile.getAbsolutePath());
      return ExitCodes.PLUGIN_FILE_NOT_FOUND;
    }

    if (!configFile.isFile()) {
      LOG.severe("Config not a file or not found: " + configFile.getAbsolutePath());
      return ExitCodes.CONFIG_FILE_NOT_FOUND;
    }

    // Extract implementation metadata and run the compatibility checks

    PluginMetadata metadata = PluginMetadata.fromExternalJarFile(new JarFile(pluginFile));
    if (metadata.getLaunchers().isEmpty()) {
      LOG.severe("No plugin launchers found: " + pluginFile.getAbsolutePath());
      return ExitCodes.NO_LAUNCHERS_FOUND;
    }

    List<TCK> tcks = new ArrayList<TCK>();
    for (String spiVersion : metadata.getSpiVersions()) {

      TCK tck = TCKS.get(spiVersion);
      if (tck == null) {
        LOG.severe("No compatibility kit available for this plugin version: " + spiVersion);
        return ExitCodes.UNSUPPORTED_SPI_VERSION;
      }
      tcks.add(tck);
    }

    for (TCK tck : tcks) {
      Summary summary = tck.validate(pluginFile, metadata, parseConfigFile(configFile).resolve());
      if (summary.hasErrors()) {
        logErrors(summary);
        logWarnings(summary);

        LOG.severe("Validation failed. See above for details.");
        return ExitCodes.PLUGIN_VALIDATION_FAILED;

      } else {
        logWarnings(summary);

        LOG.info("Validation succeeded.");
      }
    }

    return ExitCodes.OK;
  }

  private static void logErrors(Summary summary) {
    if (summary.hasErrors()) {
      LOG.severe("Plugin validation errors:");
      for (String error : summary.getErrors()) {
        LOG.severe("* " + error);
      }
    }
  }

  private static void logWarnings(Summary summary) {
    if (summary.hasWarnings()) {
      LOG.warning("Plugin validation warnings:");
      for (String warning : summary.getWarnings()) {
        LOG.warning("* " + warning);
      }
    }
  }

  private static Config parseConfigFile(File configFile) {
    ConfigParseOptions options = ConfigParseOptions.defaults()
        .setSyntax(ConfigSyntax.CONF)
        .setAllowMissing(false);

    return ConfigFactory.parseFileAnySyntax(configFile, options);
  }
}
