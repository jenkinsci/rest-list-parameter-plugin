package io.jenkins.plugins.restlistparam.model;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.jayway.jsonpath.PathNotFoundException;
import hudson.Extension;
import hudson.RelativePath;
import hudson.model.Item;
import hudson.util.FormValidation;
import io.jenkins.plugins.restlistparam.Messages;
import io.jenkins.plugins.restlistparam.logic.ValueResolver;
import io.jenkins.plugins.restlistparam.util.PathExpressionValidationUtils;
import okhttp3.HttpUrl;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Reads a continuation token from the Json body and requests the configured endpoint again with the token
 * as a query parameter, as Nexus 3 ({@code continuationToken}) or offset based APIs ({@code nextPageStart}) do.
 */
public class ContinuationTokenPagination extends Pagination {
  private static final long serialVersionUID = 1L;
  private static final Logger log = Logger.getLogger(ContinuationTokenPagination.class.getName());

  // a Json-Path expression such as $.continuationToken that locates the token, not a credential
  @SuppressWarnings("lgtm[jenkins/plaintext-storage]")
  private final String tokenExpression;
  private final String queryParameter;

  @DataBoundConstructor
  public ContinuationTokenPagination(final String tokenExpression, final String queryParameter) {
    this.tokenExpression = tokenExpression != null ? tokenExpression.trim() : "";
    this.queryParameter = queryParameter != null ? queryParameter.trim() : "";
  }

  public String getTokenExpression() {
    return tokenExpression;
  }

  public String getQueryParameter() {
    return queryParameter;
  }

  @Override
  public Optional<NextPage> next(final Page page) {
    Object token;
    try {
      token = JsonPath.parse(page.getBody()).read(tokenExpression);
    }
    catch (PathNotFoundException ignored) {
      return Optional.empty();
    }
    catch (JsonPathException | IllegalArgumentException ex) {
      log.warning(Messages.RLP_Pagination_warn_TokenExpressionErr(tokenExpression, ex.getClass().getName()));
      return Optional.empty();
    }

    final String text;
    if (token instanceof String) {
      text = (String) token;
    }
    else if (token instanceof Number) {
      text = ValueResolver.convertToString(token);
    }
    else {
      if (token != null) {
        log.warning(Messages.RLP_Pagination_warn_TokenNotScalar(tokenExpression, token.getClass().getName()));
      }
      return Optional.empty();
    }
    if (text.isEmpty()) {
      return Optional.empty();
    }

    // replaces any value of the parameter already in the endpoint, keeps the other parameters, encodes the token
    HttpUrl url = page.getEndpoint().newBuilder().setQueryParameter(queryParameter, text).build();
    return Optional.of(new NextPage(url, text));
  }

  @Override
  public Optional<String> incompatibilityWith(final MimeType mimeType) {
    if (mimeType != MimeType.APPLICATION_JSON) {
      return Optional.of(Messages.RLP_ContinuationTokenPagination_ValidationErr_RequiresJson());
    }
    return Optional.empty();
  }

  @Override
  public FormValidation validate(final MimeType mimeType) {
    FormValidation token = checkTokenExpression(tokenExpression, mimeType);
    if (token.kind != FormValidation.Kind.OK) {
      return token;
    }
    FormValidation parameter = checkQueryParameter(queryParameter);
    if (parameter.kind != FormValidation.Kind.OK) {
      return parameter;
    }
    return super.validate(mimeType);
  }

  static FormValidation checkTokenExpression(final String value, final MimeType mimeType) {
    if (mimeType == MimeType.APPLICATION_XML) {
      return FormValidation.error(Messages.RLP_ContinuationTokenPagination_ValidationErr_RequiresJson());
    }
    if (value == null || value.isBlank()) {
      return FormValidation.error(Messages.RLP_ContinuationTokenPagination_ValidationErr_TokenExpressionEmpty());
    }
    return PathExpressionValidationUtils.doCheckJsonPathExpression(value.trim());
  }

  static FormValidation checkQueryParameter(final String value) {
    if (value == null || value.isBlank()) {
      return FormValidation.error(Messages.RLP_ContinuationTokenPagination_ValidationErr_QueryParameterEmpty());
    }
    return FormValidation.ok();
  }

  @Extension
  @Symbol("continuationToken")
  public static class DescriptorImpl extends PaginationDescriptor {
    @Override
    @Nonnull
    public String getDisplayName() {
      return Messages.RLP_ContinuationTokenPagination_DisplayName();
    }

    @POST
    public FormValidation doCheckTokenExpression(@AncestorInPath final Item context,
                                                 @QueryParameter final String value,
                                                 @RelativePath("..") @QueryParameter final MimeType mimeType)
    {
      checkConfigurePermission(context);
      return checkTokenExpression(value, mimeType);
    }

    @POST
    public FormValidation doCheckQueryParameter(@AncestorInPath final Item context,
                                                @QueryParameter final String value)
    {
      checkConfigurePermission(context);
      return checkQueryParameter(value);
    }
  }
}
