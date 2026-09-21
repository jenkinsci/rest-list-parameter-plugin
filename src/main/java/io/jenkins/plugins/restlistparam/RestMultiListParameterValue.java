package io.jenkins.plugins.restlistparam;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.ParameterValue;
import hudson.model.Run;
import hudson.util.VariableResolver;
import io.jenkins.plugins.restlistparam.util.MultiValueCodec;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * {@link ParameterValue} created from {@link RestMultiListParameterDefinition}: an ordered list of
 * distinct strings. Pipeline receives the list itself; the build environment receives it as a
 * compact Json array (see {@link MultiValueCodec#encode(List)}).
 */
public final class RestMultiListParameterValue extends ParameterValue {
  private static final long serialVersionUID = 1L;

  @Restricted(NoExternalUse.class)
  private final ArrayList<String> value;

  @DataBoundConstructor
  public RestMultiListParameterValue(final String name,
                                     final List<String> value)
  {
    this(name, value, null);
  }

  /**
   * @param value The elements in the order supplied; duplicates are collapsed, keeping the first
   *              occurrence. {@code null} means the value is missing.
   */
  public RestMultiListParameterValue(final String name,
                                     final List<String> value,
                                     final String description)
  {
    super(name, description);
    this.value = value != null ? new ArrayList<>(new LinkedHashSet<>(value)) : null;
  }

  /**
   * Exposes the name/value as an environment variable holding the Json array.
   */
  @Override
  public void buildEnvironment(Run<?, ?> build,
                               EnvVars env)
  {
    if (value == null) {
      return;
    }
    String encoded = MultiValueCodec.encode(value);
    env.put(name, encoded);
    env.put(name.toUpperCase(Locale.ENGLISH), encoded); // backward compatibility pre 1.345
  }

  @Override
  public VariableResolver<String> createVariableResolver(AbstractBuild<?, ?> build) {
    return name -> RestMultiListParameterValue.this.name.equals(name) && value != null
      ? MultiValueCodec.encode(value)
      : null;
  }

  /**
   * @return The unmodifiable list of elements, or {@code null} when the value is missing
   */
  @Override
  @Exported(visibility = 4)
  public List<String> getValue() {
    return value != null ? Collections.unmodifiableList(value) : null;
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + Objects.hashCode(value);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!super.equals(obj)) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    return Objects.equals(value, ((RestMultiListParameterValue) obj).value);
  }

  @Override
  public String toString() {
    return "{" +
      "\"type\": \"RestMultiListParameterValue\", " +
      "\"name:\": \"" + getName() + "\", " +
      "\"value\": " + (value != null ? MultiValueCodec.encode(value) : "null") +
      "}";
  }

  @Override
  public String getShortDescription() {
    return name + '=' + (value != null ? MultiValueCodec.encode(value) : "");
  }
}
