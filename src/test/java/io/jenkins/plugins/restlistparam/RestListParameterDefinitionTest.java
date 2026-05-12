package io.jenkins.plugins.restlistparam;

import io.jenkins.plugins.restlistparam.model.MimeType;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

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
}
