package io.jenkins.plugins.restlistparam;

import io.jenkins.plugins.restlistparam.logic.RestValueService;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.ResultContainer;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

// TODO more unit tests here (preferably replace integration tests)
public class RestValueServiceTest {
  @Test
  public void successfulGetIntegrationTest() {
    ResultContainer<List<String>> test = RestValueService
      .get("http://api.github.com/repos/jellyfin/jellyfin/tags?per_page=3",
           null,
           MimeType.APPLICATION_JSON,
           "$.*.name",
           null,
           ValueOrder.NONE);
    Assert.assertFalse(test.getErrorMsg().isPresent());
    Assert.assertEquals(3, test.getValue().size());
    Assert.assertArrayEquals(new String[]{"v10.6.4", "v10.6.3", "v10.6.2"}, test.getValue().toArray());
  }

  @Test
  public void unsuccessfulGetIntegrationTest() {
    ResultContainer<List<String>> test = RestValueService
      .get("https://gitlab.example.com/api/v4/projects/gitlab-org%2Fgitlab-runner/releases",
           null,
           MimeType.APPLICATION_JSON,
           "$.*.tag_name",
           null,
           ValueOrder.NONE);
    Assert.assertNotNull(test);
    Assert.assertTrue(test.getErrorMsg().isPresent());
    Assert.assertEquals(0, test.getValue().size());
    Assert.assertArrayEquals(new String[]{}, test.getValue().toArray());
  }
}
