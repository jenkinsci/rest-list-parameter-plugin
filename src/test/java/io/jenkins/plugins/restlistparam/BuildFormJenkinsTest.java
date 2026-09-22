package io.jenkins.plugins.restlistparam;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.DomNode;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlOption;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlSelect;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asynchronous loading on the "Build with Parameters" page (specs/build-parameter-form and the build form parts of
 * specs/multi-value-parameter).
 */
@WithJenkins
class BuildFormJenkinsTest {

  private static final String ENTRIES_JSON =
    "[{\"name\":\"Alpha\",\"id\":1},{\"name\":\"Beta\",\"id\":2},{\"name\":\"Gamma\",\"id\":3}]";

  // Values are loaded after the form renders

  @Test
  void renderingTheFormContactsNoEndpoint(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub("[\"v1.0\", \"v1.1\"]")) {
      FreeStyleProject project = project(r, single(stub, "p", 0, ""), multi(stub, "m", ""));
      JenkinsRule.WebClient wc = r.createWebClient();
      wc.getOptions().setJavaScriptEnabled(false);

      HtmlPage page = BuildForms.openWithoutWaiting(wc, project);

      assertEquals(2, page.querySelectorAll(BuildForms.LOADING).size(), "both parameters show their loading state");
      assertNotNull(page.querySelector(".jenkins-spinner"));
      assertEquals(0, stub.requests().size());
    }
  }

  @Test
  void dropdownListsEntriesAndPreselectsTheDefault(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub(ENTRIES_JSON)) {
      FreeStyleProject project = project(r, named(stub, "Beta", false));

      HtmlSelect select = BuildForms.open(r, project).querySelector("select[name=value]");

      assertEquals(List.of("Alpha", "Beta", "Gamma"), texts(select.getOptions()));
      assertEquals(List.of("Beta"), texts(select.getSelectedOptions()));
    }
  }

  @Test
  void emptyOptionStaysSelectableAndIsTheDefaultWithoutOne(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub(ENTRIES_JSON)) {
      FreeStyleProject project = project(r, named(stub, "", true));

      HtmlPage page = BuildForms.open(r, project);
      HtmlSelect select = page.querySelector("select[name=value]");

      assertEquals(List.of("—", "Alpha", "Beta", "Gamma"), texts(select.getOptions()));
      assertEquals("", select.getSelectedOptions().get(0).getValueAttribute());
      r.submit(page.getFormByName("parameters"));
      r.waitUntilNoActivity();
      assertEquals("", submitted(project, "p"));
    }
  }

  @Test
  void onlyTheLoadedEntriesAreRequestedOncePerForm(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub(ENTRIES_JSON)) {
      FreeStyleProject project = project(r, named(stub, "", false));

      BuildForms.open(r, project);

      assertEquals(1, stub.requestCount("/list"));
    }
  }

  // Refresh action per parameter

  @Test
  void newEntryAppearsAfterRefresh(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub("[\"v1.0\", \"v1.1\"]")) {
      FreeStyleProject project = project(r, single(stub, "p", 10, ""));
      HtmlPage page = BuildForms.open(r, project);
      stub.respondJson("/list", "[\"v1.0\", \"v1.1\", \"v1.2\"]");

      refresh(page, "p");

      HtmlSelect select = parameter(page, "p").querySelector("select");
      assertEquals(List.of("v1.0", "v1.1", "v1.2"), texts(select.getOptions()));
      assertEquals(2, stub.requestCount("/list"));
      assertEquals("no-cache", stub.lastRequest().cacheControl());
    }
  }

  @Test
  void refreshKeepsTheSelection(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub("[\"v1.0\", \"v1.1\"]")) {
      FreeStyleProject project = project(r, single(stub, "p", 0, "v1.1"));
      HtmlPage page = BuildForms.open(r, project);
      HtmlSelect select = parameter(page, "p").querySelector("select");
      select.getOptionByValue("v1.0").setSelected(true);
      stub.respondJson("/list", "[\"v0.9\", \"v1.0\", \"v1.1\"]");

      refresh(page, "p");

      select = parameter(page, "p").querySelector("select");
      assertEquals(List.of("v0.9", "v1.0", "v1.1"), texts(select.getOptions()));
      assertEquals(List.of("v1.0"), texts(select.getSelectedOptions()));
    }
  }

  @Test
  void refreshAppliesTheDefaultWhenTheSelectionIsGone(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub("[\"v1.0\", \"v1.1\"]")) {
      FreeStyleProject project = project(r, single(stub, "p", 0, "v1.1"));
      HtmlPage page = BuildForms.open(r, project);
      ((HtmlSelect) parameter(page, "p").querySelector("select")).getOptionByValue("v1.0").setSelected(true);
      stub.respondJson("/list", "[\"v1.1\", \"v1.2\"]");

      refresh(page, "p");

      HtmlSelect select = parameter(page, "p").querySelector("select");
      assertEquals(List.of("v1.1"), texts(select.getSelectedOptions()));
    }
  }

  @Test
  void refreshKeepsTypedFreeText(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub("[\"v1.0\", \"v1.1\"]")) {
      RestListParameterDefinition def = single(stub, "p", 0, "v1.0");
      def.setEnableValidation(false);
      FreeStyleProject project = project(r, def);
      HtmlPage page = BuildForms.open(r, project);
      org.htmlunit.html.HtmlInput input = parameter(page, "p").querySelector("input[name=value]");
      assertEquals("v1.0", input.getValue());
      input.setValue("custom");
      stub.respondJson("/list", "[\"v2.0\"]");

      refresh(page, "p");

      assertEquals("custom", input.getValue());
      List<String> suggestions = parameter(page, "p").querySelectorAll("datalist option").stream()
        .map(node -> ((DomElement) node).getAttribute("value")).toList();
      assertEquals(List.of("v2.0"), suggestions);
    }
  }

  @Test
  void multiRefreshKeepsSelectedEntriesStillReturned(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub(ENTRIES_JSON)) {
      FreeStyleProject project = project(r, multi(stub, "m", "[\"Alpha\",\"Gamma\"]"));
      HtmlPage page = BuildForms.open(r, project);
      HtmlSelect select = parameter(page, "m").querySelector("select");
      assertEquals(List.of("Alpha", "Gamma"), texts(select.getSelectedOptions()));
      stub.respondJson("/list", "[{\"name\":\"Alpha\",\"id\":1},{\"name\":\"Beta\",\"id\":2}]");

      refresh(page, "m");

      select = parameter(page, "m").querySelector("select");
      assertEquals(List.of("Alpha", "Beta"), texts(select.getOptions()));
      assertEquals(List.of("Alpha"), texts(select.getSelectedOptions()));
    }
  }

  @Test
  void multiRefreshKeepsFreeFormEntries(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub(ENTRIES_JSON)) {
      RestMultiListParameterDefinition def = multi(stub, "m", "[\"Zeta\",\"Alpha\"]");
      def.setEnableValidation(false);
      FreeStyleProject project = project(r, def);
      HtmlPage page = BuildForms.open(r, project);

      refresh(page, "m");

      HtmlSelect select = parameter(page, "m").querySelector("select");
      assertEquals(List.of("Alpha", "Zeta"), texts(select.getSelectedOptions()));
    }
  }

  // Fetch errors are shown on the form

  @Test
  void retryAfterRecovery(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respond("/list", 503, "text/plain", "down");
      FreeStyleProject project = project(r, single(stub, "p", 0, ""));
      HtmlPage page = BuildForms.open(r, project);
      DomElement error = parameter(page, "p").querySelector(".rlp-error");
      assertFalse(error.hasAttribute("hidden"), "error is shown");
      assertTrue(error.asNormalizedText().contains("Encountered Http Server Error: 503"), error.asNormalizedText());
      assertTrue(((HtmlSelect) parameter(page, "p").querySelector("select")).getOptions().isEmpty());
      stub.respondJson("/list", "[\"v1.0\"]");

      ((HtmlElement) error.querySelector(".rlp-retry")).click();
      BuildForms.waitUntilLoaded(page);

      assertTrue(error.hasAttribute("hidden"), "error is gone");
      assertEquals(List.of("v1.0"), texts(((HtmlSelect) parameter(page, "p").querySelector("select")).getOptions()));
    }
  }

  @Test
  void errorDetailsAreShownToAnAdministrator(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respond("/list", 503, "text/plain", "down");
      FreeStyleProject project = project(r, single(stub, "p", 0, ""));

      HtmlPage page = BuildForms.open(r, project);

      DomElement details = parameter(page, "p").querySelector(".rlp-details");
      assertFalse(details.hasAttribute("hidden"));
      assertTrue(details.asNormalizedText().contains(stub.url("/list")), details.asNormalizedText());
      assertTrue(details.asNormalizedText().contains("503"), details.asNormalizedText());
    }
  }

  // Submission waits until entries are loaded

  @Test
  void buildClickedWhileLoadingIsNotSubmitted(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub("[\"v1.0\", \"v1.1\"]")) {
      stub.withDelay("/list", Duration.ofSeconds(4));
      FreeStyleProject project = project(r, single(stub, "p", 0, "v1.1"));
      HtmlPage page = BuildForms.openWithoutWaiting(r.createWebClient(), project);
      page.getWebClient().waitForBackgroundJavaScript(200);
      HtmlElement build = submitButton(page);
      assertEquals("true", build.getAttribute("aria-disabled"));

      build.click();
      page.getWebClient().waitForBackgroundJavaScript(200);

      assertFalse(isHidden(parameter(page, "p"), ".rlp-hint"), "hint is shown");
      assertTrue(parameter(page, "p").asNormalizedText().contains("Values are still loading"));
      assertTrue(r.jenkins.getQueue().isEmpty());
      assertNull(project.getLastBuild());

      BuildForms.waitUntilLoaded(page);
      assertTrue(isHidden(parameter(page, "p"), ".rlp-hint"), "hint is gone");
      assertFalse(submitButton(page).hasAttribute("aria-disabled"));
      r.submit(page.getFormByName("parameters"));
      r.waitUntilNoActivity();
      assertEquals("v1.1", submitted(project, "p"));
    }
  }

  @Test
  void buildAfterAFailedLoadIsSubmitted(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respond("/list", 503, "text/plain", "down");
      RestListParameterDefinition def = single(stub, "p", 0, "");
      def.setAllowEmptyValue(true);
      FreeStyleProject project = project(r, def);

      HtmlPage page = BuildForms.open(r, project);
      r.submit(page.getFormByName("parameters"));
      r.waitUntilNoActivity();

      assertEquals("", submitted(project, "p"), "server validation accepted the empty value");
    }
  }

  // Loading independence (7.5)

  /**
   * HtmlUnit runs the asynchronous requests of one page, and their callbacks, one after another on the page's
   * JavaScript thread, while a browser runs the requests in parallel. This test therefore checks what HtmlUnit can
   * observe: the page is returned without waiting, and each parameter is loaded with its own request.
   * {@link #slowLoadDoesNotHoldUpAnotherLoadOnTheServer} shows that the server answers a fast load while a slow one
   * is still in flight.
   */
  @Test
  void slowParameterDoesNotDelayTheOthers(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/fast", "[\"f1\", \"f2\"]");
      stub.respondJson("/slow", "[\"s1\"]").withDelay("/slow", Duration.ofSeconds(5));
      RestListParameterDefinition fast = new RestListParameterDefinition(
        "fast", "d", stub.url("/fast"), "", MimeType.APPLICATION_JSON, "$.*", "$", ValueOrder.NONE, ".*", 0, "", false);
      RestListParameterDefinition slow = new RestListParameterDefinition(
        "slow", "d", stub.url("/slow"), "", MimeType.APPLICATION_JSON, "$.*", "$", ValueOrder.NONE, ".*", 0, "", false);
      FreeStyleProject project = project(r, slow, fast);

      long start = System.nanoTime();
      HtmlPage page = BuildForms.openWithoutWaiting(r.createWebClient(), project);
      long pageMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();
      assertTrue(pageMillis < 4000, "the page waited for an endpoint: " + pageMillis + " ms");
      assertEquals("loading", parameter(page, "slow").getAttribute("data-rlp-state"));
      assertEquals("loading", parameter(page, "fast").getAttribute("data-rlp-state"));

      BuildForms.waitUntilLoaded(page);
      assertEquals(List.of("f1", "f2"), texts(((HtmlSelect) parameter(page, "fast").querySelector("select")).getOptions()));
      assertEquals(List.of("s1"), texts(((HtmlSelect) parameter(page, "slow").querySelector("select")).getOptions()));
      assertEquals(1, stub.requestCount("/fast"), "each parameter is loaded with its own request");
      assertEquals(1, stub.requestCount("/slow"), "each parameter is loaded with its own request");
    }
  }

  @Test
  void slowLoadDoesNotHoldUpAnotherLoadOnTheServer(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/fast", "[\"f1\"]");
      stub.respondJson("/slow", "[\"s1\"]").withDelay("/slow", Duration.ofSeconds(5));
      FreeStyleProject project = r.createFreeStyleProject();
      ValueLoader slow = new RestListParameterDefinition(
        "slow", "d", stub.url("/slow"), "", MimeType.APPLICATION_JSON, "$.*", "$", ValueOrder.NONE, ".*", 0, "", false)
        .createLoader(project);
      ValueLoader fast = new RestListParameterDefinition(
        "fast", "d", stub.url("/fast"), "", MimeType.APPLICATION_JSON, "$.*", "$", ValueOrder.NONE, ".*", 0, "", false)
        .createLoader(project);
      java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
      try {
        java.util.concurrent.Future<net.sf.json.JSONObject> slowResult = executor.submit(() -> slow.load(false));
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (stub.requestCount("/slow") == 0 && System.nanoTime() < deadline) {
          Thread.sleep(20);
        }
        assertEquals(1, stub.requestCount("/slow"), "the slow load has started");

        long start = System.nanoTime();
        net.sf.json.JSONObject fastResult = executor.submit(() -> fast.load(false)).get();
        long fastMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertEquals("ok", fastResult.getString("status"));
        assertTrue(fastMillis < 2500, "the fast load waited for the slow one: " + fastMillis + " ms");
        assertFalse(slowResult.isDone(), "the slow load is still in flight");
        assertEquals("ok", slowResult.get().getString("status"));
      }
      finally {
        executor.shutdownNow();
      }
    }
  }

  // Each viewer sees the result of their own load (7.6)

  @Test
  void concurrentViewersSeeOnlyTheirOwnOutcome(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub("[\"v1.0\"]")) {
      stub.respondOnce("/list", 503, "text/plain", "down");
      FreeStyleProject project = project(r, single(stub, "p", 0, ""));

      HtmlPage first = BuildForms.openWithoutWaiting(r.createWebClient(), project);
      HtmlPage second = BuildForms.openWithoutWaiting(r.createWebClient(), project);
      BuildForms.waitUntilLoaded(first);
      BuildForms.waitUntilLoaded(second);

      assertEquals(2, stub.requestCount("/list"));
      List<HtmlPage> failed = new java.util.ArrayList<>();
      List<HtmlPage> succeeded = new java.util.ArrayList<>();
      for (HtmlPage page : List.of(first, second)) {
        ("error".equals(parameter(page, "p").getAttribute("data-rlp-state")) ? failed : succeeded).add(page);
      }
      assertEquals(1, failed.size(), "exactly one viewer got the 503");
      assertEquals(1, succeeded.size());

      DomElement failedParameter = parameter(failed.get(0), "p");
      assertTrue(failedParameter.asNormalizedText().contains("Encountered Http Server Error: 503"));
      assertTrue(((HtmlSelect) failedParameter.querySelector("select")).getOptions().isEmpty());

      DomElement succeededParameter = parameter(succeeded.get(0), "p");
      assertFalse(succeededParameter.asNormalizedText().contains("Encountered Http Server Error"),
        succeededParameter.asNormalizedText());
      assertTrue(isHidden(succeededParameter, ".rlp-error"));
      assertEquals(List.of("v1.0"), texts(((HtmlSelect) succeededParameter.querySelector("select")).getOptions()));
    }
  }

  private static void refresh(final HtmlPage page, final String name) throws Exception {
    ((HtmlElement) parameter(page, name).querySelector(".rlp-refresh")).click();
    BuildForms.waitUntilLoaded(page);
  }

  private static boolean isHidden(final DomElement parent, final String selector) {
    DomElement element = parent.querySelector(selector);
    assertNotNull(element, selector);
    return element.hasAttribute("hidden");
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

  private static HtmlElement submitButton(final HtmlPage page) {
    HtmlElement button = page.getFormByName("parameters")
      .querySelector("button:not([type]), button[type=submit], input[type=submit]");
    assertNotNull(button, "no submit button");
    return button;
  }

  private static StubHttpServer listStub(final String json) throws Exception {
    StubHttpServer stub = new StubHttpServer();
    stub.respondJson("/list", json);
    return stub;
  }

  private static RestListParameterDefinition single(final StubHttpServer stub, final String name,
                                                    final int cacheTime, final String defaultValue)
  {
    return new RestListParameterDefinition(
      name, "d", stub.url("/list"), "", MimeType.APPLICATION_JSON, "$.*", "$", ValueOrder.NONE, ".*", cacheTime,
      defaultValue, false);
  }

  private static RestListParameterDefinition named(final StubHttpServer stub, final String defaultValue,
                                                   final boolean allowEmpty)
  {
    return new RestListParameterDefinition(
      "p", "d", stub.url("/list"), "", MimeType.APPLICATION_JSON, "$.*", "$.name", ValueOrder.NONE, ".*", 0,
      defaultValue, allowEmpty);
  }

  private static RestMultiListParameterDefinition multi(final StubHttpServer stub, final String name,
                                                        final String defaultValue)
  {
    return new RestMultiListParameterDefinition(
      name, "d", stub.url("/list"), "", MimeType.APPLICATION_JSON, "$.*", "$.name", ValueOrder.NONE, ".*", 0,
      defaultValue, true);
  }

  private static FreeStyleProject project(final JenkinsRule r, final ParameterDefinition... definitions)
    throws Exception
  {
    FreeStyleProject project = r.createFreeStyleProject();
    project.addProperty(new ParametersDefinitionProperty(definitions));
    return project;
  }

  private static Object submitted(final FreeStyleProject project, final String name) {
    FreeStyleBuild build = project.getLastBuild();
    assertNotNull(build, "build was not scheduled");
    return build.getAction(ParametersAction.class).getParameter(name).getValue();
  }

  private static List<String> texts(final List<HtmlOption> options) {
    return options.stream().map(option -> option.getText().trim()).toList();
  }
}
