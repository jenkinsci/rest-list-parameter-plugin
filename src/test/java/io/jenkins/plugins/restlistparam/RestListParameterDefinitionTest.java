package io.jenkins.plugins.restlistparam;

import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.ValueItem;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@WithJenkins
class RestListParameterDefinitionTest {

  @Test
  void nullDisplayExpressionDefaultsToRoot(JenkinsRule r) {
    RestListParameterDefinition def = new RestListParameterDefinition(
      "PARAM", "desc", "https://example.com/api", null, MimeType.APPLICATION_JSON, "$.tags", null);
    assertEquals("$", def.getDisplayExpression());
  }

  @Test
  void blankDisplayExpressionDefaultsToRoot(JenkinsRule r) {
    RestListParameterDefinition def = new RestListParameterDefinition(
      "PARAM", "desc", "https://example.com/api", null, MimeType.APPLICATION_JSON, "$.tags", "  ");
    assertEquals("$", def.getDisplayExpression());
  }

  @Test
  void freeTextDefaultResolvesToMatchingEntryValue(JenkinsRule r) {
    List<ValueItem> values = List.of(
      new ValueItem("{\"name\":\"v10.7.6\"}", "v10.7.6"),
      new ValueItem("{\"name\":\"v10.7.7\"}", "v10.7.7"));
    assertEquals("{\"name\":\"v10.7.7\"}", freeTextDefinition("v10.7.7").resolveFreeTextDefault(values));
  }

  @Test
  void freeTextDefaultWithoutMatchIsVerbatim(JenkinsRule r) {
    List<ValueItem> values = List.of(new ValueItem("v1.0", "v1.0"));
    assertEquals("v0.9", freeTextDefinition("v0.9").resolveFreeTextDefault(values));
  }

  @Test
  void emptyFreeTextDefaultStaysEmpty(JenkinsRule r) {
    List<ValueItem> values = List.of(new ValueItem("", ""), new ValueItem("v1.0", "v1.0"));
    assertEquals("", freeTextDefinition("").resolveFreeTextDefault(values));
  }

  private static RestListParameterDefinition freeTextDefinition(final String defaultValue) {
    RestListParameterDefinition def = new RestListParameterDefinition(
      "PARAM", "desc", "https://example.com/api", null, MimeType.APPLICATION_JSON, "$.*", "$.name",
      ValueOrder.NONE, ".*", 0, defaultValue, false);
    def.setEnableValidation(false);
    return def;
  }
}
