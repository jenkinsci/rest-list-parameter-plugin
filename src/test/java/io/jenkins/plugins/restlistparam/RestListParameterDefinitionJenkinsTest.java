package io.jenkins.plugins.restlistparam;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import hudson.model.Descriptor;
import hudson.model.ParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.util.Secret;
import io.jenkins.plugins.restlistparam.model.CustomHeader;
import io.jenkins.plugins.restlistparam.model.LinkHeaderPagination;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.ValueItem;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@WithJenkins
class RestListParameterDefinitionJenkinsTest {

  /**
   * Regression guard: ensures the {@code DescriptorImpl} is picked up as an Extension.
   * A silent failure here is what produces {@code Invalid parameter type "RESTList"} in
   * Pipeline DSL when the sezpoz extension index is missing from the packaged hpi.
   */
  @Test
  void descriptorIsRegistered(JenkinsRule r) {
    ParameterDefinition.ParameterDescriptor descriptor =
      (ParameterDefinition.ParameterDescriptor) Jenkins.get().getDescriptor(RestListParameterDefinition.class);
    assertNotNull(descriptor, "DescriptorImpl not registered — plugin failed to load");
    assertEquals(RestListParameterDefinition.DescriptorImpl.class, descriptor.getClass());
  }

  /**
   * Regression guard: verifies the {@code RESTList} symbol resolves and the
   * {@code allowEmptyValue} flag flows through {@code DescribableModel}, which is what the
   * workflow-cps Pipeline DSL uses to instantiate parameters by symbol.
   */
  @Test
  void pipelineDslAcceptsRestListSymbolWithAllowEmptyValue(JenkinsRule r) throws Exception {
    Descriptor<?> bySymbol = SymbolLookup.get().findDescriptor(ParameterDefinition.class, "RESTList");
    assertNotNull(bySymbol, "@Symbol(\"RESTList\") not registered — extension index broken");
    assertEquals(RestListParameterDefinition.DescriptorImpl.class, bySymbol.getClass());

    DescribableModel<RestListParameterDefinition> model = new DescribableModel<>(RestListParameterDefinition.class);
    Map<String, Object> args = new HashMap<>();
    args.put("name", "VERSION");
    args.put("description", "pick a version");
    args.put("restEndpoint", "http://127.0.0.1:1/none");
    args.put("credentialId", "");
    args.put("mimeType", "APPLICATION_JSON");
    args.put("valueExpression", "$.tags");
    args.put("displayExpression", "$");
    args.put("allowEmptyValue", true);
    args.put("defaultValue", "");

    RestListParameterDefinition def = model.instantiate(args);
    assertEquals("VERSION", def.getName());
    assertTrue(def.isAllowEmptyValue(), "allowEmptyValue DataBoundSetter not applied");
    assertEquals("", def.getDefaultValue());
  }

  @Test
  void allowEmptyValueDefaultsToFalse(JenkinsRule r) {
    RestListParameterDefinition def = new RestListParameterDefinition(
      "p", "d", "http://127.0.0.1:1/none", "", MimeType.APPLICATION_JSON, "$.*", "$");
    assertFalse(def.isAllowEmptyValue());
  }

  @Test
  void isValidAcceptsEmptyOnlyWhenAllowed(JenkinsRule r) {
    RestListParameterDefinition allowed = new RestListParameterDefinition(
      "p", "d", "http://127.0.0.1:1/none", "", MimeType.APPLICATION_JSON, "$.*", "$",
      ValueOrder.NONE, ".*", 0, "", true);
    // allowEmptyValue short-circuits before getValues(), so this does not depend on the REST call.
    assertTrue(allowed.isValid(new StringParameterValue("p", "")));

    RestListParameterDefinition denied = new RestListParameterDefinition(
      "p", "d", "http://127.0.0.1:1/none", "", MimeType.APPLICATION_JSON, "$.*", "$",
      ValueOrder.NONE, ".*", 0, "", false);
    assertFalse(denied.isValid(new StringParameterValue("p", "")));
  }

