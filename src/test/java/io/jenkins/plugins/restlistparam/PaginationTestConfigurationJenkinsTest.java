package io.jenkins.plugins.restlistparam;

import hudson.util.FormValidation;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.List;
import java.util.StringJoiner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Test Configuration action with the form's pagination settings (specs/configuration-validation).
 */
@WithJenkins
class PaginationTestConfigurationJenkinsTest {

  private static final String LINK_HEADER = "{\"kind\":\"linkHeader\",\"maxPages\":\"10\",\"tokenExpression\":\"\",\"queryParameter\":\"\"}";
  private static final String NEXUS =
    "{\"kind\":\"continuationToken\",\"maxPages\":\"10\",\"tokenExpression\":\"$.continuationToken\",\"queryParameter\":\"continuationToken\"}";

  @Test
  void pagedSuccessReportsPages(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = pages(3, 3, 19)) {
      FormValidation result = test(r, stub.url("/p"), MimeType.APPLICATION_JSON, LINK_HEADER);

      assertEquals(FormValidation.Kind.OK, result.kind, result.getMessage());
      assertEquals("Test Successful! 57 Values from 3 pages, first: v1.0", result.getMessage());
      assertEquals(3, stub.requests().size());
    }
  }

  @Test
  void pageLimitIsAWarning(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = pages(12, 12, 10)) {
      FormValidation result = test(r, stub.url("/p"), MimeType.APPLICATION_JSON, LINK_HEADER);

      assertEquals(FormValidation.Kind.WARNING, result.kind, result.getMessage());
      assertEquals("Test Successful! 100 Values from 10 pages, first: v1.0 (page limit reached, more values may exist)",
        result.getMessage());
      assertEquals(10, stub.requests().size());
    }
  }

  @Test
  void formPageLimitIsUsed(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = pages(5, 5, 1)) {
      FormValidation result = test(r, stub.url("/p"), MimeType.APPLICATION_JSON, LINK_HEADER.replace("\"10\"", "\"2\""));

      assertEquals(FormValidation.Kind.WARNING, result.kind, result.getMessage());
      assertEquals(2, stub.requests().size());
    }
  }

  @Test
  void unsavedContinuationTokenSettingIsUsed(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/components?repository=releases",
        "{\"items\":[{\"version\":\"v1.0\"}],\"continuationToken\":\"abc\"}");
      stub.respondJson("/components?repository=releases&continuationToken=abc",
        "{\"items\":[{\"version\":\"v2.0\"}],\"continuationToken\":null}");

      FormValidation result = r.jenkins.getDescriptorByType(RestListParameterDefinition.DescriptorImpl.class)
        .doTestConfiguration(null, stub.url("/components?repository=releases"), "", MimeType.APPLICATION_JSON,
          "$.items[*].version", "$", ".*", ValueOrder.NONE, "[]", NEXUS);

      assertEquals("Test Successful! 2 Values from 2 pages, first: v1.0", result.getMessage());
      assertEquals(List.of("/components?repository=releases", "/components?repository=releases&continuationToken=abc"),
        stub.requestUris());
    }
  }

  @Test
  void withoutPaginationTheMessageIsUnchanged(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = pages(3, 3, 2)) {
      for (String none : new String[]{null, "", "{not json", "{\"kind\":\"unknown\"}"}) {
        FormValidation result = test(r, stub.url("/p"), MimeType.APPLICATION_JSON, none);
        assertEquals("Test Successful! 2 Values, first: v1.0", result.getMessage(), String.valueOf(none));
      }
      assertEquals(4, stub.requests().size(), "one request per test, the Link header is not followed");
    }
  }

  @Test
  void invalidSettingsSendNoRequest(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = pages(3, 3, 2)) {
      assertError("Token Expression must not be empty", test(r, stub.url("/p"), MimeType.APPLICATION_JSON,
        NEXUS.replace("$.continuationToken", " ")));
      assertError("The provided Json-Path expression seems to be incorrect", test(r, stub.url("/p"),
        MimeType.APPLICATION_JSON, NEXUS.replace("$.continuationToken", "$.[")));
      assertError("Query Parameter must not be empty", test(r, stub.url("/p"), MimeType.APPLICATION_JSON,
        NEXUS.replace("\"queryParameter\":\"continuationToken\"", "\"queryParameter\":\"\"")));
      assertError("Continuation token pagination requires the JSON MIME type", test(r, stub.url("/p"),
        MimeType.APPLICATION_XML, NEXUS));
      assertError("Max pages MUST BE between 1 and 100", test(r, stub.url("/p"), MimeType.APPLICATION_JSON,
        LINK_HEADER.replace("\"10\"", "\"0\"")));
      assertError("Max pages MUST BE between 1 and 100", test(r, stub.url("/p"), MimeType.APPLICATION_JSON,
        LINK_HEADER.replace("\"10\"", "\"101\"")));

      assertTrue(stub.requests().isEmpty(), stub.requestUris().toString());
    }
  }

  @Test
  void errorOnALaterPageIsShown(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = pages(3, 3, 2)) {
      stub.respond("/p?page=2", 503, "text/plain", "unavailable");

      assertError("Encountered Http Server Error: 503 (page 2)",
        test(r, stub.url("/p"), MimeType.APPLICATION_JSON, LINK_HEADER));
    }
  }

  private static FormValidation test(final JenkinsRule r,
                                     final String endpoint,
                                     final MimeType mimeType,
                                     final String paginationJson)
  {
    return r.jenkins.getDescriptorByType(RestListParameterDefinition.DescriptorImpl.class)
      .doTestConfiguration(null, endpoint, "", mimeType, "$.*", "$", ".*", ValueOrder.NONE, "[]", paginationJson);
  }

  private static void assertError(final String message, final FormValidation result) {
    assertEquals(FormValidation.Kind.ERROR, result.kind, result.getMessage());
    assertEquals(message, result.getMessage());
  }

  /**
   * Pages {@code /p}, {@code /p?page=2} ... up to {@code count}, each linking to the next up to {@code linked},
   * page n holding {@code perPage} values {@code vn.0, vn.1, ...}.
   */
  private static StubHttpServer pages(final int count, final int linked, final int perPage) throws Exception {
    StubHttpServer stub = new StubHttpServer();
    for (int page = 1; page <= count; page++) {
      StringJoiner values = new StringJoiner(",", "[", "]");
      for (int i = 0; i < perPage; i++) {
        values.add("\"v" + page + "." + i + "\"");
      }
      String path = page == 1 ? "/p" : "/p?page=" + page;
      stub.respondJson(path, values.toString());
      if (page < linked) {
        stub.withHeader(path, "Link", "<" + stub.url("/p?page=" + (page + 1)) + ">; rel=\"next\"");
      }
    }
    return stub;
  }
}
