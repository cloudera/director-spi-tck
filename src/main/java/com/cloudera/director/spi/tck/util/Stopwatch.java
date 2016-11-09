// Copyright (c) 2015 Cloudera, Inc.

package com.cloudera.director.spi.tck.util;

import java.util.concurrent.TimeUnit;

/**
 * A simplified version of the Guava Stopwatch for measuring timeouts.
 */
public class Stopwatch {

  /**
   * Creates a new instance that records the moment when it was created.
   */
  public static Stopwatch createStarted() {
    return new Stopwatch();
  }

  private final long startTick;

  private Stopwatch() {
    this.startTick = System.nanoTime();
  }

  /**
   * Computes the time between stopwatch creation and now.
   */
  public long elapsed(TimeUnit desiredUnit) {
    return desiredUnit.convert(elapsedNanos(), TimeUnit.NANOSECONDS);
  }

  private long elapsedNanos() {
    return System.nanoTime() - startTick;
  }
}
