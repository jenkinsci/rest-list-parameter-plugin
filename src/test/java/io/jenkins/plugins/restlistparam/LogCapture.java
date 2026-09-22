package io.jenkins.plugins.restlistparam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Collects the warnings a class logs while open, so tests can assert that a warning was (or was not) logged.
 */
public class LogCapture extends Handler implements AutoCloseable {
  private final Logger logger;
  private final List<LogRecord> records = Collections.synchronizedList(new ArrayList<>());

  public LogCapture(final Class<?> loggingClass) {
    logger = Logger.getLogger(loggingClass.getName());
    logger.addHandler(this);
  }

  /** The messages of the warnings logged so far. */
  public List<String> warnings() {
    synchronized (records) {
      return records.stream()
                    .filter(record -> record.getLevel() == Level.WARNING)
                    .map(LogRecord::getMessage)
                    .collect(Collectors.toList());
    }
  }

  @Override
  public void publish(final LogRecord record) {
    records.add(record);
  }

  @Override
  public void flush() {
    // nothing buffered
  }

  @Override
  public void close() {
    logger.removeHandler(this);
  }
}
