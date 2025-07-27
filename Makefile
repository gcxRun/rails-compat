# Rails Compatibility Library - Multi-language Test Runner
# Makefile for running tests across Java, Python, TypeScript implementations

.PHONY: help test test-java test-python test-typescript test-shared clean

# Default target
help:
	@echo "Rails Compatibility Library - Test Runner"
	@echo ""
	@echo "Available targets:"
	@echo "  test          - Run all tests (Java + Python + TypeScript)"
	@echo "  test-java     - Run Java tests only"
	@echo "  test-python   - Run Python tests only"
	@echo "  test-typescript - Run TypeScript tests only"
	@echo "  test-shared   - Generate and validate shared test data"
	@echo "  clean         - Clean build artifacts"
	@echo "  help          - Show this help message"
	@echo ""
	@echo "Requirements:"
	@echo "  - Java 11+ and Maven for Java tests"
	@echo "  - Python 3.8+ and uv for Python tests"
	@echo "  - Node.js 16+ and npm for TypeScript tests"
	@echo "  - Ruby for shared test data generation"

# Run all tests
test: test-shared test-java test-python test-typescript
	@echo ""
	@echo "✅ All tests completed successfully!"

# Run Java tests
test-java:
	@echo "🔧 Running Java tests..."
	cd java && mvn test
	@echo "✅ Java tests completed"

# Run Python tests  
test-python:
	@echo "🐍 Running Python tests..."
	cd python && uv run pytest tests/ -v
	@echo "✅ Python tests completed"

# Run TypeScript tests
test-typescript:
	@echo "📘 Running TypeScript tests..."
	cd typescript && npm test
	@echo "✅ TypeScript tests completed"

# Generate and validate shared test data
test-shared:
	@echo "📊 Generating shared test data..."
	cd shared/ruby-reference && ruby generate_test_data.rb
	@echo "🔍 Validating test data with Ruby reference..."
	cd shared/ruby-reference && ruby cross_language_validator.rb validate | head -10
	@echo "✅ Shared test data ready"

# Clean build artifacts
clean:
	@echo "🧹 Cleaning build artifacts..."
	cd java && mvn clean
	cd python && rm -rf .pytest_cache __pycache__ src/railscompat/__pycache__ tests/__pycache__
	cd python && find . -name "*.pyc" -delete
	cd python && find . -name "__pycache__" -type d -exec rm -rf {} + 2>/dev/null || true
	cd typescript && npm run clean
	@echo "✅ Clean completed"

# Quick test runner for development
quick-test: test-java test-python test-typescript
	@echo "⚡ Quick tests completed"

# Individual component tests
test-marshal:
	@echo "🔧 Testing Marshal implementation (Java)..."
	cd java && mvn test -Dtest=TestMarshal
	@echo "🐍 Testing Marshal implementation (Python)..."  
	cd python && uv run pytest tests/rubycore/test_marshal.py -v
	@echo "📘 Testing Marshal implementation (TypeScript)..."
	cd typescript && npm test -- tests/rubycore/marshal.test.ts

test-cross-language:
	@echo "🔧 Testing cross-language validation (Java)..."
	cd java && mvn test -Dtest=TestCrossLanguageValidation
	@echo "🐍 Testing cross-language validation (Python)..."
	cd python && uv run pytest tests/rubycore/test_cross_language_validation.py -v
	@echo "📘 Testing cross-language validation (TypeScript)..."
	cd typescript && npm test -- tests/rubycore/crossLanguageValidation.test.ts

# Development targets
dev-setup:
	@echo "⚙️  Setting up development environment..."
	@echo "Java: Checking Maven..."
	cd java && mvn --version
	@echo "Python: Setting up virtual environment..."
	cd python && uv sync
	@echo "TypeScript: Installing dependencies..."
	cd typescript && npm install
	@echo "Ruby: Checking Ruby version..."
	ruby --version
	@echo "✅ Development environment ready"

# Test specific formats
test-formats:
	@echo "🧪 Testing specific Marshal formats..."
	cd java && mvn test -Dtest=TestMarshal#testBignums
	cd python && uv run pytest tests/rubycore/test_marshal.py::TestMarshal::test_bignums -v
	cd typescript && npm test -- --testNamePattern="should parse bignum"