package io.jenkins.plugins.restlistparam;

import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AccessDeniedException3;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.restlistparam.model.ContinuationTokenPagination;
import io.jenkins.plugins.restlistparam.model.LinkHeaderPagination;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.Pagination;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The pagination block of the job configuration form, for both parameter types.
 */
@WithJenkins
class PaginationConfigJenkinsTest {

  // Configuration round-trip (4.2)

  @Test
  void uncheckedPaginationSavesNone(JenkinsRule r) throws Exception {
    for (AbstractRestListParameterDefinition def : bothTypes()) {
      AbstractRestListParameterDefinition after = roundtrip(r, def);
      assertNull(after.getPagination(), def.getClass().getSimpleName());
      r.assertEqualDataBoundBeans(def, after);
    }
  }

  @Test
  void linkHeaderPaginationRoundTrips(JenkinsRule r) throws Exception {
    for (AbstractRestListParameterDefinition def : bothTypes()) {
      LinkHeaderPagination pagination = new LinkHeaderPagination();
      pagination.setMaxPages(5);
      def.setPagination(pagination);

      AbstractRestListParameterDefinition after = roundtrip(r, def);

      LinkHeaderPagination saved = assertInstanceOf(LinkHeaderPagination.class, after.getPagination(),
        def.getClass().getSimpleName());
      assertEquals(5, saved.getMaxPages());
      r.assertEqualDataBoundBeans(def, after);
    }
  }

  @Test
  void continuationTokenPaginationRoundTrips(JenkinsRule r) throws Exception {
    for (AbstractRestListParameterDefinition def : bothTypes()) {
      def.setPagination(new ContinuationTokenPagination("$.continuationToken", "continuationToken"));

      AbstractRestListParameterDefinition after = roundtrip(r, def);

      ContinuationTokenPagination saved = assertInstanceOf(ContinuationTokenPagination.class, after.getPagination(),
        def.getClass().getSimpleName());
      assertEquals("$.continuationToken", saved.getTokenExpression());
      assertEquals("continuationToken", saved.getQueryParameter());
      assertEquals(Pagination.DEFAULT_MAX_PAGES, saved.getMaxPages());
      r.assertEqualDataBoundBeans(def, after);
    }
  }

  // Pagination settings check (4.3)

  @Test
  void continuationTokenOnXmlIsAnError(JenkinsRule r) throws Exception {
    FreeStyleProject project = r.createFreeStyleProject();
    assertError("Continuation token pagination requires the JSON MIME type",
      continuationDescriptor().doCheckTokenExpression(project, "$.continuationToken", MimeType.APPLICATION_XML));
    assertOk(continuationDescriptor().doCheckTokenExpression(project, "$.continuationToken", MimeType.APPLICATION_JSON));
  }

  @Test
  void tokenExpressionMustNotBeEmpty(JenkinsRule r) throws Exception {
    FreeStyleProject project = r.createFreeStyleProject();
    assertError("Token Expression must not be empty",
      continuationDescriptor().doCheckTokenExpression(project, "  ", MimeType.APPLICATION_JSON));
    assertError("Token Expression must not be empty",
      continuationDescriptor().doCheckTokenExpression(project, null, MimeType.APPLICATION_JSON));
  }

  @Test
  void tokenExpressionMustCompile(JenkinsRule r) throws Exception {
    FreeStyleProject project = r.createFreeStyleProject();
    assertError("The provided Json-Path expression seems to be incorrect",
      continuationDescriptor().doCheckTokenExpression(project, "$.[", MimeType.APPLICATION_JSON));
  }

  @Test
  void queryParameterMustNotBeEmpty(JenkinsRule r) throws Exception {
    FreeStyleProject project = r.createFreeStyleProject();
    assertError("Query Parameter must not be empty", continuationDescriptor().doCheckQueryParameter(project, " "));
    assertOk(continuationDescriptor().doCheckQueryParameter(project, "continuationToken"));
  }

