# Rails Compatibility Library - Python Implementation

Python implementation of Rails compatibility layer providing interoperability with Rails applications.

## Installation

```bash
# Install with uv (recommended)
uv add rails-compat

# Or install with pip
pip install rails-compat
```

## Development Setup

```bash
# Clone the repository
git clone <repository-url>
cd rails-compat/python

# Install with development dependencies using uv
uv sync --dev

# Or create virtual environment and install with pip
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
pip install -e ".[dev]"
```

## Modules

### activesupport
Rails framework utilities compatible with ActiveSupport:
- **KeyGenerator** - PBKDF2 key derivation with caching (compatible with Rails' ActiveSupport::KeyGenerator)
- **MessageVerifier** - Message signing and verification with HMAC-SHA256

### actionpack
Web framework compatibility features:
- **session/RubySession** - Rails session cookie decryption (AES/GCM with metadata parsing)

### rubycore  
Core Ruby functionality for data format compatibility:
- **Marshal** - Complete Ruby Marshal format parser/deserializer (handles Ruby objects, arrays, hashes, symbols)

## Usage Examples

### Key Generation
```python
from railscompat.activesupport import KeyGenerator

# Create key generator (compatible with Rails)
generator = KeyGenerator("your-secret-key", 1000, with_cache=True)

# Generate key for specific salt and size
key = generator.generate_key("authenticated encrypted cookie", 256)
```

### Message Verification
```python
from railscompat.activesupport import MessageVerifier

# Create message verifier
secret = b"your-secret-key-bytes"
verifier = MessageVerifier(secret)

# Sign a message
signed = verifier.generate("Hello, World!", "greeting")

# Verify the message
result = verifier.verify(signed, "greeting")
print(result)  # "Hello, World!"
```

### Session Decryption
```python
from railscompat.actionpack.session import RubySession

# Decrypt Rails session cookie
session = RubySession.from_cookie_value(cookie_value, secret_key_base)
session_data = session.decrypt()
```

### Ruby Marshal Format
```python
from railscompat.rubycore import Marshal

# Load Ruby Marshal data
ruby_data = Marshal.load(marshal_bytes)

# Load from base64
ruby_data = Marshal.load_b64(base64_marshal_string)
```

## Development Commands

```bash
# Run tests
uv run pytest

# Run tests with coverage
uv run pytest --cov=src/railscompat --cov-report=html

# Run specific test
uv run pytest tests/activesupport/test_key_generator.py

# Type checking
uv run mypy src/

# Code formatting
uv run black src/ tests/

# Linting
uv run ruff check src/ tests/
```

## Dependencies

- **Python 3.9+** - Required runtime
- **cryptography** - Modern cryptographic library for AES/GCM, PBKDF2, and HMAC operations

## Architecture

This Python implementation maintains the same API structure as the Java version while following Python idioms:

- **Type hints** throughout for better IDE support and documentation
- **Dataclasses** for simple data structures
- **Enums** for constants
- **Context managers** where appropriate
- **Exception chaining** for better error debugging

## Cross-Language Compatibility

The Python implementation is designed to be fully compatible with:
- Rails session cookies
- Ruby Marshal format data
- Java implementation of the same library
- TypeScript implementation (when available)

All cryptographic operations and data formats match exactly with Rails behavior to ensure seamless interoperability.

## Security Notes

This library includes TODO comments for security improvements that should be addressed before production use:

- Input validation for all public APIs
- DoS protection (size limits, recursion depth)
- Timing-safe comparisons for cryptographic operations
- Proper exception handling without information leakage

## Testing

The test suite includes:
- Unit tests for all public APIs
- Cross-compatibility tests with Rails-generated data
- Edge case handling
- Security-focused test cases

Run the full test suite with `uv run pytest` or individual test files as needed.