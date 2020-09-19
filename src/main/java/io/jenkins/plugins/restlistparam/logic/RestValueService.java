package io.jenkins.plugins.restlistparam.logic;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
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
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class RestValueService {
  private static final Logger log = Logger.getLogger(RestValueService.class.getName());
  private static final OkHttpClient client = new OkHttpClient();

  private RestValueService()
  {
    throw new IllegalStateException("Static Logic class");
  }

  public static ResultContainer<Collection<String>> get(final String restEndpoint,
                                                        final StandardCredentials credentials,
                                                        final MimeType mimeType,
                                                        final String expression,
                                                        final String filter)
  {
    ResultContainer<Collection<String>> valueCollection = new ResultContainer<>(Collections.emptyList());
    ResultContainer<String> rawValues = getValueStringFromRestEndpoint(restEndpoint, credentials, mimeType);
    Optional<String> rawValueError = rawValues.getErrorMsg();

    if (!rawValueError.isPresent()) {
      valueCollection = convertToValuesCollection(mimeType, rawValues.getValue(), expression);
    }
    else {
      valueCollection.setErrorMsg(rawValueError.get());
    }

    if (!valueCollection.getErrorMsg().isPresent() && StringUtils.isNotBlank(filter)) {
      Collection<String> filteredValues = filterValues(valueCollection.getValue(), filter);
      valueCollection.setValue(filteredValues);
    }

    return valueCollection;
  }

  private static Headers buildHeaders(final StandardCredentials credentials,
                                      final MimeType mimeType)
  {
    Headers.Builder headBuilder = new Headers.Builder()
      .add(HTTPHeaders.ACCEPT, mimeType.getMime());

    if (credentials != null) {
      String authTypeAndCredential = "";

      if (credentials instanceof StandardUsernamePasswordCredentials) {
        log.fine("Using Basic auth type to request REST-Values");
        StandardUsernamePasswordCredentials cred = (StandardUsernamePasswordCredentials) credentials;
        String uNameAndPasswd = cred.getUsername() + ":" + cred.getPassword().getPlainText();
        authTypeAndCredential = "Basic" + Base64.getEncoder()
                                                .encodeToString(uNameAndPasswd.getBytes(StandardCharsets.UTF_8));
      }
      else if (credentials instanceof StringCredentials) {
        log.fine("Using Bearer auth type to request REST-Values");
        StringCredentials cred = (StringCredentials) credentials;
        authTypeAndCredential = "Bearer" + cred.getSecret().getPlainText();
      }
      else {
        log.warning("Attempted to use unknown Credential type: " + credentials.getClass().getName());
      }

      headBuilder.add(HTTPHeaders.AUTHORIZATION, authTypeAndCredential);
    }

    return headBuilder.build();
  }

  private static ResultContainer<String> getValueStringFromRestEndpoint(final String restEndpoint,
                                                                        final StandardCredentials credentials,
                                                                        final MimeType mimeType)
  {
    ResultContainer<String> container = new ResultContainer<>(null);

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
    } catch (UnknownHostException ex) {
      log.warning(Messages.RLP_RestValueService_warn_UnknownHost(ex.getMessage()));
      container.setErrorMsg(Messages.RLP_RestValueService_warn_UnknownHost(ex.getMessage()));
    } catch (IOException ex) {
      log.warning(Messages.RLP_RestValueService_warn_OkHttpErr(ex.getClass().getName()));
      container.setErrorMsg(Messages.RLP_RestValueService_warn_OkHttpErr(ex.getClass().getName()));
      log.fine("Exception Class: " + ex.getClass().getName() + "\n"
                 + "Exception Message: " + ex.getMessage());
    }

    return container;
  }

  private static ResultContainer<Collection<String>> convertToValuesCollection(final MimeType mimeType,
                                                                               final String valueString,
                                                                               final String expression)
  {
    ResultContainer<Collection<String>> container;

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

  private static Collection<String> filterValues(Collection<String> values,
                                                 String filter)
  {
    return values.stream()
                 .filter(value -> value.matches(filter))
                 .collect(Collectors.toList());
  }

}
