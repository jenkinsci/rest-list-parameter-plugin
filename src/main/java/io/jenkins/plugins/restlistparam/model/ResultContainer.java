package io.jenkins.plugins.restlistparam.model;

import java.util.Optional;

public class ResultContainer<V> {
  private String errorMsg = null;
  private V value;

  public ResultContainer(V defaultValue) {
    this.value = defaultValue;
  }

  public void setErrorMsg(String errorMsg) {
    this.errorMsg = errorMsg;
  }

  public Optional<String> getErrorMsg() {
    return Optional.ofNullable(errorMsg);
  }

  public void setValue(V value) {
    this.value = value;
  }

  public V getValue() {
    return value;
  }
}
