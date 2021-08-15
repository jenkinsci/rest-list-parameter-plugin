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
  public int index;

  public RestListOptionParameterValue(String name, String description, int index) {
    super(name, description);
    this.index = index;
  }

  public RestListOptionParameterValue(String name, int index) {
    this(name, null, index);
  }

  @DataBoundConstructor
  public RestListOptionParameterValue(String name, String value) {
    this(name, null, Integer.parseInt(value));
  }

  public int getIndex() {
    return index;
  }

  @DataBoundSetter
  public void setIndex(int index) {
    this.index = index;
  }
}
