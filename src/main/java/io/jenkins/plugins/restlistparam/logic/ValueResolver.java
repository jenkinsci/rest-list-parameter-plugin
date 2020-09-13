package io.jenkins.plugins.restlistparam.logic;

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
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

public class ValueResolver {
  private static final Logger log = Logger.getLogger(ValueResolver.class.getName());

  private ValueResolver()
  {
    throw new IllegalStateException("Static Logic class");
  }

  public static ResultContainer<Collection<String>> resolveXPath(final String xmlStr,
                                                                 final String expression)
  {
    ResultContainer<Collection<String>> container = new ResultContainer<>(Collections.emptyList());

    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document xmlDocument = builder.parse(new InputSource(new StringReader(xmlStr)));
      XPath xPath = XPathFactory.newInstance().newXPath();

      NodeList nodeList = (NodeList) xPath.evaluate(expression, xmlDocument, XPathConstants.NODESET);
      if (nodeList.getLength() > 0) {
        container.setValue(xmlNodeListToCollection(nodeList));
      }
      else {
        log.warning(Messages.RLP_ValueResolver_warn_xPath_NoValues());
        log.fine(buildFineLogMsg(Messages.RLP_ValueResolver_warn_xPath_NoValues(), expression, xmlStr));
        container.setErrorMsg(Messages.RLP_ValueResolver_warn_xPath_NoValues());
      }
    } catch (SAXParseException saxEx) {
      log.warning(Messages.RLP_ValueResolver_warn_xPath_ExpressionErr());
      log.fine(buildFineLogMsg(Messages.RLP_ValueResolver_warn_xPath_ExpressionErr(), expression, xmlStr));
      container.setErrorMsg(Messages.RLP_ValueResolver_warn_xPath_ExpressionErr());
    } catch (Exception ex) {
      log.warning(Messages.RLP_ValueResolver_warn_xPath_GenericErr(ex.getClass().getName()));
      log.fine(buildFineLogMsg(Messages.RLP_ValueResolver_warn_xPath_GenericErr(ex.getClass().getName()), expression, xmlStr));
      container.setErrorMsg(Messages.RLP_ValueResolver_warn_xPath_GenericErr(ex.getClass().getName()));
    }

    return container;
  }

  public static ResultContainer<Collection<String>> resolveJsonPath(final String jsonStr,
                                                                    final String expression)
  {
    ResultContainer<Collection<String>> container = new ResultContainer<>(Collections.emptyList());

    try {
      final Collection<String> resolved = JsonPath.parse(jsonStr).read(expression);

      if (!resolved.isEmpty()) {
        container.setValue(resolved);
      }
      else {
        log.warning(Messages.RLP_ValueResolver_warn_jPath_NoValues());
        log.fine(buildFineLogMsg(Messages.RLP_ValueResolver_warn_jPath_NoValues(), expression, jsonStr));
        container.setErrorMsg(Messages.RLP_ValueResolver_warn_jPath_NoValues());
      }
    } catch (PathNotFoundException pnfEx) {
      log.warning(Messages.RLP_ValueResolver_warn_jPath_ExpressionErr());
      log.fine(buildFineLogMsg(Messages.RLP_ValueResolver_warn_jPath_ExpressionErr(), expression, jsonStr));
      container.setErrorMsg(Messages.RLP_ValueResolver_warn_jPath_ExpressionErr());
    }

    return container;
  }

  private static Collection<String> xmlNodeListToCollection(NodeList nodeList)
  {
    Collection<String> res = new ArrayList<>(nodeList.getLength());

    for (int i = 0; i < nodeList.getLength(); ++i) {
      res.add(nodeList.item(i).getTextContent());
    }

    return res;
  }

  private static String buildFineLogMsg(String errorMsg,
                                        String expression,
                                        String valueString)
  {
    return "ERROR: " + errorMsg + "\n"
      + "Expression: '" + expression + "'\n"
      + "ValueString: \n" + valueString + "\n";
  }
}
