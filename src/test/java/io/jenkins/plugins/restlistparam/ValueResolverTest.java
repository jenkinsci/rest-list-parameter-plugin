package io.jenkins.plugins.restlistparam;

import com.jayway.jsonpath.JsonPath;
import io.jenkins.plugins.restlistparam.logic.ValueResolver;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.ResultContainer;
import io.jenkins.plugins.restlistparam.model.ValueItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValueResolverTest {
  // Json-Path Tests
  @Test
  void resolveJsonPathTest() {
    ResultContainer<List<ValueItem>> res = ValueResolver.resolveJsonPath(TestConst.validTestJson, "$.*.name", "$");
    assertNotNull(res);
    assertFalse(res.getErrorMsg().isPresent());
    assertEquals(3, res.getValue().size());
    assertArrayEquals(new String[]{"v10.6.4", "v10.6.3", "v10.6.2"},
                             res.getValue().stream().map(ValueItem::getValue).toArray());
    assertArrayEquals(new String[]{"v10.6.4", "v10.6.3", "v10.6.2"},
                             res.getValue().stream().map(ValueItem::getDisplayValue).toArray());
  }

  @Test
  void resolveJsonPathNumberLikeValuesTest() {
    ResultContainer<List<ValueItem>> res = ValueResolver.resolveJsonPath(TestConst.numberLikeTestJson, "$.*.name", "$");
    assertNotNull(res);
    assertFalse(res.getErrorMsg().isPresent());
    assertEquals(3, res.getValue().size());
    assertArrayEquals(new String[]{"2024.19", "2024.20", "2024.0"},
                             res.getValue().stream().map(ValueItem::getValue).toArray());
    assertArrayEquals(new String[]{"2024.19", "2024.20", "2024.0"},
                             res.getValue().stream().map(ValueItem::getDisplayValue).toArray());
  }

  @Test
  void resolveJsonPathNumberValuesTest() {
    ResultContainer<List<ValueItem>> res = ValueResolver.resolveJsonPath(TestConst.numberValuesTestJson, "$.*.value", "$");
    assertNotNull(res);
    assertFalse(res.getErrorMsg().isPresent());
    assertEquals(3, res.getValue().size());
    assertArrayEquals(new String[]{"1.0", "10", "11.5"},
                             res.getValue().stream().map(ValueItem::getValue).toArray());
    // trailing 0 and decimal points are shaved off numbers by JSONStringer
    assertArrayEquals(new String[]{"1", "10", "11.5"},
                             res.getValue().stream().map(ValueItem::getDisplayValue).toArray());
  }

  @Test
  void resolveJsonPathMixedTypeValuesTest() {
    ResultContainer<List<ValueItem>> res = ValueResolver.resolveJsonPath(TestConst.mixedTypeValuesTestJson, "$.*.value", "$");
    assertNotNull(res);
    assertFalse(res.getErrorMsg().isPresent());
    assertEquals(5, res.getValue().size());
    assertArrayEquals(new String[]{"1.0", "10", "11.50", "true", "false"},
                             res.getValue().stream().map(ValueItem::getValue).toArray());
    // trailing 0 and decimal points are shaved off numbers by JSONStringer
    assertArrayEquals(new String[]{"1", "10", "11.50", "true", "false"},
                             res.getValue().stream().map(ValueItem::getDisplayValue).toArray());
  }

  @Test
  void resolveJsonPathWithDifferentDisplayExpressionTest() {
    ResultContainer<List<ValueItem>> res = ValueResolver.resolveJsonPath(TestConst.validTestJson, "$.*", "$.name");
    assertNotNull(res);
    assertFalse(res.getErrorMsg().isPresent());
    assertEquals(3, res.getValue().size());
    assertArrayEquals(new String[]{"v10.6.4", "v10.6.3", "v10.6.2"},
                             res.getValue().stream().map(ValueItem::getValue).map(JsonPath::parse).map(context -> context.read("$.name")).toArray());
    assertArrayEquals(new String[]{"v10.6.4", "v10.6.3", "v10.6.2"},
                             res.getValue().stream().map(ValueItem::getDisplayValue).toArray());
  }

  @Test
  void emptyJsonPathResultsTest() {
    ResultContainer<List<ValueItem>> res = ValueResolver.resolveJsonPath("[]", "$.*", "$");
    assertNotNull(res);
    assertTrue(res.getErrorMsg().isPresent());
    assertEquals(0, res.getValue().size());
    assertArrayEquals(new String[]{}, res.getValue().stream().map(ValueItem::getValue).toArray());
  }

  @Test
  void resolveJsonPathError1Test() {
    ResultContainer<List<ValueItem>> res = ValueResolver.resolveJsonPath(TestConst.validTestJson, "$.", "$");
    assertNotNull(res);
    assertTrue(res.getErrorMsg().isPresent());
    assertEquals(0, res.getValue().size());
    assertArrayEquals(new String[]{}, res.getValue().stream().map(ValueItem::getValue).toArray());
  }

  @Test
  void resolveJsonPathError2Test() {
    ResultContainer<List<ValueItem>> res = ValueResolver.resolveJsonPath(TestConst.validTestJson, "$.name", "$");
    assertNotNull(res);
    assertTrue(res.getErrorMsg().isPresent());
    assertEquals(0, res.getValue().size());
    assertArrayEquals(new String[]{}, res.getValue().stream().map(ValueItem::getValue).toArray());
  }

  @Test
  void invalidJsonErrorTest() {
    ResultContainer<List<ValueItem>> res = ValueResolver.resolveJsonPath(TestConst.invalidTestJson, "$.*.name", "$");
    assertNotNull(res);
    assertTrue(res.getErrorMsg().isPresent());
    assertEquals(0, res.getValue().size());
    assertArrayEquals(new String[]{}, res.getValue().stream().map(ValueItem::getValue).toArray());
  }

  @Test
  void displayJsonValueFuncTest() {
    String displayValue = ValueResolver.parseDisplayValue(MimeType.APPLICATION_JSON, TestConst.validJsonValueItem, "$.name");
    assertEquals("v10.6.4", displayValue);
  }

  // xPath Tests
  @Test
  void resolveXPathTest() {
    ResultContainer<List<ValueItem>> res = ValueResolver.resolveXPath(TestConst.validTestXml, "//row/name", "/");
    assertNotNull(res);
    assertFalse(res.getErrorMsg().isPresent());
    assertEquals(3, res.getValue().size());
    assertArrayEquals(new String[]{"v10.6.4", "v10.6.3", "v10.6.2"},
                             res.getValue().stream().map(ValueItem::getValue).toArray());
  }

  @Test
  void emptyXPathResultTest() {
    ResultContainer<List<ValueItem>> res = ValueResolver.resolveXPath(TestConst.validTestXml, "//row_name", "/");
    assertNotNull(res);
    assertTrue(res.getErrorMsg().isPresent());
    assertEquals(0, res.getValue().size());
    assertArrayEquals(new String[]{}, res.getValue().stream().map(ValueItem::getValue).toArray());
  }

  @Test
  void resolveXPathErrorTest() {
    ResultContainer<List<ValueItem>> res = ValueResolver.resolveXPath(TestConst.validTestXml, "\\row_name", "/");
    assertNotNull(res);
    assertTrue(res.getErrorMsg().isPresent());
    assertEquals(0, res.getValue().size());
    assertArrayEquals(new String[]{}, res.getValue().stream().map(ValueItem::getValue).toArray());
  }

  @Test
  void invalidXMLErrorTest() {
    ResultContainer<List<ValueItem>> res = ValueResolver.resolveXPath(TestConst.invalidTestXml, "//row/name", "/");
    assertNotNull(res);
    assertTrue(res.getErrorMsg().isPresent());
    assertEquals(0, res.getValue().size());
    assertArrayEquals(new String[]{}, res.getValue().stream().map(ValueItem::getValue).toArray());
  }
}
