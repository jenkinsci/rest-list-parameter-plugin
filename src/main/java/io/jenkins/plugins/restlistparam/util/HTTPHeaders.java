package io.jenkins.plugins.restlistparam.util;

public class HTTPHeaders {
  private HTTPHeaders() {
    throw new IllegalStateException("Static strings utility class");
  }

  public static final String AUTHORIZATION = "Authorization";
  public static final String ACCEPT = "Accept";
}
