package io.jenkins.plugins.restlistparam;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

  @Test
  void delaysOnlyTheConfiguredPath() throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/slow", "[\"s\"]").withDelay("/slow", Duration.ofMillis(1500));
      stub.respondJson("/fast", "[\"f\"]");
      HttpClient client = HttpClient.newHttpClient();

      long start = System.nanoTime();
      var slow = client.sendAsync(HttpRequest.newBuilder(URI.create(stub.url("/slow"))).build(),
        HttpResponse.BodyHandlers.ofString());
      HttpResponse<String> fast = client.send(HttpRequest.newBuilder(URI.create(stub.url("/fast"))).build(),
        HttpResponse.BodyHandlers.ofString());
      long fastMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();
      HttpResponse<String> slowResponse = slow.get();
      long slowMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

      assertEquals("[\"f\"]", fast.body());
      assertTrue(fastMillis < 1000, "fast path waited for the slow one: " + fastMillis + " ms");
      assertEquals("[\"s\"]", slowResponse.body());
      assertTrue(slowMillis >= 1500, "slow path answered after " + slowMillis + " ms");
    }
  }

  @Test
  void recordsCacheControlRequestHeader() throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/api", "[]");
      HttpClient client = HttpClient.newHttpClient();

      client.send(HttpRequest.newBuilder(URI.create(stub.url("/api"))).header("Cache-Control", "no-cache").build(),
        HttpResponse.BodyHandlers.ofString());
      assertEquals("no-cache", stub.lastRequest().cacheControl());

      client.send(HttpRequest.newBuilder(URI.create(stub.url("/api"))).build(), HttpResponse.BodyHandlers.ofString());
      assertNull(stub.lastRequest().cacheControl());
    }
  }

  @Test
  void oneTimeResponsesComeFirst() throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/api", "[\"ok\"]").respondOnce("/api", 503, "text/plain", "down");
      HttpClient client = HttpClient.newHttpClient();

      HttpResponse<String> first = client.send(HttpRequest.newBuilder(URI.create(stub.url("/api"))).build(),
        HttpResponse.BodyHandlers.ofString());
      HttpResponse<String> second = client.send(HttpRequest.newBuilder(URI.create(stub.url("/api"))).build(),
        HttpResponse.BodyHandlers.ofString());

      assertEquals(503, first.statusCode());
      assertEquals(200, second.statusCode());
      assertEquals("[\"ok\"]", second.body());
    }
  }
}
