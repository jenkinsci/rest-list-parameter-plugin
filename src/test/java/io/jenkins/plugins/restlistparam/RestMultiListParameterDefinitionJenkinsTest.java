package io.jenkins.plugins.restlistparam;

import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterValue;
import io.jenkins.plugins.restlistparam.model.LinkHeaderPagination;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.ValueItem;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.junit.jupiter.api.Test;
import org.kohsuke.stapler.StaplerRequest2;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class RestMultiListParameterDefinitionJenkinsTest {

  private static final String UNREACHABLE = "http://127.0.0.1:1/none";
  private static final StaplerRequest2 NO_REQUEST = null;

  // Declaration (5.1)

  @Test
  void descriptorIsRegistered(JenkinsRule r) {
    ParameterDefinition.ParameterDescriptor descriptor =
      (ParameterDefinition.ParameterDescriptor) Jenkins.get().getDescriptor(RestMultiListParameterDefinition.class);
    assertNotNull(descriptor, "DescriptorImpl not registered");
    assertEquals(RestMultiListParameterDefinition.DescriptorImpl.class, descriptor.getClass());
    assertEquals("REST Multi List Parameter", descriptor.getDisplayName());
  }

  @Test
  void bothTypesAreOffered(JenkinsRule r) {
    List<String> offered = ParameterDefinition.all().stream().map(Descriptor::getDisplayName).toList();
    assertTrue(offered.contains("REST List Parameter"), offered.toString());
    assertTrue(offered.contains("REST Multi List Parameter"), offered.toString());
  }

  @Test
  void symbolsResolveToTheMultiType(JenkinsRule r) {
    for (String symbol : List.of("RESTMultiList", "RestMultiList", "RESTMultiListParam")) {
      Descriptor<?> bySymbol = SymbolLookup.get().findDescriptor(ParameterDefinition.class, symbol);
      assertNotNull(bySymbol, symbol);
      assertEquals(RestMultiListParameterDefinition.DescriptorImpl.class, bySymbol.getClass(), symbol);
    }
    assertEquals(RestListParameterDefinition.DescriptorImpl.class,
      SymbolLookup.get().findDescriptor(ParameterDefinition.class, "RESTList").getClass());
  }

  @Test
  void minimalDeclarationUsesDefaults(JenkinsRule r) throws Exception {
    Map<String, Object> args = new HashMap<>();
    args.put("name", "V");
    args.put("description", "");
    args.put("restEndpoint", "https://h/api");
    args.put("credentialId", "");
    args.put("mimeType", "APPLICATION_JSON");
    args.put("valueExpression", "$.*");

    RestMultiListParameterDefinition def = new DescribableModel<>(RestMultiListParameterDefinition.class).instantiate(args);

    assertEquals("V", def.getName());
    assertTrue(def.isEnableValidation());
    assertFalse(def.isAllowEmptyValue());
    assertEquals("", def.getDefaultValue());
    assertEquals("$", def.getDisplayExpression());
    assertEquals(".*", def.getFilter());
    assertEquals(ValueOrder.NONE, def.getValueOrder());
    assertTrue(def.getCustomHeaders().isEmpty());
  }

  @Test
  void declarationAcceptsOptionalProperties(JenkinsRule r) throws Exception {
    Map<String, Object> args = new HashMap<>();
    args.put("name", "V");
    args.put("description", "");
    args.put("restEndpoint", "https://h/api");
    args.put("credentialId", "");
    args.put("mimeType", "APPLICATION_JSON");
    args.put("valueExpression", "$.*");
    args.put("displayExpression", "$.name");
    args.put("defaultValue", "[\"a\",\"b\"]");
    args.put("allowEmptyValue", true);
    args.put("enableValidation", false);
    args.put("filter", "v.*");
    args.put("valueOrder", "DSC");
    args.put("cacheTime", 5);

    RestMultiListParameterDefinition def = new DescribableModel<>(RestMultiListParameterDefinition.class).instantiate(args);

    assertEquals("$.name", def.getDisplayExpression());
    assertEquals("[\"a\",\"b\"]", def.getDefaultValue());
    assertTrue(def.isAllowEmptyValue());
    assertFalse(def.isEnableValidation());
    assertEquals("v.*", def.getFilter());
    assertEquals(ValueOrder.DSC, def.getValueOrder());
    assertEquals(5, def.getCacheTime());
  }

  @Test
  void offersSameEntriesAsSingleType(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/tags", TestConst.validTestJson);
      RestListParameterDefinition single = new RestListParameterDefinition(
        "p", "d", stub.url("/tags"), "", MimeType.APPLICATION_JSON, "$.*", "$.name",
        ValueOrder.DSC, ".*v10\\.6\\.[34].*", 0, "", false);
      RestMultiListParameterDefinition multi = new RestMultiListParameterDefinition(
        "p", "d", stub.url("/tags"), "", MimeType.APPLICATION_JSON, "$.*", "$.name",
        ValueOrder.DSC, ".*v10\\.6\\.[34].*", 0, "", false);

      assertEquals(single.getValues(), multi.getValues());
      assertEquals(2, multi.getValues().size());
    }
  }

  @Test
  void entriesFromAllPagesAreOffered(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/tags", "[\"a\",\"b\"]")
          .withHeader("/tags", "Link", "<" + stub.url("/tags?page=2") + ">; rel=\"next\"");
      stub.respondJson("/tags?page=2", "[\"c\"]");
      RestMultiListParameterDefinition def = new RestMultiListParameterDefinition(
        "TARGETS", "d", stub.url("/tags"), "", MimeType.APPLICATION_JSON, "$.*", "$",
        ValueOrder.NONE, ".*", 0, "", false);
      def.setPagination(new LinkHeaderPagination());

      assertEquals(List.of("a", "b", "c"), def.getValues().stream().map(ValueItem::getValue).toList());
      assertTrue(def.isValid(multi("a", "c")), "entries from page 2 are valid choices");
    }
  }

  // Per-element validation (5.2)

  @Test
  void acceptsElementsThatAreAllFetched(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub()) {
      assertTrue(validating(stub.url("/list"), false).isValid(multi("a", "c")));
    }
  }

  @Test
  void rejectsWhenOneElementIsNotFetched(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub()) {
      RestMultiListParameterDefinition def = validating(stub.url("/list"), false);
      assertFalse(def.isValid(multi("a", "bogus")));
      assertEquals("Illegal value for parameter TARGETS: bogus",
        assertThrows(IllegalArgumentException.class, () -> def.createValue("[\"a\",\"bogus\",\"worse\"]")).getMessage());
      assertEquals(2, stub.requestCount("/list"), "validation should fetch once per value");
    }
  }

  @Test
  void validatesAgainstValuesNotDisplayValues(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/tags", "[{\"name\":\"a\"},{\"name\":\"c\"}]");
      RestMultiListParameterDefinition def = new RestMultiListParameterDefinition(
        "TARGETS", "d", stub.url("/tags"), "", MimeType.APPLICATION_JSON, "$.*", "$.name",
        ValueOrder.NONE, ".*", 0, "", false);

      assertFalse(def.isValid(multi("a")));
      assertTrue(def.isValid(multi("{\"name\":\"a\"}", "{\"name\":\"c\"}")));
    }
  }

  @Test
  void emptyListAcceptedOnlyWhenAllowed(JenkinsRule r) {
    RestMultiListParameterDefinition allowed = freeForm(true);
    assertTrue(allowed.isValid(multi()));
    RestMultiListParameterDefinition validatingAllowed = validating(UNREACHABLE, true);
    assertTrue(validatingAllowed.isValid(multi()));

    RestMultiListParameterDefinition denied = freeForm(false);
    assertFalse(denied.isValid(multi()));
    assertEquals("Illegal value for parameter TARGETS: []",
      assertThrows(IllegalArgumentException.class, () -> denied.createValue("[]")).getMessage());
  }

  @Test
  void emptyElementIsAlwaysRejected(JenkinsRule r) {
    RestMultiListParameterDefinition def = freeForm(true);
    assertFalse(def.isValid(multi("a", "")));
    assertEquals("Illegal value for parameter TARGETS: \"\"",
      assertThrows(IllegalArgumentException.class, () -> def.createValue("[\"a\",\"\"]")).getMessage());
  }

  @Test
  void firstOffendingElementInListOrderIsReported(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub()) {
      RestMultiListParameterDefinition def = validating(stub.url("/list"), true);
      assertEquals("Illegal value for parameter TARGETS: bogus",
        assertThrows(IllegalArgumentException.class, () -> def.createValue("[\"a\",\"bogus\",\"\"]")).getMessage());
      assertEquals("Illegal value for parameter TARGETS: \"\"",
        assertThrows(IllegalArgumentException.class, () -> def.createValue("[\"a\",\"\",\"bogus\"]")).getMessage());
    }
  }

  @Test
  void arbitraryElementsAcceptedWithoutFetchingWhenValidationDisabled(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub()) {
      RestMultiListParameterDefinition def = new RestMultiListParameterDefinition(
        "TARGETS", "d", stub.url("/list"), "", MimeType.APPLICATION_JSON, "$.*", "$",
        ValueOrder.NONE, ".*", 0, "", false);
      def.setEnableValidation(false);

      assertTrue(def.isValid(multi("x", "y")));
      assertEquals(0, stub.requestCount("/list"), "validation disabled must not contact the endpoint");
    }
  }

  @Test
  void fetchFailureAtSubmissionRejects(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respond("/down", 503, "text/plain", "unavailable");
      RestMultiListParameterDefinition def = validating(stub.url("/down"), true);

      assertFalse(def.isValid(multi("a")));
      assertEquals("Illegal value for parameter TARGETS: a",
        assertThrows(IllegalArgumentException.class, () -> def.createValue("a")).getMessage());
      assertTrue(def.isValid(multi()), "an allowed empty list does not depend on the endpoint");
    }
  }

  @Test
  void missingValueIsRejected(JenkinsRule r) {
    RestMultiListParameterDefinition def = freeForm(true);
    assertFalse(def.isValid(null));
    assertFalse(def.isValid(new RestMultiListParameterValue("TARGETS", null)));
    assertThrows(IllegalArgumentException.class, () -> def.createValue((String) null));
  }

  @Test
  void stringParameterValueIsReadWithTheStringRule(JenkinsRule r) {
    RestMultiListParameterDefinition def = freeForm(false);
    assertTrue(def.isValid(new StringParameterValue("TARGETS", "[\"a\",\"c\"]")));
    assertFalse(def.isValid(new StringParameterValue("TARGETS", "[]")));
  }

  // String and form input (5.3)

  @Test
  void createValueFromStringUsesStrictParsing(JenkinsRule r) {
    RestMultiListParameterDefinition def = freeForm(true);
    assertEquals(List.of("a", "c"), def.createValue("[\"a\",\"c\"]").getValue());
    assertEquals(List.of("a,c"), def.createValue("a,c").getValue());
    assertEquals(List.of("[\"x\"]"), def.createValue("[\"[\\\"x\\\"]\"]").getValue());
    assertEquals(Collections.emptyList(), def.createValue("").getValue());
    assertEquals(List.of("b", "a"), def.createValue("[\"b\",\"a\",\"b\"]").getValue());
    RestMultiListParameterValue value = (RestMultiListParameterValue) def.createValue("a");
    assertEquals("d", value.getDescription());
  }

  @Test
  void createValueFromFormArray(JenkinsRule r) {
    RestMultiListParameterDefinition def = freeForm(true);
    JSONObject form = JSONObject.fromObject(
      "{\"name\":\"TARGETS\",\"value\":[\"a\",\"{\\\"name\\\": \\\"c\\\"}\",\"[1, 2]\",\"a\"]}");

    RestMultiListParameterValue value = (RestMultiListParameterValue) def.createValue(NO_REQUEST, form);

    assertEquals(List.of("a", "{\"name\": \"c\"}", "[1, 2]"), value.getValue());
    assertEquals("d", value.getDescription());
  }

  @Test
  void createValueFromFormEmptyArrayOrMissingValue(JenkinsRule r) {
    RestMultiListParameterDefinition allowed = freeForm(true);
    assertEquals(Collections.emptyList(),
      allowed.createValue(NO_REQUEST, JSONObject.fromObject("{\"name\":\"TARGETS\",\"value\":[]}")).getValue());
    assertEquals(Collections.emptyList(),
      allowed.createValue(NO_REQUEST, JSONObject.fromObject("{\"name\":\"TARGETS\"}")).getValue());

    RestMultiListParameterDefinition denied = freeForm(false);
    assertEquals("Illegal value for parameter TARGETS: []",
      assertThrows(IllegalArgumentException.class,
        () -> denied.createValue(NO_REQUEST, JSONObject.fromObject("{\"name\":\"TARGETS\"}"))).getMessage());
  }

  @Test
  void createValueFromFormString(JenkinsRule r) {
    RestMultiListParameterDefinition def = freeForm(false);
    assertEquals(List.of("a", "c"),
      def.createValue(NO_REQUEST, JSONObject.fromObject("{\"name\":\"TARGETS\",\"value\":\"[\\\"a\\\",\\\"c\\\"]\"}")).getValue());
  }

  // Remote API (5.4, design D7)

  @Test
  void remoteApiReadsSingleParameter(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub()) {
      FreeStyleProject project = project(r, validating(stub.url("/list"), false));
      JenkinsRule.WebClient wc = r.createWebClient();

      assertEquals(201, buildWithParameters(wc, project, "TARGETS=" + encode("[\"a\",\"c\"]")).getWebResponse().getStatusCode());
      r.waitUntilNoActivity();
      assertEquals(List.of("a", "c"), targets(project, 1));

      buildWithParameters(wc, project, "TARGETS=b");
      r.waitUntilNoActivity();
      assertEquals(List.of("b"), targets(project, 2));
    }
  }

  @Test
  void remoteApiRejectsInvalidElement(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub()) {
      FreeStyleProject project = project(r, validating(stub.url("/list"), false));
      JenkinsRule.WebClient wc = r.createWebClient();
      wc.setThrowExceptionOnFailingStatusCode(false);

      Page page = buildWithParameters(wc, project, "TARGETS=" + encode("[\"a\",\"bogus\"]"));
      r.waitUntilNoActivity();

      assertTrue(page.getWebResponse().getStatusCode() >= 400);
      assertTrue(page.getWebResponse().getContentAsString().contains("Illegal value for parameter TARGETS: bogus"),
        page.getWebResponse().getContentAsString());
      assertNull(project.getLastBuild(), "no build may start");
    }
  }

  @Test
  void remoteApiRejectsRepeatedParameter(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub()) {
      FreeStyleProject project = project(r, validating(stub.url("/list"), false));
      JenkinsRule.WebClient wc = r.createWebClient();
      wc.setThrowExceptionOnFailingStatusCode(false);

      Page page = buildWithParameters(wc, project, "TARGETS=a&TARGETS=c");
      r.waitUntilNoActivity();

      assertTrue(page.getWebResponse().getStatusCode() >= 400);
      assertNull(project.getLastBuild(), "no build may start");
    }
  }

  // Defaults and re-run (5.5)

  @Test
  void jsonRerunMapsElementsToDisplayValues(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/tags", "[{\"name\":\"a\"},{\"name\":\"b\"},{\"name\":\"c\"}]");
      RestMultiListParameterDefinition def = new RestMultiListParameterDefinition(
        "TARGETS", "d", stub.url("/tags"), "", MimeType.APPLICATION_JSON, "$.*", "$.name",
        ValueOrder.NONE, ".*", 0, "", false);

      RestMultiListParameterDefinition rerun = (RestMultiListParameterDefinition) def.copyWithDefaultValue(
        new RestMultiListParameterValue("TARGETS", List.of("{\"name\":\"a\"}", "{\"name\":\"c\"}")));

      assertEquals("[\"a\",\"c\"]", rerun.getDefaultValue());
      List<ValueItem> values = rerun.getValues();
      assertEquals(List.of(true, false, true), values.stream().map(rerun::isDefaultSelected).toList());
    }
  }

  @Test
  void xmlRerunKeepsElementsVerbatim(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respond("/list.xml", 200, "application/xml", "<root><v>v1</v><v>v2</v><v>v3</v></root>");
      RestMultiListParameterDefinition def = new RestMultiListParameterDefinition(
        "TARGETS", "d", stub.url("/list.xml"), "", MimeType.APPLICATION_XML, "//v", "",
        ValueOrder.NONE, ".*", 0, "", false);

      RestMultiListParameterDefinition rerun = (RestMultiListParameterDefinition) def.copyWithDefaultValue(
        new RestMultiListParameterValue("TARGETS", List.of("v1", "v2")));

      assertEquals("[\"v1\",\"v2\"]", rerun.getDefaultValue());
      assertEquals(List.of(true, true, false), rerun.getValues().stream().map(rerun::isDefaultSelected).toList());
    }
  }

  @Test
  void rerunWithEmptyListHasNoDefaults(JenkinsRule r) {
    RestMultiListParameterDefinition rerun = (RestMultiListParameterDefinition)
      freeForm(true).copyWithDefaultValue(new RestMultiListParameterValue("TARGETS", Collections.emptyList()));
    assertFalse(rerun.isDefaultSelected(new ValueItem("a", "a")));
    assertTrue(rerun.getUnmatchedDefaults(Collections.emptyList()).isEmpty());
  }

  @Test
  void otherValueTypesKeepTheDefinition(JenkinsRule r) {
    RestMultiListParameterDefinition def = freeForm(true);
    assertEquals(def, def.copyWithDefaultValue(new StringParameterValue("TARGETS", "a")));
  }

  @Test
  void severalDefaultsArePreselected(JenkinsRule r) {
    RestMultiListParameterDefinition def = withDefault("[\"Alpha\",\"Gamma\"]", true);
    assertEquals(List.of(true, false, true), ENTRIES.stream().map(def::isDefaultSelected).toList());
  }

  @Test
  void singleDefaultIsPreselected(JenkinsRule r) {
    RestMultiListParameterDefinition def = withDefault("Beta", true);
    assertEquals(List.of(false, true, false), ENTRIES.stream().map(def::isDefaultSelected).toList());
  }

  @Test
  void unmatchedDefaultIgnoredWithValidation(JenkinsRule r) {
    RestMultiListParameterDefinition def = withDefault("[\"Alpha\",\"Zeta\"]", true);
    assertEquals(List.of(true, false, false), ENTRIES.stream().map(def::isDefaultSelected).toList());
    assertTrue(def.getUnmatchedDefaults(ENTRIES).isEmpty());
  }

  @Test
  void unmatchedDefaultIsFreeFormWithoutValidation(JenkinsRule r) {
    RestMultiListParameterDefinition def = withDefault("[\"Zeta\",\"Alpha\",\"\",\"Zeta\"]", false);
    assertEquals(List.of("Zeta"), def.getUnmatchedDefaults(ENTRIES));
    assertEquals(List.of(true, false, false), ENTRIES.stream().map(def::isDefaultSelected).toList());
  }

  private static final List<ValueItem> ENTRIES = List.of(
    new ValueItem("alpha", "Alpha"), new ValueItem("beta", "Beta"), new ValueItem("gamma", "Gamma"));

  private static RestMultiListParameterDefinition withDefault(final String defaultValue, final boolean validation) {
    RestMultiListParameterDefinition def = new RestMultiListParameterDefinition(
      "TARGETS", "d", UNREACHABLE, "", MimeType.APPLICATION_JSON, "$.*", "$.name",
      ValueOrder.NONE, ".*", 0, defaultValue, false);
    def.setEnableValidation(validation);
    return def;
  }

  private static StubHttpServer listStub() throws Exception {
    StubHttpServer stub = new StubHttpServer();
    stub.respondJson("/list", "[\"a\", \"b\", \"c\"]");
    return stub;
  }

  private static RestMultiListParameterDefinition validating(final String endpoint, final boolean allowEmpty) {
    return new RestMultiListParameterDefinition(
      "TARGETS", "d", endpoint, "", MimeType.APPLICATION_JSON, "$.*", "$",
      ValueOrder.NONE, ".*", 0, "", allowEmpty);
  }

  private static RestMultiListParameterDefinition freeForm(final boolean allowEmpty) {
    RestMultiListParameterDefinition def = validating(UNREACHABLE, allowEmpty);
    def.setEnableValidation(false);
    return def;
  }

  private static RestMultiListParameterValue multi(final String... elements) {
    return new RestMultiListParameterValue("TARGETS", List.of(elements));
  }

  private static FreeStyleProject project(final JenkinsRule r, final ParameterDefinition def) throws Exception {
    FreeStyleProject project = r.createFreeStyleProject();
    project.addProperty(new ParametersDefinitionProperty(def));
    return project;
  }

  private static Page buildWithParameters(final JenkinsRule.WebClient wc,
                                          final FreeStyleProject project,
                                          final String query) throws Exception
  {
    WebRequest request = new WebRequest(new URL(wc.getContextPath() + project.getUrl() + "buildWithParameters?" + query),
      HttpMethod.POST);
    return wc.getPage(wc.addCrumb(request));
  }

  private static String encode(final String text) {
    return URLEncoder.encode(text, StandardCharsets.UTF_8);
  }

  private static Object targets(final FreeStyleProject project, final int build) {
    assertNotNull(project.getBuildByNumber(build), "build " + build + " was not started");
    Object value = project.getBuildByNumber(build).getAction(ParametersAction.class).getParameter("TARGETS");
    assertInstanceOf(RestMultiListParameterValue.class, value);
    return ((RestMultiListParameterValue) value).getValue();
  }
}
