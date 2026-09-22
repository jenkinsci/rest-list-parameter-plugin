package io.jenkins.plugins.restlistparam;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The {@code pagination} property in Pipeline declarations (specs/global-configuration): the Pipeline DSL
 * resolves {@code RESTList(...)} with a nested {@code linkHeader(...)} or {@code continuationToken(...)}
 * through the same symbols and Describable model used by {@code properties([parameters([...])])}.
 */
@WithJenkins
class PaginationPipelineJenkinsTest {

  @Test
  void linkHeaderDeclaration(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = linkedPages(10)) {
      WorkflowRun run = run(r, "pagination: linkHeader(maxPages: 5)", stub.url("/p"));

      r.assertLogContains("strategy: LinkHeaderPagination, maxPages: 5, effective: 5", run);
      r.assertLogContains("values: [1, 2, 3, 4, 5]", run);
      assertEquals(5, stub.requests().size());
    }
  }

  @Test
  void continuationTokenDeclaration(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = new StubHttpServer()) {
      stub.respondJson("/components?repository=releases", "{\"items\":[\"1.0\",\"1.1\"],\"continuationToken\":\"abc\"}");
      stub.respondJson("/components?repository=releases&continuationToken=abc",
        "{\"items\":[\"2.0\"],\"continuationToken\":null}");

      WorkflowRun run = run(r,
        "pagination: continuationToken(tokenExpression: '$.continuationToken', queryParameter: 'continuationToken')",
        stub.url("/components?repository=releases"), "$.items[*]");

      r.assertLogContains("strategy: ContinuationTokenPagination, maxPages: 10, effective: 10", run);
      r.assertLogContains("token: $.continuationToken -> continuationToken", run);
      r.assertLogContains("values: [1.0, 1.1, 2.0]", run);
      assertEquals(2, stub.requests().size());
    }
  }

  @Test
  void outOfRangePageLimitIsCapped(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = linkedPages(150)) {
      WorkflowRun run = run(r, "pagination: linkHeader(maxPages: 500)", stub.url("/p"));

      r.assertLogContains("strategy: LinkHeaderPagination, maxPages: 500, effective: 100", run);
      r.assertLogContains("count: 100", run);
      assertEquals(100, stub.requests().size());
    }
  }

  @Test
  void omittedPaginationSendsOneRequest(JenkinsRule r) throws Exception {
    try (StubHttpServer stub = linkedPages(3)) {
      WorkflowRun run = run(r, null, stub.url("/p"));

      r.assertLogContains("strategy: none", run);
      r.assertLogContains("values: [1]", run);
      assertEquals(1, stub.requests().size());
    }
  }

  private static WorkflowRun run(final JenkinsRule r, final String pagination, final String endpoint) throws Exception {
    return run(r, pagination, endpoint, "$.*");
  }

  private static WorkflowRun run(final JenkinsRule r,
                                 final String pagination,
                                 final String endpoint,
                                 final String valueExpression) throws Exception
  {
    WorkflowJob job = r.createProject(WorkflowJob.class);
    job.setDefinition(new CpsFlowDefinition(
      "def declared = RESTList(name: 'V', description: '', restEndpoint: '" + endpoint + "', credentialId: '',\n" +
      "  mimeType: 'APPLICATION_JSON', valueExpression: '" + valueExpression + "'" +
      (pagination != null ? ", " + pagination : "") + ")\n" +
      "def p = declared.instantiate(hudson.model.ParameterDefinition)\n" +
      "if (p.pagination == null) {\n" +
      "  echo 'strategy: none'\n" +
      "} else {\n" +
      "  echo \"strategy: ${p.pagination.class.simpleName}, maxPages: ${p.pagination.maxPages}, effective: ${p.pagination.effectiveMaxPages}\"\n" +
      "  if (p.pagination.hasProperty('tokenExpression')) {\n" +
      "    echo \"token: ${p.pagination.tokenExpression} -> ${p.pagination.queryParameter}\"\n" +
      "  }\n" +
      "}\n" +
      "def values = p.values.collect { it.value }\n" +
      "echo \"error: [${p.errorMsg}]\"\n" +
      "echo \"count: ${values.size()}\"\n" +
      "echo \"values: ${values}\"\n", false));
    WorkflowRun run = r.buildAndAssertSuccess(job);
    // bracketed, since the log's line separator differs between platforms
    r.assertLogContains("error: []", run);
    return run;
  }

  /** Pages {@code /p}, {@code /p?page=2} ... {@code /p?page=<count>}, page n yielding {@code ["n"]}. */
  private static StubHttpServer linkedPages(final int count) throws Exception {
    StubHttpServer stub = new StubHttpServer();
    for (int page = 1; page <= count; page++) {
      String path = page == 1 ? "/p" : "/p?page=" + page;
      stub.respondJson(path, "[\"" + page + "\"]");
      if (page < count) {
        stub.withHeader(path, "Link", "<" + stub.url("/p?page=" + (page + 1)) + ">; rel=\"next\"");
      }
    }
    return stub;
  }
}
