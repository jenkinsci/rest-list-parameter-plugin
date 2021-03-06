package io.jenkins.plugins.restlistparam.util;

import hudson.FilePath;
import io.jenkins.plugins.restlistparam.Messages;
import io.jenkins.plugins.restlistparam.RestListParameterGlobalConfig;
import jenkins.model.Jenkins;
import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.OkHttpClient;

import java.io.File;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class OkHttpUtils {
  private static final Logger log = Logger.getLogger(OkHttpUtils.class.getName());
  private static final Jenkins jenkins = Jenkins.getInstanceOrNull();
  private static final String PARAMETERS = "parameters";
  private static final String PARAMETER_ID = "restListParam";
  private static final long MEBIBYTE = 1024L * 1024L;

  private OkHttpUtils() {
    throw new IllegalStateException("Utility class");
  }

  /**
   * Builds a OkHTTP client that respects any proxy set in the Jenkins 'Plugin Manager' and offers a response cache.
   * A proxy will only be applied if the host of the {@code httpEndpoint} is NOT part of the noProxy values.
   * The response cache is only present if the creation on disk is possible, otherwise there is none
   *
   * @param httpEndpoint the host of the endpoint will get looked up against the noProxy values
   * @return OkHttpClient setup with an appropriate httpProxy value and response cache
   */
  public static OkHttpClient getClientWithProxyAndCache(final String httpEndpoint) {
    try {
      if (jenkins != null) {
        final RestListParameterGlobalConfig config = RestListParameterGlobalConfig.get();
        FilePath parameterUserContent = jenkins.getRootPath()
                                               .child(PARAMETERS)
                                               .child(PARAMETER_ID);

        if (!parameterUserContent.exists() && !parameterUserContent.isDirectory()) {
          parameterUserContent.mkdirs();
        }

        File cacheDir = new File(parameterUserContent.toURI().getPath(), "okhttp_cache");
        log.fine(Messages.PLP_OkHttpUtils_fine_CacheCreationSuccess(config.getCacheSize()));
        return new OkHttpClient.Builder()
          .cache(new Cache(cacheDir, config.getCacheSize() * MEBIBYTE))
          .proxy(getProxy(httpEndpoint))
          .build();
      }
      else {
        log.fine(Messages.PLP_OkHttpUtils_fine_NoJenkinsInstance());
      }
    }
    catch (Exception ex) {
      log.warning(Messages.PLP_OkHttpUtils_warn_CacheIOException());
      log.fine("Cache creation failed with: " + ex.getClass().getName() + '\n'
                 + "EX Message: " + ex.getMessage());
    }

    return new OkHttpClient.Builder()
      .proxy(getProxy(httpEndpoint))
      .build();
  }

  private static Proxy getProxy(final String httpEndpoint) {
    if (jenkins == null || jenkins.proxy == null) {
      return Proxy.NO_PROXY;
    }
    else {
      try {
        return jenkins.proxy.createProxy(new URL(httpEndpoint).getHost());
      }
      catch (MalformedURLException e) {
        return jenkins.proxy.createProxy(httpEndpoint);
      }
    }
  }

  public static CacheControl getCacheControl(final Integer minutesCached) {
    return new CacheControl.Builder()
      .maxAge(minutesCached, TimeUnit.MINUTES)
      .build();
  }
}
