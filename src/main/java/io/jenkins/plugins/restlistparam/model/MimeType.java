package io.jenkins.plugins.restlistparam.model;

public enum MimeType {
  APPLICATION_JSON("application/json"),
  APPLICATION_XML("application/xml");

  private final String mime;

  MimeType(String mime)
  {
    this.mime = mime;
  }

  public String getMime()
  {
    return mime;
  }
}
