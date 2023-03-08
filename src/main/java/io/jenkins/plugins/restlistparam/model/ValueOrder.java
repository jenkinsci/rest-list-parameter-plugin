package io.jenkins.plugins.restlistparam.model;

public enum ValueOrder {
  NONE,
  ASC,
  DSC,
  REV;

  @Override
  public String toString() {
    switch (this) {
      case NONE:
        return "None";
      case ASC:
        return "Ascending";
      case DSC:
        return "Descending";
      case REV:
        return "Reversed";
      default:
        return "Undefined";
    }
  }
}
