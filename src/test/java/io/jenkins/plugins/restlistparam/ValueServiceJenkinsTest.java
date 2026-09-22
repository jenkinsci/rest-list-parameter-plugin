package io.jenkins.plugins.restlistparam;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import hudson.model.FreeStyleProject;
import hudson.util.Secret;
import io.jenkins.plugins.restlistparam.logic.ValueService;
import io.jenkins.plugins.restlistparam.model.LinkHeaderPagination;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.ResultContainer;
import io.jenkins.plugins.restlistparam.model.ValueItem;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ValueService} with the value cache (specs/value-cache).
 */
@WithJenkins
class ValueServiceJenkinsTest {

  @AfterEach
  void restoreClock() {
    ValueCache.get().setClock(Clock.systemUTC());
  }

  // Processed entries are cached for the parameter's cache time

  @Test
  void formReopenedWithinTheCacheTime(JenkinsRule r) throws Exception {
    MutableClock clock = useMutableClock();
    try (StubHttpServer stub = listStub()) {
      RestListParameterDefinition def = def(stub.url("/list"), 10);
      FreeStyleProject job = r.createFreeStyleProject();

      assertEquals(List.of("a", "b"), values(ValueService.entries(def, job, false)));
      clock.advance(Duration.ofMinutes(3));
      assertEquals(List.of("a", "b"), values(ValueService.entries(def, job, false)));

      assertEquals(1, stub.requestCount("/list"));
    }
  }

