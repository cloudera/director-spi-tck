// Copyright (c) 2017 Cloudera, Inc.

package com.cloudera.director.spi.tck;

import com.cloudera.director.spi.tck.util.ClassReference;
import com.cloudera.director.spi.tck.util.ConfigFragmentWrapper;
import com.cloudera.director.spi.tck.util.Stopwatch;
import com.cloudera.director.spi.tck.util.TCKUtil;
import com.cloudera.director.spi.v2.common.http.HttpProxyParameters;
import com.cloudera.director.spi.v2.compute.ComputeInstanceTemplate;
import com.cloudera.director.spi.v2.compute.ComputeProvider;
import com.cloudera.director.spi.v2.database.DatabaseServerProvider;
import com.cloudera.director.spi.v2.model.Instance;
import com.cloudera.director.spi.v2.model.InstanceState;
import com.cloudera.director.spi.v2.model.InstanceStatus;
import com.cloudera.director.spi.v2.model.InstanceTemplate;
import com.cloudera.director.spi.v2.model.LocalizationContext;
import com.cloudera.director.spi.v2.model.util.ChildLocalizationContext;
import com.cloudera.director.spi.v2.provider.CloudProvider;
import com.cloudera.director.spi.v2.provider.CloudProviderMetadata;
import com.cloudera.director.spi.v2.provider.InstanceProvider;
import com.cloudera.director.spi.v2.provider.Launcher;
import com.cloudera.director.spi.v2.provider.ResourceProvider;
import com.cloudera.director.spi.v2.provider.ResourceProviderMetadata;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Validates an implementation of the v2 of the Director SPI.
 */
public class TCKv2 implements TCK {

  private static final String SPI_VERSION = "v2";

  private static final Logger LOG = Logger.getLogger(TCKv2.class.getName());

  private static final TCKUtil TCK_UTIL = new TCKUtil();

  private static final int DEFAULT_TIMEOUT_MINUTES = 10;
  private static final int DEFAULT_WAIT_BETWEEN_ATTEMPTS_SECONDS = 5;

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
      validate(launcher , config, summary);

      if (summary.hasErrors()) {
        break;  // no need to continue if we found some errors for one launcher
      }
    }

    return summary;
  }

  public void validate(Launcher launcher, Config config, Summary summary) throws Exception {
    // Initialize with a configuration directory the plugin test config file
    String configurationDirectory = config.getString(Configurations.CONFIGURATION_DIRECTORY_PROPERTY);

    LOG.info(String.format("Initializing the plugin with configuration directory: %s",
        configurationDirectory));

    launcher.initialize(new File(configurationDirectory), new HttpProxyParameters());

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
  }

  private void validateCloudProvider(Summary summary, Launcher launcher,
      CloudProviderMetadata metadata, Config config, LocalizationContext rootLocalizationContext)
      throws Exception {

    LOG.info(String.format("Validating cloud provider ID: %s Name: %s",
        metadata.getId(), metadata.getName(rootLocalizationContext)));

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
        metadata.getId(), metadata.getDescription(cloudLocalizationContext)));

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
    Collection<Instance> instances = provider.allocate(template, instanceIds, 1);
    if (instances.size() != 1) {
      summary.addError("Expected allocation to return exactly one instance. Found: %s",
          instances);
      return;
    }

    try {
      waitForInstanceStatus(summary, provider, template, id, InstanceStatus.RUNNING);
      if (summary.hasErrors()) {
        return;
      }

      instances = provider.find(template, instanceIds);
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
        TCK_UTIL.waitForPort(summary, instance.getPrivateIpAddress(), expectedOpenPort);
      }

      if (provider instanceof ComputeProvider) {
        checkHostKeyFingerprints((ComputeProvider) provider, (ComputeInstanceTemplate) template, id, summary);
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

  private void checkHostKeyFingerprints(ComputeProvider provider, ComputeInstanceTemplate template, String instanceId,
                                        Summary summary) throws InterruptedException {
    LOG.info("Attempting to retrieve the host key fingerprints for instance");
    Map<String, Set<String>> hostKeyFingerprints =
        provider.getHostKeyFingerprints(template, Collections.singletonList(instanceId));

    if (hostKeyFingerprints.isEmpty()) {
      LOG.warning("No host key fingerprint returned for the instance");
      return;
    }

    if (hostKeyFingerprints.size() != 1) {
      summary.addError("Expected to retrieve an empty host key fingerprint map or exactly one set of " +
          "host key fingerprints. Found: %s", hostKeyFingerprints);
      return;
    }

    Set<String> fingerprintsForInstance = hostKeyFingerprints.get(instanceId);
    if (fingerprintsForInstance.isEmpty()) {
      summary.addError("Expected the set of host key fingerprints for the instance to not be empty");
    }
  }

  private Map<String, String> convertToMap(Config section) {
    Map<String, String> result = new HashMap<String, String>();
    for (Map.Entry<String, ConfigValue> entry : section.entrySet()) {
      result.put(entry.getKey(), entry.getValue().unwrapped().toString());
    }
    return Collections.unmodifiableMap(result);
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
}
