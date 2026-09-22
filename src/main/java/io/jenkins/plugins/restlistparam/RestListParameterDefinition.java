package io.jenkins.plugins.restlistparam;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import io.jenkins.plugins.restlistparam.logic.ValueResolver;
import io.jenkins.plugins.restlistparam.model.CustomHeader;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.ValueItem;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class RestListParameterDefinition extends AbstractRestListParameterDefinition {
  private static final long serialVersionUID = 3453376762337829455L;

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
    this(name, description, restEndpoint, credentialId, mimeType, valueExpression, displayExpression,
      valueOrder, filter, cacheTime, defaultValue, allowEmptyValue, true, Collections.emptyList(), Collections.emptyList());
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
    super(name, description, restEndpoint, credentialId, mimeType, valueExpression, displayExpression,
      valueOrder, filter, cacheTime, defaultValue, allowEmptyValue, enableValidation, values, customHeaders);
  }

  /**
   * The value the dropdown preselects with validation disabled. The default value refers to a display value,
   * as in dropdown mode, so it resolves to the value of the first entry displayed as the default; a default
   * that matches no entry stays verbatim and becomes a custom value.
   *
   * @param values The entries already fetched for this form
   * @return The matching entry's value, otherwise the default value verbatim
   */
  public String resolveFreeTextDefault(final List<ValueItem> values) {
    String fallback = getDefaultValue() != null ? getDefaultValue() : "";
    if (fallback.isEmpty() || values == null) {
      return fallback;
    }
    return values.stream()
      .filter(item -> item != null && fallback.equals(item.getDisplayValue()))
      .map(ValueItem::getValue)
      .findFirst()
      .orElse(fallback);
  }

  /**
   * In dropdown mode, the entry displayed as the default value is preselected.
   */
  @Override
  public boolean isDefaultSelected(final ValueItem item) {
    return item != null && item.getDisplayValue() != null && item.getDisplayValue().equals(getDefaultValue());
  }

  @Override
  public ParameterDefinition copyWithDefaultValue(final ParameterValue defaultValue) {
    if (defaultValue instanceof RestListParameterValue) {
      RestListParameterValue value = (RestListParameterValue) defaultValue;
      return new RestListParameterDefinition(
        getName(), getDescription(), getRestEndpoint(), getCredentialId(), getMimeType(),
        getValueExpression(), getDisplayExpression(), getValueOrder(), getFilter(), getCacheTime(),
        ValueResolver.parseDisplayValue(getMimeType(), value.getValue(), getDisplayExpression()),
        isAllowEmptyValue(), isEnableValidation(), Collections.emptyList(), getCustomHeaders());
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
    Object submitted = value == null ? null : value.getValue();
    if (submitted == null) {
      return false;
    }
    // Empty submissions are governed solely by allowEmptyValue, independently of
    // enableValidation, so the two checkboxes compose orthogonally: disabling validation
    // permits arbitrary non-empty values but does not silently allow an empty one.
    if ("".equals(submitted)) {
      return isAllowEmptyValue();
    }
    if (!isEnableValidation()) {
      return true;
    }
    String element = submitted.toString();
    return entryValuesFor(List.of(element)).contains(element);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      getName(), getDescription(), getRestEndpoint(), getCredentialId(),
      getMimeType(), getValueExpression(), getFilter(), isAllowEmptyValue(), isEnableValidation());
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
    if (isAllowEmptyValue() != other.isAllowEmptyValue()) {
      return false;
    }
    if (isEnableValidation() != other.isEnableValidation()) {
      return false;
    }
    return Objects.equals(getDefaultValue(), other.getDefaultValue());
  }

  @Symbol({"RESTList", "RestList", "RESTListParam"})
  @Extension
  public static class DescriptorImpl extends AbstractRestListParameterDescriptor {
    @Override
    @Nonnull
    public String getDisplayName() {
      return Messages.RLP_DescriptorImpl_DisplayName();
    }
  }
}