  @Test
  void createValueAcceptsEmptyWhenAllowed(JenkinsRule r) {
    RestListParameterDefinition def = new RestListParameterDefinition(
      "p", "d", "http://127.0.0.1:1/none", "", MimeType.APPLICATION_JSON, "$.*", "$",
      ValueOrder.NONE, ".*", 0, "", true);
    try {
      def.createValue("");
    } catch (IllegalArgumentException e) {
      fail("empty value should be accepted when allowEmptyValue=true: " + e.getMessage());
    }
  }

  @Test
  void createValueRejectsEmptyByDefault(JenkinsRule r) {
    RestListParameterDefinition def = new RestListParameterDefinition(
      "p", "d", "http://127.0.0.1:1/none", "", MimeType.APPLICATION_JSON, "$.*", "$",
      ValueOrder.NONE, ".*", 0, "", false);
    try {
      def.createValue("anything");
      fail("expected IllegalArgumentException for unknown value");
    } catch (IllegalArgumentException expected) {
      // ok
    }
  }

  @Test
  void enableValidationDefaultsToTrue(JenkinsRule r) {
    RestListParameterDefinition def = new RestListParameterDefinition(
      "p", "d", "http://127.0.0.1:1/none", "", MimeType.APPLICATION_JSON, "$.*", "$");
    assertTrue(def.isEnableValidation());
  }

  @Test
  void isValidAcceptsArbitraryValueWhenValidationDisabled(JenkinsRule r) {
    RestListParameterDefinition def = new RestListParameterDefinition(
      "p", "d", "http://127.0.0.1:1/none", "", MimeType.APPLICATION_JSON, "$.*", "$",
      ValueOrder.NONE, ".*", 0, "", false);
    def.setEnableValidation(false);
    // With validation disabled, isValid must NOT trigger getValues() and must accept
    // anything non-null — that's the whole point of bypassing pagination limits.
    assertTrue(def.isValid(new StringParameterValue("p", "freely-typed-value")));
  }

  @Test
  void createValueAcceptsArbitraryWhenValidationDisabled(JenkinsRule r) {
    RestListParameterDefinition def = new RestListParameterDefinition(
      "p", "d", "http://127.0.0.1:1/none", "", MimeType.APPLICATION_JSON, "$.*", "$",
      ValueOrder.NONE, ".*", 0, "", false);
    def.setEnableValidation(false);
    try {
      def.createValue("page-2-value-not-in-first-page");
    } catch (IllegalArgumentException e) {
      fail("arbitrary value should be accepted when enableValidation=false: " + e.getMessage());
    }
  }

  @Test
  void isValidRejectsEmptyWhenValidationDisabledAndEmptyNotAllowed(JenkinsRule r) {
    // enableValidation and allowEmptyValue are orthogonal: turning off validation lets the
    // user type arbitrary NON-empty values, but an empty submission is still rejected while
    // allowEmptyValue is unchecked.
    RestListParameterDefinition def = new RestListParameterDefinition(
      "p", "d", "http://127.0.0.1:1/none", "", MimeType.APPLICATION_JSON, "$.*", "$",
      ValueOrder.NONE, ".*", 0, "", false);
    def.setEnableValidation(false);
    assertFalse(def.isValid(new StringParameterValue("p", "")));
    // and a non-empty value is still accepted, without hitting the REST endpoint.
    assertTrue(def.isValid(new StringParameterValue("p", "page-2-value")));
  }

  @Test
  void isValidAcceptsEmptyWhenValidationDisabledAndEmptyAllowed(JenkinsRule r) {
    RestListParameterDefinition def = new RestListParameterDefinition(
      "p", "d", "http://127.0.0.1:1/none", "", MimeType.APPLICATION_JSON, "$.*", "$",
      ValueOrder.NONE, ".*", 0, "", true);
    def.setEnableValidation(false);
    assertTrue(def.isValid(new StringParameterValue("p", "")));
  }

