package io.jenkins.plugins.restlistparam;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
}
