package io.jenkins.plugins.restlistparam;

import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.FormValidation;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.restlistparam.logic.ValueService;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import jenkins.model.Jenkins;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class GlobalConfigJenkinsTest {

  // Global fetch timeout (global-configuration)

  @Test
  void fetchTimeoutDefaultsToSixtySeconds(JenkinsRule r) {
    RestListParameterGlobalConfig config = RestListParameterGlobalConfig.get();

    assertEquals(60, config.getFetchTimeout());
    config.setFetchTimeout(0);
    assertEquals(60, config.getFetchTimeout(), "non-positive means the default");
    config.setFetchTimeout(-5);
    assertEquals(60, config.getFetchTimeout(), "non-positive means the default");
  }

  @Test
  void fetchTimeoutCheckRejectsNonPositiveInput(JenkinsRule r) {
    RestListParameterGlobalConfig config = RestListParameterGlobalConfig.get();

    FormValidation zero = config.doCheckFetchTimeout(0);
    assertEquals(FormValidation.Kind.ERROR, zero.kind);
    assertEquals("The fetch timeout MUST BE greater than 0 seconds", zero.getMessage());
    assertEquals(FormValidation.Kind.ERROR, config.doCheckFetchTimeout(null).kind);
    assertEquals(FormValidation.Kind.OK, config.doCheckFetchTimeout(30).kind);
  }

  @Test
  void fetchTimeoutCheckPassesForNonAdministrator(JenkinsRule r) {
    r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
    r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
      .grant(Jenkins.READ).everywhere().to("reader"));

    try (ACLContext ignored = ACL.as2(User.getById("reader", true).impersonate2())) {
      FormValidation result = RestListParameterGlobalConfig.get().doCheckFetchTimeout(0);
      assertEquals(FormValidation.Kind.OK, result.kind);
    }
  }

  @Test
  void fetchTimeoutIsConfigurableWithJcasc(JenkinsRule r) throws Exception {
    ConfigurationAsCode.get().configure(getClass().getResource("GlobalConfigJenkinsTest/fetchTimeout.yml").toString());

    assertEquals(120, RestListParameterGlobalConfig.get().getFetchTimeout());
  }

  @Test
  void fetchTimeoutRoundTripsThroughConfigurePage(JenkinsRule r) throws Exception {
    RestListParameterGlobalConfig.get().setFetchTimeout(45);

    r.configRoundtrip();

    assertEquals(45, RestListParameterGlobalConfig.get().getFetchTimeout());
  }

  // Clear value cache action (global-configuration, value-cache)

  @Test
  void administratorClearsTheValueCache(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/list", "[\"a\", \"b\"]");
      FreeStyleProject project = cachedProject(r, stub);
      assertEquals(1, stub.requestCount("/list"));

      r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
      r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
        .grant(Jenkins.ADMINISTER).everywhere().to("admin"));
      JenkinsRule.WebClient wc = r.createWebClient().login("admin");
      String result = wc.getPage(wc.addCrumb(new WebRequest(new URL(r.getURL(), clearUrl()), HttpMethod.POST)))
        .getWebResponse().getContentAsString();

      assertTrue(result.contains("Value cache cleared"), result);
      assertEquals(0, ValueCache.get().size());
      BuildForms.waitUntilLoaded(BuildForms.openWithoutWaiting(wc, project));
      assertEquals(2, stub.requestCount("/list"), "the next build form contacts the endpoint");
    }
  }

  @Test
  void clearButtonOnTheConfigurePageClearsWithoutSaving(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/list", "[\"a\", \"b\"]");
      cachedProject(r, stub);
      HtmlPage page = r.createWebClient().goTo("configure");
      HtmlElement button = page.querySelector("button[data-validate-button-method=clearValueCache]");
      assertNotNull(button, "Clear Value Cache button not rendered");

      button.click();
      page.getWebClient().waitForBackgroundJavaScript(5000);

      assertEquals(0, ValueCache.get().size());
      assertTrue(page.asNormalizedText().contains("Value cache cleared"), page.asNormalizedText());
    }
  }

  @Test
  void clearRequestWithoutAdministerIsDenied(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/list", "[\"a\", \"b\"]");
      cachedProject(r, stub);
      r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
      r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
        .grant(Jenkins.READ).everywhere().to("reader"));
      JenkinsRule.WebClient wc = r.createWebClient().login("reader");
      wc.setThrowExceptionOnFailingStatusCode(false);

      int status = wc.getPage(wc.addCrumb(new WebRequest(new URL(r.getURL(), clearUrl()), HttpMethod.POST)))
        .getWebResponse().getStatusCode();

      assertEquals(403, status);
      assertEquals(1, ValueCache.get().size(), "no entries are removed");
    }
  }

  private static FreeStyleProject cachedProject(final JenkinsRule r, final StubHttpServer stub) throws Exception {
    RestListParameterDefinition def = new RestListParameterDefinition(
      "p", "d", stub.url("/list"), "", MimeType.APPLICATION_JSON, "$.*", "$", ValueOrder.NONE, ".*", 10, "", false);
    FreeStyleProject project = r.createFreeStyleProject();
    project.addProperty(new ParametersDefinitionProperty(def));
    ValueService.entries(def, project, false);
    assertEquals(1, ValueCache.get().size());
    return project;
  }

  private static String clearUrl() {
    return "descriptorByName/" + RestListParameterGlobalConfig.class.getName() + "/clearValueCache";
  }
}