  @Test
  void pipelineDslAcceptsEnableValidationFalse(JenkinsRule r) throws Exception {
    DescribableModel<RestListParameterDefinition> model = new DescribableModel<>(RestListParameterDefinition.class);
    Map<String, Object> args = new HashMap<>();
    args.put("name", "VERSION");
    args.put("description", "pick a version");
    args.put("restEndpoint", "http://127.0.0.1:1/none");
    args.put("credentialId", "");
    args.put("mimeType", "APPLICATION_JSON");
    args.put("valueExpression", "$.tags");
    args.put("displayExpression", "$");
    args.put("enableValidation", false);

    RestListParameterDefinition def = model.instantiate(args);
    assertFalse(def.isEnableValidation(), "enableValidation DataBoundSetter not applied");
  }

  @Test
  void pipelineDslAcceptsCustomHeaders(JenkinsRule r) throws Exception {
    DescribableModel<RestListParameterDefinition> model = new DescribableModel<>(RestListParameterDefinition.class);
    Map<String, Object> args = new HashMap<>();
    args.put("name", "VERSION");
    args.put("description", "pick a version");
    args.put("restEndpoint", "http://127.0.0.1:1/none");
    args.put("credentialId", "");
    args.put("mimeType", "APPLICATION_JSON");
    args.put("valueExpression", "$.tags");
    args.put("displayExpression", "$");

    Map<String, Object> authHeader = new HashMap<>();
    authHeader.put("name", "Authorization");
    authHeader.put("credentialId", "svc_netbox_prd");
    authHeader.put("valuePrefix", "Token ");
    args.put("customHeaders", Arrays.asList(authHeader));

    RestListParameterDefinition def = model.instantiate(args);
    List<CustomHeader> customHeaders = def.getCustomHeaders();
    assertEquals(1, customHeaders.size());
    assertEquals("Authorization", customHeaders.get(0).getName());
    assertEquals("svc_netbox_prd", customHeaders.get(0).getCredentialId());
    assertEquals("Token ", customHeaders.get(0).getValuePrefix());
  }

  @Test
  void secretTextCredentialFeedsCustomHeader(JenkinsRule r) throws Exception {
    SystemCredentialsProvider.getInstance().getCredentials().add(
      new StringCredentialsImpl(CredentialsScope.GLOBAL, "custom-token", "custom token",
        Secret.fromString("credential-token")));
    SystemCredentialsProvider.getInstance().save();

    try (HeaderCaptureServer server = new HeaderCaptureServer()) {
      RestListParameterDefinition def = new RestListParameterDefinition(
        "p", "d", server.url(), "", MimeType.APPLICATION_JSON, "$.*.name", "$",
        ValueOrder.NONE, ".*", 0, "", false);
      CustomHeader header = new CustomHeader("X-Auth-Token");
      header.setCredentialId("custom-token");
      header.setValuePrefix("Token ");
      def.setCustomHeaders(Collections.singletonList(header));

      assertEquals(3, def.getValues().size());
      assertEquals("Token credential-token", server.header("X-Auth-Token"));
    }
  }

  @Test
  void definitionWithCustomHeadersIsSerializable(JenkinsRule r) throws Exception {
    RestListParameterDefinition def = new RestListParameterDefinition(
      "p", "d", "http://127.0.0.1:1/none", "", MimeType.APPLICATION_JSON, "$.*.name", "$",
      ValueOrder.NONE, ".*", 0, "", false);
    CustomHeader header = new CustomHeader("Authorization");
    header.setCredentialId("custom-token");
    header.setValuePrefix("Token ");
    def.setCustomHeaders(Collections.singletonList(header));

    try (ObjectOutputStream out = new ObjectOutputStream(new ByteArrayOutputStream())) {
      out.writeObject(def);
    }
  }

  @Test
  void customHeadersDefaultToEmpty(JenkinsRule r) {
    RestListParameterDefinition def = new RestListParameterDefinition(
      "p", "d", "https://example.invalid", "", MimeType.APPLICATION_JSON, "$.*", "$");
    assertTrue(def.getCustomHeaders().isEmpty());
  }

  @Test
  void paginationDefaultsToNone(JenkinsRule r) {
    RestListParameterDefinition def = new RestListParameterDefinition(
      "p", "d", "https://example.invalid", "", MimeType.APPLICATION_JSON, "$.*", "$");
    assertNull(def.getPagination());
  }

