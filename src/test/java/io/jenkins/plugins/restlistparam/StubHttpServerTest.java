package io.jenkins.plugins.restlistparam;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StubHttpServerTest {

  @Test
  void servesCannedResponseAndRecordsHeaders() throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respond("/teapot", 418, "text/plain", "short and stout");

      HttpResponse<String> response = HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI.create(stub.url("/teapot"))).header("X-Probe", "42").build(),
        HttpResponse.BodyHandlers.ofString());

      assertEquals(418, response.statusCode());
      assertEquals("short and stout", response.body());
      assertEquals(1, stub.requestCount("/teapot"));
      assertEquals("42", stub.lastRequest().header("X-Probe"));
      assertFalse(stub.lastRequest().hasHeader("Authorization"));
    }
  }

  @Test
  void unknownPathIsNotFound() throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      HttpResponse<String> response = HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI.create(stub.url("/missing"))).build(),
        HttpResponse.BodyHandlers.ofString());

      assertEquals(404, response.statusCode());
    }
  }

  @Test
  void matchesQueryStringBeforePathAndSendsExtraHeaders() throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/api", "[\"first\"]")
          .withHeader("/api", "Link", "<" + stub.url("/api?page=2") + ">; rel=\"next\"")
          .withHeader("/api", "Link", "<" + stub.url("/api?page=9") + ">; rel=\"last\"");
      stub.respondJson("/api?page=2", "[\"second\"]");

      HttpClient client = HttpClient.newHttpClient();
      HttpResponse<String> first = client.send(
        HttpRequest.newBuilder(URI.create(stub.url("/api"))).build(), HttpResponse.BodyHandlers.ofString());
      HttpResponse<String> second = client.send(
        HttpRequest.newBuilder(URI.create(stub.url("/api?page=2"))).build(), HttpResponse.BodyHandlers.ofString());
      HttpResponse<String> fallback = client.send(
        HttpRequest.newBuilder(URI.create(stub.url("/api?page=3"))).build(), HttpResponse.BodyHandlers.ofString());

      assertEquals("[\"first\"]", first.body());
      assertEquals(List.of("<" + stub.url("/api?page=2") + ">; rel=\"next\"",
                           "<" + stub.url("/api?page=9") + ">; rel=\"last\""),
        first.headers().allValues("Link"));
      assertEquals("[\"second\"]", second.body());
      assertTrue(second.headers().allValues("Link").isEmpty());
      assertEquals("[\"first\"]", fallback.body(), "unknown query falls back to the path");
      assertEquals(List.of("/api", "/api?page=2", "/api?page=3"), stub.requestUris());
      assertEquals(3, stub.requestCount("/api"));
      assertEquals("/api?page=3", stub.lastRequest().uri());
      assertEquals("/api", stub.lastRequest().path());
    }
  }

  @Test
  void recordsQueryStringStillEncoded() throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/api", "[]");

      HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI.create(stub.url("/api?token=a%2Bb%2Fc%3D"))).build(),
        HttpResponse.BodyHandlers.ofString());

      assertEquals("/api?token=a%2Bb%2Fc%3D", stub.lastRequest().uri());
    }
  }
}
