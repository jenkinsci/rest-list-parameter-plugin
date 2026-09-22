package io.jenkins.plugins.restlistparam.logic;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.util.FormValidation;
import io.jenkins.plugins.restlistparam.Messages;
import io.jenkins.plugins.restlistparam.model.ValueItem;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.Pagination;
import io.jenkins.plugins.restlistparam.model.ResultContainer;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import io.jenkins.plugins.restlistparam.util.HTTPHeaders;
import io.jenkins.plugins.restlistparam.util.OkHttpUtils;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RestValueService {
  private static final Logger log = Logger.getLogger(RestValueService.class.getName());

  private static final String EX_CLASS = "Exception Class: ";
  private static final String EX_MESSAGE = "Exception Message: ";

  private RestValueService() {
    throw new IllegalStateException("Static Logic class");
  }

  /**
   * Returns a {@link ResultContainer} capsuling a optional String error message and a list of parsed string values.
   * <p>
   * This method uses its parameters to query a REST/Web endpoint to receive a {@link MimeType} response, which then
   * gets parsed with a supported Path expression to extract a list of string values.
   *
   * @param restEndpoint      A http/https web address to the REST/Web endpoint
   * @param credentials       The credentials required to access said endpoint
   * @param mimeType          The MIME type of the expected REST/Web response
   * @param cacheTime         Time for how long the REST response gets cached for in minutes
   * @param valueExpression   The Json-Path or xPath expression to filter the values
   * @param displayExpression The Json-Path or xPath expression to filter the display values
   * @param filter            additional regex filter on any parsed values
   * @param order             Set a {@link ValueOrder} to optionally reorder the values
   * @return A {@link ResultContainer} that capsules either the desired values or a user friendly error message.
   */
  public static ResultContainer<List<ValueItem>> get(final String restEndpoint,
                                                     final StandardCredentials credentials,
                                                     final MimeType mimeType,
                                                     final Integer cacheTime,
                                                     final String valueExpression,
                                                     final String displayExpression,
                                                     final String filter,
                                                     final ValueOrder order)
  {
    return get(restEndpoint, credentials, mimeType, cacheTime, valueExpression, displayExpression, filter, order,
      Collections.emptyMap());
  }

  public static ResultContainer<List<ValueItem>> get(final String restEndpoint,
                                                     final StandardCredentials credentials,
                                                     final MimeType mimeType,
                                                     final Integer cacheTime,
                                                     final String valueExpression,
                                                     final String displayExpression,
                                                     final String filter,
                                                     final ValueOrder order,
                                                     final Map<String, String> customHeaders)
  {
    return get(restEndpoint, credentials, mimeType, cacheTime, valueExpression, displayExpression, filter, order,
      customHeaders, null);
  }

  /**
   * Like {@link #get(String, StandardCredentials, MimeType, Integer, String, String, String, ValueOrder)}, but sends
   * {@code customHeaders} with every request and, when {@code pagination} is set, follows the endpoint's pages.
   * <p>
   * The value and display expressions are applied to each page on its own, the entries are joined in page order,
   * and the filter and order are applied once to the combined list. A failure on any page fails the whole fetch.
   *
   * @param customHeaders Additional headers sent with every request
   * @param pagination    How to find the next page, or {@code null} to send a single request
   * @return A {@link ResultContainer} with the values or a user friendly error message, and the number of pages
   * fetched and whether the page limit was reached
   */
  public static ResultContainer<List<ValueItem>> get(final String restEndpoint,
                                                     final StandardCredentials credentials,
                                                     final MimeType mimeType,
                                                     final Integer cacheTime,
                                                     final String valueExpression,
                                                     final String displayExpression,
                                                     final String filter,
                                                     final ValueOrder order,
                                                     final Map<String, String> customHeaders,
                                                     final Pagination pagination)
  {
    ResultContainer<List<ValueItem>> valueList = getValuesFromAllPages(restEndpoint, credentials, mimeType, cacheTime,
      valueExpression, displayExpression, customHeaders, pagination);

    if (!valueList.getErrorMsg().isPresent() && isFilterOrOrderSet(filter, order)) {
      ResultContainer<List<ValueItem>> filtered = filterAndSortValues(valueList.getValue(), filter, order);
      filtered.setPagesFetched(valueList.getPagesFetched());
      filtered.setPageLimitReached(valueList.isPageLimitReached());
      valueList = filtered;
    }

    return valueList;
  }

  /**
   * Fetches the first page and, with {@code pagination}, every following page up to the page limit, and extracts
   * the values of each page.
   *
   * @return The entries of all pages in page order (not yet filtered or ordered) or the first error encountered
   */
  private static ResultContainer<List<ValueItem>> getValuesFromAllPages(final String restEndpoint,
                                                                        final StandardCredentials credentials,
                                                                        final MimeType mimeType,
                                                                        final Integer cacheTime,
                                                                        final String valueExpression,
                                                                        final String displayExpression,
                                                                        final Map<String, String> customHeaders,
                                                                        final Pagination pagination)
  {
    ResultContainer<List<ValueItem>> container = new ResultContainer<>(Collections.emptyList());

    if (pagination != null) {
      Optional<String> incompatible = pagination.incompatibilityWith(mimeType);
      if (incompatible.isPresent()) {
        log.warning(incompatible.get());
        container.setErrorMsg(incompatible.get());
        return container;
      }
    }

    List<ValueItem> items = new ArrayList<>();
    String noValuesMsg = null;
    Set<HttpUrl> visitedUrls = new HashSet<>();
    Set<String> usedTokens = new HashSet<>();
    HttpUrl endpointUrl = null;
    String url = restEndpoint;
    int page = 1;

    while (true) {
      ResultContainer<PageResponse> fetched = getPageFromRestEndpoint(url, credentials, mimeType, cacheTime,
        customHeaders);
      if (fetched.getErrorMsg().isPresent()) {
        container.setErrorMsg(onPage(fetched.getErrorMsg().get(), page));
        return container;
      }
      PageResponse response = fetched.getValue();

      ResultContainer<List<ValueItem>> pageValues = convertToValuesList(mimeType, response.getBody(), valueExpression,
        displayExpression);
      if (pageValues.isNoValues()) {
        // an empty page is fine, only an empty combined result counts as "no values"
        noValuesMsg = pageValues.getErrorMsg().orElse(null);
      }
      else if (pageValues.getErrorMsg().isPresent()) {
        container.setErrorMsg(onPage(pageValues.getErrorMsg().get(), page));
        return container;
      }
      else {
        items.addAll(pageValues.getValue());
      }
      container.setPagesFetched(page);

      if (pagination == null) {
        break;
      }
      if (endpointUrl == null) {
        // the first request succeeded, so the endpoint is a valid URL
        endpointUrl = HttpUrl.get(restEndpoint);
      }
      visitedUrls.add(HttpUrl.get(url));

      Optional<Pagination.NextPage> next = pagination.next(new Pagination.Page(endpointUrl, response.getRequestUrl(),
        response.getHeaders(), response.getBody()));
      if (!next.isPresent()) {
        break;
      }
      HttpUrl nextUrl = next.get().getUrl();
      String token = next.get().getToken();
      // checked before the page limit, so a foreign link on the last allowed page is still reported
      if (!isSameOrigin(endpointUrl, nextUrl)) {
        String origin = nextUrl.scheme() + "://" + nextUrl.host() + ":" + nextUrl.port();
        log.warning(Messages.RLP_RestValueService_err_ForeignOrigin(origin));
        container.setErrorMsg(Messages.RLP_RestValueService_err_ForeignOrigin(origin));
        return container;
      }
      if (visitedUrls.contains(nextUrl) || (token != null && usedTokens.contains(token))) {
        log.warning(Messages.RLP_RestValueService_warn_RepeatedPage(page));
        break;
      }
      if (page >= pagination.getEffectiveMaxPages()) {
        log.warning(Messages.RLP_RestValueService_warn_PageLimitReached(page));
        container.setPageLimitReached(true);
        break;
      }
      if (token != null) {
        usedTokens.add(token);
      }
      url = nextUrl.toString();
      ++page;
    }

    if (items.isEmpty() && noValuesMsg != null) {
      container.setNoValues(noValuesMsg);
    }
    else {
      container.setValue(items);
    }
    return container;
  }

  private static boolean isSameOrigin(final HttpUrl endpoint, final HttpUrl other) {
    // HttpUrl normalizes scheme and host to lower case and fills in the scheme's default port
    return endpoint.scheme().equals(other.scheme())
      && endpoint.host().equals(other.host())
      && endpoint.port() == other.port();
  }

  /**
   * Names the page an error occurred on; errors on the first page are kept unchanged.
   */
  private static String onPage(final String errorMsg, final int page) {
    return page >= 2 ? Messages.RLP_RestValueService_err_OnPage(errorMsg, page) : errorMsg;
  }

  /**
   * A basic validation method usable for configuration validation.
   * <p>
   * Sends the same {@code Accept} and credential headers as a build, but no custom headers.
   * A 401 or 403 is therefore reported as a warning pointing at Test Configuration.
   *
   * @param restEndpoint A http/https web address to the REST/Web endpoint
   * @param credentials  The credentials required to access said endpoint
   * @param mimeType     The MIME type of the expected REST/Web response
   * @return A {@link FormValidation} to be used in the Jenkins configuration UI
   */
  public static FormValidation doBasicValidation(final String restEndpoint,
                                                 final StandardCredentials credentials,
                                                 final MimeType mimeType)
  {
    OkHttpClient client = OkHttpUtils.getClientWithProxyAndCache(restEndpoint);
    // don't cache the validation response
    Request.Builder builder = new Request.Builder()
      .cacheControl(OkHttpUtils.getCacheControl(0))
      .url(restEndpoint)
      .headers(buildHeaders(credentials, mimeType != null ? mimeType : MimeType.APPLICATION_JSON,
        Collections.emptyMap()));

    try (Response response = client.newCall(builder.build()).execute()) {
      int statusCode = response.code();
      if (statusCode < 400) {
        return FormValidation.ok();
      }
      else if (statusCode == 401 || statusCode == 403) {
        return FormValidation.warning(Messages.RLP_RestValueService_warn_AuthRejected(statusCode));
      }
      else if (statusCode < 500) {
        return FormValidation.error(Messages.RLP_RestValueService_warn_ReqClientErr(statusCode));
      }
      else {
        return FormValidation.error(Messages.RLP_RestValueService_warn_ReqServerErr(statusCode));
      }
    }
    catch (UnknownHostException ex) {
      return FormValidation.error(Messages.RLP_RestValueService_warn_UnknownHost(ex.getMessage()));
    }
    catch (IOException ex) {
      return FormValidation.error(Messages.RLP_RestValueService_warn_OkHttpErr(ex.getClass().getName()));
    }
  }

  /**
   * Performs the REST/Web request for one page.
   *
   * @param url          A http/https web address of the page to request
   * @param credentials  The credentials required to access said endpoint
   * @param mimeType     The MIME type of the expected REST/Web response
   * @param cacheTime    Time for how long the REST response gets cached for in minutes
   * @return A {@link ResultContainer} capsuling either the response in the desired {@link MimeType} or an error message
   */
  private static ResultContainer<PageResponse> getPageFromRestEndpoint(final String url,
                                                                       final StandardCredentials credentials,
                                                                       final MimeType mimeType,
                                                                       final Integer cacheTime,
                                                                       final Map<String, String> customHeaders)
  {
    ResultContainer<PageResponse> container = new ResultContainer<>(null);

    OkHttpClient client = OkHttpUtils.getClientWithProxyAndCache(url);
    Request request = new Request.Builder()
      .url(url)
      .cacheControl(OkHttpUtils.getCacheControl(cacheTime))
      .headers(buildHeaders(credentials, mimeType, customHeaders))
      .build();

    try (Response response = client.newCall(request).execute()) {
      int statusCode = response.code();
      if (statusCode < 400) {
        String value = "";
        okhttp3.ResponseBody body = response.body();
        if (body != null)
          value = body.string();
        container.setValue(new PageResponse(value, response.headers(), response.request().url()));
      }
      else if (statusCode < 500) {
        log.warning(Messages.RLP_RestValueService_warn_ReqClientErr(statusCode));
        container.setErrorMsg(Messages.RLP_RestValueService_warn_ReqClientErr(statusCode));
      }
      else {
        log.warning(Messages.RLP_RestValueService_warn_ReqServerErr(statusCode));
        container.setErrorMsg(Messages.RLP_RestValueService_warn_ReqServerErr(statusCode));
      }
    }
    catch (UnknownHostException ex) {
      log.warning(Messages.RLP_RestValueService_warn_UnknownHost(ex.getMessage()));
      container.setErrorMsg(Messages.RLP_RestValueService_warn_UnknownHost(ex.getMessage()));
    }
    catch (IOException ex) {
      log.warning(Messages.RLP_RestValueService_warn_OkHttpErr(ex.getClass().getName()));
      container.setErrorMsg(Messages.RLP_RestValueService_warn_OkHttpErr(ex.getClass().getName()));
      log.fine(EX_CLASS + ex.getClass().getName() + '\n'
                 + EX_MESSAGE + ex.getMessage());
    }

    return container;
  }

  /**
   * A fetched page: the body read in full, so the OkHttp response can be closed right away.
   */
  private static final class PageResponse {
    private final String body;
    private final Headers headers;
    private final HttpUrl requestUrl;

    private PageResponse(final String body, final Headers headers, final HttpUrl requestUrl) {
      this.body = body;
      this.headers = headers;
      this.requestUrl = requestUrl;
    }

    String getBody() {
      return body;
    }

    Headers getHeaders() {
      return headers;
    }

    /** The URL that returned this page, after redirects. */
    HttpUrl getRequestUrl() {
      return requestUrl;
    }
  }

  /**
   * Builds the OKHttp Headers that should get applied to the request.
   * <p>
   * Sets a <em>ACCEPT</em> header and optionally a <em>AUTHORIZATION</em> header.
   * The <em>AUTHORIZATION</em> header gets set to <em>BASIC</em> or <em>BEARER</em> depending on credential type supplied.
   * <p>
   * Currently supported credential types are {@link StandardUsernamePasswordCredentials} for BASIC and
   * {@link StringCredentials} for BEARER <em>AUTHORIZATION</em>.
   *
   * @param credentials null or the credentials to use for the <em>AUTHORIZATION</em> header
   * @param mimeType    The MIME time that should be set in the <em>ACCEPT</em> header
   * @return OKHttp headers to be applied to the REST/Web request
   */
  private static Headers buildHeaders(final StandardCredentials credentials,
                                      final MimeType mimeType,
                                      final Map<String, String> customHeaders)
  {
    Headers.Builder headBuilder = new Headers.Builder()
      .add(HTTPHeaders.ACCEPT, mimeType.getMime());

    if (credentials != null) {
      String authorization = buildAuthTypeWithCredential(credentials);
      if (!authorization.isEmpty()) {
        headBuilder.add(HTTPHeaders.AUTHORIZATION, authorization);
      }
    }

    if (customHeaders != null) {
      for (Map.Entry<String, String> header : customHeaders.entrySet()) {
        if (header.getKey() != null && !header.getKey().trim().isEmpty()
          && header.getValue() != null && !header.getValue().isEmpty())
        {
          try {
            headBuilder.set(header.getKey().trim(), header.getValue());
          }
          catch (IllegalArgumentException ex) {
            log.fine(Messages.RLP_RestValueService_fine_IgnoringInvalidCustomHeader(header.getKey()));
          }
        }
      }
    }

    return headBuilder.build();
  }

  /**
   * Helper method to determine <em>AUTHORIZATION</em> header Auth-type and credential
   *
   * @param credentials Credentials for use in <em>AUTHORIZATION</em> header
   * @return Value to be used in the <em>AUTHORIZATION</em> header or empty string
   */
  private static String buildAuthTypeWithCredential(final StandardCredentials credentials) {
    String authTypeWithCredential = "";

    if (credentials instanceof StandardUsernamePasswordCredentials) {
      log.fine(Messages.RLP_RestValueService_fine_UsingBasicAuth());
      StandardUsernamePasswordCredentials cred = (StandardUsernamePasswordCredentials) credentials;
      String uNameAndPasswd = cred.getUsername() + ":" + cred.getPassword().getPlainText();
      authTypeWithCredential = "Basic " + Base64.getEncoder()
                                                .encodeToString(uNameAndPasswd.getBytes(StandardCharsets.UTF_8));
    }
    else if (credentials instanceof StringCredentials) {
      log.fine(Messages.RLP_RestValueService_fine_UsingBearerAuth());
      StringCredentials cred = (StringCredentials) credentials;
      authTypeWithCredential = "Bearer " + cred.getSecret().getPlainText();
    }
    else {
      log.warning(Messages.RLP_RestValueService_warn_UnsupportedCredential(credentials.getClass().getName()));
    }

    return authTypeWithCredential;
  }

  /**
   * Converts a {@code valueString} of a given {@link MimeType} to a string list based on the values parsed from the expression
   *
   * @param mimeType    The {@link MimeType} of the {@code valueString}
   * @param valueString The value string to be parsed
   * @param valueExpression  The Json-Path or xPath expression to apply on the {@code valueString}
   * @param displayExpression Derives the value to be displayed to the user parsed by value expression
   * @return A {@link ResultContainer} capsuling the results of the applied expression or an error message
   */
  private static ResultContainer<List<ValueItem>> convertToValuesList(final MimeType mimeType,
                                                                      final String valueString,
                                                                      final String valueExpression,
                                                                      final String displayExpression)
  {
    ResultContainer<List<ValueItem>> container;

    switch (mimeType) {
      case APPLICATION_JSON:
        container = ValueResolver.resolveJsonPath(valueString, valueExpression, displayExpression);
        break;
      case APPLICATION_XML:
        container = ValueResolver.resolveXPath(valueString, valueExpression, displayExpression);
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + mimeType);
    }

    return container;
  }

  /**
   * Apply a simple regex filter and/or order on a list of strings
   *
   * @param values The list of string values
   * @param filter The regex expression string (if any)
   * @param order  The Order to apply (if any)
   * @return A {@link ResultContainer} capsuling a filtered string list or a user friendly error message
   */
  private static ResultContainer<List<ValueItem>> filterAndSortValues(final List<ValueItem> values,
                                                                      final String filter,
                                                                      final ValueOrder order)
  {
    ResultContainer<List<ValueItem>> container = new ResultContainer<>(Collections.emptyList());

    try {
      List<ValueItem> updatedValues;

      Stream<ValueItem> valueStream = values.stream();
      if (isFilterSet(filter)){
        valueStream = valueStream.filter(value -> value.getValue().matches(filter));
      }

      if (order == ValueOrder.ASC) {
        valueStream = valueStream.sorted(Comparator.naturalOrder());
      } else if (order == ValueOrder.DSC) {
        valueStream = valueStream.sorted(Comparator.reverseOrder());
      }

      updatedValues = valueStream.collect(Collectors.toList());
      if (!updatedValues.isEmpty()) {
        if (order == ValueOrder.REV){
          Collections.reverse(updatedValues);
        }
        container.setValue(updatedValues);
      } else {
        container.setNoValues(Messages.RLP_RestValueService_info_FilterReturnedNoValues(filter));
      }
    }
    catch (Exception ex) {
      log.warning(Messages.RLP_RestValueService_warn_FilterErr(ex.getClass().getName()));
      container.setErrorMsg(Messages.RLP_RestValueService_warn_FilterErr(ex.getClass().getName()));
      log.fine(EX_CLASS + ex.getClass().getName() + '\n'
                 + EX_MESSAGE + ex.getMessage());
    }

    return container;
  }

  // Just some helper functions to de-clutter if conditions
  private static boolean isFilterOrOrderSet(String filter, ValueOrder order) {
    return isFilterSet(filter) || isOrderSet(order);
  }

  private static boolean isFilterSet(String filter) {
    return filter != null && !filter.isBlank() && !filter.equalsIgnoreCase(".*");
  }

  private static boolean isOrderSet(ValueOrder order) {
    return order != null && order != ValueOrder.NONE;
  }

}