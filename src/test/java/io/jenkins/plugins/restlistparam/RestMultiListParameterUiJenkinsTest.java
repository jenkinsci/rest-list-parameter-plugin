package io.jenkins.plugins.restlistparam;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.util.Secret;
import io.jenkins.plugins.restlistparam.model.CustomHeader;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import org.htmlunit.html.DomNode;
import org.htmlunit.html.HtmlCheckBoxInput;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlOption;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlSelect;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class RestMultiListParameterUiJenkinsTest {

  private static final String ENTRIES_JSON =
    "[{\"name\":\"Alpha\",\"id\":1},{\"name\":\"Beta\",\"id\":2},{\"name\":\"Gamma\",\"id\":3}]";
  private static final String ALPHA = "{\"name\":\"Alpha\",\"id\":1}";
  private static final String GAMMA = "{\"name\":\"Gamma\",\"id\":3}";

  // Job configuration (6.1)

  @Test
  void configurationRoundTripPreservesAllFields(JenkinsRule r) throws Exception {
    RestMultiListParameterDefinition def = new RestMultiListParameterDefinition(
      "TARGETS", "pick targets", "https://example.invalid/api", "", MimeType.APPLICATION_JSON, "$.*", "$.name",
      ValueOrder.DSC, "v.*", 7, "[\"Alpha\",\"Gamma\"]", true);
    def.setEnableValidation(false);
    CustomHeader header = new CustomHeader("X-Auth-Token");
    header.setCredentialId("header-token");
    header.setValuePrefix("Token ");
    // the header form always submits its (empty) inline value field
    header.setValue(Secret.fromString(""));
    def.setCustomHeaders(new ArrayList<>(List.of(header)));
    FreeStyleProject project = r.createFreeStyleProject();
    project.addProperty(new ParametersDefinitionProperty(def));

    r.configRoundtrip(project);

    RestMultiListParameterDefinition after = (RestMultiListParameterDefinition)
      project.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("TARGETS");
    r.assertEqualDataBoundBeans(def, after);
    assertEquals(1, after.getCustomHeaders().size());
    assertEquals("header-token", after.getCustomHeaders().get(0).getCredentialId());
    assertEquals("Token ", after.getCustomHeaders().get(0).getValuePrefix());
  }

  @Test
  void testConfigurationButtonSucceedsAgainstStub(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/list", "[\"a\", \"b\", \"c\"]");
      FreeStyleProject project = project(r, new RestMultiListParameterDefinition(
        "TARGETS", "d", stub.url("/list"), "", MimeType.APPLICATION_JSON, "$.*", "$",
        ValueOrder.NONE, ".*", 0, "", false));

      HtmlPage page = r.createWebClient().getPage(project, "configure");
      assertTrue(page.asNormalizedText().contains("REST Multi List Parameter"), "config page did not render the type");

      HtmlElement testButton = page.querySelector("button[data-validate-button-method=testConfiguration]");
      assertNotNull(testButton, "Test Configuration button not rendered");
      HtmlElement parameterBlock = (HtmlElement) testButton.getFirstByXPath("ancestor::div[contains(@class,'repeated-chunk')][1]");
      testButton.click();
      page.getWebClient().waitForBackgroundJavaScript(5000);

      assertTrue(parameterBlock.asNormalizedText().contains("Test Successful! 3 Values, first: a"),
        parameterBlock.asNormalizedText());
    }
  }

  @Test
  void testConfigurationUsesUnsavedPaginationSettings(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/components?repository=releases",
        "{\"items\":[{\"version\":\"1.0\"},{\"version\":\"1.1\"}],\"continuationToken\":\"abc\"}");
      stub.respondJson("/components?repository=releases&continuationToken=abc",
        "{\"items\":[{\"version\":\"2.0\"}],\"continuationToken\":null}");
      FreeStyleProject project = project(r, new RestMultiListParameterDefinition(
        "TARGETS", "d", stub.url("/components?repository=releases"), "", MimeType.APPLICATION_JSON,
        "$.items[*].version", "$", ValueOrder.NONE, ".*", 0, "", false));

      JenkinsRule.WebClient wc = r.createWebClient();
      HtmlPage page = wc.getPage(project, "configure");
      HtmlElement testButton = page.querySelector("button[data-validate-button-method=testConfiguration]");
      HtmlElement parameterBlock = (HtmlElement) testButton.getFirstByXPath("ancestor::div[contains(@class,'repeated-chunk')][1]");
      for (Object advanced : parameterBlock.getByXPath(".//button[contains(@class,'advanced-button')]")) {
        ((HtmlElement) advanced).click();
      }

      HtmlCheckBoxInput enabled = parameterBlock.querySelector("input[name='paginationEnabled']");
      assertNotNull(enabled, "pagination block not rendered");
      assertFalse(enabled.isChecked());
      enabled.click();
      HtmlSelect strategy = parameterBlock.querySelector(".rlp-pagination select.dropdownList");
      strategy.setSelectedAttribute(strategy.getOptionByText("Continuation token in the Json body"), true);
      wc.waitForBackgroundJavaScript(5000);
      activeInput(parameterBlock, "tokenExpression").setValue("$.continuationToken");
      activeInput(parameterBlock, "queryParameter").setValue("continuationToken");

      // the REST Endpoint field check may already have requested the endpoint while the page loaded
      int before = stub.requests().size();
      testButton.click();
      wc.waitForBackgroundJavaScript(5000);

      List<String> requested = stub.requestUris();
      assertEquals(List.of("/components?repository=releases", "/components?repository=releases&continuationToken=abc"),
        requested.subList(before, requested.size()));
      assertTrue(parameterBlock.asNormalizedText().contains("Test Successful! 3 Values from 2 pages, first: 1.0"),
        parameterBlock.asNormalizedText());
      assertEquals("", ((HtmlInput) parameterBlock.querySelector("input[name='_.paginationJson']")).getValue(),
        "hidden field should be cleared after the request");
      assertNull(((RestMultiListParameterDefinition) project.getProperty(ParametersDefinitionProperty.class)
        .getParameterDefinition("TARGETS")).getPagination(), "the settings were tested, not saved");
    }
  }

  /** The input of the selected pagination strategy, skipping the hidden entries of the others. */
  private static HtmlInput activeInput(final HtmlElement block, final String field) {
    for (DomNode node : block.querySelectorAll("input[name='_." + field + "']")) {
      if (node.getFirstByXPath("ancestor-or-self::*[@field-disabled]") == null) {
        return (HtmlInput) node;
      }
    }
    throw new AssertionError("no active input for " + field);
  }

  // Build form (6.2)

  @Test
  void formListsEntriesAndPreselectsDefaults(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = entriesStub()) {
      FreeStyleProject project = project(r, entries(stub, "[\"Alpha\",\"Gamma\",\"Zeta\"]", true));

      HtmlSelect select = buildForm(r, project).querySelector("select[name=value]");

      assertNotNull(select, "multi-select not rendered");
      assertTrue(select.isMultipleSelectEnabled());
      assertEquals("true", select.getAttribute("data-multiple"));
      assertEquals("false", select.getAttribute("data-tags"));
      assertEquals(List.of("Alpha", "Beta", "Gamma"), texts(select.getOptions()));
      assertEquals(List.of(ALPHA, GAMMA), values(select.getSelectedOptions()));
    }
  }

  @Test
  void submitsSelectedValuesInListOrder(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = entriesStub()) {
      FreeStyleProject project = project(r, entries(stub, "Beta", false));

      HtmlPage page = buildForm(r, project);
      HtmlSelect select = page.querySelector("select[name=value]");
      assertEquals(List.of("Beta"), texts(select.getSelectedOptions()));
      select.getOptionByText("Beta").setSelected(false);
      select.getOptionByText("Gamma").setSelected(true);
      select.getOptionByText("Alpha").setSelected(true);
      r.submit(page.getFormByName("parameters"));
      r.waitUntilNoActivity();

      assertEquals(List.of(ALPHA, GAMMA), submitted(project));
    }
  }

  @Test
  void submitsEmptyListWhenNothingIsSelected(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = entriesStub()) {
      FreeStyleProject project = project(r, entries(stub, "", true));

      HtmlPage page = buildForm(r, project);
      r.submit(page.getFormByName("parameters"));
      r.waitUntilNoActivity();

      assertEquals(Collections.emptyList(), submitted(project));
    }
  }

  @Test
  void unmatchedDefaultIsPreselectedFreeFormEntryWithoutValidation(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = entriesStub()) {
      RestMultiListParameterDefinition def = entries(stub, "[\"Zeta\"]", false);
      def.setEnableValidation(false);
      FreeStyleProject project = project(r, def);

      HtmlPage page = buildForm(r, project);
      HtmlSelect select = page.querySelector("select[name=value]");
      assertEquals("true", select.getAttribute("data-tags"));
      assertEquals(List.of("Zeta"), values(select.getSelectedOptions()));
      r.submit(page.getFormByName("parameters"));
      r.waitUntilNoActivity();

      assertEquals(List.of("Zeta"), submitted(project));
    }
  }

  @Test
  void endpointErrorIsShownAndNoOptionsAreOffered(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respond("/down", 503, "text/plain", "unavailable");
      FreeStyleProject project = project(r, new RestMultiListParameterDefinition(
        "TARGETS", "d", stub.url("/down"), "", MimeType.APPLICATION_JSON, "$.*", "$",
        ValueOrder.NONE, ".*", 0, "", false));

      HtmlPage page = buildForm(r, project);

      assertTrue(page.asNormalizedText().contains("Encountered Http Server Error: 503"), page.asNormalizedText());
      assertTrue(((HtmlSelect) page.querySelector("select[name=value]")).getOptions().isEmpty());
    }
  }

  // Build page (6.4)

  @Test
  void parametersPageShowsElementsInOrder(JenkinsRule r) throws Exception {
    FreeStyleProject project = project(r, new RestMultiListParameterDefinition(
      "TARGETS", "d", "http://127.0.0.1:1/none", "", MimeType.APPLICATION_JSON, "$.*", "$",
      ValueOrder.NONE, ".*", 0, "", false));
    FreeStyleBuild build = r.assertBuildStatusSuccess(project.scheduleBuild2(0,
      new ParametersAction(new RestMultiListParameterValue("TARGETS", List.of("a", "c"), "d"))));

    HtmlPage page = r.createWebClient().getPage(build, "parameters/");

    List<String> shown = new ArrayList<>();
    for (DomNode node : page.querySelectorAll("input[name=value]")) {
      HtmlInput input = (HtmlInput) node;
      assertTrue(input.isReadOnly(), "elements must not be editable");
      shown.add(input.getValue());
    }
    assertEquals(List.of("a", "c"), shown);
  }

  private static StubHttpServer entriesStub() throws Exception {
    StubHttpServer stub = new StubHttpServer();
    stub.respondJson("/entries", ENTRIES_JSON);
    return stub;
  }

  private static RestMultiListParameterDefinition entries(final StubHttpServer stub,
                                                          final String defaultValue,
                                                          final boolean allowEmpty)
  {
    return new RestMultiListParameterDefinition(
      "TARGETS", "d", stub.url("/entries"), "", MimeType.APPLICATION_JSON, "$.*", "$.name",
      ValueOrder.NONE, ".*", 0, defaultValue, allowEmpty);
  }

  private static FreeStyleProject project(final JenkinsRule r,
                                          final RestMultiListParameterDefinition def) throws Exception
  {
    FreeStyleProject project = r.createFreeStyleProject();
    project.addProperty(new ParametersDefinitionProperty(def));
    return project;
  }

  private static HtmlPage buildForm(final JenkinsRule r, final FreeStyleProject project) throws Exception {
    return BuildForms.open(r, project);
  }

  private static List<String> submitted(final FreeStyleProject project) {
    FreeStyleBuild build = project.getLastBuild();
    assertNotNull(build, "build was not scheduled");
    return ((RestMultiListParameterValue) build.getAction(ParametersAction.class).getParameter("TARGETS")).getValue();
  }

  private static List<String> texts(final List<HtmlOption> options) {
    return options.stream().map(option -> option.getText().trim()).toList();
  }

  private static List<String> values(final List<HtmlOption> options) {
    return options.stream().map(HtmlOption::getValueAttribute).toList();
  }
}
