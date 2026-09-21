package io.jenkins.plugins.restlistparam.util;

import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Converts the value of a REST Multi List Parameter between its list form and its text form,
 * a compact Json array of Json strings such as {@code ["a","c"]}.
 * <p>
 * {@code parse(encode(list))} yields {@code list} for every list of non-null strings.
 */
public final class MultiValueCodec {

  private MultiValueCodec() {
    throw new IllegalStateException("Utility class");
  }

  /**
   * Reads a value supplied as a single string. A strict (RFC 4627) Json array whose elements are all
   * Json strings becomes the list of those strings; the empty string and {@code []} become the empty
   * list; any other string, including malformed or non-strict Json such as {@code [a,b]}, becomes a
   * one-element list holding the string verbatim. The string is never split on a delimiter.
   *
   * @param input The supplied string
   * @return The list of elements, or {@code null} when {@code input} is {@code null}
   */
  public static List<String> parse(final String input) {
    if (input == null) {
      return null;
    }
    if (input.isEmpty()) {
      return Collections.emptyList();
    }
    Object parsed;
    try {
      parsed = new JSONParser(JSONParser.MODE_RFC4627).parse(input);
    }
    catch (ParseException | RuntimeException ignored) {
      return Collections.singletonList(input);
    }
    if (!(parsed instanceof List)) {
      return Collections.singletonList(input);
    }
    List<String> elements = new ArrayList<>();
    for (Object element : (List<?>) parsed) {
      if (!(element instanceof String)) {
        return Collections.singletonList(input);
      }
      elements.add((String) element);
    }
    return elements;
  }

  /**
   * Writes a list as a compact Json array of Json strings, with no whitespace between tokens.
   * Every element is written as a Json string, even when its text is itself Json.
   *
   * @param elements The elements, none of them {@code null}
   * @return The Json array text
   */
  public static String encode(final List<String> elements) {
    return elements.stream()
      .map(JSONObject::quote)
      .collect(Collectors.joining(",", "[", "]"));
  }
}
