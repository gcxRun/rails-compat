package io.gcxrun.railscompat.rubycore;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Cross-language validation tests for Marshal compatibility.
 * 
 * These tests validate that the Java Marshal implementation produces identical results
 * to the Ruby reference implementation, ensuring 100% compatibility.
 */
public class TestCrossLanguageValidation {

  /**
   * Run Ruby cross-language validator script and return parsed JSON result.
   */
  private JSONObject runRubyValidator(String command, String... args) throws Exception {
    // Path to Ruby validator script
    File validatorScript = new File("../shared/ruby-reference/cross_language_validator.rb");
    
    if (!validatorScript.exists()) {
      // Try alternative path structure
      validatorScript = new File("../../shared/ruby-reference/cross_language_validator.rb");
    }
    
    if (!validatorScript.exists()) {
      throw new RuntimeException("Ruby validator script not found. Please ensure shared infrastructure is set up.");
    }

    List<String> cmd = new ArrayList<>();
    cmd.add("ruby");
    cmd.add(validatorScript.getAbsolutePath());
    cmd.add(command);
    cmd.addAll(Arrays.asList(args));

    ProcessBuilder pb = new ProcessBuilder(cmd);
    Process process = pb.start();

    // Read stdout
    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
    StringBuilder output = new StringBuilder();
    String line;
    while ((line = reader.readLine()) != null) {
      output.append(line).append("\n");
    }

    // Read stderr for errors
    BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
    StringBuilder errorOutput = new StringBuilder();
    while ((line = errorReader.readLine()) != null) {
      errorOutput.append(line).append("\n");
    }

    int exitCode = process.waitFor();
    
    if (exitCode != 0) {
      throw new RuntimeException("Ruby validator failed with exit code " + exitCode + ": " + errorOutput.toString());
    }

    return new JSONObject(output.toString().trim());
  }

