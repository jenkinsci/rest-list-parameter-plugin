package io.jenkins.plugins.restlistparam;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local HTTP stub for tests: serves a canned status and body per path on an ephemeral port
 * and records every request, so tests don't depend on the public internet.
 */
public class StubHttpServer implements AutoCloseable {
  private final HttpServer server;
  private final Map<String, Response> responses = new ConcurrentHashMap<>();
  private final List<RecordedRequest> requests = Collections.synchronizedList(new ArrayList<>());

  public StubHttpServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", this::handle);
    server.start();
  }

  /** Serves {@code body} with {@code status} and {@code contentType} for requests to {@code path}. */
  public StubHttpServer respond(final String path, final int status, final String contentType, final String body) {
    responses.put(path, new Response(status, contentType, body));
    return this;
  }

  public StubHttpServer respondJson(final String path, final String body) {
    return respond(path, 200, "application/json", body);
  }

  public String url(final String path) {
    return "http://127.0.0.1:" + server.getAddress().getPort() + path;
  }

  public List<RecordedRequest> requests() {
    synchronized (requests) {
      return new ArrayList<>(requests);
    }
  }

  public long requestCount(final String path) {
    return requests().stream().filter(req -> req.path.equals(path)).count();
  }

  /** The most recent request, or {@code null} if none was received. */
  public RecordedRequest lastRequest() {
    List<RecordedRequest> all = requests();
    return all.isEmpty() ? null : all.get(all.size() - 1);
  }

  private void handle(final HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    requests.add(new RecordedRequest(path, exchange.getRequestHeaders()));

    Response response = responses.getOrDefault(path, new Response(404, "text/plain", "not found"));
    byte[] body = response.body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", response.contentType);
    exchange.getResponseHeaders().set("Cache-Control", "no-store");
    exchange.sendResponseHeaders(response.status, body.length == 0 ? -1 : body.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(body);
    }
  }

  @Override
  public void close() {
    server.stop(0);
  }

  private static final class Response {
    private final int status;
    private final String contentType;
    private final String body;

    private Response(final int status, final String contentType, final String body) {
      this.status = status;
      this.contentType = contentType;
      this.body = body;
    }
  }

  public static final class RecordedRequest {
    private final String path;
    private final Headers headers;

    private RecordedRequest(final String path, final Headers headers) {
      this.path = path;
      this.headers = headers;
    }

    public String path() {
      return path;
    }

    /** First value of the header, or {@code null} when the request did not carry it. */
    public String header(final String name) {
      return headers.getFirst(name);
    }

    public boolean hasHeader(final String name) {
      return headers.containsKey(name);
    }
  }
}
