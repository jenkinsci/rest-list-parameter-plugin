package io.jenkins.plugins.restlistparam.model;

public class AuthCredentials {
  private final AuthType type;
  private final String username;
  private final String secret;

  public AuthCredentials(String username,
                         String secret,
                         AuthType type)
  {
    this.username = username;
    this.secret = secret;
    this.type = type;
  }

  public String getUsername()
  {
    return username;
  }

  public String getSecret()
  {
    return secret;
  }

  public AuthType getType()
  {
    return type;
  }
}
