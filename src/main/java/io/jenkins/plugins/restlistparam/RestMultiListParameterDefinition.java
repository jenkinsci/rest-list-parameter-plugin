package io.jenkins.plugins.restlistparam;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import io.jenkins.plugins.restlistparam.logic.ValueResolver;
import io.jenkins.plugins.restlistparam.model.CustomHeader;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.ValueItem;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import io.jenkins.plugins.restlistparam.util.MultiValueCodec;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A parameter whose value is an ordered list of entries chosen from a REST endpoint's response.
 * <p>
 * It stays a {@link hudson.model.SimpleParameterDefinition}, so string input from the CLI, the remote API
 * and the Pipeline {@code build} step is read by {@link #createValue(String)} with
 * {@link MultiValueCodec#parse(String)}. The remote API therefore takes the list as one Json array;
 * core rejects a repeated query parameter name.
 */
public final class RestMultiListParameterDefinition extends AbstractRestListParameterDefinition {
  private static final long serialVersionUID = 1L;

  @DataBoundConstructor
  public RestMultiListParameterDefinition(final String name,
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

  public RestMultiListParameterDefinition(final String name,
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

  private RestMultiListParameterDefinition(final String name,
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
   * @return The default value read with the string input rule: a list of display values
   */
  private List<String> getDefaultDisplayValues() {
    List<String> defaults = MultiValueCodec.parse(getDefaultValue());
    return defaults != null ? defaults : Collections.emptyList();
  }

  /**
   * @param item A fetched entry
   * @return Whether the entry's display value is one of the default values
   */
  @Override
  public boolean isDefaultSelected(final ValueItem item) {
    return item != null && getDefaultDisplayValues().contains(item.getDisplayValue());
  }

  /**
   * The default values that match no fetched entry's display value. They are offered as preselected
   * free-form entries when validation is disabled, and ignored when it is enabled.
   *
   * @param values The entries already fetched for this form
   * @return The unmatched, non-empty default values in order, or an empty list when validation is enabled
   */
  public List<String> getUnmatchedDefaults(final List<ValueItem> values) {
    if (isEnableValidation()) {
      return Collections.emptyList();
    }
    Set<String> displayed = values == null ? Collections.emptySet() : values.stream()
      .filter(Objects::nonNull)
      .map(ValueItem::getDisplayValue)
      .collect(Collectors.toSet());
    return getDefaultDisplayValues().stream()
      .filter(element -> !element.isEmpty() && !displayed.contains(element))
      .distinct()
      .collect(Collectors.toList());
  }

  @Override
  public ParameterDefinition copyWithDefaultValue(final ParameterValue defaultValue) {
    List<String> previous = defaultValue instanceof RestMultiListParameterValue
      ? ((RestMultiListParameterValue) defaultValue).getValue()
      : null;
    if (previous != null) {
      List<String> displayValues = previous.stream()
        .map(element -> ValueResolver.parseDisplayValue(getMimeType(), element, getDisplayExpression()))
        .collect(Collectors.toList());
      return new RestMultiListParameterDefinition(
        getName(), getDescription(), getRestEndpoint(), getCredentialId(), getMimeType(),
        getValueExpression(), getDisplayExpression(), getValueOrder(), getFilter(), getCacheTime(),
        MultiValueCodec.encode(displayValues), isAllowEmptyValue(), isEnableValidation(),
        Collections.emptyList(), getCustomHeaders());
    }
    else {
      return this;
    }
  }

  @Override
  public ParameterValue createValue(final String value) {
    RestMultiListParameterValue parameterValue =
      new RestMultiListParameterValue(getName(), MultiValueCodec.parse(value), getDescription());

    checkValue(parameterValue);
    return parameterValue;
  }

  /**
   * Reads the build form, which submits a {@code <select multiple>} as a Json array of the selected
   * options' values. A string is read with the string input rule; an absent value is the empty list.
   */
  @Override
  public ParameterValue createValue(final StaplerRequest2 req,
                                    final JSONObject jo)
  {
    Object submitted = jo.opt("value");
    List<String> elements;
    if (submitted instanceof JSONArray) {
      JSONArray array = (JSONArray) submitted;
      elements = new ArrayList<>(array.size());
      for (int i = 0; i < array.size(); i++) {
        elements.add(array.getString(i));
      }
    }
    else if (submitted instanceof String) {
      elements = MultiValueCodec.parse((String) submitted);
    }
    else {
      elements = Collections.emptyList();
    }
    RestMultiListParameterValue value = new RestMultiListParameterValue(getName(), elements, getDescription());

    checkValue(value);
    return value;
  }

  private void checkValue(final RestMultiListParameterValue value) {
    String offending = findOffendingElement(value.getValue());
    if (offending != null) {
      throw new IllegalArgumentException(Messages.RLP_Definition_ValueException(getName(), offending));
    }
  }

  @Override
  public boolean isValid(final ParameterValue value) {
    if (value == null) {
      return false;
    }
    List<String> elements;
    if (value instanceof RestMultiListParameterValue) {
      elements = ((RestMultiListParameterValue) value).getValue();
    }
    else if (value.getValue() instanceof String) {
      elements = MultiValueCodec.parse((String) value.getValue());
    }
    else {
      return false;
    }
    return findOffendingElement(elements) == null;
  }

  /**
   * Applies the per-element rules in one pass, fetching the entries at most once.
   *
   * @param elements The elements of a value
   * @return {@code null} when the value is valid, otherwise the detail for the error message: the first
   * offending element in list order, {@code ""} for an empty element, {@code []} for a disallowed empty
   * list, or {@code null} as text for a missing value
   */
  private String findOffendingElement(final List<String> elements) {
    if (elements == null) {
      return "null";
    }
    if (elements.isEmpty()) {
      return isAllowEmptyValue() ? null : "[]";
    }
    Set<String> fetched = null;
    if (isEnableValidation()) {
      Set<String> required = new LinkedHashSet<>();
      for (String element : elements) {
        if (element != null && !element.isEmpty()) {
          required.add(element);
        }
      }
      fetched = entryValuesFor(required);
    }
    for (String element : new LinkedHashSet<>(elements)) {
      if (element == null || element.isEmpty()) {
        return "\"\"";
      }
      if (fetched != null && !fetched.contains(element)) {
        return element;
      }
    }
    return null;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      getName(), getDescription(), getRestEndpoint(), getCredentialId(),
      getMimeType(), getValueExpression(), getFilter(), isAllowEmptyValue(), isEnableValidation());
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || this.getClass() != obj.getClass()) {
      return false;
    }
    RestMultiListParameterDefinition other = (RestMultiListParameterDefinition) obj;
    return Objects.equals(getName(), other.getName())
      && Objects.equals(getDescription(), other.getDescription())
      && Objects.equals(getRestEndpoint(), other.getRestEndpoint())
      && Objects.equals(getCredentialId(), other.getCredentialId())
      && Objects.equals(getMimeType(), other.getMimeType())
      && Objects.equals(getValueExpression(), other.getValueExpression())
      && Objects.equals(getFilter(), other.getFilter())
      && isAllowEmptyValue() == other.isAllowEmptyValue()
      && isEnableValidation() == other.isEnableValidation()
      && Objects.equals(getDefaultValue(), other.getDefaultValue());
  }

  @Symbol({"RESTMultiList", "RestMultiList", "RESTMultiListParam"})
  @Extension
  public static class DescriptorImpl extends AbstractRestListParameterDescriptor {
    @Override
    @Nonnull
    public String getDisplayName() {
      return Messages.RLP_MultiDescriptorImpl_DisplayName();
    }
  }
}
