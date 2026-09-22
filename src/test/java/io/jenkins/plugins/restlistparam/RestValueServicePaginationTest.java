package io.jenkins.plugins.restlistparam;

import io.jenkins.plugins.restlistparam.logic.RestValueService;
import io.jenkins.plugins.restlistparam.model.ContinuationTokenPagination;
import io.jenkins.plugins.restlistparam.model.LinkHeaderPagination;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.Pagination;
import io.jenkins.plugins.restlistparam.model.ResultContainer;
import io.jenkins.plugins.restlistparam.model.ValueItem;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import okhttp3.HttpUrl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pagination loop of {@link RestValueService} against a local stub (specs/response-pagination and the
 * paginated parts of specs/value-extraction).
 */
class RestValueServicePaginationTest {

  // Pagination is opt-in

  @Test
  void withoutPaginationOnlyTheEndpointIsRequested() throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/api", "[\"a\"]").withHeader("/api", "Link", "<" + stub.url("/api?page=2") + ">; rel=\"next\"");
      stub.respondJson("/api?page=2", "[\"b\"]");

      ResultContainer<List<ValueItem>> result = get(stub.url("/api"), "$.*", null);

      assertEquals(List.of("a"), values(result));
      assertEquals(List.of("/api"), stub.requestUris());
      assertEquals(1, result.getPagesFetched());
      assertFalse(result.isPageLimitReached());
    }
  }

  // Link header strategy

  @Test
  void followsNextLinksInOrderUntilThereIsNone() throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/api", "[\"a\"]")
          .withHeader("/api", "Link", "<" + stub.url("/api?page=2") + ">; rel=\"next\", <" + stub.url("/api?page=3") + ">; rel=\"last\"");
      stub.respondJson("/api?page=2", "[\"b\"]")
          .withHeader("/api?page=2", "Link", "<" + stub.url("/api?page=3") + ">; rel=\"next\"");
      stub.respondJson("/api?page=3", "[\"c\"]")
          .withHeader("/api?page=3", "Link", "<" + stub.url("/api?page=1") + ">; rel=\"first\"");

      ResultContainer<List<ValueItem>> result = get(stub.url("/api"), "$.*", new LinkHeaderPagination());

      assertEquals(List.of("a", "b", "c"), values(result));
      assertEquals(List.of("/api", "/api?page=2", "/api?page=3"), stub.requestUris());
      assertEquals(3, result.getPagesFetched());
      assertFalse(result.isPageLimitReached());
    }
  }

  @Test
  void relativeNextLinkIsResolvedAgainstThePage() throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/api/tags?page=1", "[\"a\"]")
          .withHeader("/api/tags?page=1", "Link", "</api/tags?page=2>; rel=\"next\"");
      stub.respondJson("/api/tags?page=2", "[\"b\"]");

      ResultContainer<List<ValueItem>> result = get(stub.url("/api/tags?page=1"), "$.*", new LinkHeaderPagination());

      assertEquals(List.of("a", "b"), values(result));
      assertEquals(List.of("/api/tags?page=1", "/api/tags?page=2"), stub.requestUris());
    }
  }

  @Test
  void xmlParameterFollowsNextLinks() throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respond("/xml", 200, "application/xml", "<root><v>a</v><v>b</v></root>")
          .withHeader("/xml", "Link", "<" + stub.url("/xml?page=2") + ">; rel=\"next\"");
      stub.respond("/xml?page=2", 200, "application/xml", "<root><v>c</v></root>");

      ResultContainer<List<ValueItem>> result = RestValueService.get(stub.url("/xml"), null, MimeType.APPLICATION_XML,
        0, "//v", "", null, ValueOrder.NONE, Collections.emptyMap(), new LinkHeaderPagination());

      assertEquals(List.of("a", "b", "c"), values(result));
      assertEquals(2, stub.requestCount("/xml"));
    }
  }

  // Continuation token strategy

  @Test
  void nexusContinuationToken() throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/service/rest/v1/components?repository=releases",
        "{\"items\":[{\"version\":\"1.0\"},{\"version\":\"1.1\"}],\"continuationToken\":\"abc\"}");
      stub.respondJson("/service/rest/v1/components?repository=releases&continuationToken=abc",
        "{\"items\":[{\"version\":\"2.0\"}],\"continuationToken\":null}");

      ResultContainer<List<ValueItem>> result = get(stub.url("/service/rest/v1/components?repository=releases"),
        "$.items[*].version", nexus());

      assertEquals(List.of("1.0", "1.1", "2.0"), values(result));
      assertEquals(List.of("/service/rest/v1/components?repository=releases",
                           "/service/rest/v1/components?repository=releases&continuationToken=abc"),
        stub.requestUris());
      assertEquals(2, result.getPagesFetched());
    }
  }

  @Test
  void continuationTokenOnXmlFailsWithoutRequest() throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respond("/xml", 200, "application/xml", "<root><v>a</v></root>");

      ResultContainer<List<ValueItem>> result = RestValueService.get(stub.url("/xml"), null, MimeType.APPLICATION_XML,
        0, "//v", "", null, ValueOrder.NONE, Collections.emptyMap(), nexus());

      assertEquals("Continuation token pagination requires the JSON MIME type", result.getErrorMsg().orElse(null));
      assertTrue(result.getValue().isEmpty());
      assertTrue(stub.requests().isEmpty());
    }
  }

  // Values from all pages are combined

  @Test
  void orderAppliesAcrossPages() throws Exception {
    try (StubHttpServer stub = twoPages("[\"b\",\"c\"]", "[\"a\"]")) {
      ResultContainer<List<ValueItem>> result = RestValueService.get(stub.url("/api"), null,
        MimeType.APPLICATION_JSON, 0, "$.*", "$", null, ValueOrder.ASC, Collections.emptyMap(),
        new LinkHeaderPagination());

      assertEquals(List.of("a", "b", "c"), values(result));
      assertEquals(2, result.getPagesFetched(), "the page count survives filtering and ordering");
    }
  }

  @Test
  void filterAppliesAcrossPages() throws Exception {
    try (StubHttpServer stub = twoPages("[\"v1\",\"x\"]", "[\"v2\"]")) {
      ResultContainer<List<ValueItem>> result = RestValueService.get(stub.url("/api"), null,
        MimeType.APPLICATION_JSON, 0, "$.*", "$", "v.*", ValueOrder.NONE, Collections.emptyMap(),
        new LinkHeaderPagination());

      assertEquals(List.of("v1", "v2"), values(result));
    }
  }

  // Emptiness is judged on all pages together

  @Test
  void emptyLastPageIsNotAnError() throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/api", "{\"items\":[{\"version\":\"1.0\"},{\"version\":\"1.1\"}],\"continuationToken\":\"t\"}");
      stub.respondJson("/api?continuationToken=t", "{\"items\":[],\"continuationToken\":null}");

      ResultContainer<List<ValueItem>> result = get(stub.url("/api"), "$.items[*].version", nexus());

      assertFalse(result.getErrorMsg().isPresent(), result.getErrorMsg().orElse(""));
      assertEquals(List.of("1.0", "1.1"), values(result));
      assertEquals(2, result.getPagesFetched());
    }
  }

  @Test
  void everyPageEmptyIsNoValues() throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/api", "{\"items\":[],\"continuationToken\":\"t\"}");
      stub.respondJson("/api?continuationToken=t", "{\"items\":[],\"continuationToken\":null}");

      ResultContainer<List<ValueItem>> result = get(stub.url("/api"), "$.items[*].version", nexus());

      assertEquals("Json-Path expression yielded no results", result.getErrorMsg().orElse(null));
      assertTrue(result.isNoValues());
      assertEquals(2, stub.requests().size());
    }
  }

  // Page limit

  @Test
  void limitReachedKeepsValuesAndWarns() throws Exception {
    try (StubHttpServer stub = endlessPages(10); LogCapture log = new LogCapture(RestValueService.class)) {
      ResultContainer<List<ValueItem>> result = get(stub.url("/p"), "$.*", linkHeader(3));

      assertFalse(result.getErrorMsg().isPresent());
      assertEquals(List.of("1", "2", "3"), values(result));
      assertEquals(3, stub.requests().size());
      assertEquals(3, result.getPagesFetched());
      assertTrue(result.isPageLimitReached());
      assertTrue(log.warnings().stream().anyMatch(msg -> msg.contains("page limit of 3")), log.warnings().toString());
    }
  }

  @Test
  void lastPageCoincidingWithLimitIsNotAWarning() throws Exception {
    try (StubHttpServer stub = endlessPages(3); LogCapture log = new LogCapture(RestValueService.class)) {
      ResultContainer<List<ValueItem>> result = get(stub.url("/p"), "$.*", linkHeader(3));

      assertEquals(List.of("1", "2", "3"), values(result));
      assertEquals(3, stub.requests().size());
      assertFalse(result.isPageLimitReached());
      assertTrue(log.warnings().isEmpty(), log.warnings().toString());
    }
  }

  @Test
  void outOfRangeLimitIsCappedAtHundredPages() throws Exception {
    try (StubHttpServer stub = endlessPages(150)) {
      ResultContainer<List<ValueItem>> result = get(stub.url("/p"), "$.*", linkHeader(500));

      assertEquals(100, stub.requests().size());
      assertEquals(100, values(result).size());
      assertTrue(result.isPageLimitReached());
    }
  }

  // Repeated pages end pagination

  @Test
  void repeatedTokenEndsPagination() throws Exception {
    try (StubHttpServer stub = new StubHttpServer(); LogCapture log = new LogCapture(RestValueService.class)) {
      stub.respondJson("/api", "{\"items\":[\"a\"],\"continuationToken\":\"abc\"}");
      stub.respondJson("/api?continuationToken=abc", "{\"items\":[\"b\"],\"continuationToken\":\"abc\"}");

      ResultContainer<List<ValueItem>> result = get(stub.url("/api"), "$.items[*]", nexus());

      assertFalse(result.getErrorMsg().isPresent());
      assertEquals(List.of("a", "b"), values(result));
      assertEquals(List.of("/api", "/api?continuationToken=abc"), stub.requestUris());
      assertFalse(result.isPageLimitReached());
      assertEquals(1, log.warnings().stream().filter(msg -> msg.contains("already fetched")).count(),
        log.warnings().toString());
    }
  }

  @Test
  void linkCycleEndsPagination() throws Exception {
    try (StubHttpServer stub = new StubHttpServer(); LogCapture log = new LogCapture(RestValueService.class)) {
      stub.respondJson("/api", "[\"a\"]").withHeader("/api", "Link", "<" + stub.url("/api?page=2") + ">; rel=\"next\"");
      stub.respondJson("/api?page=2", "[\"b\"]").withHeader("/api?page=2", "Link", "</api>; rel=\"next\"");

      ResultContainer<List<ValueItem>> result = get(stub.url("/api"), "$.*", new LinkHeaderPagination());

      assertFalse(result.getErrorMsg().isPresent());
      assertEquals(List.of("a", "b"), values(result));
      assertEquals(List.of("/api", "/api?page=2"), stub.requestUris());
      assertFalse(log.warnings().isEmpty());
    }
  }

  // Pagination stays on the endpoint's origin

  @Test
  void nextLinkToAnotherOriginFailsWithoutRequestingIt() throws Exception {
    try (StubHttpServer stub = new StubHttpServer(); StubHttpServer foreign = new StubHttpServer()) {
      foreign.respondJson("/tags", "[\"stolen\"]");
      stub.respondJson("/tags", "[\"a\"]")
          .withHeader("/tags", "Link", "<" + foreign.url("/tags?page=2&token=secret") + ">; rel=\"next\"");

      ResultContainer<List<ValueItem>> result = get(stub.url("/tags"), "$.*", new LinkHeaderPagination());

      String origin = HttpUrl.get(foreign.url("/")).port() + "";
      assertEquals("Next page is not on the same host as the Rest Endpoint: http://127.0.0.1:" + origin,
        result.getErrorMsg().orElse(null));
      assertTrue(result.getValue().isEmpty());
      assertTrue(foreign.requests().isEmpty());
    }
  }

  @Test
  void foreignLinkOnTheLastAllowedPageIsStillAnError() throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/tags", "[\"a\"]")
          .withHeader("/tags", "Link", "<https://evil.example.net/tags?page=2>; rel=\"next\"");

      ResultContainer<List<ValueItem>> result = get(stub.url("/tags"), "$.*", linkHeader(1));

      assertEquals("Next page is not on the same host as the Rest Endpoint: https://evil.example.net:443",
        result.getErrorMsg().orElse(null));
      assertTrue(result.getValue().isEmpty());
    }
  }

  @Test
  void schemeChangeIsAnotherOrigin() throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      String httpsSameHostAndPort = stub.url("/api?page=2").replace("http://", "https://");
      stub.respondJson("/api", "[\"a\"]").withHeader("/api", "Link", "<" + httpsSameHostAndPort + ">; rel=\"next\"");

      ResultContainer<List<ValueItem>> result = get(stub.url("/api"), "$.*", new LinkHeaderPagination());

      assertTrue(result.getErrorMsg().orElse("").startsWith("Next page is not on the same host as the Rest Endpoint: https://127.0.0.1:"),
        result.getErrorMsg().orElse(""));
      assertEquals(1, stub.requests().size());
    }
  }

  @Test
  void originComparisonUsesDefaultPorts() throws Exception {
    Method isSameOrigin = RestValueService.class.getDeclaredMethod("isSameOrigin", HttpUrl.class, HttpUrl.class);
    isSameOrigin.setAccessible(true);
    HttpUrl endpoint = HttpUrl.get("https://h/api");

    assertEquals(true, isSameOrigin.invoke(null, endpoint, HttpUrl.get("https://h:443/api?page=2")));
    assertEquals(true, isSameOrigin.invoke(null, endpoint, HttpUrl.get("https://H/other")));
    assertEquals(false, isSameOrigin.invoke(null, endpoint, HttpUrl.get("http://h/api?page=2")));
    assertEquals(false, isSameOrigin.invoke(null, endpoint, HttpUrl.get("https://h:8443/api?page=2")));
    assertEquals(false, isSameOrigin.invoke(null, endpoint, HttpUrl.get("https://h.evil/api?page=2")));
  }

  // A failing page fails the whole fetch

  @Test
  void serverErrorOnALaterPageNamesThePage() throws Exception {
    try (StubHttpServer stub = endlessPages(10)) {
      stub.respond("/p?page=4", 503, "text/plain", "unavailable");

      ResultContainer<List<ValueItem>> result = get(stub.url("/p"), "$.*", new LinkHeaderPagination());

      assertEquals("Encountered Http Server Error: 503 (page 4)", result.getErrorMsg().orElse(null));
      assertTrue(result.getValue().isEmpty());
      assertEquals(4, stub.requests().size());
    }
  }

  @Test
  void failureOnTheFirstPageIsUnchanged() throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      ResultContainer<List<ValueItem>> result = get(stub.url("/missing"), "$.*", new LinkHeaderPagination());

      assertEquals("Encountered Http Client Error: 404", result.getErrorMsg().orElse(null));
    }
  }

  @Test
  void malformedBodyOnALaterPageNamesThePage() throws Exception {
    try (StubHttpServer stub = twoPages("[\"a\"]", "[\"b\"")) {
      ResultContainer<List<ValueItem>> result = get(stub.url("/api"), "$.*", new LinkHeaderPagination());

      assertEquals("Tried to pars malformed Json (page 2)", result.getErrorMsg().orElse(null));
      assertTrue(result.getValue().isEmpty());
      assertFalse(result.isNoValues());
    }
  }

  private static ResultContainer<List<ValueItem>> get(final String url,
                                                      final String valueExpression,
                                                      final Pagination pagination)
  {
    return RestValueService.get(url, null, MimeType.APPLICATION_JSON, 0, valueExpression, "$", null,
      ValueOrder.NONE, Collections.emptyMap(), pagination);
  }

  private static List<String> values(final ResultContainer<List<ValueItem>> result) {
    assertFalse(result.getErrorMsg().isPresent(), result.getErrorMsg().orElse(""));
    return result.getValue().stream().map(ValueItem::getValue).collect(Collectors.toList());
  }

  private static Pagination nexus() {
    return new ContinuationTokenPagination("$.continuationToken", "continuationToken");
  }

  private static Pagination linkHeader(final int maxPages) {
    Pagination pagination = new LinkHeaderPagination();
    pagination.setMaxPages(maxPages);
    return pagination;
  }

  private static StubHttpServer twoPages(final String first, final String second) throws Exception {
    StubHttpServer stub = new StubHttpServer();
    stub.respondJson("/api", first).withHeader("/api", "Link", "<" + stub.url("/api?page=2") + ">; rel=\"next\"");
    stub.respondJson("/api?page=2", second);
    return stub;
  }

  /** Pages {@code /p}, {@code /p?page=2} ... {@code /p?page=<count>}, page n yielding {@code ["n"]}. */
  private static StubHttpServer endlessPages(final int count) throws Exception {
    StubHttpServer stub = new StubHttpServer();
    for (int page = 1; page <= count; page++) {
      String path = page == 1 ? "/p" : "/p?page=" + page;
      stub.respondJson(path, "[\"" + page + "\"]");
      if (page < count) {
        stub.withHeader(path, "Link", "<" + stub.url("/p?page=" + (page + 1)) + ">; rel=\"next\"");
      }
    }
    return stub;
  }
}
