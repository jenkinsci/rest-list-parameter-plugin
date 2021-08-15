package io.jenkins.plugins.restlistparam;

import java.util.List;

import com.jayway.jsonpath.JsonPath;
import io.jenkins.plugins.restlistparam.logic.ValueResolver;
import io.jenkins.plugins.restlistparam.model.ValueItem;
import io.jenkins.plugins.restlistparam.model.ResultContainer;
import org.junit.Assert;
import org.junit.Test;

public class ValueResolverTest {
  // Json-Path Tests
  @Test
  public void resolveJsonPathTest() {
    ResultContainer<List<ValueItem>> res = ValueResolver.resolveJsonPath(TestConst.validTestJson, "$.*.name", "$");
    Assert.assertNotNull(res);
    Assert.assertFalse(res.getErrorMsg().isPresent());
    Assert.assertEquals(3, res.getValue().size());
    Assert.assertArrayEquals(new String[]{"v10.6.4", "v10.6.3", "v10.6.2"}, res.getValue().stream().map(ValueItem::getValue).toArray());
  }

  @Test
  public void resolveJsonPathTestWithDifferentDisplayExpression() {
    ResultContainer<List<ValueItem>> res = ValueResolver.resolveJsonPath(TestConst.validTestJson, "$.*", "$.name");
    Assert.assertNotNull(res);
    Assert.assertFalse(res.getErrorMsg().isPresent());
    Assert.assertEquals(3, res.getValue().size());
    Assert.assertArrayEquals(new String[]{"v10.6.4", "v10.6.3", "v10.6.2"}, res.getValue().stream().map(ValueItem::getValue).map(JsonPath::parse).map(context -> context.read("$.name")).toArray());
    Assert.assertArrayEquals(new String[]{"v10.6.4", "v10.6.3", "v10.6.2"}, res.getValue().stream().map(ValueItem::getDisplayValue).toArray());
  }

  @Test
  public void emptyJsonPathResultsTest() {
    ResultContainer<List<ValueItem>> res = ValueResolver.resolveJsonPath("[]", "$.*", "$");
    Assert.assertNotNull(res);
    Assert.assertTrue(res.getErrorMsg().isPresent());
    Assert.assertEquals(0, res.getValue().size());
    Assert.assertArrayEquals(new String[]{}, res.getValue().stream().map(ValueItem::getValue).toArray());
  }

  @Test
  public void resolveJsonPathError1Test() {
    ResultContainer<List<ValueItem>> res = ValueResolver.resolveJsonPath(TestConst.validTestJson, "$.", "$");
    Assert.assertNotNull(res);
    Assert.assertTrue(res.getErrorMsg().isPresent());
    Assert.assertEquals(0, res.getValue().size());
    Assert.assertArrayEquals(new String[]{}, res.getValue().stream().map(ValueItem::getValue).toArray());
  }

  @Test
  public void resolveJsonPathError2Test() {
    ResultContainer<List<ValueItem>> res = ValueResolver.resolveJsonPath(TestConst.validTestJson, "$.name", "$");
    Assert.assertNotNull(res);
    Assert.assertTrue(res.getErrorMsg().isPresent());
    Assert.assertEquals(0, res.getValue().size());
    Assert.assertArrayEquals(new String[]{}, res.getValue().stream().map(ValueItem::getValue).toArray());
  }

  @Test
  public void invalidJsonErrorTest() {
    ResultContainer<List<ValueItem>> res = ValueResolver.resolveJsonPath(TestConst.invalidTestJson, "$.*.name", "$");
    Assert.assertNotNull(res);
    Assert.assertTrue(res.getErrorMsg().isPresent());
    Assert.assertEquals(0, res.getValue().size());
    Assert.assertArrayEquals(new String[]{}, res.getValue().stream().map(ValueItem::getValue).toArray());
  }

  // xPath Tests
  @Test
  public void resolveXPathTest() {
    ResultContainer<List<ValueItem>> res = ValueResolver.resolveXPath(TestConst.validTestXml, "//row/name", "/");
    Assert.assertNotNull(res);
    Assert.assertFalse(res.getErrorMsg().isPresent());
    Assert.assertEquals(3, res.getValue().size());
    Assert.assertArrayEquals(new String[]{"v10.6.4", "v10.6.3", "v10.6.2"}, res.getValue().stream().map(ValueItem::getValue).toArray());
  }

  @Test
  public void emptyXPathResultTest() {
    ResultContainer<List<ValueItem>> res = ValueResolver.resolveXPath(TestConst.validTestXml, "//row_name", "/");
    Assert.assertNotNull(res);
    Assert.assertTrue(res.getErrorMsg().isPresent());
    Assert.assertEquals(0, res.getValue().size());
    Assert.assertArrayEquals(new String[]{}, res.getValue().stream().map(ValueItem::getValue).toArray());
  }

  @Test
  public void resolveXPathErrorTest() {
    ResultContainer<List<ValueItem>> res = ValueResolver.resolveXPath(TestConst.validTestXml, "\\row_name", "/");
    Assert.assertNotNull(res);
    Assert.assertTrue(res.getErrorMsg().isPresent());
    Assert.assertEquals(0, res.getValue().size());
    Assert.assertArrayEquals(new String[]{}, res.getValue().stream().map(ValueItem::getValue).toArray());
  }

  @Test
  public void invalidXMLErrorTest() {
    ResultContainer<List<ValueItem>> res = ValueResolver.resolveXPath(TestConst.invalidTestXml, "//row/name", "/");
    Assert.assertNotNull(res);
    Assert.assertTrue(res.getErrorMsg().isPresent());
    Assert.assertEquals(0, res.getValue().size());
    Assert.assertArrayEquals(new String[]{}, res.getValue().stream().map(ValueItem::getValue).toArray());
  }
}
