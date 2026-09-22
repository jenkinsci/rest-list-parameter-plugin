package io.jenkins.plugins.restlistparam;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.jayway.jsonpath.JsonPath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.util.FormValidation;
import hudson.util.Secret;
import io.jenkins.plugins.restlistparam.logic.ValueService;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlOption;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlSelect;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class ValueResolutionJenkinsTest {

  private static final String TAGS_JSON = "[{\"name\":\"v10.7.6\",\"id\":1},{\"name\":\"v10.7.7\",\"id\":2}]";

  // Rerun default (build-parameter-form)

  @Test
  void xmlParameterRerunsWithPreviousValue(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respond("/list.xml", 200, "application/xml", "<root><v>v1</v><v>v2</v></root>");
      RestListParameterDefinition def = new RestListParameterDefinition(
        "p", "d", stub.url("/list.xml"), "", MimeType.APPLICATION_XML, "//v", "",
        ValueOrder.NONE, ".*", 0, "", false);

      RestListParameterDefinition rerun = (RestListParameterDefinition)
        def.copyWithDefaultValue(new RestListParameterValue("p", "v2", "d"));

      assertEquals("v2", rerun.getDefaultValue());
    }
  }

  @Test
  void jsonParameterRerunMapsToDisplayValue(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/list", "[\"v1.0\", \"v1.1\"]");
      RestListParameterDefinition def = new RestListParameterDefinition(
        "p", "d", stub.url("/list"), "", MimeType.APPLICATION_JSON, "$.*", "$",
        ValueOrder.NONE, ".*", 0, "", false);

      RestListParameterDefinition rerun = (RestListParameterDefinition)
        def.copyWithDefaultValue(new RestListParameterValue("p", "v1.0", "d"));

      assertEquals("v1.0", rerun.getDefaultValue());
    }
  }

  @Test
  void rerunKeepsNonJsonValueVerbatim(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/list", TAGS_JSON);
      RestListParameterDefinition def = new RestListParameterDefinition(
        "p", "d", stub.url("/list"), "", MimeType.APPLICATION_JSON, "$.*", "$.name",
        ValueOrder.NONE, ".*", 0, "", false);

      RestListParameterDefinition rerun = (RestListParameterDefinition)
        def.copyWithDefaultValue(new RestListParameterValue("p", "typed {not json", "d"));

      assertEquals("typed {not json", rerun.getDefaultValue());
    }
  }

  @Test
  void preparingRerunDoesNotContactTheEndpoint(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/list", "[\"v1.0\", \"v1.1\"]");
      RestListParameterDefinition def = new RestListParameterDefinition(
        "p", "d", stub.url("/list"), "", MimeType.APPLICATION_JSON, "$.*", "$",
        ValueOrder.NONE, ".*", 0, "", false);

      RestListParameterDefinition rerun = (RestListParameterDefinition)
        def.copyWithDefaultValue(new RestListParameterValue("p", "v1.0", "d"));

      assertEquals("v1.0", rerun.getDefaultValue());
      assertEquals(0, stub.requests().size(), "the re-run form loads its entries itself");
    }
  }

  // Custom-value dropdown (build-parameter-form)

  @Test
  void customDropdownPreselectsTheMatchingEntryValue(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/tags", TAGS_JSON);
      RestListParameterDefinition def = new RestListParameterDefinition(
        "p", "d", stub.url("/tags"), "", MimeType.APPLICATION_JSON, "$.*", "$.name",
        ValueOrder.NONE, ".*", 0, "v10.7.7", false);
      def.setEnableValidation(false);
      FreeStyleProject project = r.createFreeStyleProject();
      project.addProperty(new ParametersDefinitionProperty(def));

      HtmlPage page = BuildForms.open(r, project);

      HtmlSelect select = page.querySelector("select[name=value]");
      assertNotNull(select, "dropdown not rendered");
      assertNull(page.querySelector("input[name=value]"), "no free-text input");
      List<HtmlOption> selected = select.getSelectedOptions();
      assertEquals(1, selected.size());
      String value = selected.get(0).getValueAttribute();
      assertEquals("v10.7.7", JsonPath.read(value, "$.name"));
      assertEquals("v10.7.7", selected.get(0).getText().trim(), "the entry is shown by its display value");
      assertEquals(1, stub.requestCount("/tags"), "the form should fetch once per load");
    }
  }

  // Remote access API (#174)

  @Test
  void remoteApiExportsParameterValue(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/list", "[\"v1\", \"v2\"]");
      RestListParameterDefinition def = new RestListParameterDefinition(
        "p", "d", stub.url("/list"), "", MimeType.APPLICATION_JSON, "$.*", "$",
        ValueOrder.NONE, ".*", 0, "", false);
      FreeStyleProject project = r.createFreeStyleProject();
      project.addProperty(new ParametersDefinitionProperty(def));
      FreeStyleBuild build = r.assertBuildStatusSuccess(project.scheduleBuild2(0,
        new ParametersAction(new RestListParameterValue("p", "v2", "d"))));

      String json = r.createWebClient()
        .goTo(build.getUrl() + "api/json?tree=actions[parameters[name,value]]", "application/json")
        .getWebResponse().getContentAsString();

      assertTrue(json.contains("\"name\":\"p\",\"value\":\"v2\""), json);
    }
  }

  // Build form structure (#204)

  /**
   * Active Choices resolves a referenced parameter by walking the children of
   * {@code div[name=parameter]} and taking the first input not named {@code name}.
   */
  @Test
  void valueElementFollowsNameInBuildForm(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/list", "[\"v1\", \"v2\"]");
      for (boolean validation : List.of(true, false)) {
        RestListParameterDefinition def = new RestListParameterDefinition(
          "p", "some description", stub.url("/list"), "", MimeType.APPLICATION_JSON, "$.*", "$",
          ValueOrder.NONE, ".*", 0, "", false);
        def.setEnableValidation(validation);
        FreeStyleProject project = r.createFreeStyleProject();
        project.addProperty(new ParametersDefinitionProperty(def));

        JenkinsRule.WebClient wc = r.createWebClient();
        wc.getOptions().setJavaScriptEnabled(false);
        wc.setThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = wc.getPage(project, "build?delay=0sec");

        DomElement parameter = page.querySelector("div[name=parameter]");
        assertNotNull(parameter);
        DomElement firstValueCandidate = null;
        for (DomElement child : parameter.getChildElements()) {
          String name = child.getAttribute("name");
          if (!name.isEmpty() && !"name".equals(name)) {
            firstValueCandidate = child;
            break;
          }
        }
        assertNotNull(firstValueCandidate, "no value element in parameter div");
        assertEquals("value", firstValueCandidate.getAttribute("name"), "validation=" + validation);
      }
    }
  }

  @Test
  void submittedValueCarriesDefinitionDescription(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/list", "[\"v1\", \"v2\"]");
      RestListParameterDefinition def = new RestListParameterDefinition(
        "p", "some description", stub.url("/list"), "", MimeType.APPLICATION_JSON, "$.*", "$",
        ValueOrder.NONE, ".*", 0, "v2", false);
      FreeStyleProject project = r.createFreeStyleProject();
      project.addProperty(new ParametersDefinitionProperty(def));

      HtmlPage page = BuildForms.open(r, project);
      r.submit(page.getFormByName("parameters"));
      r.waitUntilNoActivity();

      FreeStyleBuild build = project.getLastBuild();
      assertNotNull(build, "build was not scheduled");
      RestListParameterValue value = (RestListParameterValue)
        build.getAction(ParametersAction.class).getParameter("p");
      assertEquals("v2", value.getValue());
      assertEquals("some description", value.getDescription());
    }
  }

  // Empty result (#209)

  @Test
  void emptyResultIsNoErrorWhenEmptyValueAllowed(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/empty", "[]");
      stub.respondJson("/filtered", "[\"a\", \"b\"]");
      stub.respond("/broken", 200, "application/json", "{not json");

      assertEquals("", errorOf(emptyAllowed(stub.url("/empty"), ".*")));
      assertEquals("", errorOf(emptyAllowed(stub.url("/filtered"), "x.*")));
      assertEquals(Messages.RLP_ValueResolver_warn_jPath_MalformedJson(), errorOf(emptyAllowed(stub.url("/broken"), ".*")),
        "genuine errors must still be reported");

      RestListParameterDefinition notAllowed = new RestListParameterDefinition(
        "p", "d", stub.url("/empty"), "", MimeType.APPLICATION_JSON, "$.*", "$",
        ValueOrder.NONE, ".*", 0, "", false);
      assertEquals("Json-Path expression yielded no results", errorOf(notAllowed));
    }
  }

  @Test
  void emptyResultStartsBuildWithEmptyValue(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/empty", "[]");
      FreeStyleProject project = r.createFreeStyleProject();
      project.addProperty(new ParametersDefinitionProperty(emptyAllowed(stub.url("/empty"), ".*")));

      HtmlPage page = BuildForms.open(r, project);
      assertFalse(page.asNormalizedText().contains("yielded no results"), page.asNormalizedText());
      r.submit(page.getFormByName("parameters"));
      r.waitUntilNoActivity();

      FreeStyleBuild build = project.getLastBuild();
      assertNotNull(build, "build was not scheduled");
      r.assertBuildStatusSuccess(build);
      assertEquals("", build.getAction(ParametersAction.class).getParameter("p").getValue());
    }
  }

  private static RestListParameterDefinition emptyAllowed(final String endpoint, final String filter) {
    RestListParameterDefinition def = new RestListParameterDefinition(
      "p", "d", endpoint, "", MimeType.APPLICATION_JSON, "$.*", "$",
      ValueOrder.NONE, filter, 0, "", false);
    def.setAllowEmptyValue(true);
    return def;
  }

  private static String errorOf(final RestListParameterDefinition def) {
    return ValueService.entries(def, null, false).getErrorMsg().orElse("");
  }

  // Endpoint check (configuration-validation)

  @Test
  void endpointCheckSendsAcceptHeader(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respond("/list.xml", 200, "application/xml", "<root/>");

      FormValidation result = descriptor(r).doCheckRestEndpoint(null, stub.url("/list.xml"), "", MimeType.APPLICATION_XML);

      assertEquals(FormValidation.Kind.OK, result.kind);
      assertEquals("application/xml", stub.lastRequest().header("Accept"));
    }
  }

  @Test
  void endpointCheckTreatsAuthRejectionAsWarning(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respond("/401", 401, "text/plain", "no");
      stub.respond("/403", 403, "text/plain", "no");

      for (String path : List.of("/401", "/403")) {
        FormValidation result = descriptor(r).doCheckRestEndpoint(null, stub.url(path), "", MimeType.APPLICATION_JSON);
        assertEquals(FormValidation.Kind.WARNING, result.kind, path);
        assertTrue(result.getMessage().contains("Test Configuration"), result.getMessage());
      }
    }
  }

  @Test
  void endpointCheckReportsOtherClientErrors(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      FormValidation result = descriptor(r).doCheckRestEndpoint(null, stub.url("/missing"), "", MimeType.APPLICATION_JSON);

      assertEquals(FormValidation.Kind.ERROR, result.kind);
      assertEquals("Encountered Http Client Error: 404", result.getMessage());
    }
  }

  // Test Configuration with custom headers (configuration-validation)

  @Test
  void testConfigurationSendsCustomHeaders(JenkinsRule r) throws Exception {
    SystemCredentialsProvider.getInstance().getCredentials().add(
      new StringCredentialsImpl(CredentialsScope.GLOBAL, "api-key", "api key", Secret.fromString("s3cr3t")));
    SystemCredentialsProvider.getInstance().save();

    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/list", "[\"a\", \"b\"]");
      String headersJson = "[{\"name\":\"X-API-Key\",\"value\":\"\",\"credentialId\":\"api-key\",\"valuePrefix\":\"\"}]";

      FormValidation result = descriptor(r).doTestConfiguration(null, stub.url("/list"), "", MimeType.APPLICATION_JSON,
        "$.*", "$", ".*", ValueOrder.NONE, headersJson, null);

      assertEquals(FormValidation.Kind.OK, result.kind, result.getMessage());
      assertEquals("Test Successful! 2 Values, first: a", result.getMessage());
      assertEquals("s3cr3t", stub.lastRequest().header("X-API-Key"));
    }
  }

  @Test
  void testConfigurationIgnoresMalformedHeaderJson(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/list", "[\"a\", \"b\"]");

      FormValidation result = descriptor(r).doTestConfiguration(null, stub.url("/list"), "", MimeType.APPLICATION_JSON,
        "$.*", "$", ".*", ValueOrder.NONE, "{not json", null);

      assertEquals(FormValidation.Kind.OK, result.kind, result.getMessage());
      assertFalse(stub.lastRequest().hasHeader("X-API-Key"));
    }
  }

  @Test
  void testConfigurationButtonSendsUnsavedHeaderRow(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/list", "[\"a\", \"b\"]");
      RestListParameterDefinition def = new RestListParameterDefinition(
        "p", "d", stub.url("/list"), "", MimeType.APPLICATION_JSON, "$.*", "$",
        ValueOrder.NONE, ".*", 0, "", false);
      FreeStyleProject project = r.createFreeStyleProject();
      project.addProperty(new ParametersDefinitionProperty(def));

      JenkinsRule.WebClient wc = r.createWebClient();
      HtmlPage page = wc.getPage(project, "configure");

      HtmlElement headers = page.querySelector(".rlp-custom-headers");
      assertNotNull(headers, "custom headers block not rendered");
      HtmlElement parameterBlock = (HtmlElement) headers.getFirstByXPath(
        "ancestor::div[contains(@class,'repeated-chunk')][1]");
      for (Object advanced : parameterBlock.getByXPath(".//button[contains(@class,'advanced-button')]")) {
        ((HtmlElement) advanced).click();
      }
      ((HtmlElement) headers.querySelector("button.repeatable-add")).click();
      wc.waitForBackgroundJavaScript(2000);

      HtmlElement row = headers.querySelector(".repeated-chunk:not(.to-be-removed)");
      assertNotNull(row, "header row was not added");
      ((HtmlInput) row.querySelector("input[name='_.name']")).setValue("X-API-Key");
      ((HtmlInput) row.querySelector("input[name='_.value']")).setValue("typed-secret");

      HtmlElement testButton = parameterBlock.querySelector("button[data-validate-button-method=testConfiguration]");
      testButton.click();
      wc.waitForBackgroundJavaScript(5000);

      assertEquals("typed-secret", stub.lastRequest().header("X-API-Key"));
      assertTrue(parameterBlock.asNormalizedText().contains("Test Successful! 2 Values, first: a"),
        "Test Configuration result not shown");
      assertEquals("", ((HtmlInput) parameterBlock.querySelector("input[name='_.customHeadersJson']")).getValue(),
        "hidden field should be cleared after the request");

      HtmlForm form = page.getFormByName("config");
      r.submit(form);
      String configXml = project.getConfigFile().asString();
      assertFalse(configXml.contains("customHeadersJson"), configXml);
      assertTrue(configXml.contains("X-API-Key"), "the header row itself should be saved");
    }
  }

  private static RestListParameterDefinition.DescriptorImpl descriptor(JenkinsRule r) {
    return r.jenkins.getDescriptorByType(RestListParameterDefinition.DescriptorImpl.class);
  }
}
