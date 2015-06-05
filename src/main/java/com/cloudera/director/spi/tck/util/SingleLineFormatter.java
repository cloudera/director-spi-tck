// Copyright (c) 2015 Cloudera, Inc.

package com.cloudera.director.spi.tck.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * A JUL formatter that puts the entire content on a single line
 */
public class SingleLineFormatter extends Formatter {

  private static final String LINE_SEPARATOR = System.getProperty("line.separator");

  // SimpleDateFormat is not thread-safe, so create one to each thread
  private static final ThreadLocal<SimpleDateFormat> FORMATTER =
      new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
          return new SimpleDateFormat("HH:mm:ss");
        }
      };

  @Override
  public String format(LogRecord record) {
    StringBuilder accumulator = new StringBuilder();

    accumulator.append(FORMATTER.get().format(new Date(record.getMillis())))
        .append(" ")
        .append(record.getLevel().getLocalizedName())
        .append(": ")
        .append(formatMessage(record))
        .append(LINE_SEPARATOR);

    if (record.getThrown() != null) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      record.getThrown().printStackTrace(pw);
      pw.close();

      accumulator.append(sw.toString());
    }

    return accumulator.toString();
  }
}
