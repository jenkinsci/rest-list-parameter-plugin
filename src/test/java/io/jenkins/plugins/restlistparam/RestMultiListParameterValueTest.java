package io.jenkins.plugins.restlistparam;

import hudson.EnvVars;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestMultiListParameterValueTest {

  @Test
  void duplicatesCollapseKeepingFirstOccurrence() {
    assertEquals(List.of("b", "a"), new RestMultiListParameterValue("p", List.of("b", "a", "b")).getValue());
  }

  @Test
  void suppliedOrderIsKept() {
    assertEquals(List.of("c", "a"), new RestMultiListParameterValue("p", List.of("c", "a")).getValue());
  }

  @Test
  void valueIsUnmodifiableAndDetachedFromInput() {
    List<String> input = new ArrayList<>(List.of("a"));
    RestMultiListParameterValue value = new RestMultiListParameterValue("p", input);
    input.add("b");

    assertEquals(List.of("a"), value.getValue());
    assertThrows(UnsupportedOperationException.class, () -> value.getValue().add("c"));
  }

  @Test
  void missingValueStaysNull() {
    RestMultiListParameterValue value = new RestMultiListParameterValue("p", null);
    assertNull(value.getValue());
    EnvVars env = new EnvVars();
    value.buildEnvironment(null, env);
    assertNull(env.get("p"));
  }

  @Test
  void environmentHoldsJsonArrayUnderNameAndUpperCasedName() {
    EnvVars env = environment(new RestMultiListParameterValue("targets", List.of("a", "c")));
    assertEquals("[\"a\",\"c\"]", env.get("targets"));
    assertEquals("[\"a\",\"c\"]", env.get("TARGETS"));
  }

  @Test
  void emptyListIsEmptyJsonArray() {
    assertEquals("[]", environment(new RestMultiListParameterValue("p", Collections.emptyList())).get("p"));
  }

  @Test
  void elementContainingJsonTextIsEncodedAsString() {
    assertEquals("[\"{\\\"name\\\":\\\"v1\\\"}\"]",
      environment(new RestMultiListParameterValue("p", List.of("{\"name\":\"v1\"}"))).get("p"));
  }

  @Test
  void variableResolverUsesJsonArray() {
    RestMultiListParameterValue value = new RestMultiListParameterValue("p", List.of("a", "c"));
    assertEquals("[\"a\",\"c\"]", value.createVariableResolver(null).resolve("p"));
    assertNull(value.createVariableResolver(null).resolve("other"));
  }

  @Test
  void shortDescriptionUsesJsonArray() {
    assertEquals("p=[\"a\",\"c\"]", new RestMultiListParameterValue("p", List.of("a", "c")).getShortDescription());
  }

  @Test
  void equalityFollowsTheList() {
    assertEquals(new RestMultiListParameterValue("p", List.of("a", "c")),
      new RestMultiListParameterValue("p", Arrays.asList("a", "c", "a")));
    assertEquals(new RestMultiListParameterValue("p", List.of("a", "c")).hashCode(),
      new RestMultiListParameterValue("p", Arrays.asList("a", "c", "a")).hashCode());
    assertNotEquals(new RestMultiListParameterValue("p", List.of("a", "c")),
      new RestMultiListParameterValue("p", List.of("c", "a")));
    assertNotEquals(new RestMultiListParameterValue("p", List.of("a")),
      new RestMultiListParameterValue("q", List.of("a")));
  }

  private static EnvVars environment(final RestMultiListParameterValue value) {
    EnvVars env = new EnvVars();
    value.buildEnvironment(null, env);
    return env;
  }
}
