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
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Optional;

// TODO add Form-validation for select value

public class RestListParameterDefinition extends SimpleParameterDefinition {
  private static final long serialVersionUID = 3453376762337829455L;

  private final String restEndpoint;
  private final String credentialId;
  private final MimeType mimeType;
  private final String valueExpression;
  private final String filter;
  private final String defaultValue;
  private String errorMsg = "";

  @DataBoundConstructor
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

  public String getRestEndpoint()
  {
    return restEndpoint;
  }

  public String getCredentialId()
  {
    return credentialId;
  }

  public MimeType getMimeType()
  {
    return mimeType;
  }

  public String getValueExpression()
  {
    return valueExpression;
  }

  public String getFilter()
  {
    return filter;
  }

  public String getDefaultValue()
  {
    return defaultValue;
  }

  void setErrorMsg(String errorMsg)
  {
    this.errorMsg = errorMsg;
  }

  public String getErrorMsg()
  {
    return errorMsg;
  }

  @Exported
  public Collection<String> getValues()
  {
    Optional<StandardCredentials> credentials = CredentialsUtils.findCredentials(credentialId);

    ResultContainer<Collection<String>> values = RestValueService.get(restEndpoint,
                                                                      credentials.orElse(null),
                                                                      mimeType,
                                                                      valueExpression,
                                                                      filter);

    setErrorMsg(values.getErrorMsg().orElse(""));

    return values.getValue();
  }

  @Override
  public ParameterDefinition copyWithDefaultValue(ParameterValue defaultValue)
  {
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
  public ParameterValue createValue(String value)
  {
    return new RestListParameterValue(getName(), value, getDescription());
  }

  @Override
  @CheckForNull
  public ParameterValue createValue(StaplerRequest req,
                                    JSONObject jo)
  {
    return req.bindJSON(RestListParameterValue.class, jo);
  }

  @Symbol({"RESTList", "RestList", "RESTListParam"})
  @Extension
  public static class DescriptorImpl extends ParameterDescriptor {
    @Override
    @Nonnull
    public String getDisplayName()
    {
      return Messages.RLP_DescriptorImpl_DisplayName();
    }

    public ListBoxModel doFillCredentialIdItems(@AncestorInPath Item context,
                                                @QueryParameter String credentialId)
    {
      return CredentialsUtils.doFillCredentialsIdItems(context, credentialId);
    }

    public FormValidation doCheckCredentialIdItems(@QueryParameter final String value)
    {
      return CredentialsUtils.doCheckFillCredentialsId(value);
    }
  }
}