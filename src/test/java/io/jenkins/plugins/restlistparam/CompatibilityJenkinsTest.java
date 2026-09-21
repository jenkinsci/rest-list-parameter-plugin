package io.jenkins.plugins.restlistparam;

import hudson.EnvVars;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.TaskListener;
import io.jenkins.plugins.restlistparam.model.CustomHeader;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the on-disk format of the REST List Parameter. The job and build XML under the
 * {@code CompatibilityJenkinsTest} resources were written by the plugin before the shared base
 * class was extracted, and must keep loading and saving unchanged.
 */
@WithJenkins
class CompatibilityJenkinsTest {

  private static final String JSON_VALUE = "{\"name\":\"v1.2\"}";

  @Test
  @LocalData
  void legacyJobConfigRoundTripsUnchanged(JenkinsRule r) throws Exception {
    FreeStyleProject project = r.jenkins.getItemByFullName("legacy", FreeStyleProject.class);
    assertNotNull(project, "legacy job did not load");
    String before = normalizeLineEndings(project.getConfigFile().asString());

    RestListParameterDefinition def = (RestListParameterDefinition)
      project.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("VERSION");
    assertEquals("pick a version", def.getDescription());
    assertEquals("https://example.invalid/api/tags", def.getRestEndpoint());
    assertEquals("api-token", def.getCredentialId());
    assertEquals(MimeType.APPLICATION_JSON, def.getMimeType());
    assertEquals("$.*", def.getValueExpression());
    assertEquals("$.name", def.getDisplayExpression());
    assertEquals(ValueOrder.DSC, def.getValueOrder());
    assertEquals("v1.2", def.getDefaultValue());
    assertEquals("v.*", def.getFilter());
    assertEquals(15, def.getCacheTime());
    assertTrue(def.isAllowEmptyValue());
    assertFalse(def.isEnableValidation());
    List<CustomHeader> headers = def.getCustomHeaders();
    assertEquals(1, headers.size());
    assertEquals("X-Auth-Token", headers.get(0).getName());
    assertEquals("header-token", headers.get(0).getCredentialId());
    assertEquals("Token ", headers.get(0).getValuePrefix());

    project.save();

    assertEquals(before, normalizeLineEndings(project.getConfigFile().asString()));
  }

  @Test
  @LocalData
  void legacyBuildLoadsWithSameEnvironment(JenkinsRule r) throws Exception {
    FreeStyleProject project = r.jenkins.getItemByFullName("legacy", FreeStyleProject.class);
    assertNotNull(project, "legacy job did not load");
    FreeStyleBuild build = project.getBuildByNumber(1);
    assertNotNull(build, "legacy build did not load");

    ParameterValue value = build.getAction(ParametersAction.class).getParameter("VERSION");
    assertInstanceOf(RestListParameterValue.class, value);
    assertEquals(JSON_VALUE, value.getValue());
    assertEquals("pick a version", value.getDescription());

    EnvVars env = build.getEnvironment(TaskListener.NULL);
    assertEquals(JSON_VALUE, env.get("VERSION"));
    assertEquals(JSON_VALUE, build.getBuildVariableResolver().resolve("VERSION"));
  }

  /**
   * Git may check the fixture out with CRLF line endings (e.g. on Windows), while Jenkins writes LF;
   * only the content is compared.
   */
  private static String normalizeLineEndings(final String text) {
    return text.replace("\r\n", "\n");
  }
}
