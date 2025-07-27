/**
 * Tests for Ruby Marshal format parser.
 * 
 * This module provides comprehensive tests for the Marshal class that parses Ruby Marshal format
 * data for cross-language compatibility with Rails applications. Tests use pre-generated test data
 * from Ruby reference implementation to ensure 100% compatibility.
 */

import * as fs from 'fs';
import * as path from 'path';
import { Marshal, MarshalException, RubyType, ObjectWrapper } from '../../src/railscompat/rubycore/marshal';

interface TestCase {
  description: string;
  ruby_literal: string;
  marshal_base64: string;
  expected_result: unknown;
  data_size: number;
}

interface TestData {
  metadata: {
    generated_at: string;
    ruby_version: string;
    marshal_version: string;
    description: string;
  };
  test_cases: Record<string, TestCase>;
}

describe('Marshal', () => {
  let testData: TestData;

  beforeAll(() => {
    // Load pre-generated test data from Ruby reference implementation
    // Try multiple possible locations
    const possiblePaths = [
      path.join(__dirname, '../../shared/test-data/marshal_test_cases.json'),
      path.join(__dirname, '../../../shared/test-data/marshal_test_cases.json'),
      path.join(__dirname, '../../../../shared/test-data/marshal_test_cases.json'),
      path.resolve(process.cwd(), '../shared/test-data/marshal_test_cases.json'),
    ];
    
    let testDataPath: string | null = null;
    for (const possiblePath of possiblePaths) {
      if (fs.existsSync(possiblePath)) {
        testDataPath = possiblePath;
        break;
      }
    }
    
    if (!testDataPath) {
      const searchedPaths = possiblePaths.map(p => `  - ${p}`).join('\n');
      throw new Error(`Test data file not found. Searched in:\n${searchedPaths}\nPlease run the Ruby test data generator first.`);
    }
    
    testData = JSON.parse(fs.readFileSync(testDataPath, 'utf8')) as TestData;

    console.log(`Loaded ${Object.keys(testData.test_cases).length} test cases from Ruby reference data`);
  });

  describe('Basic Types', () => {
    const basicTypes = ['nil_value', 'true_value', 'false_value'];

    test.each(basicTypes)('should parse %s correctly', (caseName) => {
      if (!testData.test_cases[caseName]) {
        throw new Error(`Test case not found: ${caseName}`);
      }

      const testCase = testData.test_cases[caseName];
      const result = Marshal.loadB64(testCase.marshal_base64);
      expect(result).toBe(testCase.expected_result);
    });
  });

  describe('Integers', () => {
    const integerTypes = [
      'zero', 'small_positive', 'small_positive_2', 'small_positive_122',
      'small_positive_123', 'small_positive_255', 'medium_positive',
      'large_positive', 'very_large_positive', 'small_negative',
      'small_negative_2', 'small_negative_123', 'medium_negative', 'large_negative'
    ];

    test.each(integerTypes)('should parse %s correctly', (caseName) => {
      if (!testData.test_cases[caseName]) {
        throw new Error(`Test case not found: ${caseName}`);
      }

      const testCase = testData.test_cases[caseName];
      const result = Marshal.loadB64(testCase.marshal_base64);
      expect(typeof result).toBe('bigint');
      expect(result).toBe(BigInt(testCase.expected_result as number));
    });
  });

  describe('Bignums', () => {
    test('should parse bignum_positive correctly', () => {
      const testCase = testData.test_cases['bignum_positive'];
      if (!testCase) {
        throw new Error('bignum_positive test case not found');
      }

      const result = Marshal.loadB64(testCase.marshal_base64);
      // Expected value is 2**100 = 1267650600228229401496703205376
      const expected = 1267650600228229401496703205376n;
      expect(result).toBe(expected);
    });

    test('should parse bignum_negative correctly', () => {
      const testCase = testData.test_cases['bignum_negative'];
      if (!testCase) {
        throw new Error('bignum_negative test case not found');
      }

      const result = Marshal.loadB64(testCase.marshal_base64);
      // Expected value is -(2**100) = -1267650600228229401496703205376
      const expected = -1267650600228229401496703205376n;
      expect(result).toBe(expected);
    });
  });

  describe('Strings', () => {
    const stringTypes = [
      'empty_string', 'simple_string', 'string_with_spaces', 'long_string',
      'unicode_string', 'string_with_quotes', 'string_with_newlines'
    ];

    test.each(stringTypes)('should parse %s correctly', (caseName) => {
      if (!testData.test_cases[caseName]) {
        throw new Error(`Test case not found: ${caseName}`);
      }

      const testCase = testData.test_cases[caseName];
      const result = Marshal.loadB64(testCase.marshal_base64);
      expect(typeof result).toBe('string');
      expect(result).toBe(testCase.expected_result);
    });
  });

  describe('Symbols', () => {
    const symbolTypes = [
      'simple_symbol', 'symbol_azerty', 'complex_symbol',
      'symbol_with_underscore', 'symbol_with_numbers'
    ];

    test.each(symbolTypes)('should parse %s correctly', (caseName) => {
      if (!testData.test_cases[caseName]) {
        throw new Error(`Test case not found: ${caseName}`);
      }

      const testCase = testData.test_cases[caseName];
      const result = Marshal.loadB64(testCase.marshal_base64);
      expect(typeof result).toBe('string');
      expect((result as string).startsWith(':')).toBe(true);

      // Expected result might be a symbol representation, convert for comparison
      let expected = String(testCase.expected_result);
      if (!expected.startsWith(':')) {
        expected = ':' + expected;
      }
      expect(result).toBe(expected);
    });
  });

  describe('Arrays', () => {
    const arrayTypes = [
      'empty_array', 'single_element_array', 'simple_array', 'mixed_array',
      'nested_array', 'array_with_hash', 'deep_nested_array'
    ];

    test.each(arrayTypes)('should parse %s correctly', (caseName) => {
      if (!testData.test_cases[caseName]) {
        throw new Error(`Test case not found: ${caseName}`);
      }

      const testCase = testData.test_cases[caseName];
      const result = Marshal.loadB64(testCase.marshal_base64);
      expect(Array.isArray(result)).toBe(true);

      const resultArray = result as unknown[];
      const expectedArray = testCase.expected_result as unknown[];
      expect(resultArray.length).toBe(expectedArray.length);
    });
  });

  describe('Hashes', () => {
    const hashTypes = [
      'empty_hash', 'single_pair_hash', 'simple_hash', 'hash_az_qs',
      'complex_hash', 'symbol_keys_hash', 'mixed_keys_hash', 'nested_hash', 'hash_with_array'
    ];

    test.each(hashTypes)('should parse %s correctly', (caseName) => {
      if (!testData.test_cases[caseName]) {
        throw new Error(`Test case not found: ${caseName}`);
      }

      const testCase = testData.test_cases[caseName];
      const result = Marshal.loadB64(testCase.marshal_base64);
      expect(result instanceof Map).toBe(true);

      const resultMap = result as Map<unknown, unknown>;
      const expectedMap = testCase.expected_result as Record<string, unknown>;
      expect(resultMap.size).toBe(Object.keys(expectedMap).length);
    });
  });

  describe('Rails Sessions', () => {
    const sessionCases = ['rails_session_simple', 'rails_session_complex'];

    test.each(sessionCases)('should parse %s correctly', (caseName) => {
      if (!testData.test_cases[caseName]) {
        throw new Error(`Test case not found: ${caseName}`);
      }

      const testCase = testData.test_cases[caseName];
      const result = Marshal.loadB64(testCase.marshal_base64);
      expect(result instanceof Map).toBe(true);

      const sessionMap = result as Map<unknown, unknown>;
      expect(sessionMap.has('session_id')).toBe(true);
    });
  });

  describe('Complex Structures', () => {
    const complexCases = ['deeply_nested', 'mixed_complex'];

    test.each(complexCases)('should parse %s correctly', (caseName) => {
      if (!testData.test_cases[caseName]) {
        throw new Error(`Test case not found: ${caseName}`);
      }

      const testCase = testData.test_cases[caseName];
      const result = Marshal.loadB64(testCase.marshal_base64);
      expect(result).not.toBeNull();
      expect(result instanceof Map).toBe(true);
    });
  });

  describe('Symbol Reuse', () => {
    test('should handle symbol reuse correctly', () => {
      const testCase = testData.test_cases['symbol_reuse'];
      if (!testCase) {
        throw new Error('symbol_reuse test case not found');
      }

      const result = Marshal.loadB64(testCase.marshal_base64);
      expect(Array.isArray(result)).toBe(true);

      const resultArray = result as unknown[];
      expect(resultArray.length).toBe(4);

      // Check symbol reuse (first, second, and fourth elements should be identical)
      expect(resultArray[0]).toBe(resultArray[1]);
      expect(resultArray[0]).toBe(resultArray[3]);
      expect(resultArray[2]).not.toBe(resultArray[0]);
    });
  });

  describe('Error Handling', () => {
    test('should throw MarshalException for invalid Base64 input', () => {
      expect(() => {
        Marshal.loadB64('invalid_base64_data!');
      }).toThrow(MarshalException);
    });

    test('should throw MarshalException for malformed Marshal data', () => {
      expect(() => {
        // Valid Base64 but invalid Marshal data
        Marshal.loadB64('YWJjZGVmZ2g='); // "abcdefgh" in base64
      }).toThrow(MarshalException);
    });

    test('should throw MarshalException for empty data', () => {
      expect(() => {
        Marshal.load(new Uint8Array(0));
      }).toThrow(MarshalException);

      expect(() => {
        Marshal.loadB64('');
      }).toThrow(MarshalException);
    });

    test('should throw MarshalException for null input', () => {
      expect(() => {
        Marshal.load(null as any);
      }).toThrow(MarshalException);

      expect(() => {
        Marshal.loadB64(null as any);
      }).toThrow(MarshalException);
    });
  });

  describe('Known Marshal Data', () => {
    test('should parse known Marshal integer', () => {
      const result = Marshal.loadB64('BAhpBg==');
      expect(result).toBe(1n);
    });

    test('should parse known Marshal string', () => {
      const result = Marshal.loadB64('BAhJIgthemVydHkGOgZFVA==');
      expect(result).toBe('azerty');
    });

    test('should parse known Marshal symbol', () => {
      const result = Marshal.loadB64('BAg6C2F6ZXJ0eQ==');
      expect(result).toBe(':azerty');
    });

    test('should parse known Marshal hash', () => {
      const result = Marshal.loadB64('BAh7BkkiB2F6BjoGRVRJIgdxcwY7AFQ=');
      expect(result instanceof Map).toBe(true);
      const resultMap = result as Map<string, string>;
      expect(resultMap.get('az')).toBe('qs');
    });
  });

  describe('Type System', () => {
    test('should have correct RubyType enum values', () => {
      expect(RubyType.NIL).toBe(48);
      expect(RubyType.TRUE).toBe(84);
      expect(RubyType.FALSE).toBe(70);
      expect(RubyType.INT).toBe(105);
      expect(RubyType.STRING).toBe(34);
      expect(RubyType.SYMBOL).toBe(58);
      expect(RubyType.ARRAY).toBe(91);
      expect(RubyType.HASH).toBe(123);
    });

    test('should create ObjectWrapper correctly', () => {
      const wrapper = ObjectWrapper.wrap(RubyType.OBJECT, 'test');
      expect(wrapper.rubyType).toBe(RubyType.OBJECT);
      expect(wrapper.obj).toBe('test');
      expect(wrapper.children).toEqual([]);

      wrapper.add('child1');
      wrapper.add('child2');
      expect(wrapper.children).toEqual(['child1', 'child2']);
    });
  });

  describe('Large Data Handling', () => {
    test('should handle large data structures', () => {
      // Find the largest test case
      let largestCase = 'mixed_complex';
      let maxSize = 0;
      
      Object.entries(testData.test_cases).forEach(([caseName, testCase]) => {
        if (testCase.data_size > maxSize) {
          maxSize = testCase.data_size;
          largestCase = caseName;
        }
      });

      const testCase = testData.test_cases[largestCase];
      if (!testCase) {
        throw new Error(`Test case not found: ${largestCase}`);
      }

      const result = Marshal.loadB64(testCase.marshal_base64);
      expect(result).not.toBeNull();

      console.log(`Successfully parsed largest case: ${largestCase} (${testCase.data_size} bytes)`);
    });
  });

  describe('Comprehensive Parsing', () => {
    test('should parse all test cases successfully', () => {
      const totalCases = Object.keys(testData.test_cases).length;
      let successfulCases = 0;
      const failures: string[] = [];

      Object.entries(testData.test_cases).forEach(([caseName, testCase]) => {
        try {
          const result = Marshal.loadB64(testCase.marshal_base64);
          // nil_value should actually be null, so don't assert not null for it
          if (caseName !== 'nil_value') {
            expect(result).not.toBeNull();
          }
          successfulCases++;
        } catch (error) {
          failures.push(`${caseName}: ${(error as Error).message}`);
        }
      });

      if (failures.length > 0) {
        throw new Error(`Failed to parse ${failures.length}/${totalCases} cases: ${failures.join(', ')}`);
      }

      expect(successfulCases).toBe(totalCases);
      console.log(`Successfully parsed all ${totalCases} test cases from Ruby reference data`);
    });
  });
});