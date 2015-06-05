// Copyright (c) 2015 Cloudera, Inc.

package com.cloudera.director.spi.tck.util;

import static junit.framework.TestCase.assertEquals;

import org.junit.Test;

public class ClassReferenceTest {

  @Test
  public void testSimpleClass() {
    ClassReference ref = new ClassReference("com.acme.Something");

    assertEquals("Something", ref.getSimpleClassName());
    assertEquals("com.acme", ref.getPackageName());
  }

  @Test
  public void testClassWithNoPackage() {
    ClassReference ref = new ClassReference("Something");

    assertEquals("Something", ref.getSimpleClassName());
    assertEquals("", ref.getPackageName());
  }

  @Test(expected = NullPointerException.class)
  public void testNullNotAllowed() {
    new ClassReference(null);
  }

  @Test
  public void testInnerClass() {
    ClassReference ref = new ClassReference("com.acme.A$B");

    assertEquals("com.acme", ref.getPackageName());
    assertEquals("B", ref.getSimpleClassName());
  }
}
