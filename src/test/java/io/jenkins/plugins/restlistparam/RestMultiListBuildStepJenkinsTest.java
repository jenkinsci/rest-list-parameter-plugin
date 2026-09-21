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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@WithJenkins
class RestMultiListBuildStepJenkinsTest {

  /**
   * The {@code build} step converts a string parameter for a downstream {@code SimpleParameterDefinition}
   * with {@code createValue(String)}, which reads it with the string input rule.
   */
  @Test
  void downstreamReceivesListFromStringParameter(JenkinsRule r) throws Exception {
    WorkflowJob down = r.createProject(WorkflowJob.class, "down");
    RestMultiListParameterDefinition def = new RestMultiListParameterDefinition(
      "TARGETS", "d", "http://127.0.0.1:1/none", "", MimeType.APPLICATION_JSON, "$.*", "$",
      ValueOrder.NONE, ".*", 0, "", false);
    def.setEnableValidation(false);
    down.addProperty(new ParametersDefinitionProperty(def));
    down.setDefinition(new CpsFlowDefinition("echo \"got ${params.TARGETS.size()}: ${params.TARGETS.join('|')}\"", true));

    WorkflowJob up = r.createProject(WorkflowJob.class, "up");
    up.setDefinition(new CpsFlowDefinition(
      "build job: 'down', parameters: [string(name: 'TARGETS', value: '[\"a\",\"c\"]')]", true));

    r.assertBuildStatusSuccess(up.scheduleBuild2(0));

    WorkflowRun downRun = down.getLastBuild();
    assertNotNull(downRun, "downstream build was not started");
    Object value = downRun.getAction(ParametersAction.class).getParameter("TARGETS");
    assertInstanceOf(RestMultiListParameterValue.class, value);
    assertEquals(List.of("a", "c"), ((RestMultiListParameterValue) value).getValue());
    r.assertLogContains("got 2: a|c", downRun);
  }
}
