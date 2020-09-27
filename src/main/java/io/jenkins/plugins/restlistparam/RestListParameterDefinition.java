package io.jenkins.plugins.restlistparam;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.SimpleParameterDefinition;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.restlistparam.logic.RestValueService;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.ResultContainer;
import io.jenkins.plugins.restlistparam.util.CredentialsUtils;
import io.jenkins.plugins.restlistparam.util.PathExpressionValidationUtils;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.*;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

public class RestListParameterDefinition extends SimpleParameterDefinition {
  private static final long serialVersionUID = 3453376762337829455L;

  private final String restEndpoint;
  private final String credentialId;
  private final MimeType mimeType;
  private final String valueExpression;
  private String filter;
  private String defaultValue;
  private String errorMsg = "";
  private Collection<String> values = Collections.emptyList();

  @DataBoundConstructor
  public RestListParameterDefinition(String name,
                                     String description,
                                     String restEndpoint,
                                     String credentialId,
                                     MimeType mimeType,
                                     String valueExpression)
  {
    this(name, description, restEndpoint, credentialId, mimeType, valueExpression, ".*", "");
  }

  public RestListParameterDefinition(String name,
                                     String description,
                                     String restEndpoint,
                                     String credentialId,
                                     MimeType mimeType,
                                     String valueExpression,
                                     String filter,
                                     String defaultValue)
  {
    super(name, description);
    this.restEndpoint = restEndpoint;
    this.mimeType = mimeType;
    this.valueExpression = valueExpression;
    this.credentialId = StringUtils.isNotBlank(credentialId) ? credentialId : "";
    this.defaultValue = StringUtils.isNotBlank(defaultValue) ? defaultValue : "";
    this.filter = StringUtils.isNotBlank(filter) ? filter : ".*";
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

  @DataBoundSetter
  public void setFilter(String filter) {
    this.filter = filter;
  }

  public String getDefaultValue() {
    return defaultValue;
  }

  @DataBoundSetter
  public void setDefaultValue(String defaultValue) {
    this.defaultValue = defaultValue;
  }

  void setErrorMsg(String errorMsg) {
    this.errorMsg = errorMsg;
  }

  public String getErrorMsg() {
    return errorMsg;
  }

  public Collection<String> getValues() {
    if (values == null || values.isEmpty()) {
      Optional<StandardCredentials> credentials = CredentialsUtils.findCredentials(credentialId);

      ResultContainer<Collection<String>> container = RestValueService.get(restEndpoint,
                                                                           credentials.orElse(null),
                                                                           mimeType,
                                                                           valueExpression,
                                                                           filter);

      setErrorMsg(container.getErrorMsg().orElse(""));
      values = container.getValue();
    }
    return values;
  }

  @Override
  public ParameterDefinition copyWithDefaultValue(ParameterValue defaultValue) {
    if (defaultValue instanceof RestListParameterValue) {
      RestListParameterValue value = (RestListParameterValue) defaultValue;
      return new RestListParameterDefinition(
        getName(), getDescription(), getRestEndpoint(), getCredentialId(),
        getMimeType(), getValueExpression(), getFilter(), value.getValue());
    }
    else {
      return this;
    }
  }

  @Override
  public ParameterValue createValue(String value) {
    RestListParameterValue parameterValue = new RestListParameterValue(getName(), value, getDescription());
    checkValue(parameterValue);
    return parameterValue;
  }

  @Override
  @CheckForNull
  public ParameterValue createValue(StaplerRequest req,
                                    JSONObject jo)
  {
    RestListParameterValue value = req.bindJSON(RestListParameterValue.class, jo);
    checkValue(value);
    return value;
  }

  private void checkValue(RestListParameterValue value) {
    if (!isValid(value)) {
      throw new IllegalArgumentException("Illegal value for parameter " + getName() + ": " + value.getValue());
    }
  }

  public boolean isValid(ParameterValue value) {
    return values.contains(((RestListParameterValue) value).getValue());
  }

  @Symbol({"RESTList", "RestList", "RESTListParam"})
  @Extension
  public static class DescriptorImpl extends ParameterDescriptor {
    @Override
    @Nonnull
    public String getDisplayName() {
      return Messages.RLP_DescriptorImpl_DisplayName();
    }

    public FormValidation doCheckRestEndpoint(@QueryParameter final String value) {
      if (StringUtils.isNotBlank(value)) {
        if (value.matches("^http(s)?://.+")) {
          return FormValidation.ok();
        }
        return FormValidation.error("Rest Endpoint is no URL format (http:// or https://)");
      }
      return FormValidation.error("Rest Endpoint must not be empty");
    }

    public FormValidation doCheckCredentialId(@QueryParameter final String value) {
      return CredentialsUtils.doCheckFillCredentialsId(value);
    }

    public FormValidation doCheckValueExpression(@QueryParameter final String value,
                                                 @QueryParameter final MimeType mimeType)
    {
      if (StringUtils.isNotBlank(value)) {
        switch (mimeType) {
          case APPLICATION_JSON:
            return PathExpressionValidationUtils.doCheckJsonPathExpression(value);
          case APPLICATION_XML:
            return PathExpressionValidationUtils.doCheckXPathExpression(value);
          default:
            return FormValidation.error("Unknown MimeType");
        }
      }
      return FormValidation.error("Value Expression must not be empty");
    }

    public ListBoxModel doFillCredentialIdItems(@AncestorInPath final Item context,
                                                @QueryParameter final String credentialId)
    {
      return CredentialsUtils.doFillCredentialsIdItems(context, credentialId);
    }

    public FormValidation doCheckCredentialIdItems(@QueryParameter final String value) {
      return CredentialsUtils.doCheckFillCredentialsId(value);
    }
  }
}