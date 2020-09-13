package io.jenkins.plugins.restlistparam.model;

public enum AuthType {
  BASIC("Basic"),
  BEARER("Bearer");

  private final String mode;

  AuthType(String mode)
  {
    this.mode = mode;
  }


  @Override
  public String toString()
  {
    return mode;
  }
}
