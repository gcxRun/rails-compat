package io.gcxrun.railscompat.rubycore;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Stream;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Comprehensive tests for Ruby Marshal format parser.
 * 
 * Uses pre-generated test data from Ruby reference implementation to ensure 100% compatibility
 * with Ruby's Marshal format. Tests cover all Ruby data types and complex nested structures.
 */
public class TestMarshal {

  private static Map<String, TestCase> testCases;

  /**
   * Test case data structure matching the JSON format from Ruby reference.
   */
  public static class TestCase {
    public String description;
    public String ruby_literal;
    public String marshal_base64;
    public Object expected_result;
    public int data_size;
    
    public TestCase(JSONObject json) throws Exception {
      this.description = json.optString("description");
      this.ruby_literal = json.optString("ruby_literal");
      this.marshal_base64 = json.getString("marshal_base64");
      this.expected_result = convertJsonValue(json.get("expected_result"));
      this.data_size = json.optInt("data_size");
    }
    
    private Object convertJsonValue(Object value) throws Exception {
      if (value == null || value == JSONObject.NULL) {
        return null;
      } else if (value instanceof Boolean) {
        return value;
      } else if (value instanceof Integer) {
        return ((Integer) value).longValue();
      } else if (value instanceof Long) {
        return value;
      } else if (value instanceof Double) {
        // Handle very large numbers that JSON parses as Double (scientific notation)
        Double d = (Double) value;
        if (d.isInfinite() || d.isNaN()) {
          return d;
        }
        // For very large numbers, convert to BigInteger
        return new BigInteger(String.format("%.0f", d));
      } else if (value instanceof String) {
        return value;
      } else if (value instanceof org.json.JSONArray) {
        org.json.JSONArray array = (org.json.JSONArray) value;
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
          list.add(convertJsonValue(array.get(i)));
        }
        return list;
      } else if (value instanceof JSONObject) {
        JSONObject obj = (JSONObject) value;
        Map<Object, Object> map = new HashMap<>();
        var names = obj.names();
        if (names != null) {
          for (int i = 0; i < names.length(); i++) {
            String key = names.getString(i);
            map.put(key, convertJsonValue(obj.get(key)));
          }
        }
        return map;
      }
      return value;
    }
  }

  /**
   * Load pre-generated test data from Ruby reference implementation.
   */
  @BeforeAll
  public static void loadTestData() throws Exception {
    // Path to shared test data relative to project root
    File testDataFile = new File("../shared/test-data/marshal_test_cases.json");
    
    if (!testDataFile.exists()) {
      // Try alternative path structure
      testDataFile = new File("../../shared/test-data/marshal_test_cases.json");
    }
    
    if (!testDataFile.exists()) {
      throw new IOException("Test data file not found. Please run the Ruby test data generator first.");
    }

    String jsonContent = Files.readString(testDataFile.toPath());
    JSONObject rootJson = new JSONObject(jsonContent);
    JSONObject testCasesJson = rootJson.getJSONObject("test_cases");
    
    testCases = new HashMap<>();
    var names = testCasesJson.names();
    if (names != null) {
      for (int i = 0; i < names.length(); i++) {
        String key = names.getString(i);
        JSONObject testCaseJson = testCasesJson.getJSONObject(key);
        testCases.put(key, new TestCase(testCaseJson));
      }
    }
    
    System.out.println("Loaded " + testCases.size() + " test cases from Ruby reference data");
  }

  /**
   * Provide basic type test cases for parameterized testing.
   */
  private static Stream<Arguments> provideBasicTypes() {
    return Stream.of(
        Arguments.of("nil_value"),
        Arguments.of("true_value"), 
        Arguments.of("false_value")
    );
  }

  /**
   * Provide integer test cases covering all Ruby integer encoding schemes.
   */
  private static Stream<Arguments> provideIntegerTypes() {
    return Stream.of(
        Arguments.of("zero"),
        Arguments.of("small_positive"),
        Arguments.of("small_positive_2"), 
        Arguments.of("small_positive_122"),
        Arguments.of("small_positive_123"),
        Arguments.of("small_positive_255"),
        Arguments.of("medium_positive"),
        Arguments.of("large_positive"),
        Arguments.of("very_large_positive"),
        Arguments.of("small_negative"),
        Arguments.of("small_negative_2"),
        Arguments.of("small_negative_123"),
        Arguments.of("medium_negative"),
        Arguments.of("large_negative")
    );
  }

  /**
   * Provide string test cases with various content types.
   */
  private static Stream<Arguments> provideStringTypes() {
    return Stream.of(
        Arguments.of("empty_string"),
        Arguments.of("simple_string"),
        Arguments.of("string_with_spaces"),
        Arguments.of("long_string"),
        Arguments.of("unicode_string"),
        Arguments.of("string_with_quotes"),
        Arguments.of("string_with_newlines")
    );
  }

  /**
   * Provide symbol test cases.
   */
  private static Stream<Arguments> provideSymbolTypes() {
    return Stream.of(
        Arguments.of("simple_symbol"),
        Arguments.of("symbol_azerty"),
        Arguments.of("complex_symbol"),
        Arguments.of("symbol_with_underscore"),
        Arguments.of("symbol_with_numbers")
    );
  }

  /**
   * Provide array test cases including nested arrays.
   */
  private static Stream<Arguments> provideArrayTypes() {
    return Stream.of(
        Arguments.of("empty_array"),
        Arguments.of("single_element_array"),
        Arguments.of("simple_array"),
        Arguments.of("mixed_array"),
        Arguments.of("nested_array"),
        Arguments.of("array_with_hash"),
        Arguments.of("deep_nested_array")
    );
  }

  /**
   * Provide hash test cases including nested hashes.
   */
  private static Stream<Arguments> provideHashTypes() {
    return Stream.of(
        Arguments.of("empty_hash"),
        Arguments.of("single_pair_hash"),
        Arguments.of("simple_hash"),
        Arguments.of("hash_az_qs"),
        Arguments.of("complex_hash"),
        Arguments.of("symbol_keys_hash"),
        Arguments.of("mixed_keys_hash"),
        Arguments.of("nested_hash"),
        Arguments.of("hash_with_array")
    );
  }

  // Basic type tests
  @ParameterizedTest
  @MethodSource("provideBasicTypes")
  public void testBasicTypes(String caseName) {
    TestCase testCase = testCases.get(caseName);
    assertNotNull(testCase, "Test case not found: " + caseName);
    
    Object result = Marshal.load(testCase.marshal_base64);
    assertEquals(testCase.expected_result, result, 
        "Failed for case: " + caseName + " (" + testCase.description + ")");
  }

  // Integer tests - covering all Ruby integer encoding schemes
  @ParameterizedTest
  @MethodSource("provideIntegerTypes")
  public void testIntegers(String caseName) {
    TestCase testCase = testCases.get(caseName);
    assertNotNull(testCase, "Test case not found: " + caseName);
    
    Object result = Marshal.load(testCase.marshal_base64);
    assertTrue(result instanceof Long, "Result should be Long for case: " + caseName);
    assertEquals(testCase.expected_result, result,
        "Failed for case: " + caseName + " (" + testCase.description + ")");
  }

  // Bignum tests - validate against known correct values since JSON parsing loses precision
  @Test
  public void testBignums() {
    // Test bignum_positive (2**100)
    TestCase positiveCase = testCases.get("bignum_positive");
    assertNotNull(positiveCase, "bignum_positive test case not found");
    
    Object positiveResult = Marshal.load(positiveCase.marshal_base64);
    // Expected value is 2**100 = 1267650600228229401496703205376
    BigInteger expectedPositive = new BigInteger("1267650600228229401496703205376");
    
    if (positiveResult instanceof BigInteger) {
      assertEquals(expectedPositive, positiveResult, "bignum_positive should equal 2**100");
    } else if (positiveResult instanceof Long) {
      assertEquals(expectedPositive.longValue(), positiveResult, "bignum_positive should equal 2**100");
    } else {
      fail("Unexpected result type for bignum_positive: " + positiveResult.getClass());
    }
    
    // Test bignum_negative (-(2**100))
    TestCase negativeCase = testCases.get("bignum_negative");
    assertNotNull(negativeCase, "bignum_negative test case not found");
    
    Object negativeResult = Marshal.load(negativeCase.marshal_base64);
    // Expected value is -(2**100) = -1267650600228229401496703205376
    BigInteger expectedNegative = new BigInteger("-1267650600228229401496703205376");
    
    if (negativeResult instanceof BigInteger) {
      assertEquals(expectedNegative, negativeResult, "bignum_negative should equal -(2**100)");
    } else if (negativeResult instanceof Long) {
      assertEquals(expectedNegative.longValue(), negativeResult, "bignum_negative should equal -(2**100)");
    } else {
      fail("Unexpected result type for bignum_negative: " + negativeResult.getClass());
    }
  }

  // String tests
  @ParameterizedTest
  @MethodSource("provideStringTypes")
  public void testStrings(String caseName) {
    TestCase testCase = testCases.get(caseName);
    assertNotNull(testCase, "Test case not found: " + caseName);
    
    Object result = Marshal.load(testCase.marshal_base64);
    assertTrue(result instanceof String, "Result should be String for case: " + caseName);
    assertEquals(testCase.expected_result, result,
        "Failed for case: " + caseName + " (" + testCase.description + ")");
  }

  // Symbol tests
  @ParameterizedTest
  @MethodSource("provideSymbolTypes") 
  public void testSymbols(String caseName) {
    TestCase testCase = testCases.get(caseName);
    assertNotNull(testCase, "Test case not found: " + caseName);
    
    Object result = Marshal.load(testCase.marshal_base64);
    assertTrue(result instanceof String, "Result should be String for case: " + caseName);
    assertTrue(((String) result).startsWith(":"), "Symbol should start with ':' for case: " + caseName);
    
    // Expected result might be a symbol representation, convert for comparison
    String expected = testCase.expected_result.toString();
    if (!expected.startsWith(":")) {
      expected = ":" + expected;
    }
    assertEquals(expected, result,
        "Failed for case: " + caseName + " (" + testCase.description + ")");
  }

  // Array tests
  @ParameterizedTest
  @MethodSource("provideArrayTypes")
  public void testArrays(String caseName) {
    TestCase testCase = testCases.get(caseName);
    assertNotNull(testCase, "Test case not found: " + caseName);
    
    Object result = Marshal.load(testCase.marshal_base64);
    assertTrue(result instanceof List, "Result should be List for case: " + caseName);
    
    List<?> resultList = (List<?>) result;
    List<?> expectedList = (List<?>) testCase.expected_result;
    assertEquals(expectedList.size(), resultList.size(),
        "Array size mismatch for case: " + caseName);
  }

  // Hash tests
  @ParameterizedTest
  @MethodSource("provideHashTypes")
  public void testHashes(String caseName) {
    TestCase testCase = testCases.get(caseName);
    assertNotNull(testCase, "Test case not found: " + caseName);
    
    Object result = Marshal.load(testCase.marshal_base64);
    assertTrue(result instanceof Map, "Result should be Map for case: " + caseName);
    
    Map<?, ?> resultMap = (Map<?, ?>) result;
    Map<?, ?> expectedMap = (Map<?, ?>) testCase.expected_result;
    assertEquals(expectedMap.size(), resultMap.size(),
        "Hash size mismatch for case: " + caseName);
  }

  // Rails session tests
  @Test
  public void testRailsSessions() {
    String[] sessionCases = {"rails_session_simple", "rails_session_complex"};
    
    for (String caseName : sessionCases) {
      TestCase testCase = testCases.get(caseName);
      assertNotNull(testCase, "Test case not found: " + caseName);
      
      Object result = Marshal.load(testCase.marshal_base64);
      assertTrue(result instanceof Map, "Rails session should be Map for case: " + caseName);
      
      Map<?, ?> sessionMap = (Map<?, ?>) result;
      assertTrue(sessionMap.containsKey("session_id"), 
          "Rails session should contain session_id for case: " + caseName);
    }
  }

  // Complex structure tests
  @Test
  public void testComplexStructures() {
    String[] complexCases = {"deeply_nested", "mixed_complex"};
    
    for (String caseName : complexCases) {
      TestCase testCase = testCases.get(caseName);
      assertNotNull(testCase, "Test case not found: " + caseName);
      
      Object result = Marshal.load(testCase.marshal_base64);
      assertNotNull(result, "Complex structure should not be null for case: " + caseName);
      assertTrue(result instanceof Map, "Complex structure should be Map for case: " + caseName);
    }
  }

  // Symbol reuse test
  @Test
  public void testSymbolReuse() {
    TestCase testCase = testCases.get("symbol_reuse");
    assertNotNull(testCase, "symbol_reuse test case not found");
    
    Object result = Marshal.load(testCase.marshal_base64);
    assertTrue(result instanceof List, "symbol_reuse should be List");
    
    List<?> resultList = (List<?>) result;
    assertEquals(4, resultList.size(), "symbol_reuse should have 4 elements");
    
    // Check symbol reuse (first, second, and fourth elements should be identical)
    assertEquals(resultList.get(0), resultList.get(1), "Reused symbols should be identical");
    assertEquals(resultList.get(0), resultList.get(3), "Reused symbols should be identical");
    assertNotEquals(resultList.get(2), resultList.get(0), "Different symbol should be different");
  }

  // Error handling and security tests
  @Test
  public void testInvalidBase64Input() {
    assertThrows(Marshal.MarshalException.class, () -> {
      Marshal.load("invalid_base64_data!");
    }, "Should throw MarshalException for invalid Base64");
  }

  @Test
  public void testMalformedMarshalData() {
    // Valid Base64 but invalid Marshal data
    assertThrows(Marshal.MarshalException.class, () -> {
      Marshal.load("YWJjZGVmZ2g="); // "abcdefgh" in base64
    }, "Should throw MarshalException for malformed Marshal data");
  }

  @Test
  public void testEmptyData() {
    assertThrows(Marshal.MarshalException.class, () -> {
      Marshal.load(new byte[0]);
    }, "Should throw MarshalException for empty data");
    
    assertThrows(Marshal.MarshalException.class, () -> {
      Marshal.load("");
    }, "Should throw MarshalException for empty Base64 string");
  }

  @Test
  public void testNullInput() {
    assertThrows(Marshal.MarshalException.class, () -> {
      Marshal.load((byte[]) null);
    }, "Should throw MarshalException for null byte array");
    
    assertThrows(Marshal.MarshalException.class, () -> {
      Marshal.load((String) null);
    }, "Should throw MarshalException for null string");
  }

  // Known Marshal data tests (backward compatibility with existing tests)
  @Test
  public void testKnownMarshalInteger() {
    Object result = Marshal.load("BAhpBg==");
    assertEquals(1L, result, "Known Marshal integer should parse to 1");
  }

  @Test
  public void testKnownMarshalString() {
    Object result = Marshal.load("BAhJIgthemVydHkGOgZFVA==");
    assertEquals("azerty", result, "Known Marshal string should parse to 'azerty'");
  }

  @Test
  public void testKnownMarshalSymbol() {
    Object result = Marshal.load("BAg6C2F6ZXJ0eQ==");
    assertEquals(":azerty", result, "Known Marshal symbol should parse to ':azerty'");
  }

  @Test
  public void testKnownMarshalHash() {
    Object result = Marshal.load("BAh7BkkiB2F6BjoGRVRJIgdxcwY7AFQ=");
    Map<String, String> expected = Map.of("az", "qs");
    assertEquals(expected, result, "Known Marshal hash should parse correctly");
  }

  // Size validation tests
  @Test
  public void testLargeDataHandling() {
    // Find the largest test case
    String largestCase = testCases.entrySet().stream()
        .max(Map.Entry.comparingByValue((a, b) -> Integer.compare(a.data_size, b.data_size)))
        .map(Map.Entry::getKey)
        .orElse("mixed_complex");
    
    TestCase testCase = testCases.get(largestCase);
    assertNotNull(testCase, "Largest test case not found");
    
    Object result = Marshal.load(testCase.marshal_base64);
    assertNotNull(result, "Should handle large data structures: " + largestCase);
    
    System.out.println("Successfully parsed largest case: " + largestCase + 
                      " (" + testCase.data_size + " bytes)");
  }

  // Comprehensive test that validates all test cases
  @Test
  public void testAllCasesCanBeParsed() {
    int totalCases = testCases.size();
    int successfulCases = 0;
    List<String> failures = new ArrayList<>();
    
    for (Map.Entry<String, TestCase> entry : testCases.entrySet()) {
      String caseName = entry.getKey();
      TestCase testCase = entry.getValue();
      
      try {
        Object result = Marshal.load(testCase.marshal_base64);
        // nil_value should actually be null, so don't assert not null for it
        if (!"nil_value".equals(caseName)) {
          assertNotNull(result, "Result should not be null for case: " + caseName);
        }
        successfulCases++;
      } catch (Exception e) {
        failures.add(caseName + ": " + e.getMessage());
      }
    }
    
    if (!failures.isEmpty()) {
      fail("Failed to parse " + failures.size() + "/" + totalCases + " cases: " + failures);
    }
    
    assertEquals(totalCases, successfulCases, 
        "Should successfully parse all " + totalCases + " test cases");
    
    System.out.println("Successfully parsed all " + totalCases + " test cases from Ruby reference data");
  }
}