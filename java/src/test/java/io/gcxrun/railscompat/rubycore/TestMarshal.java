package io.gcxrun.railscompat.rubycore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class TestMarshal {

  public static String serialize(String literal) throws IOException {
    Process process = Runtime.getRuntime().exec(new String[] {"ruby", "marshal.rb"});
    // Pass the literal as stdin to the Ruby script
    process.getOutputStream().write(literal.getBytes());
    process.getOutputStream().close();

    // Read the result from stdout of the Ruby script
    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
    String result = reader.readLine();
    reader.close();

    assertNotNull(result);
    return result;
  }

  private static Stream<Arguments> provideParameters() {
    return Stream.of(
        Arguments.of(true, "true"),
        Arguments.of(false, "false"),
        Arguments.of(null, "nil"),
        Arguments.of(0L, "0"),
        Arguments.of(1L, "1"),
        Arguments.of(2L, "2"),
        Arguments.of(3L, "3"),
        Arguments.of(4L, "4"),
        Arguments.of(5L, "5"),
        Arguments.of(6L, "6"),
        Arguments.of(12L, "12"),
        Arguments.of(123L, "123"),
        Arguments.of(124L, "124"),
        Arguments.of(232L, "232"),
        Arguments.of(132138561L, "132138561"),
        Arguments.of(-1L, "-1"),
        Arguments.of(-48L, "-48"),
        Arguments.of(-100L, "-100"),
        Arguments.of(-180L, "-180"),
        Arguments.of(-345L, "-345"),
        Arguments.of(":azerty", ":azerty"),
        Arguments.of(Map.of(), "{}"),
        Arguments.of(Map.of("a", "b"), "{\"a\"=>\"b\"}"));
  }

  private Object getObject(String literal) throws IOException {
    return Marshal.load(serialize(literal));
  }

  private Object getStringObject(String stringLiteral) throws IOException {
    return Marshal.load(serialize("\"" + stringLiteral + "\""));
  }

  @ParameterizedTest
  @MethodSource("provideParameters")
  public void testParametersFromMethod(Object expected, String literal) throws IOException {
    assertEquals(expected, getObject(literal));
  }

  @Test
  public void testMarshal_numbers_3() throws IOException {
    assertEquals(1695905840L, getObject("1695905840"));
  }

  @Test
  public void testMarshal_strings() throws IOException {
    assertEquals("", getStringObject(""));
    assertEquals("a very basic string", getStringObject("a very basic string"));
  }

  @Test
  public void testMarshal_hashes() throws IOException {
    assertEquals(
        Map.of("az", "qs", "b", "c", "d", "e"),
        getObject("{\"az\"=>\"qs\", \"b\"=>\"c\", \"d\"=>\"e\"}"));
    // assertEquals("BoH%",getObject("BoH%"));
  }

  @Test
  public void testMarshalInteger() {
    var o = Marshal.load("BAhpBg==");
    assertEquals(o, 1L);
  }

  @Test
  public void testMarshalString() {
    var o = Marshal.load("BAhJIgthemVydHkGOgZFVA==");
    assertEquals(o, "azerty");
  }

  @Test
  public void testMarshalSymbol() {
    var o = Marshal.load("BAg6C2F6ZXJ0eQ==");
    assertEquals(o, ":azerty");
  }

  @Test
  public void testMarshalHash() {
    var o = Marshal.load("BAh7BkkiB2F6BjoGRVRJIgdxcwY7AFQ=");
    assertEquals(o, Map.of("az", "qs"));
  }
}
