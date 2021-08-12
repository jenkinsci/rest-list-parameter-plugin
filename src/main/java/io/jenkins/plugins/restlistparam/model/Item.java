package io.jenkins.plugins.restlistparam.model;

import java.io.Serializable;
import java.util.Objects;

public class Item implements Comparable<Item>, Serializable {
  private String value;
  private String displayValue;

  public Item() {
  }

  public Item(String value, String displayValue) {
    this.value = value;
    this.displayValue = displayValue;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public String getDisplayValue() {
    return displayValue;
  }

  public void setDisplayValue(String displayValue) {
    this.displayValue = displayValue;
  }

  @Override
  public String toString() {
    return value;
  }


  @Override
  public int compareTo(Item o) {
    return value.compareTo(o.getValue());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Item item = (Item) o;
    return Objects.equals(value, item.value) && Objects.equals(displayValue, item.displayValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value, displayValue);
  }
}