  @Test
  void maxPagesMustBeOneToHundred(JenkinsRule r) throws Exception {
    FreeStyleProject project = r.createFreeStyleProject();
    for (Pagination.PaginationDescriptor descriptor : List.of(linkDescriptor(), continuationDescriptor())) {
      for (String invalid : Arrays.asList("0", "101", "-1", "abc", "2.5", "", null)) {
        assertError("Max pages MUST BE between 1 and 100", descriptor.doCheckMaxPages(project, invalid));
      }
      for (String valid : List.of("1", "10", "100")) {
        assertOk(descriptor.doCheckMaxPages(project, valid));
      }
    }
  }

  @Test
  void paginationChecksRequireConfigurePermission(JenkinsRule r) throws Exception {
    r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
    r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
      .grant(Jenkins.READ, Item.READ).everywhere().to("reader")
      .grant(Jenkins.READ, Item.READ, Item.CONFIGURE).everywhere().to("configurer"));
    FreeStyleProject project = r.createFreeStyleProject();

    try (ACLContext ignored = ACL.as2(User.getById("reader", true).impersonate2())) {
      assertThrows(AccessDeniedException3.class, () ->
        continuationDescriptor().doCheckTokenExpression(project, "$.t", MimeType.APPLICATION_JSON));
      assertThrows(AccessDeniedException3.class, () -> continuationDescriptor().doCheckQueryParameter(project, "t"));
      assertThrows(AccessDeniedException3.class, () -> linkDescriptor().doCheckMaxPages(project, "5"));
      assertThrows(AccessDeniedException3.class, () -> linkDescriptor().doCheckMaxPages(null, "5"));
    }
    try (ACLContext ignored = ACL.as2(User.getById("configurer", true).impersonate2())) {
      assertOk(continuationDescriptor().doCheckTokenExpression(project, "$.t", MimeType.APPLICATION_JSON));
      assertOk(linkDescriptor().doCheckMaxPages(project, "5"));
      assertThrows(AccessDeniedException3.class, () -> linkDescriptor().doCheckMaxPages(null, "5"),
        "without a job, Administer is required");
    }
  }

  private static ContinuationTokenPagination.DescriptorImpl continuationDescriptor() {
    return (ContinuationTokenPagination.DescriptorImpl) Jenkins.get().getDescriptorOrDie(ContinuationTokenPagination.class);
  }

  private static LinkHeaderPagination.DescriptorImpl linkDescriptor() {
    return (LinkHeaderPagination.DescriptorImpl) Jenkins.get().getDescriptorOrDie(LinkHeaderPagination.class);
  }

  static void assertError(final String message, final FormValidation validation) {
    assertEquals(FormValidation.Kind.ERROR, validation.kind, validation.getMessage());
    assertEquals(message, validation.getMessage());
  }

  static void assertOk(final FormValidation validation) {
    assertEquals(FormValidation.Kind.OK, validation.kind, validation.getMessage());
  }

  private static AbstractRestListParameterDefinition roundtrip(final JenkinsRule r,
                                                               final AbstractRestListParameterDefinition def)
    throws Exception
  {
    FreeStyleProject project = r.createFreeStyleProject();
    project.addProperty(new ParametersDefinitionProperty(def));
    r.configRoundtrip(project);
    return (AbstractRestListParameterDefinition)
      project.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("P");
  }

  static AbstractRestListParameterDefinition[] bothTypes() {
    RestListParameterDefinition single = new RestListParameterDefinition(
      "P", "d", "https://example.invalid/api", "", MimeType.APPLICATION_JSON, "$.*", "$",
      ValueOrder.NONE, ".*", 0, "", false);
    single.setEnableValidation(false);
    RestMultiListParameterDefinition multi = new RestMultiListParameterDefinition(
      "P", "d", "https://example.invalid/api", "", MimeType.APPLICATION_JSON, "$.*", "$",
      ValueOrder.NONE, ".*", 0, "", false);
    multi.setEnableValidation(false);
    return new AbstractRestListParameterDefinition[]{single, multi};
  }
}
