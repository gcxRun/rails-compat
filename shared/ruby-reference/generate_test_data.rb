#!/usr/bin/env ruby

require 'base64'
require 'json'
require 'time'

# Comprehensive test cases for Ruby Marshal format
test_cases = {
  # Basic types
  'nil_value' => nil,
  'true_value' => true,
  'false_value' => false,
  
  # Integers - covering different encoding schemes
  'zero' => 0,
  'small_positive' => 1,
  'small_positive_2' => 5,
  'small_positive_122' => 122,
  'small_positive_123' => 123,
  'small_positive_255' => 255,
  'medium_positive' => 1000,
  'large_positive' => 132138561,
  'very_large_positive' => 1695905840,
  
  'small_negative' => -1,
  'small_negative_2' => -48,
  'small_negative_123' => -123,
  'medium_negative' => -1000,
  'large_negative' => -345,
  
  # Bignum (numbers that don't fit in 32-bit)
  'bignum_positive' => 2**100,
  'bignum_negative' => -(2**100),
  
  # Strings
  'empty_string' => '',
  'simple_string' => 'hello',
  'string_with_spaces' => 'hello world',
  'long_string' => 'a very basic string that is somewhat longer',
  'unicode_string' => 'héllo wørld 🌍',
  'string_with_quotes' => 'string "with" quotes',
  'string_with_newlines' => "line1\nline2\nline3",
  
  # Symbols
  'simple_symbol' => :symbol,
  'symbol_azerty' => :azerty,
  'complex_symbol' => :'complex-symbol!',
  'symbol_with_underscore' => :snake_case,
  'symbol_with_numbers' => :symbol123,
  
  # Arrays
  'empty_array' => [],
  'single_element_array' => [1],
  'simple_array' => [1, 2, 3],
  'mixed_array' => [1, 'string', :symbol, nil],
  'nested_array' => [[1, 2], [3, 4]],
  'array_with_hash' => [{'key' => 'value'}],
  'deep_nested_array' => [[[1, 2], [3, 4]], [[5, 6], [7, 8]]],
  
  # Hashes
  'empty_hash' => {},
  'single_pair_hash' => {'key' => 'value'},
  'simple_hash' => {'a' => 'b', 'c' => 'd'},
  'hash_az_qs' => {'az' => 'qs'},
  'complex_hash' => {'az' => 'qs', 'b' => 'c', 'd' => 'e'},
  'symbol_keys_hash' => {:key => 'value', :another => 'data'},
  'mixed_keys_hash' => {'string_key' => 'value1', :symbol_key => 'value2'},
  'nested_hash' => {'outer' => {'inner' => 'value'}},
  'hash_with_array' => {'key' => [1, 2, 3]},
  
  # Rails-like session data
  'rails_session_simple' => {
    'session_id' => 'abc123def456',
    'user_id' => 42
  },
  'rails_session_complex' => {
    'session_id' => 'abc123def456ghi789',
    'user_id' => 42,
    'username' => 'john_doe',
    'flash' => {
      'notice' => 'Welcome back!',
      'alert' => nil
    },
    '_csrf_token' => 'token123456789',
    'preferences' => {
      'theme' => 'dark',
      'language' => 'en',
      'notifications' => true
    },
    'cart_items' => [
      {'product_id' => 1, 'quantity' => 2},
      {'product_id' => 5, 'quantity' => 1}
    ]
  },
  
  # Complex nested structures
  'deeply_nested' => {
    'level1' => {
      'level2' => {
        'level3' => {
          'level4' => ['deep', 'array', 'data']
        }
      }
    }
  },
  
  # Symbol links (reused symbols)
  'symbol_reuse' => [:same_symbol, :same_symbol, :different, :same_symbol],
  
  # Mixed complex structure
  'mixed_complex' => {
    'metadata' => {
      'version' => 1,
      'created_at' => '2024-01-01T00:00:00Z'
    },
    'data' => [
      {'id' => 1, 'name' => 'Item 1', 'active' => true},
      {'id' => 2, 'name' => 'Item 2', 'active' => false},
      {'id' => 3, 'name' => nil, 'active' => true}
    ],
    'settings' => {
      :debug => true,
      :timeout => 30,
      'features' => [:feature_a, :feature_b, :feature_c]
    }
  }
}

# Generate JSON test data file
output = {
  'metadata' => {
    'generated_at' => Time.now.iso8601,
    'ruby_version' => RUBY_VERSION,
    'marshal_version' => Marshal::MAJOR_VERSION.to_s + '.' + Marshal::MINOR_VERSION.to_s,
    'description' => 'Comprehensive Ruby Marshal test cases for cross-language compatibility'
  },
  'test_cases' => {}
}

puts "Generating test data for #{test_cases.length} test cases..."

test_cases.each do |name, value|
  begin
    marshal_data = Marshal.dump(value)
    base64_data = Base64.strict_encode64(marshal_data)
    
    output['test_cases'][name] = {
      'description' => "Test case: #{name}",
      'ruby_literal' => value.inspect,
      'marshal_base64' => base64_data,
      'expected_result' => value,
      'data_size' => marshal_data.length
    }
    
    puts "✓ Generated: #{name} (#{marshal_data.length} bytes)"
  rescue => e
    puts "✗ Failed to generate #{name}: #{e.message}"
  end
end

# Write to JSON file
output_file = File.join(File.dirname(__FILE__), '..', 'test-data', 'marshal_test_cases.json')
File.write(output_file, JSON.pretty_generate(output))

puts "\nTest data generated successfully!"
puts "Output file: #{output_file}"
puts "Total test cases: #{output['test_cases'].length}"
puts "Total size: #{File.size(output_file)} bytes"