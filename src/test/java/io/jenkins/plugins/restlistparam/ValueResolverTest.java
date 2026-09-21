package io.jenkins.plugins.restlistparam;

import com.jayway.jsonpath.JsonPath;
import io.jenkins.plugins.restlistparam.logic.ValueResolver;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.ResultContainer;
import io.jenkins.plugins.restlistparam.model.ValueItem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

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

  @Test
  void largeIntegersKeepAllDigitsTest() {
    ResultContainer<List<ValueItem>> res = ValueResolver.resolveJsonPath(
      "[9007199254740993, 12345678901234567890]", "$.*", "$");
    assertFalse(res.getErrorMsg().isPresent());
    assertArrayEquals(new String[]{"9007199254740993", "12345678901234567890"},
                      res.getValue().stream().map(ValueItem::getValue).toArray());
    assertArrayEquals(new String[]{"9007199254740993", "12345678901234567890"},
                      res.getValue().stream().map(ValueItem::getDisplayValue).toArray());
  }

  @Test
  void nullMatchesAreSkippedTest() {
    ResultContainer<List<ValueItem>> res = ValueResolver.resolveJsonPath("[\"a\", null, \"b\"]", "$.*", "$");
    assertFalse(res.getErrorMsg().isPresent());
    assertArrayEquals(new String[]{"a", "b"}, res.getValue().stream().map(ValueItem::getValue).toArray());
  }

  @Test
  void onlyNullMatchesIsNoResultsErrorTest() {
    ResultContainer<List<ValueItem>> res = ValueResolver.resolveJsonPath("[null, null]", "$.*", "$");
    assertEquals("Json-Path expression yielded no results", res.getErrorMsg().orElse(null));
    assertTrue(res.getValue().isEmpty());
  }

  @Test
  void displayFallsBackToValuePerEntryTest() {
    try (LogCapture log = new LogCapture()) {
      ResultContainer<List<ValueItem>> res = ValueResolver.resolveJsonPath(
        "[{\"name\":\"a\"}, {\"id\":2}]", "$.*", "$.name");
      assertFalse(res.getErrorMsg().isPresent());
      assertEquals(2, res.getValue().size());
      assertEquals("a", res.getValue().get(0).getDisplayValue());
      assertEquals("{\"id\":2}", res.getValue().get(1).getValue());
      assertEquals(res.getValue().get(1).getValue(), res.getValue().get(1).getDisplayValue());
      assertEquals(1, log.warnings().size(), "expected one aggregated warning: " + log.warnings());
      assertTrue(log.warnings().get(0).startsWith("1 of 2 entries"));
    }
  }

  @Test
  void numericDisplayValueTest() {
    ResultContainer<List<ValueItem>> res = ValueResolver.resolveJsonPath("[{\"id\":2}]", "$.*", "$.id");
    assertFalse(res.getErrorMsg().isPresent());
    assertEquals("2", res.getValue().get(0).getDisplayValue());
  }

  @Test
  void displayPathMissingOnEveryElementTest() {
    try (LogCapture log = new LogCapture()) {
      ResultContainer<List<ValueItem>> res = ValueResolver.resolveJsonPath(
        "[{\"id\":1}, {\"id\":2}, {\"id\":3}]", "$.*", "$.name");
      assertFalse(res.getErrorMsg().isPresent());
      assertEquals(3, res.getValue().size());
      res.getValue().forEach(item -> assertEquals(item.getValue(), item.getDisplayValue()));
      assertEquals(1, log.warnings().size(), "expected one aggregated warning: " + log.warnings());
    }
  }

  @Test
  void invalidDisplayExpressionIsExpressionErrorTest() {
    ResultContainer<List<ValueItem>> res = ValueResolver.resolveJsonPath("[{\"id\":1}]", "$.*", "$.");
    assertEquals("Error in provided Json-Path expression", res.getErrorMsg().orElse(null));
  }

  @Test
  void parseDisplayValueKeepsNonJsonVerbatimTest() {
    assertEquals("not {json", ValueResolver.parseDisplayValue(MimeType.APPLICATION_JSON, "not {json", "$.name"));
    assertEquals("{\"id\":2}", ValueResolver.parseDisplayValue(MimeType.APPLICATION_JSON, "{\"id\":2}", "$.name"));
  }

  /** Captures warnings logged by {@link ValueResolver} while open. */
  private static final class LogCapture extends Handler implements AutoCloseable {
    private final Logger logger = Logger.getLogger(ValueResolver.class.getName());
    private final List<String> warnings = new ArrayList<>();

    LogCapture() {
      logger.addHandler(this);
    }

    List<String> warnings() {
      return warnings;
    }

    @Override
    public void publish(LogRecord record) {
      if (record.getLevel() == Level.WARNING) {
        warnings.add(record.getMessage());
      }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
      logger.removeHandler(this);
    }
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
