package io.jenkins.plugins.restlistparam;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import hudson.util.Secret;
import io.jenkins.plugins.restlistparam.logic.RestValueService;
import io.jenkins.plugins.restlistparam.model.CustomHeader;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.ValueItem;
import io.jenkins.plugins.restlistparam.model.ResultContainer;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import okhttp3.Headers;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Task for later: more unit tests here (preferably replace integration tests)
class RestValueServiceTest {

  @Test
  void successfulGetIntegrationTest() {
    ResultContainer<List<ValueItem>> test = RestValueService
      .get("https://api.github.com/repos/jellyfin/jellyfin/tags?per_page=3",
        null,
        MimeType.APPLICATION_JSON,
        0,
        "$.*.name",
        "$",
        null,
        ValueOrder.NONE);
    assertFalse(test.getErrorMsg().isPresent());
    assertEquals(3, test.getValue().size());
  }

  @Test
  void unsuccessfulGetIntegrationTest() {
    ResultContainer<List<ValueItem>> test = RestValueService
      .get("https://gitlab.example.com/api/v4/projects/gitlab-org%2Fgitlab-runner/releases",
        null,
        MimeType.APPLICATION_JSON,
        0,
        "$.*.tag_name",
        "$",
        null,
        ValueOrder.NONE);
    assertNotNull(test);
    assertTrue(test.getErrorMsg().isPresent());
    assertEquals(0, test.getValue().size());
  }

  @Test
  void sendsCustomHeader() throws Exception {
    Method buildHeaders = RestValueService.class.getDeclaredMethod("buildHeaders",
      StandardCredentials.class, MimeType.class, Map.class);
    buildHeaders.setAccessible(true);

    Headers headers = (Headers) buildHeaders.invoke(null, null, MimeType.APPLICATION_JSON,
      Map.of("X-API-Key", "static-token"));

    assertEquals("static-token", headers.get("X-API-Key"));
  }

  @Test
  void trimsCustomHeaderNameOnConstruction() {
    CustomHeader header = new CustomHeader("Authorization ");
    header.setValue(Secret.fromString("token"));

    assertEquals("Authorization", header.getName());
    assertEquals("token", header.resolve(null));
  }

  @Test
  void unsupportedCredentialTypeSendsNoAuthorizationHeader() throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/list", "[\"a\", \"b\"]");

      ResultContainer<List<ValueItem>> result = RestValueService.get(
        stub.url("/list"), new UnsupportedCredentials(), MimeType.APPLICATION_JSON, 0, "$.*", "$", null,
        ValueOrder.NONE);

      assertFalse(result.getErrorMsg().isPresent());
      assertEquals(1, stub.requestCount("/list"));
      assertFalse(stub.lastRequest().hasHeader("Authorization"));
      assertEquals("application/json", stub.lastRequest().header("Accept"));
    }
  }

  private static final class UnsupportedCredentials extends BaseStandardCredentials {
    UnsupportedCredentials() {
      super(CredentialsScope.GLOBAL, "unsupported", "neither username/password nor secret text");
    }
  }
}
