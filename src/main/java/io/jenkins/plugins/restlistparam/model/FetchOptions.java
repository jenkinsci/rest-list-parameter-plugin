package io.jenkins.plugins.restlistparam.model;

import io.jenkins.plugins.restlistparam.RestListParameterGlobalConfig;
import jenkins.model.Jenkins;

/**
 * How one fetch of a parameter's entries is made: how long it may take in total, across all its pages, and
 * whether it is forced to reach the endpoint instead of using cached responses.
 */
public final class FetchOptions {
  /** The fetch timeout when no Jenkins instance is available, matching the global default. */
  static final int DEFAULT_TIMEOUT_SECONDS = 60;

  private final int timeoutSeconds;
  private final boolean forced;

  /**
   * @param timeoutSeconds How many seconds the whole fetch may take; non-positive means the default
   * @param forced         Whether every page request bypasses the HTTP response cache
   */
  public FetchOptions(final int timeoutSeconds, final boolean forced) {
    this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
    this.forced = forced;
  }

  /**
   * @return A normal (not forced) fetch limited by the global fetch timeout
   */
  public static FetchOptions defaults() {
    return withGlobalTimeout(false);
  }

  /**
   * @param forced Whether the fetch bypasses the HTTP response cache
   * @return A fetch limited by the global fetch timeout
   */
  public static FetchOptions withGlobalTimeout(final boolean forced) {
    int timeout = Jenkins.getInstanceOrNull() != null
      ? RestListParameterGlobalConfig.get().getFetchTimeout()
      : DEFAULT_TIMEOUT_SECONDS;
    return new FetchOptions(timeout, forced);
  }

  public int getTimeoutSeconds() {
    return timeoutSeconds;
  }

  public boolean isForced() {
    return forced;
  }
}
