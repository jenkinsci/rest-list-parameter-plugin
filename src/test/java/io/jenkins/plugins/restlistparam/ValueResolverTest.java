package io.jenkins.plugins.restlistparam;

import io.jenkins.plugins.restlistparam.logic.ValueResolver;
import io.jenkins.plugins.restlistparam.model.ResultContainer;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class ValueResolverTest {
  // Json-Path Tests
  @Test
  public void resolveJsonPathTest() {
    ResultContainer<List<String>> res = ValueResolver.resolveJsonPath(TestConst.validTestJson, "$.*.name");
    Assert.assertNotNull(res);
    Assert.assertFalse(res.getErrorMsg().isPresent());
    Assert.assertEquals(3, res.getValue().size());
    Assert.assertArrayEquals(new String[]{"v10.6.4", "v10.6.3", "v10.6.2"}, res.getValue().toArray());
  }

  @Test
  public void emptyJsonPathResultsTest() {
    ResultContainer<List<String>> res = ValueResolver.resolveJsonPath("[]", "$.*");
    Assert.assertNotNull(res);
    Assert.assertTrue(res.getErrorMsg().isPresent());
    Assert.assertEquals(0, res.getValue().size());
    Assert.assertArrayEquals(new String[]{}, res.getValue().toArray());
  }

  @Test
  public void resolveJsonPathError1Test() {
    ResultContainer<List<String>> res = ValueResolver.resolveJsonPath(TestConst.validTestJson, "$.");
    Assert.assertNotNull(res);
    Assert.assertTrue(res.getErrorMsg().isPresent());
    Assert.assertEquals(0, res.getValue().size());
    Assert.assertArrayEquals(new String[]{}, res.getValue().toArray());
  }

  @Test
  public void resolveJsonPathError2Test() {
    ResultContainer<List<String>> res = ValueResolver.resolveJsonPath(TestConst.validTestJson, "$.name");
    Assert.assertNotNull(res);
    Assert.assertTrue(res.getErrorMsg().isPresent());
    Assert.assertEquals(0, res.getValue().size());
    Assert.assertArrayEquals(new String[]{}, res.getValue().toArray());
  }

  @Test
  public void invalidJsonErrorTest() {
    ResultContainer<List<String>> res = ValueResolver.resolveJsonPath(TestConst.invalidTestJson, "$.*.name");
    Assert.assertNotNull(res);
    Assert.assertTrue(res.getErrorMsg().isPresent());
    Assert.assertEquals(0, res.getValue().size());
    Assert.assertArrayEquals(new String[]{}, res.getValue().toArray());
  }

  // xPath Tests
  @Test
  public void resolveXPathTest() {
    ResultContainer<List<String>> res = ValueResolver.resolveXPath(TestConst.validTestXml, "//row/name");
    Assert.assertNotNull(res);
    Assert.assertFalse(res.getErrorMsg().isPresent());
    Assert.assertEquals(3, res.getValue().size());
    Assert.assertArrayEquals(new String[]{"v10.6.4", "v10.6.3", "v10.6.2"}, res.getValue().toArray());
  }

  @Test
  public void emptyXPathResultTest() {
    ResultContainer<List<String>> res = ValueResolver.resolveXPath(TestConst.validTestXml, "//row_name");
    Assert.assertNotNull(res);
    Assert.assertTrue(res.getErrorMsg().isPresent());
    Assert.assertEquals(0, res.getValue().size());
    Assert.assertArrayEquals(new String[]{}, res.getValue().toArray());
  }

  @Test
  public void resolveXPathErrorTest() {
    ResultContainer<List<String>> res = ValueResolver.resolveXPath(TestConst.validTestXml, "\\row_name");
    Assert.assertNotNull(res);
    Assert.assertTrue(res.getErrorMsg().isPresent());
    Assert.assertEquals(0, res.getValue().size());
    Assert.assertArrayEquals(new String[]{}, res.getValue().toArray());
  }

  @Test
  public void invalidXMLErrorTest() {
    ResultContainer<List<String>> res = ValueResolver.resolveXPath(TestConst.invalidTestXml, "//row/name");
    Assert.assertNotNull(res);
    Assert.assertTrue(res.getErrorMsg().isPresent());
    Assert.assertEquals(0, res.getValue().size());
    Assert.assertArrayEquals(new String[]{}, res.getValue().toArray());
  }
}
