package io.jenkins.plugins.restlistparam.logic;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.util.FormValidation;
import io.jenkins.plugins.restlistparam.Messages;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.ResultContainer;
import io.jenkins.plugins.restlistparam.util.HTTPHeaders;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class RestValueService {
  private static final Logger log = Logger.getLogger(RestValueService.class.getName());
  private static final OkHttpClient client = new OkHttpClient();

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
   * @param restEndpoint A http/https web address to the REST/Web endpoint
   * @param credentials  The credentials required to access said endpoint
   * @param mimeType     The MIME type of the expected REST/Web response
   * @param expression   The Json-Path or xPath expression to filter the values
   * @param filter       additional regex filter on any parsed values
   * @return A {@link ResultContainer} that capsules either the desired values or a user friendly error message.
   */
  public static ResultContainer<List<String>> get(final String restEndpoint,
                                                  final StandardCredentials credentials,
                                                  final MimeType mimeType,
                                                  final String expression,
                                                  final String filter)
  {
    ResultContainer<List<String>> valueList = new ResultContainer<>(Collections.emptyList());
    ResultContainer<String> rawValues = getValueStringFromRestEndpoint(restEndpoint, credentials, mimeType);
    Optional<String> rawValueError = rawValues.getErrorMsg();

    if (!rawValueError.isPresent()) {
      valueList = convertToValuesList(mimeType, rawValues.getValue(), expression);
    }
    else {
      valueList.setErrorMsg(rawValueError.get());
    }

    if (!valueList.getErrorMsg().isPresent()
      && StringUtils.isNotBlank(filter)
      && !filter.equalsIgnoreCase(".*"))
    {
      valueList = filterValues(valueList.getValue(), filter);
    }

    return valueList;
  }

  /**
   * A basic validation method usable for configuration validation
   *
   * @param restEndpoint A http/https web address to the REST/Web endpoint
   * @param credentials  The credentials required to access said endpoint
   * @return A {@link FormValidation} to be used in the Jenkins configuration UI
   */
  public static FormValidation doBasicValidation(final String restEndpoint,
                                                 final StandardCredentials credentials)
  {
    Request.Builder builder = new Request.Builder()
      .url(restEndpoint);

    if (credentials != null) {
      builder.addHeader(HTTPHeaders.AUTHORIZATION, buildAuthTypeWithCredential(credentials));
    }

    try (Response response = client.newCall(builder.build()).execute()) {
      int statusCode = response.code();
      if (statusCode < 400) {
        return FormValidation.ok();
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
   * Performs the REST/Web request.
   *
   * @param restEndpoint A http/https web address to the REST/Web endpoint
   * @param credentials  The credentials required to access said endpoint
   * @param mimeType     The MIME type of the expected REST/Web response
   * @return A {@link ResultContainer} capsuling either the response body string in the desired {@link MimeType} or an error message
   */
  private static ResultContainer<String> getValueStringFromRestEndpoint(final String restEndpoint,
                                                                        final StandardCredentials credentials,
                                                                        final MimeType mimeType)
  {
    ResultContainer<String> container = new ResultContainer<>("");

    Request request = new Request.Builder()
      .url(restEndpoint)
      .headers(buildHeaders(credentials, mimeType))
      .build();

    try (Response response = client.newCall(request).execute()) {
      int statusCode = response.code();
      if (statusCode < 400) {
        container.setValue(response.body() != null ? response.body().string() : "");
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
                                      final MimeType mimeType)
  {
    Headers.Builder headBuilder = new Headers.Builder()
      .add(HTTPHeaders.ACCEPT, mimeType.getMime());

    if (credentials != null) {
      headBuilder.add(HTTPHeaders.AUTHORIZATION, buildAuthTypeWithCredential(credentials));
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
   * @param expression  The Json-Path or xPath expression to apply on the {@code valueString}
   * @return A {@link ResultContainer} capsuling the results of the applied expression or an error message
   */
  private static ResultContainer<List<String>> convertToValuesList(final MimeType mimeType,
                                                                   final String valueString,
                                                                   final String expression)
  {
    ResultContainer<List<String>> container;

    switch (mimeType) {
      case APPLICATION_JSON:
        container = ValueResolver.resolveJsonPath(valueString, expression);
        break;
      case APPLICATION_XML:
        container = ValueResolver.resolveXPath(valueString, expression);
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + mimeType);
    }

    return container;
  }

  /**
   * Apply a simple regex filter on a list of strings
   *
   * @param values The list of string values
   * @param filter The regex expression string
   * @return A {@link ResultContainer} capsuling a filtered string list or a user friendly error message
   */
  private static ResultContainer<List<String>> filterValues(final List<String> values,
                                                            final String filter)
  {
    ResultContainer<List<String>> container = new ResultContainer<>(Collections.emptyList());

    try {
      List<String> filteredValues = values.stream()
                                          .filter(value -> value.matches(filter))
                                          .collect(Collectors.toList());
      if (!filteredValues.isEmpty()) {
        container.setValue(filteredValues);
      }
      else {
        container.setErrorMsg(Messages.RLP_RestValueService_info_FilterReturnedNoValues(filter));
      }
    }
    catch (Exception ex) {
      log.warning(Messages.RLP_RestValueService_warn_FilterErr());
      container.setErrorMsg(Messages.RLP_RestValueService_warn_FilterErr());
      log.fine(EX_CLASS + ex.getClass().getName() + '\n'
                 + EX_MESSAGE + ex.getMessage());
    }

    return container;
  }

}