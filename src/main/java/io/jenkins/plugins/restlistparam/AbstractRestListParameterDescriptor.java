package io.jenkins.plugins.restlistparam;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.model.Item;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterDefinition.ParameterDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import io.jenkins.plugins.restlistparam.logic.RestValueService;
import io.jenkins.plugins.restlistparam.model.ContinuationTokenPagination;
import io.jenkins.plugins.restlistparam.model.CustomHeader;
import io.jenkins.plugins.restlistparam.model.LinkHeaderPagination;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.Pagination;
import io.jenkins.plugins.restlistparam.model.ResultContainer;
import io.jenkins.plugins.restlistparam.model.ValueItem;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import io.jenkins.plugins.restlistparam.util.CredentialsUtils;
import io.jenkins.plugins.restlistparam.util.PathExpressionValidationUtils;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.verb.POST;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Job configuration checks and the Test Configuration action shared by the descriptors of the
 * REST List and REST Multi List parameters. Stapler resolves these web methods on descriptor
 * superclasses, so both parameter types' {@code config.jelly} bind to them unchanged.
 */
public abstract class AbstractRestListParameterDescriptor extends ParameterDescriptor {
  /** Form name of the "Follow paginated responses" checkbox in {@code config.jelly}. */
  static final String PAGINATION_ENABLED = "paginationEnabled";
  private static final String PAGINATION = "pagination";

  public Integer getDefaultCacheTime() {
    return RestListParameterGlobalConfig.get().getCacheTime();
  }

  /**
   * @return The global fetch timeout in seconds, which the build form uses to give up on a load without a response
   */
  public Integer getFetchTimeout() {
    return RestListParameterGlobalConfig.get().getFetchTimeout();
  }

  /**
   * The pagination block is an inline {@code f:optionalBlock}, so the strategy binds straight to the
   * {@code pagination} field. Inline blocks submit their fields even while unchecked, so an unchecked block
   * is turned into "no pagination" here.
   */
  @Override
  public ParameterDefinition newInstance(final StaplerRequest2 req, final JSONObject formData) throws FormException {
    if (formData != null) {
      if (!formData.optBoolean(PAGINATION_ENABLED, false)) {
        formData.remove(PAGINATION);
      }
      formData.remove(PAGINATION_ENABLED);
    }
    return super.newInstance(req, formData);
  }

  @POST
  public FormValidation doCheckRestEndpoint(@AncestorInPath final Item context,
                                            @QueryParameter final String value,
                                            @QueryParameter final String credentialId,
                                            @QueryParameter final MimeType mimeType)
  {
    if (context == null) {
      Jenkins.get().checkPermission(Jenkins.ADMINISTER);
    }
    else {
      context.checkPermission(Item.CONFIGURE);
    }

    if (value != null && !value.trim().isEmpty()) {
      if (value.matches("^http(s)?://.+")) {
        Optional<StandardCredentials> credentials = CredentialsUtils.findCredentials(context, credentialId);
        return RestValueService.doBasicValidation(value, credentials.orElse(null), mimeType);
      }
      return FormValidation.error(Messages.RLP_DescriptorImpl_ValidationErr_EndpointUrl());
    }
    return FormValidation.error(Messages.RLP_DescriptorImpl_ValidationErr_EndpointEmpty());
  }

  @POST
  public FormValidation doCheckValueExpression(@AncestorInPath final Item context,
                                               @QueryParameter final String value,
                                               @QueryParameter final MimeType mimeType)
  {
    if (context == null) {
      Jenkins.get().checkPermission(Jenkins.ADMINISTER);
    }
    else {
      context.checkPermission(Item.CONFIGURE);
    }

    if (value != null && !value.trim().isEmpty()) {
      switch (mimeType) {
        case APPLICATION_JSON:
          return PathExpressionValidationUtils.doCheckJsonPathExpression(value);
        case APPLICATION_XML:
          return PathExpressionValidationUtils.doCheckXPathExpression(value);
        default:
          return FormValidation.error(Messages.RLP_DescriptorImpl_ValidationErr_UnknownMime());
      }
    }
    return FormValidation.error(Messages.RLP_DescriptorImpl_ValidationErr_ExpressionEmpty());
  }

