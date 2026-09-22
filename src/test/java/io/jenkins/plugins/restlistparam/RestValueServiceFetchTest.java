package io.jenkins.plugins.restlistparam;

import io.jenkins.plugins.restlistparam.logic.RestValueService;
import io.jenkins.plugins.restlistparam.model.FetchErrorDetails;
import io.jenkins.plugins.restlistparam.model.FetchOptions;
import io.jenkins.plugins.restlistparam.model.LinkHeaderPagination;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.Pagination;
import io.jenkins.plugins.restlistparam.model.ResultContainer;
import io.jenkins.plugins.restlistparam.model.ValueItem;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fetch time limit and the error details of {@link RestValueService} (specs/value-fetching, "Fetch time limit",
 * and specs/build-parameter-form, "Fetch errors are shown on the form").
 */
class RestValueServiceFetchTest {

  // Fetch time limit

  @Test
  void hangingEndpointTimesOut() throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/hang", "[\"a\"]").withDelay("/hang", Duration.ofSeconds(30));

      long start = System.nanoTime();
      ResultContainer<List<ValueItem>> result = get(stub.url("/hang"), null, new FetchOptions(2, false));
      long elapsed = Duration.ofNanos(System.nanoTime() - start).toMillis();

      assertEquals("Fetching values timed out after 2 seconds", result.getErrorMsg().orElse(null));
      assertTrue(result.getValue().isEmpty());
      assertTrue(elapsed >= 1900 && elapsed < 10_000, "took " + elapsed + " ms");
      assertTrue(result.getErrorDetails().isPresent());
    }
  }

  @Test
  void pagesAddUpToTheTimeLimit() throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      for (int page = 1; page <= 5; page++) {
        String path = page == 1 ? "/p" : "/p?page=" + page;
        stub.respondJson(path, "[\"" + page + "\"]").withDelay(path, Duration.ofMillis(1200));
        if (page < 5) {
          stub.withHeader(path, "Link", "<" + stub.url("/p?page=" + (page + 1)) + ">; rel=\"next\"");
        }
      }

      ResultContainer<List<ValueItem>> result = get(stub.url("/p"), new LinkHeaderPagination(),
        new FetchOptions(3, false));

      assertEquals("Fetching values timed out after 3 seconds", result.getErrorMsg().orElse(null));
      assertTrue(result.getValue().isEmpty(), "entries of pages already fetched are discarded");
      assertTrue(stub.requests().size() < 5, "stopped before the last page: " + stub.requestUris());
    }
  }

  @Test
  void fastEndpointIsUnaffected() throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/p", "[\"1\"]").withHeader("/p", "Link", "<" + stub.url("/p?page=2") + ">; rel=\"next\"");
      stub.respondJson("/p?page=2", "[\"2\"]");

      ResultContainer<List<ValueItem>> result = get(stub.url("/p"), new LinkHeaderPagination(),
        new FetchOptions(60, false));

      assertFalse(result.getErrorMsg().isPresent(), result.getErrorMsg().orElse(""));
      assertEquals(List.of("1", "2"), result.getValue().stream().map(ValueItem::getValue).collect(Collectors.toList()));
      assertFalse(result.getErrorDetails().isPresent());
    }
  }

  // Error details

  @Test
  void errorDetailsNameTheFailingPageWithoutSecrets() throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      String endpoint = stub.url("/api?token=s3cret").replace("http://", "http://u:p@");
      stub.respondJson("/api?token=s3cret", "[\"a\"]")
          .withHeader("/api?token=s3cret", "Link", "<" + stub.url("/api?token=s3cret&page=2") + ">; rel=\"next\"");
      stub.respond("/api?token=s3cret&page=2", 503, "text/plain", "unavailable");

      ResultContainer<List<ValueItem>> result = get(endpoint, new LinkHeaderPagination(), new FetchOptions(60, false));

      assertEquals("Encountered Http Server Error: 503 (page 2)", result.getErrorMsg().orElse(null));
      FetchErrorDetails details = result.getErrorDetails().orElseThrow();
      assertEquals(stub.url("/api"), details.getUrl());
      assertEquals(2, details.getPage());
      assertEquals("503", details.getCause());
      assertTrue(details.getDurationMs() >= 0);
    }
  }

  @Test
  void sanitizedUrlDropsQueryAndUserInfo() {
    assertEquals("https://h/api", FetchErrorDetails.sanitizeUrl("https://u:p@h/api?token=s3cret"));
    assertEquals("https://h/api", new FetchErrorDetails("https://u:p@h/api?token=s3cret#x", 2, "503", 5).getUrl());
    assertEquals("ftp://h/x", FetchErrorDetails.sanitizeUrl("ftp://u:p@h/x?token=s3cret"));
  }

  @Test
  void refusedConnectionNamesTheExceptionInDetails() {
    ResultContainer<List<ValueItem>> result = get("http://127.0.0.1:1/api", null, new FetchOptions(60, false));

    assertEquals("OKHttp request threw java.net.ConnectException", result.getErrorMsg().orElse(null));
    FetchErrorDetails details = result.getErrorDetails().orElseThrow();
    assertEquals("ConnectException", details.getCause());
    assertEquals(1, details.getPage());
  }

  private static ResultContainer<List<ValueItem>> get(final String url,
                                                      final Pagination pagination,
                                                      final FetchOptions options)
  {
    return RestValueService.get(url, null, MimeType.APPLICATION_JSON, 0, "$.*", "$", null, ValueOrder.NONE,
      Collections.emptyMap(), pagination, options);
  }
}
