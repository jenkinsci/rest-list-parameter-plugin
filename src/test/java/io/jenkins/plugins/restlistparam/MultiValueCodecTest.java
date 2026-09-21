package io.jenkins.plugins.restlistparam;

import io.jenkins.plugins.restlistparam.util.MultiValueCodec;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MultiValueCodecTest {

  // parse: "String input is parsed strictly"

  @Test
  void jsonArrayOfStringsBecomesTheList() {
    assertEquals(List.of("a", "c"), MultiValueCodec.parse("[\"a\",\"c\"]"));
  }

  @Test
  void jsonArrayMayContainWhitespace() {
    assertEquals(List.of("a", "c"), MultiValueCodec.parse(" [ \"a\" , \"c\" ] "));
  }

  @Test
  void plainStringIsOneElement() {
    assertEquals(List.of("a"), MultiValueCodec.parse("a"));
  }

  @Test
  void commaIsNotADelimiter() {
    assertEquals(List.of("a,c"), MultiValueCodec.parse("a,c"));
  }

  @Test
  void emptyStringIsTheEmptyList() {
    assertEquals(Collections.emptyList(), MultiValueCodec.parse(""));
  }

  @Test
  void emptyArrayIsTheEmptyList() {
    assertEquals(Collections.emptyList(), MultiValueCodec.parse("[]"));
  }

  @Test
  void arrayWithNonStringElementsIsOneElement() {
    assertEquals(List.of("[1,2]"), MultiValueCodec.parse("[1,2]"));
    assertEquals(List.of("[\"a\",null]"), MultiValueCodec.parse("[\"a\",null]"));
    assertEquals(List.of("[[\"a\"]]"), MultiValueCodec.parse("[[\"a\"]]"));
  }

  @Test
  void escapedArrayIsOneElement() {
    assertEquals(List.of("[\"x\"]"), MultiValueCodec.parse("[\"[\\\"x\\\"]\"]"));
  }

  @Test
  void unquotedArrayIsOneElement() {
    assertEquals(List.of("[a,b]"), MultiValueCodec.parse("[a,b]"));
  }

  @Test
  void nonStrictJsonIsOneElement() {
    assertEquals(List.of("['a','b']"), MultiValueCodec.parse("['a','b']"));
    assertEquals(List.of("[\"a\",]"), MultiValueCodec.parse("[\"a\",]"));
    assertEquals(List.of("[\"a\"] trailing"), MultiValueCodec.parse("[\"a\"] trailing"));
  }

  @Test
  void jsonScalarsAndObjectsAreOneElement() {
    assertEquals(List.of("\"a\""), MultiValueCodec.parse("\"a\""));
    assertEquals(List.of("{\"a\":1}"), MultiValueCodec.parse("{\"a\":1}"));
    assertEquals(List.of("42"), MultiValueCodec.parse("42"));
  }

  @Test
  void nullIsNull() {
    assertNull(MultiValueCodec.parse(null));
  }

  // encode: "Value exposed as a JSON array in the environment"

  @Test
  void encodesCompactArray() {
    assertEquals("[\"a\",\"c\"]", MultiValueCodec.encode(List.of("a", "c")));
  }

  @Test
  void encodesEmptyList() {
    assertEquals("[]", MultiValueCodec.encode(Collections.emptyList()));
  }

  @Test
  void encodesJsonTextAsString() {
    assertEquals("[\"{\\\"name\\\":\\\"v1\\\"}\"]", MultiValueCodec.encode(List.of("{\"name\":\"v1\"}")));
  }

  @Test
  void parseReversesEncode() {
    List<List<String>> lists = List.of(
      Collections.emptyList(),
      List.of(""),
      List.of("a", "c"),
      List.of("with \"quotes\"", "back\\slash", "trailing\\"),
      List.of("unicode ü € 😀", " line sep", "ctrl\n\t\r\b\f"),
      List.of("[\"x\"]", "[a,b]", "[]", "["),
      List.of("{\"name\":\"v1\"}", "{", "</script>"),
      List.of("a,c", " spaced ", "null", "42"));
    for (List<String> list : lists) {
      assertEquals(list, MultiValueCodec.parse(MultiValueCodec.encode(list)), list.toString());
    }
  }
}