  @POST
  public ListBoxModel doFillCredentialIdItems(@AncestorInPath final Item context,
                                              @QueryParameter final String credentialId)
  {
    return CredentialsUtils.doFillCredentialsIdItems(context, credentialId);
  }

  @POST
  public FormValidation doCheckCredentialId(@AncestorInPath final Item context,
                                            @QueryParameter final String value)
  {
    return CredentialsUtils.doCheckCredentialsId(context, value);
  }

  @POST
  public FormValidation doCheckCacheTime(@AncestorInPath final Item context,
                                         @QueryParameter final Integer cacheTime)
  {
    if (context == null) {
      Jenkins.get().checkPermission(Jenkins.ADMINISTER);
    }
    else {
      context.checkPermission(Item.CONFIGURE);
    }

    if (cacheTime != null && cacheTime >= 0) {
      return FormValidation.ok();
    }

    return FormValidation.error(Messages.RLP_DescriptorImpl_ValidationErr_CacheTime());
  }

  @POST
  public FormValidation doTestConfiguration(@AncestorInPath final Item context,
                                            @QueryParameter final String restEndpoint,
                                            @QueryParameter final String credentialId,
                                            @QueryParameter final MimeType mimeType,
                                            @QueryParameter final String valueExpression,
                                            @QueryParameter final String displayExpression,
                                            @QueryParameter final String filter,
                                            @QueryParameter final ValueOrder valueOrder,
                                            @QueryParameter final String customHeadersJson,
                                            @QueryParameter final String paginationJson)
  {
    if (context == null) {
      Jenkins.get().checkPermission(Jenkins.ADMINISTER);
    }
    else {
      context.checkPermission(Item.CONFIGURE);
    }

    FormValidation missing = checkRequiredTestFields(restEndpoint, mimeType, valueExpression);
    if (missing != null) {
      return missing;
    }
    JSONObject paginationSettings = parsePaginationSettings(paginationJson);
    Pagination pagination = toPagination(paginationSettings);
    if (pagination != null) {
      FormValidation invalid = validatePagination(pagination, paginationSettings, mimeType);
      if (invalid.kind != FormValidation.Kind.OK) {
        return invalid;
      }
    }
    Optional<StandardCredentials> credentials = CredentialsUtils.findCredentials(context, credentialId);
    if (credentialId != null && !credentialId.trim().isEmpty() && !credentials.isPresent()) {
      return FormValidation.error(Messages.RLP_CredentialsUtils_ValidationErr_CannotFind());
    }

    ResultContainer<List<ValueItem>> container = RestValueService.get(
      restEndpoint,
      credentials.orElse(null),
      mimeType,
      0,
      valueExpression,
      displayExpression != null && !displayExpression.isBlank() ? displayExpression : "$",
      filter,
      valueOrder,
      CustomHeader.resolveAll(parseCustomHeaders(customHeadersJson), context),
      pagination);

    return toTestResult(container, pagination != null);
  }

  /**
   * @return The error for the first missing required Test Configuration field, or {@code null} when all are set
   */
  private static FormValidation checkRequiredTestFields(final String restEndpoint,
                                                        final MimeType mimeType,
                                                        final String valueExpression)
  {
    if (restEndpoint == null || restEndpoint.trim().isEmpty()) {
      return FormValidation.error(Messages.RLP_DescriptorImpl_ValidationErr_EndpointEmpty());
    }
    if (mimeType == null) {
      return FormValidation.error(Messages.RLP_DescriptorImpl_ValidationErr_UnknownMime());
    }
    if (valueExpression == null || valueExpression.isBlank()) {
      return FormValidation.error(Messages.RLP_DescriptorImpl_ValidationErr_ExpressionEmpty());
    }
    return null;
  }

