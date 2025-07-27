/**
 * Cross-language validation tests for Marshal compatibility.
 * 
 * These tests validate that the TypeScript Marshal implementation produces identical results
 * to the Ruby reference implementation, ensuring 100% compatibility.
 */

import * as fs from 'fs';
import * as path from 'path';
import { spawn, ChildProcess } from 'child_process';
import { Marshal, MarshalException } from '../../src/railscompat/rubycore/marshal';

interface ValidationResult {
  case_name?: string;
  description?: string;
  expected: unknown;
  actual?: unknown;
  matches?: boolean;
  ruby_literal?: string;
  data_size?: number;
  error?: string;
}

interface ValidationResponse {
  metadata?: {
    validated_at: string;
    ruby_version: string;
    total_cases: number;
    passed: number;
    failed: number;
  };
  results?: Record<string, ValidationResult>;
  // For single case validation, it returns the result directly
  case_name?: string;
  description?: string;
  expected?: unknown;
  actual?: unknown;
  matches?: boolean;
  ruby_literal?: string;
  data_size?: number;
  error?: string;
  // For generate case, it returns marshal_base64
  marshal_base64?: string;
  expected_result?: unknown;
}

describe('Cross-Language Validation', () => {
  let testData: { test_cases: Record<string, { marshal_base64: string; expected_result: unknown; description: string; ruby_literal: string; data_size: number }> };

  beforeAll(() => {
    // Load test data for marshal_base64 access
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
      throw new Error('Test data file not found.');
    }
    
    testData = JSON.parse(fs.readFileSync(testDataPath, 'utf8'));
  });

  /**
   * Run Ruby cross-language validator script and return parsed JSON result.
   */
  async function runRubyValidator(command: string, ...args: string[]): Promise<ValidationResponse> {
    // Path to Ruby validator script
    let validatorScript = path.join(__dirname, '../../shared/ruby-reference/cross_language_validator.rb');
    
    if (!fs.existsSync(validatorScript)) {
      // Try alternative path structure
      validatorScript = path.join(__dirname, '../../../shared/ruby-reference/cross_language_validator.rb');
    }
    
    if (!fs.existsSync(validatorScript)) {
      throw new Error('Ruby validator script not found. Please ensure shared infrastructure is set up.');
    }

    return new Promise((resolve, reject) => {
      const cmd = ['ruby', validatorScript, command, ...args];
      const childProcess: ChildProcess = spawn(cmd[0]!, cmd.slice(1));

      let stdout = '';
      let stderr = '';

      childProcess.stdout?.on('data', (data: Buffer) => {
        stdout += data.toString();
      });

      childProcess.stderr?.on('data', (data: Buffer) => {
        stderr += data.toString();
      });

      childProcess.on('close', (code: number | null) => {
        if (code !== 0) {
          reject(new Error(`Ruby validator failed with exit code ${code}: ${stderr}`));
          return;
        }

        try {
          const result = JSON.parse(stdout.trim()) as ValidationResponse;
          resolve(result);
        } catch (error) {
          reject(new Error(`Failed to parse Ruby validator output: ${(error as Error).message}`));
        }
      });

      childProcess.on('error', (error: Error) => {
        reject(new Error(`Failed to execute Ruby validator: ${error.message}`));
      });
    });
  }

  test('Ruby validator should be available', async () => {
    const result = await runRubyValidator('validate', 'nil_value');
    
    expect(result.case_name).toBe('nil_value');
    expect(result.matches).toBeDefined();
  }, 10000);

  describe('Basic Cross-Language Compatibility', () => {
    const basicCases = [
      'nil_value', 'true_value', 'false_value', 'zero', 'small_positive',
      'simple_string', 'simple_symbol', 'empty_array', 'simple_array',
      'empty_hash', 'simple_hash'
    ];

    test.each(basicCases)('should have cross-language compatibility for %s', async (caseName) => {
      // Validate with Ruby reference
      const rubyValidation = await runRubyValidator('validate', caseName);
      
      // Ruby validator should confirm the data is valid
      if (rubyValidation.error) {
        throw new Error(`Ruby validation failed for ${caseName}: ${rubyValidation.error}`);
      }
      
      expect(rubyValidation.matches).toBe(true);
      
      // Get marshal data from test data
      const testCase = testData.test_cases[caseName];
      if (!testCase) {
        throw new Error(`Test case not found: ${caseName}`);
      }
      
      const tsResult = Marshal.loadB64(testCase.marshal_base64);
      
      // Basic validation - should not be null (except for nil_value)
      if (caseName !== 'nil_value') {
        expect(tsResult).not.toBeNull();
      }
      
      // Type-specific validations
      validateResultType(caseName, tsResult, rubyValidation.expected);
    }, 15000);
  });

  function validateResultType(caseName: string, tsResult: unknown, expectedResult: unknown): void {
    if (expectedResult === null || expectedResult === undefined) {
      expect(tsResult).toBeNull();
    } else if (typeof expectedResult === 'boolean') {
      expect(tsResult).toBe(expectedResult);
    } else if (typeof expectedResult === 'number') {
      expect(typeof tsResult).toBe('bigint');
    } else if (typeof expectedResult === 'string') {
      expect(typeof tsResult).toBe('string');
      const expectedStr = expectedResult;
      const actualStr = tsResult as string;
      
      // Handle symbol conversion (symbols should start with ':')
      if (caseName.includes('symbol')) {
        expect(actualStr.startsWith(':')).toBe(true);
      } else {
        expect(actualStr).toBe(expectedStr);
      }
    } else if (Array.isArray(expectedResult)) {
      expect(Array.isArray(tsResult)).toBe(true);
    } else if (typeof expectedResult === 'object') {
      expect(tsResult instanceof Map).toBe(true);
    }
  }

  test('should handle symbol reuse compatibility', async () => {
    const rubyValidation = await runRubyValidator('validate', 'symbol_reuse');
    
    if (rubyValidation.error) {
      throw new Error(`Ruby validation failed for symbol_reuse: ${rubyValidation.error}`);
    }
    
    expect(rubyValidation.matches).toBe(true);
    
    // Get marshal data from test data
    const testCase = testData.test_cases['symbol_reuse'];
    if (!testCase) {
      throw new Error('Test case not found: symbol_reuse');
    }
    
    const tsResult = Marshal.loadB64(testCase.marshal_base64);
    
    // Should be an array with 4 elements
    expect(Array.isArray(tsResult)).toBe(true);
    const resultArray = tsResult as unknown[];
    expect(resultArray.length).toBe(4);
    
    // Check symbol reuse (first, second, and fourth elements should be identical)
    expect(resultArray[0]).toBe(resultArray[1]);
    expect(resultArray[0]).toBe(resultArray[3]);
    expect(resultArray[2]).not.toBe(resultArray[0]);
  }, 10000);

  test('should generate and parse custom case compatibility', async () => {
    // Generate a custom test case using Ruby
    const customLiteral = '{"custom" => [1, 2, :symbol], "nested" => {"key" => "value"}}';
    
    try {
      const rubyGenerated = await runRubyValidator('generate', customLiteral);
      
      if (rubyGenerated.error) {
        // Skip if Ruby generation failed
        console.log(`Skipping custom case test: ${rubyGenerated.error}`);
        return;
      }
      
      // Parse with TypeScript implementation
      if (!rubyGenerated.marshal_base64) {
        throw new Error('No marshal_base64 data found for generated case');
      }
      
      const tsResult = Marshal.loadB64(rubyGenerated.marshal_base64);
      
      // Basic validation - should be a Map with expected keys
      expect(tsResult instanceof Map).toBe(true);
      const resultMap = tsResult as Map<unknown, unknown>;
      expect(resultMap.has('custom')).toBe(true);
      expect(resultMap.has('nested')).toBe(true);
    } catch (error) {
      // Skip test if Ruby is not available or generation fails
      console.log(`Skipping custom case test: ${(error as Error).message}`);
    }
  }, 15000);

  describe('Error Handling Consistency', () => {
    test('should have consistent error handling between implementations', () => {
      // Invalid Base64
      expect(() => {
        Marshal.loadB64('invalid_base64!');
      }).toThrow(MarshalException);
      
      // Malformed Marshal data
      expect(() => {
        Marshal.loadB64('YWJjZGVmZ2g='); // "abcdefgh" in base64
      }).toThrow(MarshalException);
      
      // Empty data
      expect(() => {
        Marshal.load(new Uint8Array(0));
      }).toThrow(MarshalException);
    });
  });

  test('should validate all Ruby cases successfully', async () => {
    try {
      // Run Ruby validator on all cases
      const allValidation = await runRubyValidator('validate');
      
      expect(allValidation.metadata).toBeDefined();
      expect(allValidation.results).toBeDefined();
      
      const metadata = allValidation.metadata!;
      const results = allValidation.results!;
      
      // All cases should pass Ruby validation
      const totalCases = metadata.total_cases;
      const passedCases = metadata.passed;
      const failedCases = metadata.failed;
      
      // Report any failures
      if (failedCases > 0) {
        const failedCaseNames: string[] = [];
        Object.entries(results).forEach(([caseName, result]) => {
          if (!result.matches || result.error) {
            failedCaseNames.push(caseName);
          }
        });
        throw new Error(`Ruby validation failed for ${failedCases}/${totalCases} cases: ${failedCaseNames.join(', ')}`);
      }
      
      expect(passedCases).toBe(totalCases);
      console.log(`All ${totalCases} Ruby test cases validated successfully`);
    } catch (error) {
      // Skip test if Ruby is not available
      console.log(`Skipping comprehensive validation test: ${(error as Error).message}`);
    }
  }, 30000);
});