package io.jenkins.plugins.restlistparam.model;

import okhttp3.HttpUrl;

/**
 * Request metadata of a failed fetch, shown to users who can configure the job. It never holds the response
 * body, credentials, header values or the URL's query string and user info.
 */
public final class FetchErrorDetails {
  private final String url;
  private final Integer page;
  private final String cause;
  private final long durationMs;

  /**
   * @param url        The requested URL; it is stored without query string, fragment and user info
   * @param page       The page on which the fetch failed, or {@code null} when the failure is not tied to a page
   * @param cause      The HTTP status code or the exception's simple name, or {@code null} when there is neither
   * @param durationMs How long the fetch took until it failed
   */
  public FetchErrorDetails(final String url, final Integer page, final String cause, final long durationMs) {
    this.url = sanitizeUrl(url);
    this.page = page;
    this.cause = cause;
    this.durationMs = Math.max(0, durationMs);
  }

  /**
   * @return {@code url} without query string, fragment and user info
   */
  public static String sanitizeUrl(final String url) {
    if (url == null) {
      return null;
    }
    HttpUrl parsed = HttpUrl.parse(url);
    if (parsed != null) {
      return parsed.newBuilder().username("").password("").query(null).fragment(null).build().toString();
    }
    String stripped = url.replaceFirst("[?#].*$", "");
    return stripped.replaceFirst("^([A-Za-z][A-Za-z0-9+.-]*://)[^/]*@", "$1");
  }

  public String getUrl() {
    return url;
  }

  public Integer getPage() {
    return page;
  }

  public String getCause() {
    return cause;
  }

  public long getDurationMs() {
    return durationMs;
  }
}
