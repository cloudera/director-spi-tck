// Copyright (c) 2015 Cloudera, Inc.

package com.cloudera.director.spi.tck;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class SummaryTest {

  @Test
  public void testEmptySummary() {
    Summary summary = new Summary();

    assertFalse(summary.hasErrors());
    assertFalse(summary.hasWarnings());
  }

  @Test
  public void testWithErrors() {
    Summary summary = new Summary();
    summary.addError("Test %s", "error");

    assertTrue(summary.hasErrors());
    assertFalse(summary.hasWarnings());

    assertEquals(Arrays.asList("Test error"), summary.getErrors());
  }

  @Test
  public void testWithWarnings() {
    Summary summary = new Summary();
    summary.addWarning("Test %s", "warning");

    assertFalse(summary.hasErrors());
    assertTrue(summary.hasWarnings());

    assertEquals(Arrays.asList("Test warning"), summary.getWarnings());
  }
}
