// Copyright (c) 2015 Cloudera, Inc.

package com.cloudera.director.spi.tck;

import com.cloudera.director.spi.tck.util.ClassReference;
import com.cloudera.director.spi.tck.util.ConfigFragmentWrapper;
import com.cloudera.director.spi.tck.util.Stopwatch;
import com.cloudera.director.spi.v1.compute.ComputeProvider;
import com.cloudera.director.spi.v1.database.DatabaseServerProvider;
import com.cloudera.director.spi.v1.model.Instance;
import com.cloudera.director.spi.v1.model.InstanceState;
import com.cloudera.director.spi.v1.model.InstanceStatus;
import com.cloudera.director.spi.v1.model.InstanceTemplate;
import com.cloudera.director.spi.v1.model.LocalizationContext;
import com.cloudera.director.spi.v1.model.util.ChildLocalizationContext;
import com.cloudera.director.spi.v1.provider.CloudProvider;
import com.cloudera.director.spi.v1.provider.CloudProviderMetadata;
import com.cloudera.director.spi.v1.provider.InstanceProvider;
import com.cloudera.director.spi.v1.provider.Launcher;
import com.cloudera.director.spi.v1.provider.ResourceProvider;
import com.cloudera.director.spi.v1.provider.ResourceProviderMetadata;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;

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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Validates an implementation of the v1 of the Director SPI.
 */
public class TCKv1 implements TCK {

  private static final Logger LOG = Logger.getLogger(TCKv1.class.getName());

  private static final String JAR_FILE_EXTENSION = ".jar";

  private static final int DEFAULT_TIMEOUT_MINUTES = 10;
  private static final int DEFAULT_WAIT_BETWEEN_ATTEMPTS_SECONDS = 5;

  public Summary validate(File pluginFile, PluginMetadata metadata, Config config)
      throws Exception {

    Summary summary = new Summary();

    // Check that the plugin is properly packaged and dependencies are shaded

    LOG.info("Validating plugin jar file internal structure (shading of dependencies)");
    validatePackaging(summary, metadata);
    validateThereAreNoEmbeddedJarFiles(summary, metadata);
    if (summary.hasErrors()) {
      return summary;  // no need to continue if we found some errors already
    }


    LOG.info("Loading plugin file via a new ClassLoader from: " + pluginFile.getAbsolutePath());

    final URL pluginUrl;
    try {
      pluginUrl = pluginFile.toURI().toURL();
    } catch (MalformedURLException e) {
      throw new IOException("Failed to convert JAR file to URL", e);
    }

    URLClassLoader classLoader = AccessController.doPrivileged(
        new PrivilegedAction<URLClassLoader>() {
          public URLClassLoader run() {
            return new URLClassLoader(new URL[] { pluginUrl },
                                      this.getClass().getClassLoader());
          }
        });

    // Load each launcher and try to create an instance using the default constructor

    for (ClassReference launcherClassRef : metadata.getLauncherClasses("v1")) {
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

      // Initialize with a configuration directory the plugin test config file

      String configurationDirectory = config.getString(Configurations.CONFIGURATION_DIRECTORY_PROPERTY);

      LOG.info(String.format("Initializing the plugin with configuration directory: %s",
          configurationDirectory));

      launcher.initialize(new File(configurationDirectory), null);

      // Sequentially validate all cloud providers that are part of this plugin

      Locale locale = Locale.getDefault();
      LocalizationContext rootLocalizationContext = launcher.getLocalizationContext(locale);
      for (CloudProviderMetadata providerMetadata : launcher.getCloudProviderMetadata()) {
        validateCloudProvider(summary, launcher, providerMetadata,
            config.getConfig(providerMetadata.getId()), rootLocalizationContext);

        if (summary.hasErrors()) {
          break;  // no need to continue if we found some errors for one cloud provider
        }
      }

      if (summary.hasErrors()) {
        break;  // no need to continue if we found some errors for one launcher
      }
    }

    return summary;
  }

