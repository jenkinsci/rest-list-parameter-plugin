package io.jenkins.plugins.restlistparam;

import io.jenkins.plugins.restlistparam.logic.ValueResolver;
import io.jenkins.plugins.restlistparam.model.ResultContainer;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collection;

// TODO test cover ALL error cases
public class ValueResolverTest {
  @Test
  public void resolveJsonPathTest()
  {
    ResultContainer<Collection<String>> res = ValueResolver.resolveJsonPath(TestConst.testJson, "$.*.name");
    Assert.assertNotNull(res);
    Assert.assertFalse(res.getErrorMsg().isPresent());
    Assert.assertEquals(3, res.getValue().size());
    Assert.assertArrayEquals(new String[]{"v10.6.4", "v10.6.3", "v10.6.2"}, res.getValue().toArray());
  }

  @Test
  public void resolveJsonPathErrorTest()
  {
    ResultContainer<Collection<String>> res = ValueResolver.resolveJsonPath(TestConst.testJson, "$.name");
    Assert.assertNotNull(res);
    Assert.assertTrue(res.getErrorMsg().isPresent());
    Assert.assertEquals(0, res.getValue().size());
    Assert.assertArrayEquals(new String[]{}, res.getValue().toArray());
  }

  @Test
  public void resolveXPathTest()
  {
    ResultContainer<Collection<String>> res = ValueResolver.resolveXPath(TestConst.testXml, "//row/name");
    Assert.assertNotNull(res);
    Assert.assertFalse(res.getErrorMsg().isPresent());
    Assert.assertEquals(3, res.getValue().size());
    Assert.assertArrayEquals(new String[]{"v10.6.4", "v10.6.3", "v10.6.2"}, res.getValue().toArray());
  }

  @Test
  public void resolveXPathErrorTest()
  {
    ResultContainer<Collection<String>> res = ValueResolver.resolveXPath(TestConst.testXml, "//row_name");
    Assert.assertNotNull(res);
    Assert.assertTrue(res.getErrorMsg().isPresent());
    Assert.assertEquals(0, res.getValue().size());
    Assert.assertArrayEquals(new String[]{}, res.getValue().toArray());
  }
}
