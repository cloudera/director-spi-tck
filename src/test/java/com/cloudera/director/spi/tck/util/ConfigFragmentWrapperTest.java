// Copyright (c) 2015 Cloudera, Inc.

package com.cloudera.director.spi.tck.util;

import static org.junit.Assert.assertEquals;

import com.cloudera.director.spi.v1.model.ConfigurationProperty;
import com.cloudera.director.spi.v1.model.LocalizationContext;
import com.cloudera.director.spi.v1.model.util.DefaultLocalizationContext;
import com.cloudera.director.spi.v1.model.util.SimpleConfigurationPropertyBuilder;
import com.cloudera.director.spi.v1.util.ConfigurationPropertiesUtil;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigSyntax;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ConfigFragmentWrapperTest {

  private static final ConfigParseOptions CONFIG_PARSER_OPTIONS = ConfigParseOptions.defaults()
      .setSyntax(ConfigSyntax.CONF)
      .setAllowMissing(false);

  private static final LocalizationContext LOCALIZATION_CONTEXT =
      new DefaultLocalizationContext(Locale.getDefault(), "");

  // Fully qualifying class name due to compiler bug
  public static enum TestPropertyToken
      implements com.cloudera.director.spi.v1.model.ConfigurationPropertyToken {

    TEST_REQUIRED(new SimpleConfigurationPropertyBuilder()
        .configKey("test.required")
        .required(true)
        .defaultDescription("A required property")
        .build()),
    TEST_SENSITIVE(new SimpleConfigurationPropertyBuilder()
        .configKey("test.sensitive")
        .required(true)
        .defaultDescription("A sensitive property")
        .sensitive(true)
        .build()),
    TEST_OPTIONAL(new SimpleConfigurationPropertyBuilder()
        .configKey("test.optional")
        .defaultValue("the default")
        .defaultDescription("An optional property")
        .build()),
    TEST_MISSING(new SimpleConfigurationPropertyBuilder()
        .configKey("test.missing")
        .required(true)
        .defaultDescription("A required property that will not be set")
        .build());

    private final ConfigurationProperty configurationProperty;

    TestPropertyToken(ConfigurationProperty configurationProperty) {
      this.configurationProperty = configurationProperty;
    }

    @Override
    public ConfigurationProperty unwrap() {
      return configurationProperty;
    }
  }

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testEmptyConfig() {
    ConfigFragmentWrapper wrapper = new ConfigFragmentWrapper(ConfigFactory.empty());
    assertEquals(0, wrapper.getConfiguration(LOCALIZATION_CONTEXT).size());
  }

  @Test
  public void testMixOfProperties() {
    Config config = ConfigFactory
        .parseResourcesAnySyntax("test.conf", CONFIG_PARSER_OPTIONS).resolve();

    ConfigFragmentWrapper wrapper = new ConfigFragmentWrapper(config,
        ConfigurationPropertiesUtil.asConfigurationPropertyList(TestPropertyToken.values()));

    Map<String, String> expectedMap = new HashMap<String, String>();
    expectedMap.put("test.required", "required-1");
    expectedMap.put("test.sensitive", "password");
    expectedMap.put("test.optional", "the default");

    assertEquals(expectedMap, wrapper.getConfiguration(LOCALIZATION_CONTEXT));
    assertEquals("required-1", wrapper.getConfigurationValue(
        TestPropertyToken.TEST_REQUIRED, LOCALIZATION_CONTEXT));
  }

  @Test
  public void testMissingRequiredProperty() {
    Config config = ConfigFactory
        .parseResourcesAnySyntax("test.conf", CONFIG_PARSER_OPTIONS).resolve();

    ConfigFragmentWrapper wrapper = new ConfigFragmentWrapper(config,
        ConfigurationPropertiesUtil.asConfigurationPropertyList(TestPropertyToken.values()));

    thrown.expect(NoSuchElementException.class);
    wrapper.getConfigurationValue(TestPropertyToken.TEST_MISSING, LOCALIZATION_CONTEXT);
  }
}
