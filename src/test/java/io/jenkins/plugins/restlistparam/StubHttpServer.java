package io.jenkins.plugins.restlistparam;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Local HTTP stub for tests: serves a canned status, body and optional headers per path (or path plus
 * query string) on an ephemeral port and records every request, so tests don't depend on the public internet.
 * <p>
 * A request is answered by the response registered for its exact path and raw query string
 * ({@code /api?page=2}), falling back to the one registered for its path alone ({@code /api}).
 * Requests are handled concurrently, so a delayed response does not hold up requests to other paths.
 */
public class StubHttpServer implements AutoCloseable {
  private final HttpServer server;
  private final Map<String, Response> responses = new ConcurrentHashMap<>();
  private final Map<String, Duration> delays = new ConcurrentHashMap<>();
  private final Map<String, Queue<Response>> onceResponses = new ConcurrentHashMap<>();
  private final List<RecordedRequest> requests = Collections.synchronizedList(new ArrayList<>());
  private final ExecutorService executor = Executors.newCachedThreadPool();

  public StubHttpServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", this::handle);
    server.setExecutor(executor);
    server.start();
  }

  /**
   * Serves {@code body} with {@code status} and {@code contentType} for requests to {@code path}.
   * {@code path} may carry a raw (encoded) query string to answer only that exact query.
   */
  public StubHttpServer respond(final String path, final int status, final String contentType, final String body) {
    responses.put(path, new Response(status, contentType, body));
    return this;
  }

  /**
   * Answers the next request to {@code path} (matched like {@link #respond}) with this response, before falling back
   * to the one registered with {@link #respond}. Several calls queue several one-time responses in order.
   */
  public StubHttpServer respondOnce(final String path, final int status, final String contentType, final String body) {
    onceResponses.computeIfAbsent(path, key -> new ConcurrentLinkedQueue<>()).add(new Response(status, contentType, body));
    return this;
  }

  /**
   * Adds a response header to the response already registered for {@code path}.
   * Calling it again with the same name adds another header line (e.g. several {@code Link} headers).
   * It replaces the stub's default header of that name ({@code Content-Type}, {@code Cache-Control: no-store}).
   */
  public StubHttpServer withHeader(final String path, final String name, final String value) {
    Response response = responses.get(path);
    if (response == null) {
      throw new IllegalStateException("No response registered for " + path);
    }
    response.headers.add(new String[]{name, value});
    return this;
  }

  /**
   * Waits {@code delay} before answering requests to {@code path} (matched like responses: exact path plus
   * query string first, then the path alone). The request is recorded when it arrives, before the delay.
   */
  public StubHttpServer withDelay(final String path, final Duration delay) {
    delays.put(path, delay);
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

  /** Number of requests to {@code path}, whatever their query string. */
  public long requestCount(final String path) {
    return requests().stream().filter(req -> req.path.equals(path)).count();
  }

  /** The full request URIs (path plus raw query string) in the order they were received. */
  public List<String> requestUris() {
    return requests().stream().map(RecordedRequest::uri).collect(Collectors.toList());
  }

  /** The most recent request, or {@code null} if none was received. */
  public RecordedRequest lastRequest() {
    List<RecordedRequest> all = requests();
    return all.isEmpty() ? null : all.get(all.size() - 1);
  }

  private void handle(final HttpExchange exchange) throws IOException {
    URI requestUri = exchange.getRequestURI();
    String path = requestUri.getRawPath();
    String uri = requestUri.getRawQuery() != null ? path + "?" + requestUri.getRawQuery() : path;
    requests.add(new RecordedRequest(path, uri, exchange.getRequestHeaders()));

    Duration delay = delays.containsKey(uri) ? delays.get(uri) : delays.get(path);
    if (delay != null) {
      try {
        Thread.sleep(delay.toMillis());
      }
      catch (InterruptedException e) {
        // the server is closing
        Thread.currentThread().interrupt();
        exchange.close();
        return;
      }
    }

    Response response = pollOnce(uri);
    if (response == null) {
      response = pollOnce(path);
    }
    if (response == null) {
      response = responses.get(uri);
    }
    if (response == null) {
      response = responses.getOrDefault(path, new Response(404, "text/plain", "not found"));
    }
    byte[] body = response.body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", response.contentType);
    exchange.getResponseHeaders().set("Cache-Control", "no-store");
    // headers added with withHeader replace the defaults of the same name (e.g. a cacheable Cache-Control)
    Set<String> replaced = new HashSet<>();
    for (String[] header : response.headers) {
      if (replaced.add(header[0].toLowerCase(Locale.ROOT))) {
        exchange.getResponseHeaders().remove(header[0]);
      }
      exchange.getResponseHeaders().add(header[0], header[1]);
    }
    exchange.sendResponseHeaders(response.status, body.length == 0 ? -1 : body.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(body);
    }
  }

  private Response pollOnce(final String key) {
    Queue<Response> queue = onceResponses.get(key);
    return queue != null ? queue.poll() : null;
  }

  @Override
  public void close() {
    server.stop(0);
    executor.shutdownNow();
  }

  private static final class Response {
    private final int status;
    private final String contentType;
    private final String body;
    private final List<String[]> headers = new CopyOnWriteArrayList<>();

    private Response(final int status, final String contentType, final String body) {
      this.status = status;
      this.contentType = contentType;
      this.body = body;
    }
  }

  public static final class RecordedRequest {
    private final String path;
    private final String uri;
    private final Headers headers;

    private RecordedRequest(final String path, final String uri, final Headers headers) {
      this.path = path;
      this.uri = uri;
      this.headers = headers;
    }

    public String path() {
      return path;
    }

    /** The path plus the raw (still encoded) query string, e.g. {@code /api?page=2}. */
    public String uri() {
      return uri;
    }

    /** First value of the header, or {@code null} when the request did not carry it. */
    public String header(final String name) {
      return headers.getFirst(name);
    }

    public boolean hasHeader(final String name) {
      return headers.containsKey(name);
    }

    /** The request's {@code Cache-Control} header, or {@code null} when it did not carry one. */
    public String cacheControl() {
      return header("Cache-Control");
    }
  }
}
