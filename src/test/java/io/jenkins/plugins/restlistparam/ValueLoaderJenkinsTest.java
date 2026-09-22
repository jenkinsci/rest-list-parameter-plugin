package io.jenkins.plugins.restlistparam;

import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import io.jenkins.plugins.restlistparam.model.LinkHeaderPagination;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-parameter entry endpoint of the build form (specs/build-parameter-form).
 */
@WithJenkins
class ValueLoaderJenkinsTest {

  private static final String TAGS_JSON = "[{\"name\":\"v1.0\"},{\"name\":\"v1.1\"}]";

  @Test
  void requestWithoutBuildIsDeniedAndTheEndpointNotContacted(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub()) {
      FreeStyleProject job = r.createFreeStyleProject();
      grant(r, job);
      ValueLoader loader = single(stub.url("/list"), "").createLoader(job);

      try (ACLContext ignored = as("reader")) {
        assertThrows(AccessDeniedException.class, () -> loader.load(false));
      }
      assertEquals(0, stub.requests().size());
    }
  }

  @Test
  void requestOutsideAnyJobRequiresAdminister(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub()) {
      grant(r, r.createFreeStyleProject());
      ValueLoader loader = single(stub.url("/list"), "").createLoader(null);

      try (ACLContext ignored = as("configurer")) {
        assertThrows(AccessDeniedException.class, () -> loader.load(false));
      }
      assertEquals(0, stub.requests().size());
      try (ACLContext ignored = as("admin")) {
        assertEquals("ok", loader.load(false).getString("status"));
      }
    }
  }

  @Test
  void errorDetailsAreShownOnlyToConfigurers(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/api?token=s3cret", "[\"a\"]")
          .withHeader("/api?token=s3cret", "Link", "<" + stub.url("/api?token=s3cret&page=2") + ">; rel=\"next\"");
      stub.respond("/api?token=s3cret&page=2", 503, "text/plain", "s3cret body");
      FreeStyleProject job = r.createFreeStyleProject();
      grant(r, job);
      RestListParameterDefinition def = single(stub.url("/api?token=s3cret"), "");
      def.setPagination(new LinkHeaderPagination());
      ValueLoader loader = def.createLoader(job);

      JSONObject forConfigurer;
      try (ACLContext ignored = as("configurer")) {
        forConfigurer = loader.load(false);
      }
      assertEquals("error", forConfigurer.getString("status"));
      assertEquals("Encountered Http Server Error: 503 (page 2)", forConfigurer.getString("message"));
      JSONObject details = forConfigurer.getJSONObject("details");
      assertEquals(stub.url("/api"), details.getString("url"));
      assertEquals(2, details.getInt("page"));
      assertEquals("503", details.getString("cause"));
      assertTrue(details.getLong("durationMs") >= 0);
      assertFalse(forConfigurer.toString().contains("s3cret"), forConfigurer.toString());

      JSONObject forBuilder;
      try (ACLContext ignored = as("builder")) {
        forBuilder = loader.load(false);
      }
      assertEquals("error", forBuilder.getString("status"));
      assertEquals("Encountered Http Server Error: 503 (page 2)", forBuilder.getString("message"));
      assertFalse(forBuilder.has("details"), forBuilder.toString());
      assertFalse(forBuilder.toString().contains("s3cret"), forBuilder.toString());
    }
  }

  @Test
  void rebuildCopySelectsThePreviousValue(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/tags", TAGS_JSON);
      FreeStyleProject job = r.createFreeStyleProject();
      RestListParameterDefinition def = new RestListParameterDefinition(
        "p", "d", stub.url("/tags"), "", MimeType.APPLICATION_JSON, "$.*", "$.name", ValueOrder.NONE, ".*", 0,
        "v1.0", false);

      RestListParameterDefinition rerun = (RestListParameterDefinition)
        def.copyWithDefaultValue(new RestListParameterValue("p", "{\"name\":\"v1.1\"}", "d"));
      JSONObject json = rerun.createLoader(job).load(false);

      assertEquals("ok", json.getString("status"));
      JSONArray entries = json.getJSONArray("entries");
      assertEquals(2, entries.size());
      assertEquals("v1.0", entries.getJSONObject(0).getString("display"));
      assertFalse(entries.getJSONObject(0).getBoolean("selected"));
      assertEquals("v1.1", entries.getJSONObject(1).getString("display"));
      assertEquals("{\"name\":\"v1.1\"}", entries.getJSONObject(1).getString("value"));
      assertTrue(entries.getJSONObject(1).getBoolean("selected"));
      assertFalse(json.has("freeTextValue"), "dropdown mode");
    }
  }

  @Test
  void freeTextValueResolvesTheDefault(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/tags", TAGS_JSON);
      RestListParameterDefinition def = new RestListParameterDefinition(
        "p", "d", stub.url("/tags"), "", MimeType.APPLICATION_JSON, "$.*", "$.name", ValueOrder.NONE, ".*", 0,
        "v1.1", false);
      def.setEnableValidation(false);

      JSONObject json = def.createLoader(r.createFreeStyleProject()).load(false);

      assertEquals("{\"name\":\"v1.1\"}", json.getString("freeTextValue"));
    }
  }

  @Test
  void multiListReportsSelectionAndUnmatchedDefaults(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/tags", TAGS_JSON);
      RestMultiListParameterDefinition def = new RestMultiListParameterDefinition(
        "p", "d", stub.url("/tags"), "", MimeType.APPLICATION_JSON, "$.*", "$.name", ValueOrder.NONE, ".*", 0,
        "[\"v1.1\",\"Zeta\"]", true);
      def.setEnableValidation(false);

      JSONObject json = def.createLoader(r.createFreeStyleProject()).load(false);

      JSONArray entries = json.getJSONArray("entries");
      assertFalse(entries.getJSONObject(0).getBoolean("selected"));
      assertTrue(entries.getJSONObject(1).getBoolean("selected"));
      assertEquals(List.of("Zeta"), new ArrayList<Object>(json.getJSONArray("unmatchedDefaults")));
    }
  }

  private static StubHttpServer listStub() throws Exception {
    StubHttpServer stub = new StubHttpServer();
    stub.respondJson("/list", "[\"a\", \"b\"]");
    return stub;
  }

  private static RestListParameterDefinition single(final String endpoint, final String defaultValue) {
    return new RestListParameterDefinition(
      "p", "d", endpoint, "", MimeType.APPLICATION_JSON, "$.*", "$", ValueOrder.NONE, ".*", 0, defaultValue, false);
  }

  private static void grant(final JenkinsRule r, final Item job) {
    r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
    r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
      .grant(Jenkins.ADMINISTER).everywhere().to("admin")
      .grant(Jenkins.READ).everywhere().to("reader", "builder", "configurer")
      .grant(Item.READ).onItems(job).to("reader", "builder", "configurer")
      .grant(Item.BUILD).onItems(job).to("builder", "configurer")
      .grant(Item.CONFIGURE).onItems(job).to("configurer"));
  }

  private static ACLContext as(final String user) {
    return ACL.as2(User.getById(user, true).impersonate2());
  }
}