  private void validateCloudProvider(Summary summary, Launcher launcher,
      CloudProviderMetadata metadata, Config config, LocalizationContext rootLocalizationContext)
      throws Exception {

    LOG.info(String.format("Validating cloud provider ID: %s Name: %s",
        metadata.getId(), metadata.getName(null)));

    LocalizationContext cloudLocalizationContext =
        metadata.getLocalizationContext(rootLocalizationContext);

    ConfigFragmentWrapper configWrapper = new ConfigFragmentWrapper(
        config.getConfig(Configurations.CONFIGS_SECTION),
        metadata.getCredentialsProviderMetadata().getCredentialsConfigurationProperties(),
        metadata.getProviderConfigurationProperties()
    );
    configWrapper.dump("Configuration properties for the cloud provider:", LOG,
        cloudLocalizationContext);

    CloudProvider provider =
        launcher.createCloudProvider(metadata.getId(), configWrapper,
            cloudLocalizationContext.getLocale());

    for (ResourceProviderMetadata current : metadata.getResourceProviderMetadata()) {
      validateResourceProvider(summary, provider, current, config.getConfig(current.getId()),
          cloudLocalizationContext);
      if (summary.hasErrors()) {
        break;  // no need to continue if we failed to validate one resource provider
      }
    }
  }

  private void validateResourceProvider(Summary summary, CloudProvider provider,
      ResourceProviderMetadata metadata, Config config,
      LocalizationContext cloudLocalizationContext) throws Exception {

    LOG.info(String.format("Validating resource provider ID: %s Name: %s",
        metadata.getId(), metadata.getDescription(null)));

    LocalizationContext resourceProviderLocalizationContext =
        metadata.getLocalizationContext(cloudLocalizationContext);

    ConfigFragmentWrapper configWrapper = new ConfigFragmentWrapper(
        config.getConfig(Configurations.CONFIGS_SECTION),
        metadata.getProviderConfigurationProperties()
    );
    configWrapper.dump("Configuration properties for the resource provider:", LOG,
        resourceProviderLocalizationContext);

    ResourceProvider resourceProvider =
        provider.createResourceProvider(metadata.getId(), configWrapper);

    if (resourceProvider instanceof ComputeProvider) {
      LOG.info("Attempting to use this provider as a COMPUTE provider");
      validateInstanceProvider(summary, (ComputeProvider) resourceProvider, metadata, config,
          resourceProviderLocalizationContext);

    } else if (resourceProvider instanceof DatabaseServerProvider) {
      LOG.info("Attempting to use this provider as a DATABASE SERVER provider");
      validateInstanceProvider(summary, (DatabaseServerProvider) resourceProvider, metadata, config,
          resourceProviderLocalizationContext);

    } else {
      summary.addError("Unknown resource provider type: %s",
          resourceProvider.getClass().getCanonicalName());
    }
  }

  private void validateInstanceProvider(Summary summary, InstanceProvider provider,
      ResourceProviderMetadata metadata, Config config,
      LocalizationContext resourceProviderLocalizationContext)
      throws Exception {

    LocalizationContext templateLocalizationContext =
        new ChildLocalizationContext(resourceProviderLocalizationContext, "template");

    ConfigFragmentWrapper configWrapper = new ConfigFragmentWrapper(
        config.getConfig(Configurations.RESOURCE_CONFIGS_SECTION),
        metadata.getResourceTemplateConfigurationProperties()
    );
    configWrapper.dump("Configuration properties for the instance template:", LOG,
        templateLocalizationContext);

    Map<String, String> tags = convertToMap(config.getConfig(Configurations.RESOURCE_TAGS_SECTION));
    InstanceTemplate template = (InstanceTemplate) provider.createResourceTemplate("test", configWrapper, tags);

    String id;
    do {
      id = UUID.randomUUID().toString();
    } while (Character.isDigit(id.charAt(0)));
    List<String> instanceIds = Collections.singletonList(id);

    LOG.info("Allocating one instance with ID: " + id);
    provider.allocate(template, instanceIds, 1);
    try {
      waitForInstanceStatus(summary, provider, template, id, InstanceStatus.RUNNING);
      if (summary.hasErrors()) {
        return;
      }

      Collection<Instance> instances = provider.find(template, instanceIds);
      if (instances.size() != 1) {
        summary.addError("Expected to find exactly one instance after allocation. Found: %s",
            instances);
        return;
      }
      Instance instance = instances.iterator().next();

      int expectedOpenPort = config.getInt(Configurations.EXPECTED_OPEN_PORT_PROPERTY);
      if (expectedOpenPort == -1) {
        LOG.info(String.format("Skipping check of connectivity to %s because expected open port is %d",
                               instance.getPrivateIpAddress(), expectedOpenPort));
      } else {
        LOG.info(String.format("Checking connectivity on port %d to %s",
            expectedOpenPort, instance.getPrivateIpAddress()));
        waitForPort(summary, instance.getPrivateIpAddress(), expectedOpenPort);
      }

    } finally {
      try {
        LOG.info("Deleting allocated resources");
        provider.delete(template, instanceIds);

      } catch (Exception e) {
        LOG.severe("CRITICAL: Failed to delete allocated resources. Manual clean-up is necessary");
        throw e;
      }

      waitForInstanceStatus(summary, provider, template, id, InstanceStatus.DELETED,
                            InstanceStatus.UNKNOWN);
    }
  }