  /**
   * Reports a Test Configuration fetch: the error, or the number of values and the first display value, plus the
   * pages fetched when paginated. Reaching the page limit is a warning.
   */
  private static FormValidation toTestResult(final ResultContainer<List<ValueItem>> container,
                                             final boolean paginated)
  {
    Optional<String> errorMsg = container.getErrorMsg();
    if (errorMsg.isPresent()) {
      return FormValidation.error(errorMsg.get());
    }

    // values should NEVER be empty here
    // due to all the filtering and error handling done in the RestValueService
    List<ValueItem> values = container.getValue();
    String first = values.get(0).getDisplayValue();
    if (!paginated) {
      return FormValidation.ok(Messages.RLP_DescriptorImpl_ValidationOk_ConfigValid(values.size(), first));
    }
    if (container.isPageLimitReached()) {
      return FormValidation.warning(Messages.RLP_DescriptorImpl_ValidationWarn_ConfigValidPageLimit(
        values.size(), container.getPagesFetched(), first));
    }
    return FormValidation.ok(Messages.RLP_DescriptorImpl_ValidationOk_ConfigValidPaged(
      values.size(), container.getPagesFetched(), first));
  }

  /**
   * Reads the form's (possibly unsaved) pagination block, which the Test Configuration button serializes as
   * {@code {kind, maxPages, tokenExpression, queryParameter}}, or as an empty string when the block is unchecked.
   * Malformed input is treated as no pagination.
   */
  static JSONObject parsePaginationSettings(final String paginationJson) {
    if (paginationJson == null || paginationJson.isBlank()) {
      return null;
    }
    try {
      return JSONObject.fromObject(paginationJson);
    }
    catch (JSONException ignored) {
      return null;
    }
  }

  /**
   * Builds a transient strategy from the form's pagination settings, without its page limit, which is
   * checked and applied by {@link #validatePagination}.
   */
  static Pagination toPagination(final JSONObject settings) {
    if (settings == null || settings.isNullObject()) {
      return null;
    }
    switch (settings.optString("kind", "")) {
      case "linkHeader":
        return new LinkHeaderPagination();
      case "continuationToken":
        return new ContinuationTokenPagination(settings.optString("tokenExpression", ""),
          settings.optString("queryParameter", ""));
      default:
        return null;
    }
  }

  /**
   * Runs the pagination settings checks the configuration form runs on its fields, and applies the page limit.
   *
   * @return The first failing check, or {@link FormValidation#ok()}
   */
  static FormValidation validatePagination(final Pagination pagination,
                                           final JSONObject settings,
                                           final MimeType mimeType)
  {
    FormValidation strategy = pagination.validate(mimeType);
    if (strategy.kind != FormValidation.Kind.OK) {
      return strategy;
    }
    String maxPages = settings.optString("maxPages", "");
    FormValidation pages = Pagination.checkMaxPages(maxPages);
    if (pages.kind != FormValidation.Kind.OK) {
      return pages;
    }
    pagination.setMaxPages(Integer.parseInt(maxPages.trim()));
    return FormValidation.ok();
  }

  /**
   * Builds transient custom headers from the form's (possibly unsaved) header rows, which the
   * Test Configuration button serializes as a Json array of {@code {name, value, credentialId, valuePrefix}}.
   * Malformed input is treated as no headers.
   */
  static List<CustomHeader> parseCustomHeaders(final String customHeadersJson) {
    if (customHeadersJson == null || customHeadersJson.isBlank()) {
      return Collections.emptyList();
    }
    List<CustomHeader> headers = new ArrayList<>();
    try {
      for (Object row : JSONArray.fromObject(customHeadersJson)) {
        if (!(row instanceof JSONObject)) {
          continue;
        }
        JSONObject json = (JSONObject) row;
        CustomHeader header = new CustomHeader(json.optString("name", ""));
        header.setValue(Secret.fromString(json.optString("value", "")));
        header.setCredentialId(json.optString("credentialId", ""));
        header.setValuePrefix(json.optString("valuePrefix", ""));
        headers.add(header);
      }
    }
    catch (JSONException ignored) {
      return Collections.emptyList();
    }
    return headers;
  }
}
