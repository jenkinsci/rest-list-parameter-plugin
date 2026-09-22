package io.jenkins.plugins.restlistparam;

import io.jenkins.plugins.restlistparam.logic.RestValueService;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import io.jenkins.plugins.restlistparam.util.OkHttpUtils;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

@WithJenkins
class OkHttpUtilsJenkinsTest {

  @Test
  void sequentialFetchesShareOneCache(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/list", "[\"a\", \"b\"]");
      Cache cache = OkHttpUtils.getClientWithProxyAndCache(stub.url("/list")).cache();
      assertNotNull(cache, "the client has no response cache");
      int before = cache.requestCount();

      for (int i = 0; i < 2; i++) {
        assertFalse(RestValueService.get(stub.url("/list"), null, MimeType.APPLICATION_JSON, 0, "$.*", "$", null,
          ValueOrder.NONE).getErrorMsg().isPresent());
      }

      assertEquals(before + 2, cache.requestCount(), "both fetches went through the same cache");
      assertSame(cache, OkHttpUtils.getClientWithProxyAndCache(stub.url("/list")).cache());
      assertSame(cache, OkHttpUtils.getClient(stub.url("/list"), Duration.ofSeconds(5)).cache());
    }
  }

  @Test
  void derivedClientsHaveOnlyTheCallTimeout(JenkinsRule r) {
    OkHttpClient client = OkHttpUtils.getClient("http://127.0.0.1/", Duration.ofSeconds(7));

    assertEquals(7000, client.callTimeoutMillis());
    assertEquals(0, client.connectTimeoutMillis());
    assertEquals(0, client.readTimeoutMillis());
    assertEquals(0, client.writeTimeoutMillis());
  }

  @Test
  void changedCacheSizeRebuildsTheCache(JenkinsRule r) {
    Cache before = OkHttpUtils.getClientWithProxyAndCache("http://127.0.0.1/").cache();

    RestListParameterGlobalConfig.get().setCacheSize(RestListParameterGlobalConfig.get().getCacheSize() + 1);
    Cache after = OkHttpUtils.getClientWithProxyAndCache("http://127.0.0.1/").cache();

    assertNotNull(after);
    assertNotSame(before, after);
    assertEquals(51L * 1024 * 1024, after.maxSize());
  }
}