  private Map<String, String> convertToMap(Config section) {
    Map<String, String> result = new HashMap<String, String>();
    for (Map.Entry<String, ConfigValue> entry : section.entrySet()) {
      result.put(entry.getKey(), entry.getValue().unwrapped().toString());
    }
    return Collections.unmodifiableMap(result);
  }

  private void waitForPort(Summary summary, InetAddress privateIpAddress, int port)
      throws InterruptedException, IOException {

    Stopwatch stopwatch = Stopwatch.createStarted();
    InetSocketAddress address = new InetSocketAddress(privateIpAddress.getHostName(), port);

    while (stopwatch.elapsed(TimeUnit.MINUTES) < DEFAULT_TIMEOUT_MINUTES) {
      LOG.info("Attempting connection to " + address);
      Socket socket = new Socket();
      try {
        socket.connect(address, 500);
        LOG.info(String.format("Connection successful. Found port %d open as expected", port));
        break;  // connection successful

      } catch (IOException e) {
        TimeUnit.SECONDS.sleep(DEFAULT_WAIT_BETWEEN_ATTEMPTS_SECONDS);

      } finally {
        socket.close();
      }
    }

    if (stopwatch.elapsed(TimeUnit.MINUTES) >= DEFAULT_TIMEOUT_MINUTES) {
      summary.addError("Unable to connect on port %s after %s minutes",
          port, DEFAULT_TIMEOUT_MINUTES);
    }
  }

  private void waitForInstanceStatus(Summary summary, InstanceProvider provider,
      InstanceTemplate template, String id, InstanceStatus... expectedStatuses) throws InterruptedException {

    List<InstanceStatus> expectedStatusesList = Arrays.asList(expectedStatuses);
    LOG.info(String.format("Waiting for instance status to be in %s " +
            "(%d seconds between checks, %d minutes timeout)", expectedStatusesList,
        DEFAULT_WAIT_BETWEEN_ATTEMPTS_SECONDS, DEFAULT_TIMEOUT_MINUTES));

    Stopwatch stopwatch = Stopwatch.createStarted();
    List<String> instanceIds = Collections.singletonList(id);

    while (stopwatch.elapsed(TimeUnit.MINUTES) < DEFAULT_TIMEOUT_MINUTES) {
      Map<String, InstanceState> states = provider.getInstanceState(template, instanceIds);
      if (states.containsKey(id)) {
        InstanceStatus status = states.get(id).getInstanceStatus();
        if (expectedStatusesList.contains(status)) {
          LOG.info("Found instance as expected " + status);
          break;

        } else {
          LOG.info("Instance status is " + status);
          TimeUnit.SECONDS.sleep(DEFAULT_WAIT_BETWEEN_ATTEMPTS_SECONDS);
        }

      } else {
        summary.addError("The instance ID was not part of the list of states");
        break;
      }
    }

    if (stopwatch.elapsed(TimeUnit.MINUTES) >= DEFAULT_TIMEOUT_MINUTES) {
      summary.addError("Instance did not transition to status in %s in %s minutes",
          expectedStatusesList, DEFAULT_TIMEOUT_MINUTES);
    }
  }

  /**
   * Checks that everything is shaded properly. The launcher classes should
   * define the root of the Java package hierarchy; every other class in the
   * plugin must be in that package or below it.
   */
  private void validatePackaging(Summary summary, PluginMetadata metadata) {

    // with multiple launcher classes, this only works if the launchers are all
    // in the same package, which is what we want
    for (ClassReference launcherClassRef : metadata.getLauncherClasses("v1")) {
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
