package io.jenkins.plugins.restlistparam.util;

import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import hudson.util.FormValidation;
import io.jenkins.plugins.restlistparam.Messages;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

public class PathExpressionValidationUtils {

  private PathExpressionValidationUtils() {
    throw new IllegalStateException("Utility class");
  }

  public static FormValidation doCheckXPathExpression(final String expression) {
    try {
      XPath xpath = XPathFactory.newInstance().newXPath();
      xpath.compile(expression);
      return FormValidation.ok();
    }
    catch (XPathExpressionException ignore) {
      return FormValidation.error(Messages.RLP_PathExpressionValidationUtil_FormErr_xPath());
    }
  }

  public static FormValidation doCheckJsonPathExpression(final String expression) {
    try {
      JsonPath.compile(expression);
      return FormValidation.ok();
    }
    catch (InvalidPathException ignore) {
      return FormValidation.error(Messages.RLP_PathExpressionValidationUtil_FormErr_jPath());
    }
  }
}
