package io.jenkins.plugins.restlistparam.model;

import java.util.Optional;

public class ResultContainer<V> {
  private String errorMsg = null;
  private boolean noValues = false;
  private V value;
  private int pagesFetched = 1;
  private boolean pageLimitReached = false;

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

  /**
   * @return How many pages were fetched to produce this result (1 without pagination)
   */
  public int getPagesFetched() {
    return pagesFetched;
  }

  public void setPagesFetched(int pagesFetched) {
    this.pagesFetched = pagesFetched;
  }

  /**
   * @return Whether pagination stopped at the page limit while a next page was still available
   */
  public boolean isPageLimitReached() {
    return pageLimitReached;
  }

  public void setPageLimitReached(boolean pageLimitReached) {
    this.pageLimitReached = pageLimitReached;
  }
}
