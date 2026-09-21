package io.jenkins.plugins.restlistparam;

import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.List;

@WithJenkins
class RestMultiListPipelineJenkinsTest {

  /**
   * {@code params.TARGETS} is a real list in Pipeline, while the environment holds the Json array. The
   * shell step suspends the program, so the list also survives the Pipeline's own serialization.
   */
  @Test
  void pipelineSeesListAndEnvironmentSeesJsonArray(JenkinsRule r) throws Exception {
    WorkflowJob job = r.createProject(WorkflowJob.class, "up");
    job.addProperty(new ParametersDefinitionProperty(new RestMultiListParameterDefinition(
      "TARGETS", "d", "http://127.0.0.1:1/none", "", MimeType.APPLICATION_JSON, "$.*", "$",
      ValueOrder.NONE, ".*", 0, "", false)));
    job.setDefinition(new CpsFlowDefinition(
      "echo \"is list: ${params.TARGETS instanceof List}, size: ${params.TARGETS.size()}\"\n" +
      "params.TARGETS.each { echo \"element: ${it}\" }\n" +
      "node {\n" +
      "  if (isUnix()) {\n" +
      "    sh 'echo \"shell: $TARGETS\"'\n" +
      "  } else {\n" +
      "    bat 'echo shell: %TARGETS%'\n" +
      "  }\n" +
      "}\n" +
      "echo \"env: ${env.TARGETS}\"\n" +
      "echo \"after sh: ${params.TARGETS.join('|')}\"\n", true));

    WorkflowRun run = r.assertBuildStatusSuccess(job.scheduleBuild2(0,
      new ParametersAction(new RestMultiListParameterValue("TARGETS", List.of("a", "c")))));

    r.assertLogContains("is list: true, size: 2", run);
    String log = JenkinsRule.getLog(run);
    int a = log.indexOf("element: a");
    int c = log.indexOf("element: c");
    if (a < 0 || c < a) {
      throw new AssertionError("elements not printed in order:\n" + log);
    }
    r.assertLogContains("shell: [\"a\",\"c\"]", run);
    r.assertLogContains("env: [\"a\",\"c\"]", run);
    r.assertLogContains("after sh: a|c", run);
  }
}
