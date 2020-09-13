package io.jenkins.plugins.restlistparam;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.ParameterValue;
import hudson.model.Run;
import hudson.util.VariableResolver;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

import java.util.Locale;

/**
 * {@link ParameterValue} created from {@link RestListParameterDefinition}.
 */
public class RestListParameterValue extends ParameterValue {
  @Exported(visibility = 4)
  @Restricted(NoExternalUse.class)
  public String value;

  @DataBoundConstructor
  public RestListParameterValue(String name,
                                String value)
  {
    this(name, value, null);
  }

  public RestListParameterValue(String name,
                                String value,
                                String description)
  {
    super(name, description);
    this.value = value;
  }

  /**
   * Exposes the name/value as an environment variable.
   */
  @Override
  public void buildEnvironment(Run<?, ?> build,
                               EnvVars env)
  {
    env.put(name, value);
    env.put(name.toUpperCase(Locale.ENGLISH), value); // backward compatibility pre 1.345
  }

  @Override
  public VariableResolver<String> createVariableResolver(AbstractBuild<?, ?> build)
  {
    return name -> RestListParameterValue.this.name.equals(name) ? value : null;
  }

  @Override
  public String getValue()
  {
    return value;
  }

  @Override
  public int hashCode()
  {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((value == null) ? 0 : value.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (this == obj) {
      return true;
    }
    if (!super.equals(obj)) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }

    RestListParameterValue other = (RestListParameterValue) obj;
    if (value == null) {
      return other.value == null;
    }
    else {
      return value.equals(other.value);
    }
  }

  @Override
  public String toString()
  {
    return "(RestListParameterValue) " + getName() + "='" + value + "'";
  }

  @Override
  public String getShortDescription()
  {
    return name + '=' + value;
  }
}
