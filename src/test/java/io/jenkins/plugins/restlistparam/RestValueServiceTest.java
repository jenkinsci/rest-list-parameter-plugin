package io.jenkins.plugins.restlistparam;

import io.jenkins.plugins.restlistparam.logic.RestValueService;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.ValueItem;
import io.jenkins.plugins.restlistparam.model.ResultContainer;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
