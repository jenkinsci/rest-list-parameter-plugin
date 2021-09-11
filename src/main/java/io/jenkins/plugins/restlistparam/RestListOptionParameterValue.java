package io.jenkins.plugins.restlistparam;

import hudson.model.ParameterValue;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.export.Exported;

public class RestListOptionParameterValue extends ParameterValue {
  @Exported(visibility = 4)
  @Restricted(NoExternalUse.class)
  public String index;

  public RestListOptionParameterValue(String name, String description, String index) {
    super(name, description);
    this.index = index;
  }

  @DataBoundConstructor
  public RestListOptionParameterValue(String name, String value) {
    this(name, null, value);
  }

  public String getIndex() {
    return index;
  }

  @DataBoundSetter
  public void setIndex(String index) {
    this.index = index;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (!super.equals(obj)) {
      return false;
    }
    RestListOptionParameterValue other = (RestListOptionParameterValue) obj;
    return index.equals(other.getIndex());
  }

  @Override
  public int hashCode()
  {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((index == null) ? 0 : index.hashCode());
    return result;
  }
}
