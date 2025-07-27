#!/usr/bin/env ruby

require 'base64'
require 'json'
require 'time'

# Cross-language validation tool for Marshal implementations
# This tool can be used by both Java and Python test suites to validate
# that their implementations produce identical results

class CrossLanguageValidator
  def initialize
    @test_cases = load_test_data
  end

  def load_test_data
    data_file = File.join(File.dirname(__FILE__), '..', 'test-data', 'marshal_test_cases.json')
    JSON.parse(File.read(data_file))['test_cases']
  end

  # Validate a specific test case by name
  def validate_case(case_name)
    unless @test_cases.key?(case_name)
      return { error: "Test case '#{case_name}' not found" }
    end

    test_case = @test_cases[case_name]
    expected = test_case['expected_result']
    marshal_data = Base64.strict_decode64(test_case['marshal_base64'])
    
    begin
      actual = Marshal.load(marshal_data)
      
      # Special handling for symbol test cases
      # In test data, symbols are stored as plain strings, but Ruby Marshal.load returns Symbol objects
      # and Java/Python should convert them to strings with ':' prefix
      is_symbol_case = test_case['ruby_literal']&.start_with?(':')
      if is_symbol_case && expected.is_a?(String)
        if actual.is_a?(Symbol)
          # Ruby returns Symbol, compare with expected string 
          matches = actual.to_s == expected
        elsif actual.is_a?(String)
          # Java/Python should return string with ':' prefix
          matches = actual == ":#{expected}"
        else
          matches = false
        end
      else
        matches = deep_equal?(expected, actual)
      end
      
      {
        case_name: case_name,
        description: test_case['description'],
        expected: expected,
        actual: actual,
        matches: matches,
        ruby_literal: test_case['ruby_literal'],
        data_size: test_case['data_size']
      }
    rescue => e
      {
        case_name: case_name,
        error: "Marshal.load failed: #{e.message}",
        expected: expected,
        ruby_literal: test_case['ruby_literal']
      }
    end
  end

  # Validate all test cases
  def validate_all
    results = {}
    
    @test_cases.each do |case_name, _|
      results[case_name] = validate_case(case_name)
    end
    
    {
      metadata: {
        validated_at: Time.now.iso8601,
        ruby_version: RUBY_VERSION,
        total_cases: results.size,
        passed: results.count { |_, r| r[:matches] == true },
        failed: results.count { |_, r| r[:matches] == false || r[:error] }
      },
      results: results
    }
  end

  # Generate a specific test case for external validation
  def generate_case(ruby_literal)
    begin
      ruby_object = eval(ruby_literal)
      marshal_data = Marshal.dump(ruby_object)
      
      {
        ruby_literal: ruby_literal,
        expected_result: ruby_object,
        marshal_base64: Base64.strict_encode64(marshal_data),
        data_size: marshal_data.length
      }
    rescue => e
      {
        ruby_literal: ruby_literal,
        error: "Failed to generate: #{e.message}"
      }
    end
  end

  private

  # Deep equality comparison that handles Ruby-specific types
  def deep_equal?(expected, actual)
    case expected
    when nil
      actual.nil?
    when true, false
      expected == actual
    when Integer
      actual.is_a?(Integer) && expected == actual
    when String
      if actual.is_a?(String)
        expected == actual
      elsif actual.is_a?(Symbol)
        # Expected is string, actual is symbol - compare symbol name with expected
        expected == actual.to_s
      else
        false
      end
    when Symbol
      # Symbols should be converted to string with ':' prefix
      actual.is_a?(String) && actual == ":#{expected}"
    when Array
      return false unless actual.is_a?(Array) && expected.size == actual.size
      expected.zip(actual).all? { |e, a| deep_equal?(e, a) }
    when Hash
      return false unless actual.is_a?(Hash) && expected.size == actual.size
      expected.all? do |key, value|
        # Find the matching key in actual hash
        matching_key = if key.is_a?(Symbol)
          # Expected key is symbol, look for symbol key in actual
          actual.keys.find { |k| k.is_a?(Symbol) && k == key }
        elsif key.is_a?(String)
          # Expected key is string, look for string key in actual
          actual.keys.find { |k| k.is_a?(String) && k == key } ||
          # Also check if there's a symbol key that matches the string
          actual.keys.find { |k| k.is_a?(Symbol) && k.to_s == key }
        else
          key
        end
        
        matching_key && actual.key?(matching_key) && deep_equal?(value, actual[matching_key])
      end
    else
      expected == actual
    end
  end
end

# Command-line interface
if __FILE__ == $0
  validator = CrossLanguageValidator.new
  
  case ARGV[0]
  when 'validate'
    if ARGV[1]
      # Validate specific case
      result = validator.validate_case(ARGV[1])
      puts JSON.pretty_generate(result)
    else
      # Validate all cases
      result = validator.validate_all
      puts JSON.pretty_generate(result)
    end
  when 'generate'
    if ARGV[1]
      # Generate specific case
      result = validator.generate_case(ARGV[1])
      puts JSON.pretty_generate(result)
    else
      puts "Usage: ruby cross_language_validator.rb generate '<ruby_literal>'"
    end
  else
    puts "Usage:"
    puts "  ruby cross_language_validator.rb validate [case_name]"
    puts "  ruby cross_language_validator.rb generate '<ruby_literal>'"
    puts ""
    puts "Examples:"
    puts "  ruby cross_language_validator.rb validate"
    puts "  ruby cross_language_validator.rb validate simple_hash"
    puts "  ruby cross_language_validator.rb generate '{:key => \"value\"}'"
  end
end