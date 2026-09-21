package io.jenkins.plugins.restlistparam.model;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Item;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import io.jenkins.plugins.restlistparam.Messages;
import io.jenkins.plugins.restlistparam.util.CredentialsUtils;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class CustomHeader extends AbstractDescribableImpl<CustomHeader> implements Serializable {
  private static final long serialVersionUID = 1L;

  private static final String HEADER_NAME_PATTERN = "^[!#$%&'*+.^_`|~0-9A-Za-z-]+$";

  private final String name;
  private Secret value;
  private String credentialId;
  private String valuePrefix;

  @DataBoundConstructor
  public CustomHeader(final String name) {
    this.name = name != null ? name.trim() : null;
    this.credentialId = "";
    this.valuePrefix = "";
  }

  public String getName() {
    return name;
  }

  public Secret getValue() {
    return value;
  }

  @DataBoundSetter
  public void setValue(final Secret value) {
    this.value = value;
  }

  public String getCredentialId() {
    return credentialId;
  }

  @DataBoundSetter
  public void setCredentialId(final String credentialId) {
    this.credentialId = credentialId != null && !credentialId.trim().isEmpty() ? credentialId : "";
  }

  public String getValuePrefix() {
    return valuePrefix;
  }

  @DataBoundSetter
  public void setValuePrefix(final String valuePrefix) {
    this.valuePrefix = valuePrefix != null ? valuePrefix : "";
  }

  public String resolve(final Item context) {
    if (!isValidName(name)) {
      return null;
    }

    Optional<String> headerValue = resolveValue(context);
    if (!headerValue.isPresent()) {
      return null;
    }

    return getValuePrefix() + headerValue.get();
  }

  private Optional<String> resolveValue(final Item context) {
    if (credentialId != null && !credentialId.isBlank()) {
      Optional<StringCredentials> credentials = CredentialsUtils.findStringCredentials(context, credentialId);
      return credentials.map(credential -> credential.getSecret().getPlainText())
                        .filter(value -> !value.isEmpty());
    }

    String plainValue = Secret.toString(value);
    if (plainValue == null || plainValue.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(plainValue);
  }

  /**
   * Resolves a list of custom headers to the name/value pairs sent with a request.
   * Shared by builds and Test Configuration so both send the same headers.
   *
   * @param customHeaders The configured headers ({@code null} entries are ignored)
   * @param context       The job used to look up credentials, or {@code null} for the system context
   * @return The resolved headers in configuration order, skipping headers without a usable name or value
   */
  public static Map<String, String> resolveAll(final List<CustomHeader> customHeaders, final Item context) {
    Map<String, String> headers = new LinkedHashMap<>();
    if (customHeaders == null) {
      return headers;
    }
    for (CustomHeader customHeader : customHeaders) {
      if (customHeader == null) {
        continue;
      }
      String value = customHeader.resolve(context);
      if (value != null) {
        headers.put(customHeader.getName().trim(), value);
      }
    }
    return headers;
  }

  public static boolean isValidName(final String name) {
    return name != null && name.trim().matches(HEADER_NAME_PATTERN);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || this.getClass() != obj.getClass()) {
      return false;
    }
    CustomHeader other = (CustomHeader) obj;
    return Objects.equals(name, other.name)
      && Objects.equals(value, other.value)
      && Objects.equals(credentialId, other.credentialId)
      && Objects.equals(valuePrefix, other.valuePrefix);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, value, credentialId, valuePrefix);
  }

  @Extension
  public static class DescriptorImpl extends Descriptor<CustomHeader> {
    @Override
    @Nonnull
    public String getDisplayName() {
      return Messages.RLP_CustomHeader_DisplayName();
    }

    @POST
    public FormValidation doCheckName(@AncestorInPath final Item context,
                                      @QueryParameter final String value)
    {
      if (context == null) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
      }
      else {
        context.checkPermission(Item.CONFIGURE);
      }

      if (value == null || value.trim().isEmpty()) {
        return FormValidation.error(Messages.RLP_CustomHeader_ValidationErr_NameEmpty());
      }
      if (!isValidName(value)) {
        return FormValidation.error(Messages.RLP_CustomHeader_ValidationErr_NameInvalid());
      }
      return FormValidation.ok();
    }

    @POST
    public ListBoxModel doFillCredentialIdItems(@AncestorInPath final Item context,
                                                @QueryParameter final String credentialId)
    {
      return CredentialsUtils.doFillStringCredentialsIdItems(context, credentialId);
    }
  }
}
