// Copyright (c) 2015 Cloudera, Inc.

package com.cloudera.director.spi.tck.util;

public final class Preconditions {

  private Preconditions() {
  }

  /**
   * Check if the first argument is null or not
   *
   * @return the argument unchanged if not null
   * @throws java.lang.NullPointerException if the argument is null
   */
  public static <T> T checkNotNull(T any, String message) {
    if (any == null) {
      throw new NullPointerException(message);
    }
    return any;
  }
}
