// Copyright (c) 2015 Cloudera, Inc.

package com.cloudera.director.spi.tck.util;

import static com.cloudera.director.spi.tck.util.Preconditions.checkNotNull;

/**
 * A wrapper for a canonical class name
 */
public class ClassReference {

  private final String canonicalClassName;

  public ClassReference(String canonicalClassName) {
    this.canonicalClassName =
        checkNotNull(canonicalClassName, "canonicalClassName is null");
  }

  public String getCanonicalClassName() {
    return canonicalClassName;
  }

  public String getSimpleClassName() {
    if (canonicalClassName.contains("$")) {
      return canonicalClassName.substring(canonicalClassName.lastIndexOf("$") + 1);
    } else {
      return canonicalClassName.substring(canonicalClassName.lastIndexOf(".") + 1);
    }
  }

  public String getPackageName() {
    int indexOfLastDot = canonicalClassName.lastIndexOf(".");
    if (indexOfLastDot < 0) {
      return "";
    }
    return canonicalClassName.substring(0, indexOfLastDot);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ClassReference classReference1 = (ClassReference) o;

    if (!canonicalClassName.equals(classReference1.canonicalClassName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return canonicalClassName.hashCode();
  }

  @Override
  public String toString() {
    return "ClassReference{" +
        "canonicalClassName='" + canonicalClassName + '\'' +
        '}';
  }
}
