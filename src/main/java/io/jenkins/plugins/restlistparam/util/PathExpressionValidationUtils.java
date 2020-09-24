package io.jenkins.plugins.restlistparam.util;

import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import hudson.util.FormValidation;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

public class PathExpressionValidationUtils {

  private PathExpressionValidationUtils() {
    throw new IllegalStateException("Utility class");
  }

  public static FormValidation doCheckXPathExpression(final String expression) {
    try {
      XPathFactory factory = XPathFactory.newInstance();
      XPath xpath = factory.newXPath();
      xpath.compile(expression);
      return FormValidation.ok();
    } catch (XPathExpressionException ignore) {
      return FormValidation.error("The provided xPath expression seems to be incorrect");
    }
  }

  public static FormValidation doCheckJsonPathExpression(final String expression) {
    try {
      JsonPath.compile(expression);
      return FormValidation.ok();
    }
    catch (InvalidPathException ignore) {
      return FormValidation.error("The provided Json-Path expression seems to be incorrect");
    }
  }
}
