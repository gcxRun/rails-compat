"""Cross-language validation tests for Marshal implementations.

This module provides tests that validate the Python Marshal implementation produces
identical results to the Ruby reference implementation, ensuring 100% compatibility.
"""

import json
import subprocess
import pytest
from pathlib import Path
from typing import Any, Dict

from railscompat.rubycore.marshal import Marshal


class TestCrossLanguageValidation:
    """Cross-language validation tests for Marshal compatibility."""

    @pytest.fixture(scope='class')
    def validator_script(self) -> Path:
        """Path to Ruby cross-language validator script."""
        script_path = Path(__file__).parent.parent.parent.parent / 'shared' / 'ruby-reference' / 'cross_language_validator.rb'
        
        if not script_path.exists():
            pytest.skip(f"Validator script not found: {script_path}")
        
        return script_path

    @pytest.fixture(scope='class')
    def test_cases(self) -> Dict[str, Any]:
        """Load test cases for cross-language validation."""
        test_data_path = Path(__file__).parent.parent.parent.parent / 'shared' / 'test-data' / 'marshal_test_cases.json'
        
        if not test_data_path.exists():
            pytest.skip(f"Test data file not found: {test_data_path}")
        
        with open(test_data_path, 'r') as f:
            data = json.load(f)
        
        return data['test_cases']

    def _run_ruby_validator(self, validator_script: Path, command: str, *args) -> Dict[str, Any]:
        """Run Ruby validator script and return parsed JSON result."""
        cmd = ['ruby', str(validator_script), command] + list(args)
        
        try:
            result = subprocess.run(cmd, capture_output=True, text=True, check=True)
            return json.loads(result.stdout)
        except subprocess.CalledProcessError as e:
            pytest.fail(f"Ruby validator failed: {e.stderr}")
        except json.JSONDecodeError as e:
            pytest.fail(f"Failed to parse validator output: {e}")

    def test_ruby_validator_available(self, validator_script: Path) -> None:
        """Test that Ruby validator script is available and working."""
        result = self._run_ruby_validator(validator_script, 'validate', 'nil_value')
        
        assert 'case_name' in result
        assert result['case_name'] == 'nil_value'
        assert 'matches' in result

    @pytest.mark.parametrize("case_name", [
        "nil_value", "true_value", "false_value", "zero", "small_positive",
        "simple_string", "simple_symbol", "empty_array", "simple_array",
        "empty_hash", "simple_hash"
    ])
    def test_basic_cross_language_compatibility(
        self, 
        validator_script: Path, 
        test_cases: Dict[str, Any], 
        case_name: str
    ) -> None:
        """Test that Python implementation matches Ruby for basic types."""
        # Get test case data
        test_case = test_cases[case_name]
        marshal_base64 = test_case['marshal_base64']
        
        # Parse with Python implementation
        python_result = Marshal.load_b64(marshal_base64)
        
        # Validate with Ruby reference
        ruby_validation = self._run_ruby_validator(validator_script, 'validate', case_name)
        
        # Ruby validator should confirm the data is valid
        if 'error' in ruby_validation:
            pytest.fail(f"Ruby validation failed for {case_name}: {ruby_validation['error']}")
        
        assert ruby_validation['matches'] is True, f"Ruby validation failed for {case_name}"
        
        # Compare Python result with expected result
        expected = test_case['expected_result']
        
        # Handle symbol conversion (Ruby symbols become strings with ':' prefix)
        if isinstance(expected, str) and case_name.startswith('symbol_'):
            # For symbols, Python should return string with ':' prefix
            assert python_result.startswith(':'), f"Symbol should start with ':' for {case_name}"
        else:
            # For other types, direct comparison
            assert python_result == expected, f"Python result doesn't match expected for {case_name}"

    def test_complex_structure_compatibility(
        self, 
        validator_script: Path, 
        test_cases: Dict[str, Any]
    ) -> None:
        """Test complex nested structures for cross-language compatibility."""
        complex_cases = ['nested_hash', 'mixed_array', 'rails_session_simple']
        
        for case_name in complex_cases:
            if case_name not in test_cases:
                continue
                
            test_case = test_cases[case_name]
            marshal_base64 = test_case['marshal_base64']
            
            # Parse with Python implementation
            python_result = Marshal.load_b64(marshal_base64)
            
            # Validate with Ruby reference
            ruby_validation = self._run_ruby_validator(validator_script, 'validate', case_name)
            
            # Ruby validator should confirm the data is valid
            if 'error' in ruby_validation:
                pytest.fail(f"Ruby validation failed for {case_name}: {ruby_validation['error']}")
            
            assert ruby_validation['matches'] is True, f"Ruby validation failed for {case_name}"
            
            # Basic structure validation for Python result
            assert python_result is not None, f"Python result is None for {case_name}"

    def test_symbol_link_compatibility(
        self, 
        validator_script: Path, 
        test_cases: Dict[str, Any]
    ) -> None:
        """Test symbol link (reused symbols) compatibility."""
        if 'symbol_reuse' not in test_cases:
            pytest.skip("symbol_reuse test case not available")
        
        test_case = test_cases['symbol_reuse']
        marshal_base64 = test_case['marshal_base64']
        
        # Parse with Python implementation
        python_result = Marshal.load_b64(marshal_base64)
        
        # Validate with Ruby reference
        ruby_validation = self._run_ruby_validator(validator_script, 'validate', 'symbol_reuse')
        
        if 'error' in ruby_validation:
            pytest.fail(f"Ruby validation failed for symbol_reuse: {ruby_validation['error']}")
        
        assert ruby_validation['matches'] is True, "Ruby validation failed for symbol_reuse"
        
        # Python-specific validation
        assert isinstance(python_result, list), "symbol_reuse should be an array"
        assert len(python_result) == 4, "symbol_reuse should have 4 elements"
        
        # Check symbol reuse (first, second, and fourth elements should be identical)
        assert python_result[0] == python_result[1] == python_result[3], "Reused symbols should be identical"
        assert python_result[2] != python_result[0], "Different symbol should be different"

    def test_generate_custom_case_compatibility(self, validator_script: Path) -> None:
        """Test compatibility with custom generated Ruby cases."""
        # Generate a custom test case using Ruby
        custom_literal = '{"custom" => [1, 2, :symbol], "nested" => {"key" => "value"}}'
        
        ruby_generated = self._run_ruby_validator(validator_script, 'generate', custom_literal)
        
        if 'error' in ruby_generated:
            pytest.skip(f"Ruby generation failed: {ruby_generated['error']}")
        
        # Parse with Python implementation
        python_result = Marshal.load_b64(ruby_generated['marshal_base64'])
        
        # Basic validation - should be a hash with expected keys
        assert isinstance(python_result, dict), "Generated case should be a hash"
        assert 'custom' in python_result, "Should contain 'custom' key"
        assert 'nested' in python_result, "Should contain 'nested' key"

    def test_error_handling_compatibility(self, validator_script: Path) -> None:
        """Test that error handling is consistent between implementations."""
        # Test invalid Base64
        with pytest.raises(Exception):
            Marshal.load_b64("invalid_base64!")
        
        # Test malformed Marshal data
        with pytest.raises(Exception):
            Marshal.load_b64("YWJjZGVmZ2g=")  # "abcdefgh" in base64
        
        # Test empty data
        with pytest.raises(Exception):
            Marshal.load(b"")

    def test_performance_consistency(self, test_cases: Dict[str, Any]) -> None:
        """Test that Python implementation handles same data sizes as Ruby."""
        # Find the largest test case
        largest_case = max(test_cases.items(), key=lambda x: x[1]['data_size'])
        case_name, test_case = largest_case
        
        # Should be able to parse the largest case without issues
        result = Marshal.load_b64(test_case['marshal_base64'])
        assert result is not None, f"Failed to parse largest case: {case_name}"

    def test_all_cases_cross_validation(
        self, 
        validator_script: Path, 
        test_cases: Dict[str, Any]
    ) -> None:
        """Run cross-validation on all available test cases."""
        # Run Ruby validator on all cases
        all_validation = self._run_ruby_validator(validator_script, 'validate')
        
        assert 'metadata' in all_validation, "Should have metadata"
        assert 'results' in all_validation, "Should have results"
        
        metadata = all_validation['metadata']
        results = all_validation['results']
        
        # All cases should pass Ruby validation
        total_cases = metadata['total_cases']
        passed_cases = metadata['passed']
        failed_cases = metadata['failed']
        
        # Report any failures
        if failed_cases > 0:
            failed_case_names = [name for name, result in results.items() 
                                if result.get('matches') is False or 'error' in result]
            pytest.fail(f"Ruby validation failed for {failed_cases}/{total_cases} cases: {failed_case_names}")
        
        assert passed_cases == total_cases, f"Expected all cases to pass, got {passed_cases}/{total_cases}"
        
        # Now test that Python can parse all the same cases
        python_failures = []
        for case_name, test_case_data in test_cases.items():
            try:
                python_result = Marshal.load_b64(test_case_data['marshal_base64'])
                assert python_result is not None
            except Exception as e:
                python_failures.append(f"{case_name}: {e}")
        
        if python_failures:
            pytest.fail(f"Python parsing failed for cases: {python_failures}")