package io.jenkins.plugins.restlistparam.util;

import hudson.FilePath;
import jenkins.model.Jenkins;
import okhttp3.Cache;
import okhttp3.OkHttpClient;

import java.io.File;
import java.util.logging.Logger;

public class OKHttpUtil {
  private static final Logger log = Logger.getLogger(OKHttpUtil.class.getName());
  private static final String USER_CONTENT = "userContent";
  private static final String PARAMETERS = "parameters";

  private OKHttpUtil() {
    throw new IllegalStateException("Utility class");
  }

  public static OkHttpClient buildClientWitCache() {
    try {
      Jenkins jenkins = Jenkins.getInstanceOrNull();
      if (jenkins != null) {
        FilePath parameterUserContent = jenkins.getRootPath()
                                               .child(USER_CONTENT)
                                               .child(PARAMETERS)
                                               .child("restListParam");

        if (!parameterUserContent.exists() && !parameterUserContent.isDirectory()) {
          parameterUserContent.mkdirs();
        }

        File cacheDir = new File(parameterUserContent.toURI().getPath(), "okhttp_cache");
        log.info("Using OKHttpClient WITH 50 MiB cache");
        return new OkHttpClient.Builder()
          .cache(new Cache(cacheDir, 50L * 1024L * 1024L))
          .build();
      }
      else {
        log.info("Using OKHttpClient WITHOUT cache");
        log.fine("Could not get Jenkins instance");
        return new OkHttpClient.Builder().build();
      }
    }
    catch (Exception ex) {
      log.info("Using OKHttpClient WITHOUT cache");
      log.fine("Cache creation failed with: " + ex.getClass().getName() + '\n'
                 + "EX Message: " + ex.getMessage());
      return new OkHttpClient.Builder().build();
    }
  }
}
