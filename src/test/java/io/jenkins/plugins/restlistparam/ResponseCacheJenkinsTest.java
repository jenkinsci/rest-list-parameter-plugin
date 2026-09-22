package io.jenkins.plugins.restlistparam;

import io.jenkins.plugins.restlistparam.logic.RestValueService;
import io.jenkins.plugins.restlistparam.model.FetchOptions;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.ResultContainer;
import io.jenkins.plugins.restlistparam.model.ValueItem;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The HTTP response cache below the value cache (specs/value-fetching, "Response caching").
 */
@WithJenkins
class ResponseCacheJenkinsTest {

  @Test
  void normalFetchUsesFreshCachedResponse(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = cacheableStub()) {
      fetch(stub, false);
      fetch(stub, false);

      assertEquals(1, stub.requestCount("/list"), "the second fetch is answered from the response cache");
    }
  }

  @Test
  void forcedFetchBypassesFreshCachedResponse(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = cacheableStub()) {
      fetch(stub, false);
      fetch(stub, true);

      assertEquals(2, stub.requestCount("/list"));
      assertEquals("no-cache", stub.lastRequest().cacheControl());
    }
  }

  private static StubHttpServer cacheableStub() throws Exception {
    StubHttpServer stub = new StubHttpServer();
    stub.respondJson("/list", "[\"a\", \"b\"]").withHeader("/list", "Cache-Control", "max-age=600");
    return stub;
  }

  private static void fetch(final StubHttpServer stub, final boolean forced) {
    ResultContainer<List<ValueItem>> result = RestValueService.get(stub.url("/list"), null, MimeType.APPLICATION_JSON,
      10, "$.*", "$", null, ValueOrder.NONE, Collections.emptyMap(), null, new FetchOptions(60, forced));
    assertFalse(result.getErrorMsg().isPresent(), result.getErrorMsg().orElse(""));
  }
}
