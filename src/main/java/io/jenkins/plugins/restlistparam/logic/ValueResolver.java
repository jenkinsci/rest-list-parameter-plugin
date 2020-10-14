package io.jenkins.plugins.restlistparam.logic;

import com.jayway.jsonpath.InvalidJsonException;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import io.jenkins.plugins.restlistparam.Messages;
import io.jenkins.plugins.restlistparam.model.ResultContainer;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class ValueResolver {
  private static final Logger log = Logger.getLogger(ValueResolver.class.getName());

  private ValueResolver() {
    throw new IllegalStateException("Static Logic class");
  }

  /**
   * Parses a {@code xmlStr}, applies a xPath {@code expression} and returns a list of strings based on the result
   *
   * @param xmlStr     The XML text as string
   * @param expression The xPath expression
   * @return A {@link ResultContainer} capsuling either a list of strings or a user friendly error message
   */
  public static ResultContainer<List<String>> resolveXPath(final String xmlStr,
                                                           final String expression)
  {
    ResultContainer<List<String>> container = new ResultContainer<>(Collections.emptyList());

    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document xmlDocument = builder.parse(new InputSource(new StringReader(xmlStr)));
      XPath xPath = XPathFactory.newInstance().newXPath();

      NodeList nodeList = (NodeList) xPath.evaluate(expression, xmlDocument, XPathConstants.NODESET);
      if (nodeList.getLength() > 0) {
        container.setValue(xmlNodeListToList(nodeList));
      }
      else {
        log.warning(Messages.RLP_ValueResolver_warn_xPath_NoValues());
        log.fine(buildFineLogMsg(Messages.RLP_ValueResolver_warn_xPath_NoValues(), expression, xmlStr));
        container.setErrorMsg(Messages.RLP_ValueResolver_warn_xPath_NoValues());
      }
    }
    catch (XPathExpressionException | IllegalArgumentException ignore) {
      log.warning(Messages.RLP_ValueResolver_warn_xPath_ExpressionErr());
      log.fine(buildFineLogMsg(Messages.RLP_ValueResolver_warn_xPath_ExpressionErr(), expression, xmlStr));
      container.setErrorMsg(Messages.RLP_ValueResolver_warn_xPath_ExpressionErr());
    }
    catch (SAXParseException ignore) {
      log.warning(Messages.RLP_ValueResolver_warn_xPath_MalformedXml());
      log.fine(buildFineLogMsg(Messages.RLP_ValueResolver_warn_xPath_MalformedXml(), expression, xmlStr));
      container.setErrorMsg(Messages.RLP_ValueResolver_warn_xPath_MalformedXml());
    }
    catch (Exception ex) {
      log.warning(Messages.RLP_ValueResolver_warn_xPath_ParserInit(ex.getClass().getName()));
      log.fine(buildFineLogMsg(Messages.RLP_ValueResolver_warn_xPath_ParserInit(ex.getClass().getName()), expression, xmlStr));
      container.setErrorMsg(Messages.RLP_ValueResolver_warn_xPath_ParserInit(ex.getClass().getName()));
    }

    return container;
  }

  /**
   * A helper method to convert a XML {@link NodeList} to a list of strings
   *
   * @param nodeList The {@link NodeList} to convert
   * @return A list of strings converted from the {@code nodeList} (can be empty if {@code nodeList} is empty)
   */
  private static List<String> xmlNodeListToList(NodeList nodeList) {
    List<String> res = new ArrayList<>(nodeList.getLength());

    for (int i = 0; i < nodeList.getLength(); ++i) {
      res.add(nodeList.item(i).getTextContent());
    }

    return res;
  }

  /**
   * Parses a {@code jsonStr}, applies a Json-Path {@code expression} and returns a list of strings based on the result
   *
   * @param jsonStr    The Json text as string
   * @param expression The Json-Path expression
   * @return A {@link ResultContainer} capsuling either a list of strings or a user friendly error message
   */
  public static ResultContainer<List<String>> resolveJsonPath(final String jsonStr,
                                                              final String expression)
  {
    ResultContainer<List<String>> container = new ResultContainer<>(Collections.emptyList());

    try {
      final List<String> resolved = JsonPath.parse(jsonStr).read(expression);

      if (!resolved.isEmpty()) {
        container.setValue(new ArrayList<>(resolved));
      }
      else {
        log.warning(Messages.RLP_ValueResolver_warn_jPath_NoValues());
        log.fine(buildFineLogMsg(Messages.RLP_ValueResolver_warn_jPath_NoValues(), expression, jsonStr));
        container.setErrorMsg(Messages.RLP_ValueResolver_warn_jPath_NoValues());
      }
    }
    catch (PathNotFoundException ignored) {
      log.warning(Messages.RLP_ValueResolver_warn_jPath_NoValues());
      log.fine(buildFineLogMsg(Messages.RLP_ValueResolver_warn_jPath_NoValues(), expression, jsonStr));
      container.setErrorMsg(Messages.RLP_ValueResolver_warn_jPath_NoValues());
    }
    catch (InvalidPathException ignored) {
      log.warning(Messages.RLP_ValueResolver_warn_jPath_ExpressionErr());
      log.fine(buildFineLogMsg(Messages.RLP_ValueResolver_warn_jPath_ExpressionErr(), expression, jsonStr));
      container.setErrorMsg(Messages.RLP_ValueResolver_warn_jPath_ExpressionErr());
    }
    catch (InvalidJsonException ignored) {
      log.warning(Messages.RLP_ValueResolver_warn_jPath_MalformedJson());
      log.fine(buildFineLogMsg(Messages.RLP_ValueResolver_warn_jPath_MalformedJson(), expression, jsonStr));
      container.setErrorMsg(Messages.RLP_ValueResolver_warn_jPath_MalformedJson());
    }

    return container;
  }

  /**
   * A helper method to generate user friendly debug messages (Log level {@code finer})
   *
   * @param errorMsg    The error message itself
   * @param expression  The Json-Path or xPath expression used
   * @param valueString The Json or XML string used
   * @return A beautified string ready for the debug log
   */
  private static String buildFineLogMsg(String errorMsg,
                                        String expression,
                                        String valueString)
  {
    return "ERROR: " + errorMsg + "\n"
      + "Expression: '" + expression + "'\n"
      + "ValueString: \n" + valueString + "\n";
  }
}
