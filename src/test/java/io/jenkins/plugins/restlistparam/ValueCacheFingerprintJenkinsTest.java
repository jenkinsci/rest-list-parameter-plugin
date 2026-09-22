package io.jenkins.plugins.restlistparam;

import hudson.util.Secret;
import io.jenkins.plugins.restlistparam.model.ContinuationTokenPagination;
import io.jenkins.plugins.restlistparam.model.CustomHeader;
import io.jenkins.plugins.restlistparam.model.LinkHeaderPagination;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The configuration fingerprint in the value cache key (specs/value-cache, "Cached entries are isolated per job and
 * configuration").
 */
@WithJenkins
class ValueCacheFingerprintJenkinsTest {

  @Test
  void equalConfigurationsHaveEqualFingerprints(JenkinsRule r) {
    assertEquals(ValueCache.fingerprint(base()), ValueCache.fingerprint(base()));
  }

  @Test
  void changingAnyValueSourceFieldChangesTheFingerprint(JenkinsRule r) {
    Map<String, AbstractRestListParameterDefinition> changed = new LinkedHashMap<>();
    changed.put("endpoint", def("https://h/other", "cred", MimeType.APPLICATION_JSON, "$.*", "$.name", ".*", ValueOrder.NONE));
    changed.put("credential ID", def("https://h/api", "cred2", MimeType.APPLICATION_JSON, "$.*", "$.name", ".*", ValueOrder.NONE));
    changed.put("MIME type", def("https://h/api", "cred", MimeType.APPLICATION_XML, "$.*", "$.name", ".*", ValueOrder.NONE));
    changed.put("value expression", def("https://h/api", "cred", MimeType.APPLICATION_JSON, "$.*.id", "$.name", ".*", ValueOrder.NONE));
    changed.put("display expression", def("https://h/api", "cred", MimeType.APPLICATION_JSON, "$.*", "$.title", ".*", ValueOrder.NONE));
    changed.put("filter", def("https://h/api", "cred", MimeType.APPLICATION_JSON, "$.*", "$.name", "v.*", ValueOrder.NONE));
    changed.put("order", def("https://h/api", "cred", MimeType.APPLICATION_JSON, "$.*", "$.name", ".*", ValueOrder.ASC));
    changed.put("header name", withHeader(h -> h.setValuePrefix("Bearer "), "X-Other"));
    changed.put("header prefix", withHeader(h -> h.setValuePrefix("Token "), "X-Key"));
    changed.put("header credential", withHeader(h -> {
      h.setValuePrefix("Bearer ");
      h.setCredentialId("c2");
    }, "X-Key"));
    changed.put("header secret", withHeader(h -> {
      h.setValuePrefix("Bearer ");
      h.setValue(Secret.fromString("other"));
    }, "X-Key"));
    changed.put("no pagination", withPagination(null));
    changed.put("pagination kind", withPagination(new ContinuationTokenPagination("$.next", "token")));
    LinkHeaderPagination morePages = new LinkHeaderPagination();
    morePages.setMaxPages(20);
    changed.put("max pages", withPagination(morePages));

    String base = ValueCache.fingerprint(base());
    for (Map.Entry<String, AbstractRestListParameterDefinition> entry : changed.entrySet()) {
      assertNotEquals(base, ValueCache.fingerprint(entry.getValue()), entry.getKey());
    }

    RestListParameterDefinition token = base();
    token.setPagination(new ContinuationTokenPagination("$.next", "token"));
    RestListParameterDefinition otherExpression = base();
    otherExpression.setPagination(new ContinuationTokenPagination("$.cursor", "token"));
    RestListParameterDefinition otherQuery = base();
    otherQuery.setPagination(new ContinuationTokenPagination("$.next", "cursor"));
    assertNotEquals(ValueCache.fingerprint(token), ValueCache.fingerprint(otherExpression), "token expression");
    assertNotEquals(ValueCache.fingerprint(token), ValueCache.fingerprint(otherQuery), "query parameter");
  }

  @Test
  void fieldsOutsideTheValueSourceDoNotChangeTheFingerprint(JenkinsRule r) {
    RestListParameterDefinition other = base();
    other.setDescription("another description");
    other.setDefaultValue("v2");
    other.setCacheTime(99);
    other.setAllowEmptyValue(true);
    other.setEnableValidation(false);

    assertEquals(ValueCache.fingerprint(base()), ValueCache.fingerprint(other));
  }

  @Test
  void keyDoesNotContainThePlainHeaderSecret(JenkinsRule r) throws Exception {
    String key = ValueCache.keyFor(base(), r.createFreeStyleProject("job")).toString();

    assertFalse(key.contains("s3cret"), key);
  }

  private static RestListParameterDefinition base() {
    RestListParameterDefinition def =
      def("https://h/api", "cred", MimeType.APPLICATION_JSON, "$.*", "$.name", ".*", ValueOrder.NONE);
    def.setCustomHeaders(new ArrayList<>(List.of(header("X-Key", h -> h.setValuePrefix("Bearer ")))));
    def.setPagination(new LinkHeaderPagination());
    return def;
  }

  private static RestListParameterDefinition withHeader(final Consumer<CustomHeader> change, final String name) {
    RestListParameterDefinition def = base();
    def.setCustomHeaders(new ArrayList<>(List.of(header(name, change))));
    return def;
  }

  private static RestListParameterDefinition withPagination(final io.jenkins.plugins.restlistparam.model.Pagination p) {
    RestListParameterDefinition def = base();
    def.setPagination(p);
    return def;
  }

  private static CustomHeader header(final String name, final Consumer<CustomHeader> change) {
    CustomHeader header = new CustomHeader(name);
    header.setValue(Secret.fromString("s3cret"));
    change.accept(header);
    return header;
  }

  private static RestListParameterDefinition def(final String endpoint, final String credentialId,
                                                 final MimeType mimeType, final String valueExpression,
                                                 final String displayExpression, final String filter,
                                                 final ValueOrder order)
  {
    return new RestListParameterDefinition("p", "d", endpoint, credentialId, mimeType, valueExpression,
      displayExpression, order, filter, 10, "", false);
  }
}
