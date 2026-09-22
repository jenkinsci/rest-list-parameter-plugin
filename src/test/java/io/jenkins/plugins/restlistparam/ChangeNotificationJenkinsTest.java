package io.jenkins.plugins.restlistparam;

import hudson.model.FreeStyleProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import net.sf.json.JSONArray;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.DomNode;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlSelect;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Loading entries notifies dependent form elements, such as Active Choices parameters, of value changes with a
 * {@code change} event (#204; the "Loading notifies dependent form elements" requirements of
 * specs/build-parameter-form and specs/multi-value-parameter).
 */
@WithJenkins
class ChangeNotificationJenkinsTest {

  /** Holds the first load back until the recorder is installed. */
  private static final Duration FIRST_LOAD_DELAY = Duration.ofSeconds(2);

  /**
   * Records every {@code change} event on a parameter's value element as {@code name=value}, the value being the
   * selected option values joined by {@code ,} for a multi-select.
   */
  private static final String RECORDER =
    "window.rlpChanges = [];"
      + "document.addEventListener('change', function (event) {"
      + "  var parameter = event.target.closest('div[name=parameter]');"
      + "  var name = parameter ? parameter.querySelector('input[name=name]') : null;"
      + "  var target = event.target;"
      + "  var value = target.multiple"
      + "    ? Array.prototype.filter.call(target.options, function (o) { return o.selected; })"
      + "        .map(function (o) { return o.value; }).join(',')"
      + "    : target.value;"
      + "  window.rlpChanges.push((name ? name.value : '?') + '=' + value);"
      + "}, true);";

