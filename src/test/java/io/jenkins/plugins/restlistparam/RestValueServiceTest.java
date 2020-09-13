package io.jenkins.plugins.restlistparam;

import io.jenkins.plugins.restlistparam.logic.RestValueService;
import io.jenkins.plugins.restlistparam.model.MimeType;
import org.junit.Assert;
import org.junit.Test;

public class RestValueServiceTest {
  @Test
  public void getTest()
  {
    RestValueService
      .get("http://api.github.com/repos/jellyfin/jellyfin/tags?per_page=3", null, MimeType.APPLICATION_JSON, "$.*.name", "%%%");
    Assert.assertTrue(true);
  }
}
