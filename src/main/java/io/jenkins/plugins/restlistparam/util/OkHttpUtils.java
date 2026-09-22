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
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class OkHttpUtils {
  private static final Logger log = Logger.getLogger(OkHttpUtils.class.getName());
  private static final String PARAMETERS = "parameters";
  private static final String PARAMETER_ID = "restListParam";
  private static final long MEBIBYTE = 1024L * 1024L;

  private static final Object lock = new Object();
  /**
   * The client every request is derived from. OkHttp does not support several {@link Cache} instances on one
   * directory, so all requests share this client's cache (and connection pool).
   */
  private static OkHttpClient sharedClient;
  /** The cache directory and size {@link #sharedClient} was built for; {@code null} when it has no cache. */
  private static File sharedCacheDir;
  private static long sharedCacheSize;

  private OkHttpUtils() {
    throw new IllegalStateException("Utility class");
  }

  /**
   * Builds a OkHTTP client that respects any proxy set in the Jenkins 'Plugin Manager' and offers a response cache.
   * A proxy will only be applied if the host of the {@code httpEndpoint} is NOT part of the noProxy values.
   * The response cache is only present if the creation on disk is possible, otherwise there is none.
   * <p>
   * The client has no connect, read or write timeout; callers bound a request with a call timeout
   * (see {@link #getClient(String, Duration)}).
   *
   * @param httpEndpoint the host of the endpoint will get looked up against the noProxy values
   * @return OkHttpClient setup with an appropriate httpProxy value and response cache
   */
  public static OkHttpClient getClientWithProxyAndCache(final String httpEndpoint) {
    return getSharedClient().newBuilder()
      .proxy(getProxy(httpEndpoint))
      .build();
  }

  /**
   * Like {@link #getClientWithProxyAndCache(String)}, with a call timeout that covers the whole request:
   * DNS, connecting, sending the request and reading the response body.
   *
   * @param httpEndpoint the host of the endpoint will get looked up against the noProxy values
   * @param callTimeout  how long the call may take; must be positive
   */
  public static OkHttpClient getClient(final String httpEndpoint, final Duration callTimeout) {
    return getSharedClient().newBuilder()
      .proxy(getProxy(httpEndpoint))
      .callTimeout(callTimeout)
      .build();
  }

  /**
   * @return The shared client, rebuilt when the configured cache size or the Jenkins home changed
   */
  private static OkHttpClient getSharedClient() {
    Jenkins jenkins = Jenkins.getInstanceOrNull();
    synchronized (lock) {
      try {
        if (jenkins != null) {
          long cacheSize = RestListParameterGlobalConfig.get().getCacheSize() * MEBIBYTE;
          File cacheDir = getCacheDir(jenkins);
          if (sharedClient == null || !cacheDir.equals(sharedCacheDir) || cacheSize != sharedCacheSize) {
            log.fine(Messages.PLP_OkHttpUtils_fine_CacheCreationSuccess(cacheSize / MEBIBYTE));
            // The previous cache is not closed: requests still running may be using it.
            sharedClient = newBuilder().cache(new Cache(cacheDir, cacheSize)).build();
            sharedCacheDir = cacheDir;
            sharedCacheSize = cacheSize;
          }
          return sharedClient;
        }
        log.fine(Messages.PLP_OkHttpUtils_fine_NoJenkinsInstance());
      }
      catch (Exception ex) {
        log.warning(Messages.PLP_OkHttpUtils_warn_CacheIOException());
        log.fine("Cache creation failed with: " + ex.getClass().getName() + '\n'
                   + "EX Message: " + ex.getMessage());
      }

      // no cache; the next call tries again to create one
      if (sharedClient == null || sharedCacheDir != null) {
        sharedClient = newBuilder().build();
        sharedCacheDir = null;
      }
      return sharedClient;
    }
  }

  private static OkHttpClient.Builder newBuilder() {
    // the call timeout of each request is the only limit
    return new OkHttpClient.Builder()
      .connectTimeout(0, TimeUnit.MILLISECONDS)
      .readTimeout(0, TimeUnit.MILLISECONDS)
      .writeTimeout(0, TimeUnit.MILLISECONDS);
  }

  private static File getCacheDir(final Jenkins jenkins) throws Exception {
    FilePath parameterUserContent = jenkins.getRootPath()
                                           .child(PARAMETERS)
                                           .child(PARAMETER_ID);

    if (!parameterUserContent.exists() && !parameterUserContent.isDirectory()) {
      parameterUserContent.mkdirs();
    }

    return new File(Objects.requireNonNull(parameterUserContent.toURI().getPath()), "okhttp_cache");
  }

  private static Proxy getProxy(final String httpEndpoint) {
    Jenkins jenkins = Jenkins.getInstanceOrNull();
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
