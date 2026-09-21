package io.jenkins.plugins.restlistparam;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.Extension;
import hudson.model.Item;
import io.jenkins.plugins.restlistparam.logic.ValueResolver;
import io.jenkins.plugins.restlistparam.model.ValueItem;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.SimpleParameterDefinition;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.restlistparam.logic.RestValueService;
import io.jenkins.plugins.restlistparam.model.CustomHeader;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.ResultContainer;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import io.jenkins.plugins.restlistparam.util.CredentialsUtils;
import io.jenkins.plugins.restlistparam.util.PathExpressionValidationUtils;
import jenkins.model.Jenkins;
import hudson.util.Secret;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.*;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class RestListParameterDefinition extends SimpleParameterDefinition {
  private static final long serialVersionUID = 3453376762337829455L;
  private static final RestListParameterGlobalConfig config = RestListParameterGlobalConfig.get();

  private final String restEndpoint;
  private final String credentialId;
  private final MimeType mimeType;
  private final String valueExpression;
  private String displayExpression;
  private ValueOrder valueOrder;
  private String defaultValue;
  private String filter;
  private Integer cacheTime;
  private boolean allowEmptyValue;
  private boolean enableValidation = true;
  private String errorMsg;
  private List<ValueItem> values;
  private List<CustomHeader> customHeaders;

  @DataBoundConstructor
  public RestListParameterDefinition(final String name,
                                     final String description,
                                     final String restEndpoint,
                                     final String credentialId,
                                     final MimeType mimeType,
                                     final String valueExpression,
                                     final String displayExpression)
  {
    this(name, description, restEndpoint, credentialId, mimeType, valueExpression,
      displayExpression, ValueOrder.NONE, ".*", config.getCacheTime(), "", false);
  }

  public RestListParameterDefinition(final String name,
                                     final String description,
                                     final String restEndpoint,
                                     final String credentialId,
                                     final MimeType mimeType,
                                     final String valueExpression,
                                     final String displayExpression,
                                     final ValueOrder valueOrder,
                                     final String filter,
                                     final Integer cacheTime,
                                     final String defaultValue,
                                     final boolean allowEmptyValue)
  {
    super(name);
    setDescription(description);
    this.restEndpoint = restEndpoint;
    this.mimeType = mimeType;
    this.valueExpression = valueExpression;
    this.credentialId = credentialId != null && !credentialId.trim().isEmpty() ? credentialId : "";
    if (mimeType == MimeType.APPLICATION_JSON) {
      this.displayExpression = displayExpression != null && !displayExpression.isBlank() ? displayExpression : "$";
    }
    this.defaultValue = defaultValue != null && !defaultValue.trim().isEmpty() ? defaultValue : "";
    this.valueOrder = valueOrder != null ? valueOrder : ValueOrder.NONE;
    this.filter = !filter.isBlank() ? filter : ".*";
    this.cacheTime = cacheTime != null ? cacheTime : config.getCacheTime();
    this.allowEmptyValue = allowEmptyValue;
    this.errorMsg = "";
    this.values = Collections.emptyList();
    this.customHeaders = Collections.emptyList();
  }

  private RestListParameterDefinition(final String name,
                                      final String description,
                                      final String restEndpoint,
                                      final String credentialId,
                                      final MimeType mimeType,
                                      final String valueExpression,
                                      final String displayExpression,
                                      final ValueOrder valueOrder,
                                      final String filter,
                                      final Integer cacheTime,
                                      final String defaultValue,
                                      final boolean allowEmptyValue,
                                      final boolean enableValidation,
                                      final List<ValueItem> values,
                                      final List<CustomHeader> customHeaders)
  {
    super(name);
    setDescription(description);
    this.restEndpoint = restEndpoint;
    this.mimeType = mimeType;
    this.valueExpression = valueExpression;
    this.credentialId = credentialId != null && !credentialId.trim().isEmpty() ? credentialId : "";
    if (mimeType == MimeType.APPLICATION_JSON) {
      this.displayExpression = displayExpression != null && !displayExpression.isBlank() ? displayExpression : "$";
    }
    this.defaultValue = defaultValue != null && !defaultValue.trim().isEmpty() ? defaultValue : "";
    this.valueOrder = valueOrder != null ? valueOrder : ValueOrder.NONE;
    this.filter = !filter.isBlank() ? filter : ".*";
    this.cacheTime = cacheTime != null ? cacheTime : config.getCacheTime();
    this.allowEmptyValue = allowEmptyValue;
    this.enableValidation = enableValidation;
    this.errorMsg = "";
    this.values = values;
    this.customHeaders = customHeaders != null ? customHeaders : Collections.emptyList();
  }

  public String getRestEndpoint() {
    return restEndpoint;
  }

  public String getCredentialId() {
    return credentialId;
  }

  public MimeType getMimeType() {
    return mimeType;
  }

  public String getValueExpression() {
    return valueExpression;
  }

  public String getFilter() {
    return filter;
  }

  public String getDisplayExpression() {
    if (mimeType == MimeType.APPLICATION_JSON) {
      return displayExpression != null && !displayExpression.isBlank() ? displayExpression : "$";
    }
    return "";
  }

  @DataBoundSetter
  public void setDisplayExpression(final String displayExpression) {
    this.displayExpression = displayExpression;
  }

  @DataBoundSetter
  public void setValueOrder(final ValueOrder valueOrder) {
    this.valueOrder = valueOrder;
  }

  public ValueOrder getValueOrder() {
    return valueOrder != null ? valueOrder : ValueOrder.NONE;
  }

  @DataBoundSetter
  public void setFilter(final String filter) {
    this.filter = filter;
  }

  public Integer getCacheTime() {
    return cacheTime != null ? cacheTime : config.getCacheTime();
  }

  @DataBoundSetter
  public void setCacheTime(final Integer cacheTime) {
    this.cacheTime = cacheTime;
  }

  public String getDefaultValue() {
    return defaultValue;
  }

  @DataBoundSetter
  public void setDefaultValue(final String defaultValue) {
    this.defaultValue = defaultValue;
  }

  public boolean isAllowEmptyValue() {
    return allowEmptyValue;
  }

  @DataBoundSetter
  public void setAllowEmptyValue(final boolean allowEmptyValue) {
    this.allowEmptyValue = allowEmptyValue;
  }

  public boolean isEnableValidation() {
    return enableValidation;
  }

  @DataBoundSetter
  public void setEnableValidation(final boolean enableValidation) {
    this.enableValidation = enableValidation;
  }

  public List<CustomHeader> getCustomHeaders() {
    return customHeaders != null ? customHeaders : Collections.emptyList();
  }

  @DataBoundSetter
  public void setCustomHeaders(final List<CustomHeader> customHeaders) {
    this.customHeaders = customHeaders != null ? customHeaders : Collections.emptyList();
  }

  void setErrorMsg(final String errorMsg) {
    this.errorMsg = errorMsg;
  }

  public String getErrorMsg() {
    return errorMsg;
  }

  public List<ValueItem> getValues() {
    Item context = null;

    if (Stapler.getCurrentRequest2() != null) {
      context = Stapler.getCurrentRequest2().findAncestorObject(Item.class);
    }

    Optional<StandardCredentials> credentials = CredentialsUtils.findCredentials(context, credentialId);

    ResultContainer<List<ValueItem>> container = RestValueService.get(
      getRestEndpoint(),
      credentials.orElse(null),
      getMimeType(),
      getCacheTime(),
      getValueExpression(),
      getDisplayExpression(),
      getFilter(),
      getValueOrder(),
      CustomHeader.resolveAll(getCustomHeaders(), context));

    setErrorMsg(container.getErrorMsg().orElse(""));
    values = container.getValue();
    return values;
  }

  /**
   * The prefill for the free-text input (validation disabled). The default value refers to a display value,
   * as in dropdown mode, so it resolves to the value of the first entry displayed as the default.
   *
   * @param values The entries already fetched for this form
   * @return The matching entry's value, otherwise the default value verbatim
   */
  public String resolveFreeTextDefault(final List<ValueItem> values) {
    String fallback = defaultValue != null ? defaultValue : "";
    if (fallback.isEmpty() || values == null) {
      return fallback;
    }
    return values.stream()
      .filter(item -> item != null && fallback.equals(item.getDisplayValue()))
      .map(ValueItem::getValue)
      .findFirst()
      .orElse(fallback);
  }

  @Override
  public ParameterDefinition copyWithDefaultValue(final ParameterValue defaultValue) {
    if (defaultValue instanceof RestListParameterValue) {
      RestListParameterValue value = (RestListParameterValue) defaultValue;
      return new RestListParameterDefinition(
        getName(), getDescription(), getRestEndpoint(), getCredentialId(), getMimeType(),
        getValueExpression(), getDisplayExpression(), getValueOrder(), getFilter(), getCacheTime(),
        ValueResolver.parseDisplayValue(getMimeType(), value.getValue(), getDisplayExpression()),
        isAllowEmptyValue(), isEnableValidation(), getValues(), getCustomHeaders());
    }
    else {
      return this;
    }
  }

  @Override
  public ParameterValue createValue(final String value) {
    RestListParameterValue parameterValue = new RestListParameterValue(getName(), value, getDescription());

    checkValue(parameterValue);
    return parameterValue;
  }

  @Override
  public ParameterValue createValue(final StaplerRequest2 req,
                                    final JSONObject jo)
  {
    RestListParameterValue value = req.bindJSON(RestListParameterValue.class, jo);
    value.setDescription(getDescription());

    checkValue(value);
    return value;
  }

  private void checkValue(final RestListParameterValue value) {
    if (!isValid(value)) {
      throw new IllegalArgumentException(Messages.RLP_Definition_ValueException(getName(), value.getValue()));
    }
  }

  @Override
  public boolean isValid(ParameterValue value) {
    if(value == null || value.getValue() == null) {
      return false;
    }
    // Empty submissions are governed solely by allowEmptyValue, independently of
    // enableValidation, so the two checkboxes compose orthogonally: disabling validation
    // permits arbitrary non-empty values but does not silently allow an empty one.
    if ("".equals(value.getValue())) {
      return allowEmptyValue;
    }
    if (!enableValidation) {
      return true;
    }
    getValues();
    return values.stream()
      .map(ValueItem::getValue)
      .filter(Objects::nonNull)
      .anyMatch(val -> value.getValue().equals(val));
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      getName(), getDescription(), getRestEndpoint(), getCredentialId(),
      getMimeType(), getValueExpression(), getFilter(), allowEmptyValue, enableValidation);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || this.getClass() != obj.getClass()) {
      return false;
    }
    if (this == obj) {
      return true;
    }
    RestListParameterDefinition other = (RestListParameterDefinition) obj;
    if (!Objects.equals(getName(), other.getName())) {
      return false;
    }
    if (!Objects.equals(getDescription(), other.getDescription())) {
      return false;
    }
    if (!Objects.equals(getRestEndpoint(), other.getRestEndpoint())) {
      return false;
    }
    if (!Objects.equals(getCredentialId(), other.getCredentialId())) {
      return false;
    }
    if (!Objects.equals(getMimeType(), other.getMimeType())) {
      return false;
    }
    if (!Objects.equals(getValueExpression(), other.getValueExpression())) {
      return false;
    }
    if (!Objects.equals(getFilter(), other.getFilter())) {
      return false;
    }
    if (allowEmptyValue != other.allowEmptyValue) {
      return false;
    }
    if (enableValidation != other.enableValidation) {
      return false;
    }
    return Objects.equals(defaultValue, other.defaultValue);
  }

  @Symbol({"RESTList", "RestList", "RESTListParam"})
  @Extension
  public static class DescriptorImpl extends ParameterDescriptor {
    @Override
    @Nonnull
    public String getDisplayName() {
      return Messages.RLP_DescriptorImpl_DisplayName();
    }

    public Integer getDefaultCacheTime() {
      return config.getCacheTime();
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
}