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
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.ResultContainer;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import io.jenkins.plugins.restlistparam.util.CredentialsUtils;
import io.jenkins.plugins.restlistparam.util.PathExpressionValidationUtils;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.*;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.Nonnull;
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
  private String errorMsg;
  private List<ValueItem> values;

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
      displayExpression, ValueOrder.NONE, ".*", config.getCacheTime(), "");
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
                                     final String defaultValue)
  {
    super(name);
    setDescription(description);
    this.restEndpoint = restEndpoint;
    this.mimeType = mimeType;
    this.valueExpression = valueExpression;
    this.credentialId = StringUtils.isNotBlank(credentialId) ? credentialId : "";
    if (mimeType == MimeType.APPLICATION_JSON) {
      this.displayExpression = StringUtils.isNotBlank(displayExpression) ? displayExpression : "$";
    }
    this.defaultValue = StringUtils.isNotBlank(defaultValue) ? defaultValue : "";
    this.valueOrder = valueOrder != null ? valueOrder : ValueOrder.NONE;
    this.filter = StringUtils.isNotBlank(filter) ? filter : ".*";
    this.cacheTime = cacheTime != null ? cacheTime : config.getCacheTime();
    this.errorMsg = "";
    this.values = Collections.emptyList();
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
                                      final List<ValueItem> values)
  {
    super(name);
    setDescription(description);
    this.restEndpoint = restEndpoint;
    this.mimeType = mimeType;
    this.valueExpression = valueExpression;
    this.credentialId = StringUtils.isNotBlank(credentialId) ? credentialId : "";
    if (mimeType == MimeType.APPLICATION_JSON) {
      this.displayExpression = StringUtils.isNotBlank(displayExpression) ? displayExpression : "$";
    }
    this.defaultValue = StringUtils.isNotBlank(defaultValue) ? defaultValue : "";
    this.valueOrder = valueOrder != null ? valueOrder : ValueOrder.NONE;
    this.filter = StringUtils.isNotBlank(filter) ? filter : ".*";
    this.cacheTime = cacheTime != null ? cacheTime : config.getCacheTime();
    this.errorMsg = "";
    this.values = values;
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
      return StringUtils.isNotBlank(displayExpression) ? displayExpression : "$";
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

  void setErrorMsg(final String errorMsg) {
    this.errorMsg = errorMsg;
  }

  public String getErrorMsg() {
    return errorMsg;
  }

  public List<ValueItem> getValues() {
    Item context = null;

    if (Stapler.getCurrentRequest() != null) {
      context = Stapler.getCurrentRequest().findAncestorObject(Item.class);
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
      getValueOrder());

    setErrorMsg(container.getErrorMsg().orElse(""));
    values = container.getValue();
    return values;
  }

  @Override
  public ParameterDefinition copyWithDefaultValue(final ParameterValue defaultValue) {
    if (defaultValue instanceof RestListParameterValue) {
      RestListParameterValue value = (RestListParameterValue) defaultValue;
      return new RestListParameterDefinition(
        getName(), getDescription(), getRestEndpoint(), getCredentialId(), getMimeType(),
        getValueExpression(), getDisplayExpression(), getValueOrder(), getFilter(), getCacheTime(),
        ValueResolver.parseDisplayValue(getMimeType(), value.getValue(), displayExpression), getValues());
    }
    else {
      return this;
    }
  }

  @Override
  public ParameterValue createValue(final String value) {
    RestListParameterValue parameterValue = new RestListParameterValue(getName(), value, getDescription());

    getValues();
    checkValue(parameterValue);
    return parameterValue;
  }

  @Override
  public ParameterValue createValue(final StaplerRequest req,
                                    final JSONObject jo)
  {
    RestListParameterValue value = req.bindJSON(RestListParameterValue.class, jo);

    getValues();
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

    return values.stream()
      .map(ValueItem::getValue)
      .filter(Objects::nonNull)
      .anyMatch(val -> value.getValue().equals(val));
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      getName(), getDescription(), getRestEndpoint(), getCredentialId(),
      getMimeType(), getValueExpression(), getFilter());
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
                                              @QueryParameter final String credentialId)
    {
      if (context == null) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
      }
      else {
        context.checkPermission(Item.CONFIGURE);
      }

      if (StringUtils.isNotBlank(value)) {
        if (value.matches("^http(s)?://.+")) {
          Optional<StandardCredentials> credentials = CredentialsUtils.findCredentials(context, credentialId);
          return RestValueService.doBasicValidation(value, credentials.orElse(null));
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

      if (StringUtils.isNotBlank(value)) {
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
                                              @QueryParameter final ValueOrder valueOrder)
    {
      if (context == null) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
      }
      else {
        context.checkPermission(Item.CONFIGURE);
      }

      if (StringUtils.isBlank(restEndpoint)) {
        return FormValidation.error(Messages.RLP_DescriptorImpl_ValidationErr_EndpointEmpty());
      }
      if (mimeType == null) {
        return FormValidation.error(Messages.RLP_DescriptorImpl_ValidationErr_UnknownMime());
      }
      if (StringUtils.isBlank(valueExpression)) {
        return FormValidation.error(Messages.RLP_DescriptorImpl_ValidationErr_ExpressionEmpty());
      }
      Optional<StandardCredentials> credentials = CredentialsUtils.findCredentials(context, credentialId);
      if (StringUtils.isNotBlank(credentialId) && !credentials.isPresent()) {
        return FormValidation.error(Messages.RLP_CredentialsUtils_ValidationErr_CannotFind());
      }

      ResultContainer<List<ValueItem>> container = RestValueService.get(
        restEndpoint,
        credentials.orElse(null),
        mimeType,
        0,
        valueExpression,
        StringUtils.isNotBlank(displayExpression) ? displayExpression : "$",
        filter,
        valueOrder);

      Optional<String> errorMsg = container.getErrorMsg();
      List<ValueItem> values = container.getValue();
      if (errorMsg.isPresent()) {
        return FormValidation.error(errorMsg.get());
      }

      // values should NEVER be empty here
      // due to all the filtering and error handling done in the RestValueService
      return FormValidation.ok(Messages.RLP_DescriptorImpl_ValidationOk_ConfigValid(values.size(), values.get(0).getDisplayValue()));
    }
  }
}