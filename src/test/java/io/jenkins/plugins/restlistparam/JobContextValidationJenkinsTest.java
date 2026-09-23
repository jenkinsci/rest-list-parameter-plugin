package io.jenkins.plugins.restlistparam;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.ExtensionList;
import hudson.cli.CLICommandInvoker;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.User;
import hudson.security.ACL;
import hudson.util.Secret;
import io.jenkins.plugins.restlistparam.model.CustomHeader;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import io.jenkins.plugins.restlistparam.util.CredentialsUtils;
import jenkins.security.QueueItemAuthenticator;
import jenkins.security.QueueItemAuthenticatorProvider;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.springframework.security.core.Authentication;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Credentials used by submission-time validation outside a web request (Pipeline {@code build} step, CLI) are looked
 * up in the context of the job holding the parameter (specs/value-fetching, "Authorization from credentials").
 */
@WithJenkins
class JobContextValidationJenkinsTest {
  static final String CREDENTIAL_ID = "job-token";
  static final String SECRET = "s3cret";

  // Test credentials provider

  @Test
  void jobScopedCredentialIsVisibleOnlyInItsJob(JenkinsRule r) throws Exception {
    FreeStyleProject down = r.createFreeStyleProject("down");
    FreeStyleProject other = r.createFreeStyleProject("other");
    JobScopedCredentialsProvider.register("down", null);

    assertTrue(CredentialsUtils.findCredentials(down, CREDENTIAL_ID).isPresent());
    assertFalse(CredentialsUtils.findCredentials(other, CREDENTIAL_ID).isPresent());
    assertFalse(CredentialsUtils.findCredentials(null, CREDENTIAL_ID).isPresent());
  }

  // Pipeline and CLI triggers

