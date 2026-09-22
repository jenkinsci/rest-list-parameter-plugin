package io.jenkins.plugins.restlistparam;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.restlistparam.logic.ValueService;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Submission-time validation backed by the value cache (specs/value-validation, "Membership check when validation is
 * enabled", and specs/multi-value-parameter, "Per-element validation").
 */
@WithJenkins
class ValidationJenkinsTest {

  @AfterEach
  void restoreClock() {
    ValueCache.get().setClock(Clock.systemUTC());
  }

  // REST List Parameter

  @Test
  void valueInFreshCachedEntriesIsAcceptedWithoutRequest(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub("[\"v1.0\", \"v1.1\"]")) {
      RestListParameterDefinition def = single(stub, 10);
      ValueService.entries(def, null, false);

      assertTrue(def.isValid(new RestListParameterValue("p", "v1.1", "d")));
      assertEquals(1, stub.requestCount("/list"), "only the fetch that filled the cache");
    }
  }

  @Test
  void valuePublishedAfterCachingIsAcceptedThroughRemoteApi(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub("[\"v1.0\", \"v1.1\"]")) {
      stub.withHeader("/list", "Cache-Control", "max-age=1800");
      RestListParameterDefinition def = single(stub, 30);
      FreeStyleProject job = r.createFreeStyleProject();
      job.addProperty(new ParametersDefinitionProperty(def));
      ValueService.entries(def, job, false);
      stub.respondJson("/list", "[\"v1.0\", \"v1.1\", \"v1.2\"]").withHeader("/list", "Cache-Control", "max-age=1800");

      JenkinsRule.WebClient wc = r.createWebClient();
      WebResponse response = wc.getPage(wc.addCrumb(new WebRequest(
        new URL(r.getURL(), job.getUrl() + "buildWithParameters?p=v1.2"), HttpMethod.POST))).getWebResponse();
      assertEquals(201, response.getStatusCode(), response.getContentAsString());
      r.waitUntilNoActivity();

      assertEquals(2, stub.requestCount("/list"), "exactly one forced request at submission");
      assertEquals("no-cache", stub.lastRequest().cacheControl());
      FreeStyleBuild build = job.getLastBuild();
      assertNotNull(build);
      assertEquals("v1.2", build.getAction(ParametersAction.class).getParameter("p").getValue());
    }
  }

  @Test
  void valueInNeitherCacheNorEndpointIsRejectedAfterOneRequest(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub("[\"v1.0\", \"v1.1\"]")) {
      RestListParameterDefinition def = single(stub, 10);
      ValueService.entries(def, null, false);

      assertFalse(def.isValid(new RestListParameterValue("p", "bogus", "d")));
      assertEquals(2, stub.requestCount("/list"));
      assertEquals("no-cache", stub.lastRequest().cacheControl());
    }
  }

  @Test
  void cacheTimeZeroChecksAgainstOneFetch(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub("[\"v1.0\", \"v1.1\"]")) {
      RestListParameterDefinition def = single(stub, 0);

      assertTrue(def.isValid(new RestListParameterValue("p", "v1.0", "d")));
      assertEquals(1, stub.requestCount("/list"));
      assertFalse(def.isValid(new RestListParameterValue("p", "bogus", "d")));
      assertEquals(2, stub.requestCount("/list"));
    }
  }

  @Test
  void endpointFailureRejectsEveryNonEmptyValue(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respond("/list", 503, "text/plain", "down");
      RestListParameterDefinition def = single(stub, 10);

      assertFalse(def.isValid(new RestListParameterValue("p", "v1.0", "d")));
      assertEquals(1, stub.requestCount("/list"));
    }
  }

  @Test
  void expiredEntriesContainingTheValueAreNotUsedWhenTheEndpointFails(JenkinsRule r) throws Exception {
    ValueServiceJenkinsTest.MutableClock clock = new ValueServiceJenkinsTest.MutableClock();
    ValueCache.get().setClock(clock);
    try (StubHttpServer stub = listStub("[\"v1.0\", \"v1.1\"]")) {
      RestListParameterDefinition def = single(stub, 10);
      ValueService.entries(def, null, false);
      clock.advance(Duration.ofMinutes(11));
      stub.respond("/list", 503, "text/plain", "down");

      assertFalse(def.isValid(new RestListParameterValue("p", "v1.0", "d")));
      assertEquals(2, stub.requestCount("/list"));
    }
  }

  // REST Multi List Parameter

  @Test
  void listWithAllElementsCachedIsAcceptedWithoutRequest(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub("[\"a\", \"b\", \"c\"]")) {
      RestMultiListParameterDefinition def = multi(stub, 10);
      ValueService.entries(def, null, false);

      assertTrue(def.isValid(new RestMultiListParameterValue("p", List.of("a", "b"), "d")));
      assertEquals(1, stub.requestCount("/list"));
    }
  }

  @Test
  void listWithOneElementMissingFromCacheIsCheckedAgainstOneFetch(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub("[\"a\", \"b\"]")) {
      RestMultiListParameterDefinition def = multi(stub, 10);
      ValueService.entries(def, null, false);
      stub.respondJson("/list", "[\"a\", \"c\"]");

      assertTrue(def.isValid(new RestMultiListParameterValue("p", List.of("a", "c"), "d")));
      assertEquals(2, stub.requestCount("/list"), "the endpoint is contacted once");
      assertEquals("no-cache", stub.lastRequest().cacheControl());
    }
  }

  @Test
  void listWithBogusElementIsRejectedAfterOneFetch(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub("[\"a\", \"b\"]")) {
      RestMultiListParameterDefinition def = multi(stub, 10);
      ValueService.entries(def, null, false);

      assertFalse(def.isValid(new RestMultiListParameterValue("p", List.of("a", "bogus", "other"), "d")));
      assertEquals(2, stub.requestCount("/list"), "at most one fetch per validation");
    }
  }

  private static StubHttpServer listStub(final String json) throws Exception {
    StubHttpServer stub = new StubHttpServer();
    stub.respondJson("/list", json);
    return stub;
  }

  private static RestListParameterDefinition single(final StubHttpServer stub, final int cacheTime) {
    return new RestListParameterDefinition(
      "p", "d", stub.url("/list"), "", MimeType.APPLICATION_JSON, "$.*", "$", ValueOrder.NONE, ".*", cacheTime, "", false);
  }

  private static RestMultiListParameterDefinition multi(final StubHttpServer stub, final int cacheTime) {
    return new RestMultiListParameterDefinition(
      "p", "d", stub.url("/list"), "", MimeType.APPLICATION_JSON, "$.*", "$", ValueOrder.NONE, ".*", cacheTime, "", false);
  }
}