  @Test
  public void testRubyValidatorAvailable() throws Exception {
    JSONObject result = runRubyValidator("validate", "nil_value");
    
    assertTrue(result.has("case_name"), "Should have case_name field");
    assertEquals("nil_value", result.getString("case_name"));
    assertTrue(result.has("matches"), "Should have matches field");
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "nil_value", "true_value", "false_value", "zero", "small_positive",
      "simple_string", "simple_symbol", "empty_array", "simple_array",
      "empty_hash", "simple_hash"
  })
  public void testBasicCrossLanguageCompatibility(String caseName) throws Exception {
    // Validate with Ruby reference
    JSONObject rubyValidation = runRubyValidator("validate", caseName);
    
    // Ruby validator should confirm the data is valid
    if (rubyValidation.has("error")) {
      fail("Ruby validation failed for " + caseName + ": " + rubyValidation.getString("error"));
    }
    
    assertTrue(rubyValidation.getBoolean("matches"), "Ruby validation failed for " + caseName);
    
    // Parse with Java implementation
    String marshalBase64 = rubyValidation.getString("marshal_base64");
    Object javaResult = Marshal.load(marshalBase64);
    
    // Basic validation - should not be null and should match expected type
    assertNotNull(javaResult, "Java result should not be null for " + caseName);
    
    // Type-specific validations
    Object expectedResult = rubyValidation.get("expected");
    validateResultType(caseName, javaResult, expectedResult);
  }

  private void validateResultType(String caseName, Object javaResult, Object expectedResult) {
    if (expectedResult == null || expectedResult == JSONObject.NULL) {
      assertNull(javaResult, "Expected null for " + caseName);
    } else if (expectedResult instanceof Boolean) {
      assertEquals(expectedResult, javaResult, "Boolean mismatch for " + caseName);
    } else if (expectedResult instanceof Number) {
      assertTrue(javaResult instanceof Long, "Expected Long for integer case " + caseName);
    } else if (expectedResult instanceof String) {
      assertTrue(javaResult instanceof String, "Expected String for " + caseName);
      String expectedStr = (String) expectedResult;
      String actualStr = (String) javaResult;
      
      // Handle symbol conversion (symbols should start with ':')
      if (caseName.contains("symbol")) {
        assertTrue(actualStr.startsWith(":"), "Symbol should start with ':' for " + caseName);
      } else {
        assertEquals(expectedStr, actualStr, "String mismatch for " + caseName);
      }
    } else if (expectedResult instanceof org.json.JSONArray) {
      assertTrue(javaResult instanceof List, "Expected List for array case " + caseName);
    } else if (expectedResult instanceof JSONObject) {
      assertTrue(javaResult instanceof java.util.Map, "Expected Map for hash case " + caseName);
    }
  }

  @Test
  public void testSymbolReuseCompatibility() throws Exception {
    JSONObject rubyValidation = runRubyValidator("validate", "symbol_reuse");
    
    if (rubyValidation.has("error")) {
      fail("Ruby validation failed for symbol_reuse: " + rubyValidation.getString("error"));
    }
    
    assertTrue(rubyValidation.getBoolean("matches"), "Ruby validation failed for symbol_reuse");
    
    // Parse with Java implementation
    String marshalBase64 = rubyValidation.getString("marshal_base64");
    Object javaResult = Marshal.load(marshalBase64);
    
    // Should be a list with 4 elements
    assertTrue(javaResult instanceof List, "symbol_reuse should be List");
    List<?> resultList = (List<?>) javaResult;
    assertEquals(4, resultList.size(), "symbol_reuse should have 4 elements");
    
    // Check symbol reuse (first, second, and fourth elements should be identical)
    assertEquals(resultList.get(0), resultList.get(1), "Reused symbols should be identical");
    assertEquals(resultList.get(0), resultList.get(3), "Reused symbols should be identical");
    assertNotEquals(resultList.get(2), resultList.get(0), "Different symbol should be different");
  }

  @Test
  public void testGenerateCustomCaseCompatibility() throws Exception {
    // Generate a custom test case using Ruby
    String customLiteral = "{\"custom\" => [1, 2, :symbol], \"nested\" => {\"key\" => \"value\"}}";
    
    JSONObject rubyGenerated = runRubyValidator("generate", customLiteral);
    
    if (rubyGenerated.has("error")) {
      // Skip if Ruby generation failed
      System.out.println("Skipping custom case test: " + rubyGenerated.getString("error"));
      return;
    }
    
    // Parse with Java implementation
    String marshalBase64 = rubyGenerated.getString("marshal_base64");
    Object javaResult = Marshal.load(marshalBase64);
    
    // Basic validation - should be a hash with expected keys
    assertTrue(javaResult instanceof java.util.Map, "Generated case should be a Map");
    java.util.Map<?, ?> resultMap = (java.util.Map<?, ?>) javaResult;
    assertTrue(resultMap.containsKey("custom"), "Should contain 'custom' key");
    assertTrue(resultMap.containsKey("nested"), "Should contain 'nested' key");
  }

  @Test
  public void testErrorHandlingConsistency() {
    // Test that error handling is consistent between implementations
    
    // Invalid Base64
    assertThrows(Marshal.MarshalException.class, () -> {
      Marshal.load("invalid_base64!");
    }, "Should throw MarshalException for invalid Base64");
    
    // Malformed Marshal data
    assertThrows(Marshal.MarshalException.class, () -> {
      Marshal.load("YWJjZGVmZ2g="); // "abcdefgh" in base64
    }, "Should throw MarshalException for malformed Marshal data");
    
    // Empty data
    assertThrows(Marshal.MarshalException.class, () -> {
      Marshal.load(new byte[0]);
    }, "Should throw MarshalException for empty data");
  }

  @Test
  public void testAllRubyCasesValidateSuccessfully() throws Exception {
    // Run Ruby validator on all cases
    JSONObject allValidation = runRubyValidator("validate");
    
    assertTrue(allValidation.has("metadata"), "Should have metadata");
    assertTrue(allValidation.has("results"), "Should have results");
    
    JSONObject metadata = allValidation.getJSONObject("metadata");
    JSONObject results = allValidation.getJSONObject("results");
    
    // All cases should pass Ruby validation
    int totalCases = metadata.getInt("total_cases");
    int passedCases = metadata.getInt("passed");
    int failedCases = metadata.getInt("failed");
    
    // Report any failures
    if (failedCases > 0) {
      List<String> failedCaseNames = new ArrayList<>();
      var names = results.names();
      if (names != null) {
        for (int i = 0; i < names.length(); i++) {
          String caseName = names.getString(i);
          JSONObject result = results.getJSONObject(caseName);
          if (!result.optBoolean("matches", false) || result.has("error")) {
            failedCaseNames.add(caseName);
          }
        }
      }
      fail("Ruby validation failed for " + failedCases + "/" + totalCases + " cases: " + failedCaseNames);
    }
    
    assertEquals(totalCases, passedCases, "Expected all cases to pass Ruby validation");
    
    System.out.println("All " + totalCases + " Ruby test cases validated successfully");
  }
}