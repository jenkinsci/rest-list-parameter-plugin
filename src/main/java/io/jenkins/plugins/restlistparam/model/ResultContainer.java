package io.jenkins.plugins.restlistparam.model;

import java.util.Optional;

public class ResultContainer<V> {
  private String errorMsg = null;
  private boolean noValues = false;
  private V value;

  public ResultContainer(V defaultValue) {
    this.value = defaultValue;
  }

  public void setErrorMsg(String errorMsg) {
    this.errorMsg = errorMsg;
  }

  /**
   * Records that the response was fetched and parsed fine but yielded no values.
   * Unlike other errors, this may be an expected outcome (see {@code allowEmptyValue}).
   */
  public void setNoValues(String errorMsg) {
    this.errorMsg = errorMsg;
    this.noValues = true;
  }

  public boolean isNoValues() {
    return noValues && errorMsg != null;
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
