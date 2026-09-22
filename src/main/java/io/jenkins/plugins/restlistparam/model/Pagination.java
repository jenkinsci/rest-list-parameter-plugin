package io.jenkins.plugins.restlistparam.model;

import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.util.FormValidation;
import io.jenkins.plugins.restlistparam.Messages;
import jenkins.model.Jenkins;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import java.io.Serializable;
import java.util.Optional;

/**
 * How a parameter follows a paginated REST API: given one fetched page, a strategy tells where the next
 * page is. The fetch loop, the page limit and the origin and loop guards live in
 * {@link io.jenkins.plugins.restlistparam.logic.RestValueService}.
 */
public abstract class Pagination extends AbstractDescribableImpl<Pagination> implements Serializable {
  private static final long serialVersionUID = 1L;

  public static final int DEFAULT_MAX_PAGES = 10;
  public static final int MIN_MAX_PAGES = 1;
  public static final int MAX_MAX_PAGES = 100;

  // stored as entered, so an out-of-range value keeps showing its configuration error
  private Integer maxPages;

  public int getMaxPages() {
    return maxPages != null ? maxPages : DEFAULT_MAX_PAGES;
  }

  @DataBoundSetter
  public void setMaxPages(final int maxPages) {
    this.maxPages = maxPages;
  }

  /**
   * @return The page limit actually applied: {@link #getMaxPages()} clamped to 1..100
   */
  public int getEffectiveMaxPages() {
    return Math.max(MIN_MAX_PAGES, Math.min(MAX_MAX_PAGES, getMaxPages()));
  }

  /**
   * Finds the next page to request after {@code page}.
   *
   * @param page The page just fetched
   * @return The next page, or empty when {@code page} is the last one
   */
  public abstract Optional<NextPage> next(Page page);

  /**
   * The error that makes this strategy unusable with {@code mimeType} at fetch time, if any.
   * Configuration mistakes that can still be fetched (such as an out-of-range page limit) are not errors here.
   */
  public Optional<String> incompatibilityWith(final MimeType mimeType) {
    return Optional.empty();
  }

  /**
   * Runs the same checks as the configuration form's field checks, for settings that were never saved.
   *
   * @param mimeType The parameter's MIME type
   * @return The first failing check, or {@link FormValidation#ok()}
   */
  public FormValidation validate(final MimeType mimeType) {
    return checkMaxPages(String.valueOf(getMaxPages()));
  }

  public static FormValidation checkMaxPages(final String value) {
    try {
      int pages = Integer.parseInt(value != null ? value.trim() : "");
      if (pages >= MIN_MAX_PAGES && pages <= MAX_MAX_PAGES) {
        return FormValidation.ok();
      }
    }
    catch (NumberFormatException ignored) {
      // reported below
    }
    return FormValidation.error(Messages.RLP_Pagination_ValidationErr_MaxPages());
  }

  static void checkConfigurePermission(final Item context) {
    if (context == null) {
      Jenkins.get().checkPermission(Jenkins.ADMINISTER);
    }
    else {
      context.checkPermission(Item.CONFIGURE);
    }
  }

  /**
   * A fetched page as seen by a strategy.
   */
  public static final class Page {
    private final HttpUrl endpoint;
    private final HttpUrl requestUrl;
    private final Headers headers;
    private final String body;

    /**
     * @param endpoint   The configured REST endpoint
     * @param requestUrl The URL that returned this page, after redirects
     * @param headers    The response headers
     * @param body       The raw response body
     */
    public Page(final HttpUrl endpoint, final HttpUrl requestUrl, final Headers headers, final String body) {
      this.endpoint = endpoint;
      this.requestUrl = requestUrl;
      this.headers = headers;
      this.body = body;
    }

    public HttpUrl getEndpoint() {
      return endpoint;
    }

    public HttpUrl getRequestUrl() {
      return requestUrl;
    }

    public Headers getHeaders() {
      return headers;
    }

    public String getBody() {
      return body;
    }
  }

  /**
   * Where the next page is: its URL and, for token based strategies, the token (used to detect loops).
   */
  public static final class NextPage {
    private final HttpUrl url;
    // the server's page cursor, held in memory for one fetch only: NextPage is never serialized
    @SuppressWarnings("lgtm[jenkins/plaintext-storage]")
    private final String token;

    public NextPage(final HttpUrl url, final String token) {
      this.url = url;
      this.token = token;
    }

    public HttpUrl getUrl() {
      return url;
    }

    /**
     * @return The continuation token, or {@code null} for strategies without one
     */
    public String getToken() {
      return token;
    }
  }

  public abstract static class PaginationDescriptor extends Descriptor<Pagination> {
    public int getDefaultMaxPages() {
      return DEFAULT_MAX_PAGES;
    }

    @POST
    public FormValidation doCheckMaxPages(@AncestorInPath final Item context,
                                          @QueryParameter final String value)
    {
      checkConfigurePermission(context);
      return checkMaxPages(value);
    }
  }
}
