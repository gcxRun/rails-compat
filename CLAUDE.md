# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Structure

This is a multi-language Rails compatibility library with implementations in Java, Python, and TypeScript. Each language provides interoperability with Rails applications through compatible APIs and data formats.

```
rails-compat/
├── java/                       # Java implementation (Maven)
│   ├── pom.xml
│   └── src/main/java/com/company/railscompat/
│       ├── activesupport/      # Rails ActiveSupport compatibility
│       ├── actionpack/         # Rails ActionPack compatibility  
│       └── rubycore/          # Ruby core functionality (Marshal, etc.)
├── python/                     # Python implementation (uv)
│   ├── pyproject.toml
│   ├── uv.lock
│   └── src/railscompat/
│       ├── activesupport/
│       ├── actionpack/
│       └── rubycore/
├── typescript/                 # TypeScript implementation (npm)
│   ├── package.json
│   ├── tsconfig.json
│   └── src/
│       ├── activesupport/
│       ├── actionpack/
│       └── rubycore/
└── shared/                     # Cross-language resources
    ├── test-data/             # Reference test data
    ├── ruby-reference/        # Ruby scripts for test generation
    └── specifications/        # Protocol documentation
```

## Development Commands

### Java (Maven)
```bash
# Build and compile
mvn clean compile
mvn clean package

# Run tests
mvn test
mvn test -Dtest=SpecificTestClass

# Code formatting
mvn fmt:format

# Working directory: java/
```

### Python (uv)
```bash
# Environment setup
uv venv
uv sync

# Build package
uv build

# Run tests
uv run pytest
uv run pytest tests/specific_test.py
uv run pytest -k "test_pattern"

# Code quality
uv run ruff check
uv run ruff format

# Working directory: python/
```

### TypeScript
```bash
# Install dependencies
npm install
# or: yarn install

# Build
npm run build
# or: yarn build

# Run tests
npm test
npm run test:watch
# or: yarn test

# Code quality
npm run lint
npm run format
# or: yarn lint, yarn format

# Working directory: typescript/
```

## Architecture Overview

### Domain Modules

**activesupport/** - Rails framework utilities
- Key generation (PBKDF2, compatible with Rails' ActiveSupport::KeyGenerator)
- Message verification and signing
- Secure random generation
- Time and date utilities

**actionpack/** - Web framework compatibility  
- Session handling compatible with Rails sessions
- Cookie parsing and generation
- HTTP request/response abstractions
- Rack middleware compatibility (e.g., Rack::Attack rate limiting)

**rubycore/** - Core Ruby functionality
- Ruby Marshal format implementation for data serialization
- String and encoding utilities
- Data type conversions

### Cross-Language Design Principles

1. **API Consistency**: All languages expose similar interfaces for the same functionality
2. **Data Format Compatibility**: Serialized data (Marshal, JSON, etc.) is interchangeable between implementations
3. **Rails Compatibility**: Behavior matches Rails framework expectations exactly
4. **Ruby Reference Testing**: Use Ruby scripts to generate canonical test data for validation

### Testing Strategy

- **Unit Tests**: Each language has comprehensive unit tests for all modules
- **Integration Tests**: Cross-language compatibility tests using shared test data
- **Ruby Reference**: Ruby scripts in `shared/ruby-reference/` generate expected outputs
- **Property-Based Testing**: Especially important for complex formats like Ruby Marshal

## Development Guidelines

### Adding New Features

1. **Research Rails Behavior**: Study the original Rails implementation thoroughly
2. **Create Ruby Reference**: Write Ruby script to generate test cases and expected outputs
3. **Implement Across Languages**: Maintain API consistency between Java, Python, and TypeScript
4. **Cross-Language Testing**: Ensure all implementations produce identical results
5. **Update Documentation**: Document new features in both code and markdown files

### Code Organization

- Use domain-based packages/modules (activesupport, actionpack, rubycore)
- Mirror the organizational structure across all three languages
- Place shared utilities in appropriate domain modules
- Keep language-specific build configurations in root of each language directory

### Testing Requirements

- Every public API must have unit tests
- Complex algorithms (like Marshal) require property-based tests
- Integration tests must verify cross-language compatibility
- Use Ruby reference implementations to validate correctness against Rails

### Cross-Language Consistency

- Method/function names should follow each language's conventions but maintain logical consistency
- Error handling should be equivalent across implementations
- Performance characteristics should be similar where possible
- All implementations must handle the same edge cases

## Key Implementation Notes

Based on the existing Java implementation, focus areas include:

- **Ruby Marshal**: Complete Ruby object serialization format implementation
- **Rack::Attack**: Rate limiting with configurable throttling and servlet integration  
- **Session Handling**: Ruby session format compatibility
- **Key Generation**: PBKDF2 key derivation matching Rails' ActiveSupport
- **Message Verification**: Cryptographic signing/verification compatible with Rails

When working on any of these areas, refer to the existing Java implementation patterns and ensure the same level of Ruby compatibility across all language implementations.