  @Test
  void endpointForbiddingHttpCachingIsCachedAnyway(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub()) {
      stub.withHeader("/list", "Cache-Control", "no-store");
      RestListParameterDefinition def = def(stub.url("/list"), 10);
      FreeStyleProject job = r.createFreeStyleProject();

      ValueService.entries(def, job, false);
      ValueService.entries(def, job, false);

      assertEquals(1, stub.requestCount("/list"));
    }
  }

  @Test
  void cacheTimeElapsed(JenkinsRule r) throws Exception {
    MutableClock clock = useMutableClock();
    try (StubHttpServer stub = listStub()) {
      RestListParameterDefinition def = def(stub.url("/list"), 10);
      FreeStyleProject job = r.createFreeStyleProject();

      ValueService.entries(def, job, false);
      clock.advance(Duration.ofMinutes(11));
      ValueService.entries(def, job, false);

      assertEquals(2, stub.requestCount("/list"));
    }
  }

  @Test
  void paginatedEndpointRequestsEachPageOnce(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      for (int page = 1; page <= 3; page++) {
        String path = page == 1 ? "/p" : "/p?page=" + page;
        stub.respondJson(path, "[\"" + page + "\"]");
        if (page < 3) {
          stub.withHeader(path, "Link", "<" + stub.url("/p?page=" + (page + 1)) + ">; rel=\"next\"");
        }
      }
      RestListParameterDefinition def = def(stub.url("/p"), 10);
      def.setPagination(new LinkHeaderPagination());
      FreeStyleProject job = r.createFreeStyleProject();

      assertEquals(List.of("1", "2", "3"), values(ValueService.entries(def, job, false)));
      assertEquals(List.of("1", "2", "3"), values(ValueService.entries(def, job, false)));

      assertEquals(List.of("/p", "/p?page=2", "/p?page=3"), stub.requestUris());
    }
  }

  // Cache time 0 disables the value cache

  @Test
  void cacheTimeZeroFetchesEveryTime(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub()) {
      RestListParameterDefinition def = def(stub.url("/list"), 0);
      FreeStyleProject job = r.createFreeStyleProject();

      ValueService.entries(def, job, false);
      ValueService.entries(def, job, false);

      assertEquals(2, stub.requestCount("/list"));
      assertEquals(0, ValueCache.get().size());
    }
  }

  // Cached entries are isolated per job and configuration

  @Test
  void twoJobsWithDifferentCredentials(JenkinsRule r) throws Exception {
    addSecretText("token-a", "secret-a");
    addSecretText("token-b", "secret-b");
    try (StubHttpServer stub = listStub()) {
      FreeStyleProject a = r.createFreeStyleProject("A");
      FreeStyleProject b = r.createFreeStyleProject("B");
      RestListParameterDefinition defA = new RestListParameterDefinition(
        "p", "d", stub.url("/list"), "token-a", MimeType.APPLICATION_JSON, "$.*", "$", ValueOrder.NONE, ".*", 10, "", false);
      RestListParameterDefinition defB = new RestListParameterDefinition(
        "p", "d", stub.url("/list"), "token-b", MimeType.APPLICATION_JSON, "$.*", "$", ValueOrder.NONE, ".*", 10, "", false);

      ValueService.entries(defA, a, false);
      ValueService.entries(defB, b, false);

      assertEquals(2, stub.requestCount("/list"));
      assertEquals("Bearer secret-b", stub.lastRequest().header("Authorization"));
    }
  }

  @Test
  void sameConfigurationInTwoJobsIsCachedSeparately(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub()) {
      RestListParameterDefinition def = def(stub.url("/list"), 10);

      ValueService.entries(def, r.createFreeStyleProject("A"), false);
      ValueService.entries(def, r.createFreeStyleProject("B"), false);
      ValueService.entries(def, null, false);

      assertEquals(3, stub.requestCount("/list"));
    }
  }

  @Test
  void changedConfigurationFetchesWithTheNewExpression(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/list", "[{\"id\":\"1\",\"name\":\"one\"},{\"id\":\"2\",\"name\":\"two\"}]");
      FreeStyleProject job = r.createFreeStyleProject();
      RestListParameterDefinition before = new RestListParameterDefinition(
        "p", "d", stub.url("/list"), "", MimeType.APPLICATION_JSON, "$.*.id", "$", ValueOrder.NONE, ".*", 10, "", false);
      RestListParameterDefinition after = new RestListParameterDefinition(
        "p", "d", stub.url("/list"), "", MimeType.APPLICATION_JSON, "$.*.name", "$", ValueOrder.NONE, ".*", 10, "", false);

      assertEquals(List.of("1", "2"), values(ValueService.entries(before, job, false)));
      assertEquals(List.of("one", "two"), values(ValueService.entries(after, job, false)));

      assertEquals(2, stub.requestCount("/list"));
    }
  }

  // Failed fetches are not cached

  @Test
  void failureIsNotCached(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respond("/list", 503, "text/plain", "down");
      RestListParameterDefinition def = def(stub.url("/list"), 10);
      FreeStyleProject job = r.createFreeStyleProject();

      ResultContainer<List<ValueItem>> failed = ValueService.entries(def, job, false);
      assertEquals("Encountered Http Server Error: 503", failed.getErrorMsg().orElse(null));
      stub.respondJson("/list", "[\"a\"]");
      ResultContainer<List<ValueItem>> recovered = ValueService.entries(def, job, false);

      assertFalse(recovered.getErrorMsg().isPresent());
      assertEquals(List.of("a"), values(recovered));
      assertEquals(2, stub.requestCount("/list"));
    }
  }

  @Test
  void failedForcedFetchKeepsEarlierEntries(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub()) {
      RestListParameterDefinition def = def(stub.url("/list"), 10);
      FreeStyleProject job = r.createFreeStyleProject();
      ValueService.entries(def, job, false);

      stub.respond("/list", 503, "text/plain", "down");
      ResultContainer<List<ValueItem>> forced = ValueService.entries(def, job, true);
      assertTrue(forced.getErrorMsg().isPresent());
      assertEquals("no-cache", stub.lastRequest().cacheControl());
      ResultContainer<List<ValueItem>> next = ValueService.entries(def, job, false);

      assertEquals(List.of("a", "b"), values(next));
      assertEquals(2, stub.requestCount("/list"), "the next request is served from the cache");
    }
  }

  // Forced fetch bypasses both caches

  @Test
  void forcedFetchReplacesCachedEntries(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub()) {
      RestListParameterDefinition def = def(stub.url("/list"), 10);
      FreeStyleProject job = r.createFreeStyleProject();
      ValueService.entries(def, job, false);

      stub.respondJson("/list", "[\"a\", \"b\", \"c\"]");
      assertEquals(List.of("a", "b", "c"), values(ValueService.entries(def, job, true)));
      assertEquals("no-cache", stub.lastRequest().cacheControl());

      assertEquals(List.of("a", "b", "c"), values(ValueService.entries(def, job, false)));
      assertEquals(2, stub.requestCount("/list"));
    }
  }

  // Empty result (#209)

  @Test
  void emptyResultIsASuccessWhenEmptyValueAllowed(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/empty", "[]");
      RestListParameterDefinition def = def(stub.url("/empty"), 0);
      def.setAllowEmptyValue(true);

      ResultContainer<List<ValueItem>> result = ValueService.entries(def, null, false);

      assertFalse(result.getErrorMsg().isPresent());
      assertTrue(result.getValue().isEmpty());
    }
  }

  private static StubHttpServer listStub() throws Exception {
    StubHttpServer stub = new StubHttpServer();
    stub.respondJson("/list", "[\"a\", \"b\"]");
    return stub;
  }

  private static RestListParameterDefinition def(final String endpoint, final int cacheTime) {
    return new RestListParameterDefinition(
      "p", "d", endpoint, "", MimeType.APPLICATION_JSON, "$.*", "$", ValueOrder.NONE, ".*", cacheTime, "", false);
  }

  private static List<String> values(final ResultContainer<List<ValueItem>> result) {
    assertFalse(result.getErrorMsg().isPresent(), result.getErrorMsg().orElse(""));
    return result.getValue().stream().map(ValueItem::getValue).collect(Collectors.toList());
  }

  private static void addSecretText(final String id, final String secret) throws Exception {
    SystemCredentialsProvider.getInstance().getCredentials().add(
      new StringCredentialsImpl(CredentialsScope.GLOBAL, id, id, Secret.fromString(secret)));
    SystemCredentialsProvider.getInstance().save();
  }

  private static MutableClock useMutableClock() {
    MutableClock clock = new MutableClock();
    ValueCache.get().setClock(clock);
    return clock;
  }

  static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-01-01T00:00:00Z");

    void advance(final Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(final ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
