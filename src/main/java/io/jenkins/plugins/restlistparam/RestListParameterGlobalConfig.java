package io.jenkins.plugins.restlistparam;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.util.FormValidation;
import jenkins.YesNoMaybe;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

@Extension(dynamicLoadable = YesNoMaybe.YES)
@Symbol({"restListParam", "restListParamConfig"})
public final class RestListParameterGlobalConfig extends GlobalConfiguration {

  /**
   * The default for {@link #cacheSize}.
   */
  private static final long DEFAULT_CACHE_SIZE = 50L;

  /**
   * The default for {@link #cacheTime}.
   */
  private static final int DEFAULT_CACHE_TIME = 0;

  private Long cacheSize;
  private Integer cacheTime;

  public RestListParameterGlobalConfig() {
    load();
  }

  /**
   * Get the current RestListParameter global configuration.
   *
   * @return the RestListParameter configuration, or {@code null} if Jenkins has been shut down
   */
  public static RestListParameterGlobalConfig get() {
    return ExtensionList.lookupSingleton(RestListParameterGlobalConfig.class);
  }

  public Long getCacheSize() {
    return cacheSize != null && cacheSize > 0 ? cacheSize : DEFAULT_CACHE_SIZE;
  }

  @DataBoundSetter
  public void setCacheSize(Long cacheSize) {
    this.cacheSize = cacheSize;
    save();
  }

  @POST
  public FormValidation doCheckCacheSize(@QueryParameter Long cacheSize) {
    if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
      return FormValidation.ok();
    }

    if (cacheSize != null && cacheSize > 0) {
      return FormValidation.ok();
    }

    return FormValidation.error(Messages.RLP_GlobalConfig_ValidationErr_CacheSize());
  }

  public Integer getCacheTime() {
    return cacheTime != null && cacheTime > 0 ? cacheTime : DEFAULT_CACHE_TIME;
  }

  @DataBoundSetter
  public void setCacheTime(Integer cacheTime) {
    this.cacheTime = cacheTime;
    save();
  }

  @POST
  public FormValidation doCheckCacheTime(@QueryParameter Integer cacheTime) {
    if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
      return FormValidation.ok();
    }

    if (cacheTime != null && cacheTime >= 0) {
      return FormValidation.ok();
    }

    return FormValidation.error(Messages.RLP_GlobalConfig_ValidationErr_CacheTime());
  }
}