  @Test
  void pipelineTriggerValidatesWithJobScopedCredential(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = protectedStub()) {
      JobScopedCredentialsProvider.register("down", null);
      WorkflowJob down = r.createProject(WorkflowJob.class, "down");
      down.addProperty(new ParametersDefinitionProperty(single(stub, CREDENTIAL_ID, 0)));
      down.setDefinition(new CpsFlowDefinition("echo \"got ${params.P}\"", true));

      r.assertBuildStatusSuccess(upstream(r, "string(name: 'P', value: 'v1')").scheduleBuild2(0));

      WorkflowRun downRun = down.getLastBuild();
      assertNotNull(downRun, "downstream build was not started");
      assertEquals("v1", downRun.getAction(ParametersAction.class).getParameter("P").getValue());
      assertEveryRequestAuthorized(stub);
    }
  }

  @Test
  void cliTriggerValidatesWithJobScopedCredential(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = protectedStub()) {
      JobScopedCredentialsProvider.register("down", null);
      FreeStyleProject down = r.createFreeStyleProject("down");
      down.addProperty(new ParametersDefinitionProperty(single(stub, CREDENTIAL_ID, 0)));

      CLICommandInvoker.Result result = new CLICommandInvoker(r, "build").invokeWithArgs("down", "-s", "-p", "P=v1");

      assertEquals(0, result.returnCode(), result.stderr());
      FreeStyleBuild build = down.getLastBuild();
      assertNotNull(build, "the build was not started");
      assertEquals("v1", build.getAction(ParametersAction.class).getParameter("P").getValue());
      assertEveryRequestAuthorized(stub);
    }
  }

  @Test
  void customHeaderCredentialIsResolvedOutsideWebRequest(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub()) {
      stub.requireHeader("/list", "X-Token", SECRET);
      JobScopedCredentialsProvider.register("down", null);
      CustomHeader header = new CustomHeader("X-Token");
      header.setCredentialId(CREDENTIAL_ID);
      RestListParameterDefinition def = single(stub, "", 0);
      def.setCustomHeaders(List.of(header));
      WorkflowJob down = r.createProject(WorkflowJob.class, "down");
      down.addProperty(new ParametersDefinitionProperty(def));
      down.setDefinition(new CpsFlowDefinition("echo \"got ${params.P}\"", true));

      r.assertBuildStatusSuccess(upstream(r, "string(name: 'P', value: 'v1')").scheduleBuild2(0));

      assertNotNull(down.getLastBuild(), "downstream build was not started");
      assertTrue(stub.requestCount("/list") > 0);
      for (StubHttpServer.RecordedRequest request : stub.requests()) {
        assertEquals(SECRET, request.header("X-Token"));
      }
    }
  }

  @Test
  void jobAuthenticationAppliesOutsideWebRequest(JenkinsRule r) throws Exception {
    r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
    try (StubHttpServer stub = protectedStub()) {
      // only the system authentication sees the credential, the job runs as bob
      JobScopedCredentialsProvider.register("down", ACL.SYSTEM2.getName());
      WorkflowJob down = r.createProject(WorkflowJob.class, "down");
      down.addProperty(new ParametersDefinitionProperty(single(stub, CREDENTIAL_ID, 0)));
      down.setDefinition(new CpsFlowDefinition("echo \"got ${params.P}\"", true));
      RunAs.register(down, "bob");

      WorkflowRun upRun = r.buildAndAssertStatus(Result.FAILURE, upstream(r, "string(name: 'P', value: 'v1')"));

      r.assertLogContains("Illegal value for parameter P: v1", upRun);
      assertNull(down.getLastBuild());
      assertTrue(stub.requestCount("/list") > 0);
      for (StubHttpServer.RecordedRequest request : stub.requests()) {
        assertFalse(request.hasHeader("Authorization"));
      }
    }
  }

  @Test
  void definitionNotHeldByAnyJobUsesRootCredentials(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub()) {
      stub.requireHeader("/list", "Authorization", "Bearer global-secret");
      SystemCredentialsProvider.getInstance().getCredentials().add(new StringCredentialsImpl(
        CredentialsScope.GLOBAL, "global-token", "", Secret.fromString("global-secret")));
      SystemCredentialsProvider.getInstance().save();
      RestListParameterDefinition def = single(stub, "global-token", 0);

      assertEquals("v1", def.createValue("v1").getValue());
      assertEquals("Bearer global-secret", stub.lastRequest().header("Authorization"));
    }
  }

  @Test
  void pipelineValidationReusesEntriesCachedByBuildForm(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = protectedStub()) {
      JobScopedCredentialsProvider.register("down", null);
      RestListParameterDefinition def = single(stub, CREDENTIAL_ID, 30);
      WorkflowJob down = r.createProject(WorkflowJob.class, "down");
      down.addProperty(new ParametersDefinitionProperty(def));
      down.setDefinition(new CpsFlowDefinition("echo \"got ${params.P}\"", true));
      def.createLoader(down).load(false);
      assertEquals(1, stub.requestCount("/list"), "the build form fetched the entries");

      r.assertBuildStatusSuccess(upstream(r, "string(name: 'P', value: 'v1')").scheduleBuild2(0));

      assertNotNull(down.getLastBuild(), "downstream build was not started");
      assertEquals(1, stub.requestCount("/list"), "validation used the entries the form cached under the job");
    }
  }

  // Owner lookup

  @Test
  void ownerIsTheJobHoldingTheInstance(JenkinsRule r) throws Exception {
    RestListParameterDefinition def = single(null, "", 0);
    FreeStyleProject first = r.createFreeStyleProject("first");
    // an equal definition in another job must not be taken for this one
    r.createFreeStyleProject("twin").addProperty(new ParametersDefinitionProperty(single(null, "", 0)));
    first.addProperty(new ParametersDefinitionProperty(def));

    assertSame(first, def.findOwner());
    assertNull(((AbstractRestListParameterDefinition) def.copyWithDefaultValue(
      new RestListParameterValue("P", "v1", "d"))).findOwner());

    first.removeProperty(ParametersDefinitionProperty.class);
    FreeStyleProject second = r.createFreeStyleProject("second");
    second.addProperty(new ParametersDefinitionProperty(def));
    assertSame(second, def.findOwner());
  }

  @Test
  void ownerIsNotPersisted(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = listStub()) {
      RestListParameterDefinition def = single(stub, "", 0);
      FreeStyleProject job = r.createFreeStyleProject("down");
      job.addProperty(new ParametersDefinitionProperty(def));
      assertSame(job, def.findOwner());

      FreeStyleBuild build = r.assertBuildStatusSuccess(job.scheduleBuild2(0,
        new ParametersAction(def.createValue("v1"))));
      job.save();
      build.save();

      for (String xml : List.of(
        Files.readString(job.getConfigFile().getFile().toPath()),
        Files.readString(build.getRootDir().toPath().resolve("build.xml"))))
      {
        assertFalse(xml.contains("<owner"), xml);
        assertFalse(xml.contains("WeakReference"), xml);
      }
    }
  }

  // Helpers

  static StubHttpServer listStub() throws Exception {
    StubHttpServer stub = new StubHttpServer();
    stub.respondJson("/list", "[\"v1\", \"v2\"]");
    return stub;
  }

  /** An endpoint that answers only requests carrying the job-scoped credential. */
  static StubHttpServer protectedStub() throws Exception {
    return listStub().requireHeader("/list", "Authorization", "Bearer " + SECRET);
  }

  static void assertEveryRequestAuthorized(final StubHttpServer stub) {
    assertTrue(stub.requestCount("/list") > 0, "validation contacted the endpoint");
    for (StubHttpServer.RecordedRequest request : stub.requests()) {
      assertEquals("Bearer " + SECRET, request.header("Authorization"));
    }
  }

  static WorkflowJob upstream(final JenkinsRule r, final String parameter) throws Exception {
    WorkflowJob up = r.createProject(WorkflowJob.class, "up");
    up.setDefinition(new CpsFlowDefinition("build job: 'down', parameters: [" + parameter + "]", true));
    return up;
  }

  private static RestListParameterDefinition single(final StubHttpServer stub,
                                                    final String credentialId,
                                                    final int cacheTime)
  {
    RestListParameterDefinition def = new RestListParameterDefinition(
      "P", "d", stub != null ? stub.url("/list") : "http://127.0.0.1:1/none", credentialId,
      MimeType.APPLICATION_JSON, "$.*", "$", ValueOrder.NONE, ".*", cacheTime, "", false);
    def.setEnableValidation(true);
    return def;
  }

  /**
   * Returns a secret text credential only for the named job, like a credential stored in the job's folder, and
   * optionally only to one authentication. It returns nothing at the Jenkins root.
   * <p>
   * Registered in code: test sources are compiled without annotation processing, so {@code @TestExtension} is not
   * indexed.
   */
  static final class JobScopedCredentialsProvider extends CredentialsProvider {
    private final String jobName;
    private final String authenticationName;
    private final StringCredentialsImpl credential = new StringCredentialsImpl(
      CredentialsScope.GLOBAL, CREDENTIAL_ID, "", Secret.fromString(SECRET));

    private JobScopedCredentialsProvider(final String jobName, final String authenticationName) {
      this.jobName = jobName;
      this.authenticationName = authenticationName;
    }

    /**
     * @param authenticationName The only authentication that sees the credential, or {@code null} for any
     */
    static void register(final String jobName, final String authenticationName) {
      ExtensionList.lookup(CredentialsProvider.class).add(new JobScopedCredentialsProvider(jobName, authenticationName));
    }

    @Override
    public <C extends Credentials> List<C> getCredentialsInItem(final Class<C> type,
                                                                final Item item,
                                                                final Authentication authentication,
                                                                final List<DomainRequirement> domainRequirements)
    {
      List<C> result = new ArrayList<>();
      if (item != null && jobName.equals(item.getFullName())
        && (authenticationName == null || authenticationName.equals(authentication.getName()))
        && type.isInstance(credential))
      {
        result.add(type.cast(credential));
      }
      return result;
    }

    @Override
    public <C extends Credentials> List<C> getCredentialsInItemGroup(final Class<C> type,
                                                                     final ItemGroup itemGroup,
                                                                     final Authentication authentication,
                                                                     final List<DomainRequirement> domainRequirements)
    {
      return new ArrayList<>();
    }
  }

  /** Makes one job run as a user, like the Authorize Project plugin. */
  static final class RunAs extends QueueItemAuthenticator {
    private final Job<?, ?> job;
    private final String user;

    private RunAs(final Job<?, ?> job, final String user) {
      this.job = job;
      this.user = user;
    }

    static void register(final Job<?, ?> job, final String user) {
      QueueItemAuthenticator authenticator = new RunAs(job, user);
      ExtensionList.lookup(QueueItemAuthenticatorProvider.class).add(new QueueItemAuthenticatorProvider() {
        @Override
        public List<QueueItemAuthenticator> getAuthenticators() {
          return List.of(authenticator);
        }
      });
    }

    @Override
    public Authentication authenticate2(final Queue.Task task) {
      return task == job ? User.getById(user, true).impersonate2() : null;
    }

    @Override
    public Authentication authenticate2(final Queue.Item item) {
      return authenticate2(item.task);
    }
  }
}
