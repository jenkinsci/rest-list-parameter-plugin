package io.jenkins.plugins.restlistparam;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.model.Item;
import hudson.model.ParameterDefinition.ParameterDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import io.jenkins.plugins.restlistparam.logic.RestValueService;
import io.jenkins.plugins.restlistparam.model.CustomHeader;
import io.jenkins.plugins.restlistparam.model.MimeType;
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
  public Integer getDefaultCacheTime() {
    return RestListParameterGlobalConfig.get().getCacheTime();
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
                                            @QueryParameter final String customHeadersJson)
  {
    if (context == null) {
      Jenkins.get().checkPermission(Jenkins.ADMINISTER);
    }
    else {
      context.checkPermission(Item.CONFIGURE);
    }

    if (restEndpoint == null || restEndpoint.trim().isEmpty()) {
      return FormValidation.error(Messages.RLP_DescriptorImpl_ValidationErr_EndpointEmpty());
    }
    if (mimeType == null) {
      return FormValidation.error(Messages.RLP_DescriptorImpl_ValidationErr_UnknownMime());
    }
    if (valueExpression == null || valueExpression.isBlank()) {
      return FormValidation.error(Messages.RLP_DescriptorImpl_ValidationErr_ExpressionEmpty());
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
      CustomHeader.resolveAll(parseCustomHeaders(customHeadersJson), context));

    Optional<String> errorMsg = container.getErrorMsg();
    List<ValueItem> values = container.getValue();
    if (errorMsg.isPresent()) {
      return FormValidation.error(errorMsg.get());
    }

    // values should NEVER be empty here
    // due to all the filtering and error handling done in the RestValueService
    return FormValidation.ok(Messages.RLP_DescriptorImpl_ValidationOk_ConfigValid(values.size(), values.get(0).getDisplayValue()));
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
