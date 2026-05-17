package io.jenkins.plugins.restlistparam;

import hudson.model.Descriptor;
import hudson.model.ParameterDefinition;
import hudson.model.StringParameterValue;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@WithJenkins
class RestListParameterDefinitionJenkinsTest {

  /**
   * Regression guard: ensures the {@code DescriptorImpl} is picked up as an Extension.
   * A silent failure here is what produces {@code Invalid parameter type "RESTList"} in
   * Pipeline DSL when the sezpoz extension index is missing from the packaged hpi.
   */
  @Test
  void descriptorIsRegistered(JenkinsRule r) {
    ParameterDefinition.ParameterDescriptor descriptor =
      (ParameterDefinition.ParameterDescriptor) Jenkins.get().getDescriptor(RestListParameterDefinition.class);
    assertNotNull(descriptor, "DescriptorImpl not registered — plugin failed to load");
    assertEquals(RestListParameterDefinition.DescriptorImpl.class, descriptor.getClass());
  }

  /**
   * Regression guard: verifies the {@code RESTList} symbol resolves and the
   * {@code allowEmptyValue} flag flows through {@code DescribableModel}, which is what the
   * workflow-cps Pipeline DSL uses to instantiate parameters by symbol.
   */
  @Test
  void pipelineDslAcceptsRestListSymbolWithAllowEmptyValue(JenkinsRule r) throws Exception {
    Descriptor<?> bySymbol = SymbolLookup.get().findDescriptor(ParameterDefinition.class, "RESTList");
    assertNotNull(bySymbol, "@Symbol(\"RESTList\") not registered — extension index broken");
    assertEquals(RestListParameterDefinition.DescriptorImpl.class, bySymbol.getClass());

    DescribableModel<RestListParameterDefinition> model = new DescribableModel<>(RestListParameterDefinition.class);
    Map<String, Object> args = new HashMap<>();
    args.put("name", "VERSION");
    args.put("description", "pick a version");
    args.put("restEndpoint", "http://127.0.0.1:1/none");
    args.put("credentialId", "");
    args.put("mimeType", "APPLICATION_JSON");
    args.put("valueExpression", "$.tags");
    args.put("displayExpression", "$");
    args.put("allowEmptyValue", true);
    args.put("defaultValue", "");

    RestListParameterDefinition def = model.instantiate(args);
    assertEquals("VERSION", def.getName());
    assertTrue(def.isAllowEmptyValue(), "allowEmptyValue DataBoundSetter not applied");
    assertEquals("", def.getDefaultValue());
  }

  @Test
  void allowEmptyValueDefaultsToFalse(JenkinsRule r) {
    RestListParameterDefinition def = new RestListParameterDefinition(
      "p", "d", "http://127.0.0.1:1/none", "", MimeType.APPLICATION_JSON, "$.*", "$");
    assertFalse(def.isAllowEmptyValue());
  }

  @Test
  void isValidAcceptsEmptyOnlyWhenAllowed(JenkinsRule r) {
    RestListParameterDefinition allowed = new RestListParameterDefinition(
      "p", "d", "http://127.0.0.1:1/none", "", MimeType.APPLICATION_JSON, "$.*", "$",
      ValueOrder.NONE, ".*", 0, "", true);
    // allowEmptyValue short-circuits before getValues(), so this does not depend on the REST call.
    assertTrue(allowed.isValid(new StringParameterValue("p", "")));

    RestListParameterDefinition denied = new RestListParameterDefinition(
      "p", "d", "http://127.0.0.1:1/none", "", MimeType.APPLICATION_JSON, "$.*", "$",
      ValueOrder.NONE, ".*", 0, "", false);
    assertFalse(denied.isValid(new StringParameterValue("p", "")));
  }

  @Test
  void createValueAcceptsEmptyWhenAllowed(JenkinsRule r) {
    RestListParameterDefinition def = new RestListParameterDefinition(
      "p", "d", "http://127.0.0.1:1/none", "", MimeType.APPLICATION_JSON, "$.*", "$",
      ValueOrder.NONE, ".*", 0, "", true);
    try {
      def.createValue("");
    } catch (IllegalArgumentException e) {
      fail("empty value should be accepted when allowEmptyValue=true: " + e.getMessage());
    }
  }

  @Test
  void createValueRejectsEmptyByDefault(JenkinsRule r) {
    RestListParameterDefinition def = new RestListParameterDefinition(
      "p", "d", "http://127.0.0.1:1/none", "", MimeType.APPLICATION_JSON, "$.*", "$",
      ValueOrder.NONE, ".*", 0, "", false);
    try {
      def.createValue("anything");
      fail("expected IllegalArgumentException for unknown value");
    } catch (IllegalArgumentException expected) {
      // ok
    }
  }

  @Test
  void enableValidationDefaultsToTrue(JenkinsRule r) {
    RestListParameterDefinition def = new RestListParameterDefinition(
      "p", "d", "http://127.0.0.1:1/none", "", MimeType.APPLICATION_JSON, "$.*", "$");
    assertTrue(def.isEnableValidation());
  }

  @Test
  void isValidAcceptsArbitraryValueWhenValidationDisabled(JenkinsRule r) {
    RestListParameterDefinition def = new RestListParameterDefinition(
      "p", "d", "http://127.0.0.1:1/none", "", MimeType.APPLICATION_JSON, "$.*", "$",
      ValueOrder.NONE, ".*", 0, "", false);
    def.setEnableValidation(false);
    // With validation disabled, isValid must NOT trigger getValues() and must accept
    // anything non-null — that's the whole point of bypassing pagination limits.
    assertTrue(def.isValid(new StringParameterValue("p", "freely-typed-value")));
  }

  @Test
  void createValueAcceptsArbitraryWhenValidationDisabled(JenkinsRule r) {
    RestListParameterDefinition def = new RestListParameterDefinition(
      "p", "d", "http://127.0.0.1:1/none", "", MimeType.APPLICATION_JSON, "$.*", "$",
      ValueOrder.NONE, ".*", 0, "", false);
    def.setEnableValidation(false);
    try {
      def.createValue("page-2-value-not-in-first-page");
    } catch (IllegalArgumentException e) {
      fail("arbitrary value should be accepted when enableValidation=false: " + e.getMessage());
    }
  }

  @Test
  void pipelineDslAcceptsEnableValidationFalse(JenkinsRule r) throws Exception {
    DescribableModel<RestListParameterDefinition> model = new DescribableModel<>(RestListParameterDefinition.class);
    Map<String, Object> args = new HashMap<>();
    args.put("name", "VERSION");
    args.put("description", "pick a version");
    args.put("restEndpoint", "http://127.0.0.1:1/none");
    args.put("credentialId", "");
    args.put("mimeType", "APPLICATION_JSON");
    args.put("valueExpression", "$.tags");
    args.put("displayExpression", "$");
    args.put("enableValidation", false);

    RestListParameterDefinition def = model.instantiate(args);
    assertFalse(def.isEnableValidation(), "enableValidation DataBoundSetter not applied");
  }
}
