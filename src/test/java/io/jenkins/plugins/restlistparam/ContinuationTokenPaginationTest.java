package io.jenkins.plugins.restlistparam;

import io.jenkins.plugins.restlistparam.model.ContinuationTokenPagination;
import io.jenkins.plugins.restlistparam.model.Pagination;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContinuationTokenPaginationTest {

  private static final String NEXUS = "https://nexus/service/rest/v1/components?repository=releases";

  @Test
  void nexusContinuationToken() {
    Optional<Pagination.NextPage> next = next(NEXUS, "$.continuationToken", "continuationToken",
      "{\"items\":[{\"version\":\"1.0\"}],\"continuationToken\":\"abc\"}");
    assertEquals("https://nexus/service/rest/v1/components?repository=releases&continuationToken=abc",
      next.orElseThrow().getUrl().toString());
    assertEquals("abc", next.get().getToken());
  }

  @Test
  void nullMissingOrEmptyTokenEndsPagination() {
    assertTrue(next(NEXUS, "$.continuationToken", "continuationToken",
      "{\"items\":[],\"continuationToken\":null}").isEmpty());
    assertTrue(next(NEXUS, "$.continuationToken", "continuationToken", "{\"items\":[]}").isEmpty());
    assertTrue(next(NEXUS, "$.continuationToken", "continuationToken",
      "{\"items\":[],\"continuationToken\":\"\"}").isEmpty());
  }

  @Test
  void numericOffsetToken() {
    Optional<Pagination.NextPage> next = next("https://h/rest/api/1.0/projects", "$.nextPageStart", "start",
      "{\"values\":[],\"nextPageStart\":25,\"isLastPage\":false}");
    assertEquals("https://h/rest/api/1.0/projects?start=25", next.orElseThrow().getUrl().toString());
    assertEquals("25", next.get().getToken());
  }

  @Test
  void existingQueryParameterIsReplacedAndOthersKept() {
    HttpUrl url = next("https://h/api?start=0&limit=25", "$.nextPageStart", "start", "{\"nextPageStart\":25}")
      .orElseThrow().getUrl();
    assertEquals(List.of("25"), url.queryParameterValues("start"));
    assertEquals("25", url.queryParameter("limit"));
  }

  @Test
  void tokenIsUrlEncoded() {
    HttpUrl url = next("https://h/api", "$.token", "token", "{\"token\":\"a+b/c=\"}").orElseThrow().getUrl();
    assertEquals("a+b/c=", url.queryParameter("token"));
    assertEquals("token=a%2Bb%2Fc%3D", url.encodedQuery());
  }

  @Test
  void nonScalarTokenEndsPaginationWithWarning() {
    try (LogCapture log = new LogCapture(ContinuationTokenPagination.class)) {
      assertTrue(next(NEXUS, "$.continuationToken", "continuationToken",
        "{\"continuationToken\":{\"next\":\"abc\"}}").isEmpty());
      assertTrue(next(NEXUS, "$.continuationToken", "continuationToken",
        "{\"continuationToken\":true}").isEmpty());
      assertTrue(next(NEXUS, "$.items[*].token", "continuationToken",
        "{\"items\":[{\"token\":\"a\"}]}").isEmpty());

      assertEquals(3, log.warnings().size(), log.warnings().toString());
      assertTrue(log.warnings().get(0).contains("$.continuationToken"), log.warnings().get(0));
    }
  }

  @Test
  void endOfPaginationWithoutTokenLogsNoWarning() {
    try (LogCapture log = new LogCapture(ContinuationTokenPagination.class)) {
      next(NEXUS, "$.continuationToken", "continuationToken", "{\"continuationToken\":null}");
      next(NEXUS, "$.continuationToken", "continuationToken", "{}");
      assertTrue(log.warnings().isEmpty(), log.warnings().toString());
    }
  }

  private static Optional<Pagination.NextPage> next(final String endpoint,
                                                    final String tokenExpression,
                                                    final String queryParameter,
                                                    final String body)
  {
    HttpUrl url = HttpUrl.get(endpoint);
    return new ContinuationTokenPagination(tokenExpression, queryParameter)
      .next(new Pagination.Page(url, url, Headers.of(), body));
  }
}