  @Test
  void recorderSeesNoEventBeforeTheEntriesArrive(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub("[\"v1\", \"v2\"]")) {
      FreeStyleProject project = project(r, single(stub, "p", "v2"));

      HtmlPage page = openRecording(r, project, false);

      assertEquals("loading", parameter(page, "p").getAttribute("data-rlp-state"));
      assertEquals(List.of(), changes(page));
    }
  }

  // Dropdown

  @Test
  void preselectedDefaultNotifiesOnce(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub("[\"v1\", \"v2\", \"v3\"]")) {
      FreeStyleProject project = project(r, single(stub, "p", "v2"));

      HtmlPage page = openRecording(r, project, true);

      assertEquals(List.of("p=v2"), changes(page));
    }
  }

  @Test
  void defaultMatchingNoEntryKeepsTheEmptyOptionWithoutNotifying(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub("[\"v1\", \"v2\"]")) {
      RestListParameterDefinition def = single(stub, "p", "v9");
      def.setAllowEmptyValue(true);
      FreeStyleProject project = project(r, def);

      HtmlPage page = openRecording(r, project, true);

      assertEquals(List.of(), changes(page));
    }
  }

  @Test
  void firstEntryBecomesTheValueWithoutTheEmptyOption(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub("[\"v1\", \"v2\"]")) {
      FreeStyleProject project = project(r, single(stub, "p", "v9"));

      HtmlPage page = openRecording(r, project, true);

      assertEquals(List.of("p=v1"), changes(page), "the form submits the first entry, so referencing parameters see it");
    }
  }

  @Test
  void refreshKeepingTheSelectionDoesNotNotify(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub("[\"v1\", \"v2\", \"v3\"]")) {
      FreeStyleProject project = project(r, single(stub, "p", "v2"));
      HtmlPage page = openRecording(r, project, true);
      stub.respondJson("/list", "[\"v2\", \"v3\"]");

      refresh(page, "p");

      assertEquals(List.of("p=v2"), changes(page));
    }
  }

  @Test
  void refreshDroppingTheSelectionNotifies(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub("[\"v1\", \"v2\", \"v3\"]")) {
      FreeStyleProject project = project(r, single(stub, "p", "v2"));
      HtmlPage page = openRecording(r, project, true);
      stub.respondJson("/list", "[\"v1\", \"v3\"]");

      refresh(page, "p");

      assertEquals(List.of("p=v2", "p=v1"), changes(page));
    }
  }

  @Test
  void failedRefreshClearingTheValueNotifies(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub("[\"v1\", \"v2\"]")) {
      FreeStyleProject project = project(r, single(stub, "p", "v2"));
      HtmlPage page = openRecording(r, project, true);
      stub.respond("/list", 503, "text/plain", "down");

      refresh(page, "p");

      assertEquals("error", parameter(page, "p").getAttribute("data-rlp-state"));
      assertEquals(List.of("p=v2", "p="), changes(page));
    }
  }

  // Custom values (validation disabled)

  @Test
  void customDefaultResolvedToAnotherValueNotifies(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub("[{\"name\":\"Alpha\"},{\"name\":\"Beta\"}]")) {
      RestListParameterDefinition def = new RestListParameterDefinition(
        "p", "d", stub.url("/list"), "", MimeType.APPLICATION_JSON, "$.*", "$.name", ValueOrder.NONE, ".*", 0,
        "Beta", false);
      def.setEnableValidation(false);
      FreeStyleProject project = project(r, def);

      HtmlPage page = openRecording(r, project, true);

      assertEquals(List.of("p={\"name\":\"Beta\"}"), changes(page));
    }
  }

  @Test
  void customDefaultResolvedToItselfDoesNotNotify(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub("[\"v1\", \"v2\"]")) {
      RestListParameterDefinition def = single(stub, "p", "v2");
      def.setEnableValidation(false);
      FreeStyleProject project = project(r, def);

      HtmlPage page = openRecording(r, project, true);

      assertEquals(List.of(), changes(page));
    }
  }

  @Test
  void customValuePickedByTheUserSurvivesRefreshWithoutNotifying(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub("[\"v1\", \"v2\"]")) {
      RestListParameterDefinition def = single(stub, "p", "v2");
      def.setEnableValidation(false);
      FreeStyleProject project = project(r, def);
      HtmlPage page = openRecording(r, project, true);
      // the user's own pick is recorded here as select2 fires it, whatever the refresh adds must be nothing
      BuildForms.pickCustomValue(page, "p", "my-branch");
      List<String> picked = changes(page);
      stub.respondJson("/list", "[\"v3\"]");

      refresh(page, "p");

      HtmlSelect select = (HtmlSelect) parameter(page, "p").querySelector("select");
      assertEquals(List.of("my-branch"), select.getSelectedOptions().stream()
        .map(option -> option.getText().trim()).toList());
      assertEquals(picked, changes(page));
    }
  }

  // Multi-select

  @Test
  void multiDefaultsPreselectedNotifyOnce(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub("[\"a\", \"b\", \"c\"]")) {
      FreeStyleProject project = project(r, multi(stub, "m", "[\"a\",\"c\"]"));

      HtmlPage page = openRecording(r, project, true);

      assertEquals(List.of("m=a,c"), changes(page));
    }
  }

  @Test
  void multiWithoutDefaultDoesNotNotify(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub("[\"a\", \"b\", \"c\"]")) {
      FreeStyleProject project = project(r, multi(stub, "m", ""));

      HtmlPage page = openRecording(r, project, true);

      assertEquals(List.of(), changes(page));
    }
  }

  @Test
  void multiRefreshKeepingTheSelectionDoesNotNotify(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub("[\"a\", \"b\", \"c\"]")) {
      FreeStyleProject project = project(r, multi(stub, "m", "[\"a\",\"c\"]"));
      HtmlPage page = openRecording(r, project, true);
      stub.respondJson("/list", "[\"a\", \"b\", \"c\", \"d\"]");

      refresh(page, "m");

      assertEquals(List.of("m=a,c"), changes(page));
    }
  }

  @Test
  void multiRefreshDroppingASelectedEntryNotifies(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub("[\"a\", \"b\", \"c\"]")) {
      FreeStyleProject project = project(r, multi(stub, "m", "[\"a\",\"c\"]"));
      HtmlPage page = openRecording(r, project, true);
      stub.respondJson("/list", "[\"a\", \"b\"]");

      refresh(page, "m");

      assertEquals(List.of("m=a,c", "m=a"), changes(page));
    }
  }

  // Stale responses

  /**
   * Refresh and Retry are disabled while a load is in flight, so a response only goes stale after the browser
   * watchdog ({@code fetchTimeout} + 30 s) ended its load and the user retried. The loader's callbacks are held back
   * by replacing the bound proxy's {@code load}.
   */
  @Test
  void staleResponseDoesNotNotify(JenkinsRule r) throws Exception {
    // above FIRST_LOAD_DELAY, so the first load succeeds; the watchdog fires after this + 30 s
    RestListParameterGlobalConfig.get().setFetchTimeout(3);
    try (StubHttpServer stub = listStub("[\"v1\", \"v2\"]")) {
      FreeStyleProject project = project(r, single(stub, "p", "v2"));
      HtmlPage page = openRecording(r, project, true);
      page.executeJavaScript(
        "window.rlpPending = [];"
          + "window[document.querySelector('[data-rlp-loader]').dataset.rlpLoader].load ="
          + "  function (forced, callback) { window.rlpPending.push(callback); };");

      ((HtmlElement) parameter(page, "p").querySelector(".rlp-refresh")).click();
      BuildForms.waitUntilLoaded(page, Duration.ofSeconds(60));
      assertEquals("error", parameter(page, "p").getAttribute("data-rlp-state"), "the watchdog ended the refresh");
      assertEquals(List.of("p=v2", "p="), changes(page));

      ((HtmlElement) parameter(page, "p").querySelector(".rlp-retry")).click();
      page.executeJavaScript("window.rlpPending[0](" + OK_RESPONSE + ");");
      page.getWebClient().waitForBackgroundJavaScript(500);

      assertEquals("loading", parameter(page, "p").getAttribute("data-rlp-state"), "the retry is still in flight");
      assertEquals(List.of("p=v2", "p="), changes(page), "the stale response is ignored");

      page.executeJavaScript("window.rlpPending[1](" + OK_RESPONSE + ");");
      BuildForms.waitUntilLoaded(page);

      assertEquals(List.of("p=v2", "p=", "p=v2"), changes(page), "the current response is rendered");
    }
  }

  /** A loader transport answering with entries {@code v1} and {@code v2}, {@code v2} preselected. */
  private static final String OK_RESPONSE =
    "{ responseObject: function () { return { status: 'ok', entries: ["
      + "{ value: 'v1', display: 'v1', selected: false }, { value: 'v2', display: 'v2', selected: true }] }; } }";

  private static void refresh(final HtmlPage page, final String name) throws Exception {
    ((HtmlElement) parameter(page, name).querySelector(".rlp-refresh")).click();
    BuildForms.waitUntilLoaded(page);
  }

  /**
   * Opens the build form while the first load is held back, installs the recorder and, if {@code wait}, waits
   * until every parameter has loaded.
   */
  private static HtmlPage openRecording(final JenkinsRule r, final FreeStyleProject project, final boolean wait)
    throws Exception
  {
    HtmlPage page = BuildForms.openWithoutWaiting(r.createWebClient(), project);
    page.executeJavaScript(RECORDER);
    return wait ? BuildForms.waitUntilLoaded(page) : page;
  }

  private static List<String> changes(final HtmlPage page) {
    Object json = page.executeJavaScript("JSON.stringify(window.rlpChanges)").getJavaScriptResult();
    List<String> changes = new java.util.ArrayList<>();
    for (Object change : JSONArray.fromObject(String.valueOf(json))) {
      changes.add(String.valueOf(change));
    }
    return changes;
  }

  private static DomElement parameter(final HtmlPage page, final String name) {
    for (DomNode node : page.querySelectorAll("div[name=parameter]")) {
      DomNode hidden = node.querySelector("input[name=name]");
      if (hidden != null && name.equals(((DomElement) hidden).getAttribute("value"))) {
        return (DomElement) node;
      }
    }
    throw new AssertionError("no parameter " + name);
  }

  private static StubHttpServer listStub(final String json) throws Exception {
    StubHttpServer stub = new StubHttpServer();
    stub.respondJson("/list", json).withDelay("/list", FIRST_LOAD_DELAY);
    return stub;
  }

  private static RestListParameterDefinition single(final StubHttpServer stub, final String name,
                                                    final String defaultValue)
  {
    return new RestListParameterDefinition(
      name, "d", stub.url("/list"), "", MimeType.APPLICATION_JSON, "$.*", "$", ValueOrder.NONE, ".*", 0,
      defaultValue, false);
  }

  private static RestMultiListParameterDefinition multi(final StubHttpServer stub, final String name,
                                                        final String defaultValue)
  {
    return new RestMultiListParameterDefinition(
      name, "d", stub.url("/list"), "", MimeType.APPLICATION_JSON, "$.*", "$", ValueOrder.NONE, ".*", 0,
      defaultValue, true);
  }

  private static FreeStyleProject project(final JenkinsRule r, final ParameterDefinition... definitions)
    throws Exception
  {
    FreeStyleProject project = r.createFreeStyleProject();
    project.addProperty(new ParametersDefinitionProperty(definitions));
    return project;
  }
}