  @Test
  void valuesFromAllPagesAreOffered(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/tags", "[\"v1\",\"v2\"]")
          .withHeader("/tags", "Link", "<" + stub.url("/tags?page=2") + ">; rel=\"next\"");
      stub.respondJson("/tags?page=2", "[\"v3\"]");
      RestListParameterDefinition def = new RestListParameterDefinition(
        "p", "d", stub.url("/tags"), "", MimeType.APPLICATION_JSON, "$.*", "$",
        ValueOrder.NONE, ".*", 0, "", false);
      def.setPagination(new LinkHeaderPagination());

      assertEquals(List.of("v1", "v2", "v3"), def.getValues().stream().map(ValueItem::getValue).toList());
      assertEquals("", def.getErrorMsg());
      assertTrue(def.isValid(new StringParameterValue("p", "v3")), "a value from page 2 is a valid choice");
    }
  }

  @Test
  void everyPageCarriesCredentialAndCustomHeaders(JenkinsRule r) throws Exception {
    SystemCredentialsProvider.getInstance().getCredentials().add(
      new StringCredentialsImpl(CredentialsScope.GLOBAL, "api-token", "api token", Secret.fromString("s3cret")));
    SystemCredentialsProvider.getInstance().save();

    try (StubHttpServer stub = threePages(null)) {
      RestListParameterDefinition def = new RestListParameterDefinition(
        "p", "d", stub.url("/p"), "api-token", MimeType.APPLICATION_JSON, "$.*", "$",
        ValueOrder.NONE, ".*", 0, "", false);
      CustomHeader header = new CustomHeader("X-API-Key");
      header.setValue(Secret.fromString("key-123"));
      def.setCustomHeaders(Collections.singletonList(header));
      def.setPagination(new LinkHeaderPagination());

      assertEquals(3, def.getValues().size());

      assertEquals(3, stub.requests().size());
      for (StubHttpServer.RecordedRequest request : stub.requests()) {
        assertEquals("Bearer s3cret", request.header("Authorization"), request.uri());
        assertEquals("key-123", request.header("X-API-Key"), request.uri());
        assertEquals("application/json", request.header("Accept"), request.uri());
      }
    }
  }

  @Test
  void everyPageIsCached(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = threePages("max-age=600")) {
      RestListParameterDefinition def = new RestListParameterDefinition(
        "p", "d", stub.url("/p"), "", MimeType.APPLICATION_JSON, "$.*", "$",
        ValueOrder.NONE, ".*", 10, "", false);
      def.setPagination(new LinkHeaderPagination());

      assertEquals(3, def.getValues().size());
      assertEquals(3, stub.requests().size());

      assertEquals(3, def.getValues().size());
      assertEquals(3, stub.requests().size(), "a second fetch within the cache time sends no request");
    }
  }

  /** Pages {@code /p}, {@code /p?page=2}, {@code /p?page=3} linked by {@code Link} headers. */
  private static StubHttpServer threePages(final String cacheControl) throws Exception {
    StubHttpServer stub = new StubHttpServer();
    for (int page = 1; page <= 3; page++) {
      String path = page == 1 ? "/p" : "/p?page=" + page;
      stub.respondJson(path, "[\"" + page + "\"]");
      if (page < 3) {
        stub.withHeader(path, "Link", "<" + stub.url("/p?page=" + (page + 1)) + ">; rel=\"next\"");
      }
      if (cacheControl != null) {
        stub.withHeader(path, "Cache-Control", cacheControl);
      }
    }
    return stub;
  }

  private static class HeaderCaptureServer implements AutoCloseable {
    private final HttpServer server;
    private final AtomicReference<com.sun.net.httpserver.Headers> lastHeaders = new AtomicReference<>();

    HeaderCaptureServer() throws IOException {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", this::respond);
      server.start();
    }

    String url() {
      return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    String header(final String name) {
      com.sun.net.httpserver.Headers headers = lastHeaders.get();
      return headers != null ? headers.getFirst(name) : null;
    }

    private void respond(final HttpExchange exchange) throws IOException {
      lastHeaders.set(exchange.getRequestHeaders());
      byte[] response = TestConst.validTestJson.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    }

    @Override
    public void close() {
      server.stop(0);
    }
  }
}
