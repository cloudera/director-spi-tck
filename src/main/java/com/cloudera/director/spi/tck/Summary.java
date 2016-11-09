// Copyright (c) 2015 Cloudera, Inc.

package com.cloudera.director.spi.tck;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An object that collects validation errors and warnings.
 */
public class Summary {

  private List<String> errors = new ArrayList<String>();
  private List<String> warnings = new ArrayList<String>();

  public void add(Summary other) {
    errors.addAll(other.getErrors());
    warnings.addAll(other.getWarnings());
  }

  public void addError(String format, Object... args) {
    errors.add(String.format(format, args));
  }

  public boolean hasErrors() {
    return errors.size() > 0;
  }

  public List<String> getErrors() {
    return Collections.unmodifiableList(errors);
  }

  public void addWarning(String format, Object... args) {
    warnings.add(String.format(format, args));
  }

  public boolean hasWarnings() {
    return warnings.size() > 0;
  }

  public List<String> getWarnings() {
    return Collections.unmodifiableList(warnings);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Summary that = (Summary) o;

    if (!errors.equals(that.errors)) return false;
    if (!warnings.equals(that.warnings)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = errors.hashCode();
    result = 31 * result + warnings.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "Summary{" +
        "errors=" + errors +
        ", warnings=" + warnings +
        '}';
  }
}
