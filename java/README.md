# Rails Compatibility Library - Java Implementation

Java implementation of Rails compatibility layer providing interoperability with Rails applications.

## Modules

### activesupport
Rails framework utilities compatible with ActiveSupport:
- **KeyGenerator** - PBKDF2 key derivation with caching (compatible with Rails' ActiveSupport::KeyGenerator)
- **MessageVerifier** - Message signing and verification with HMAC-SHA256

### actionpack
Web framework compatibility features:
- **session/RubySession** - Rails session cookie decryption (AES/GCM with metadata parsing)
- **rackattack/** - Complete Rack::Attack rate limiting implementation with servlet integration

### rubycore  
Core Ruby functionality for data format compatibility:
- **Marshal** - Complete Ruby Marshal format parser/deserializer (handles Ruby objects, arrays, hashes, symbols)

## Development Commands

```bash
# Build and compile
mvn clean compile
mvn clean package

# Run tests
mvn test
mvn test -Dtest=SpecificTestClass

# Code formatting
mvn fmt:format
```

## Dependencies

- **Java 21** - Required runtime
- **Jakarta Servlet API 6.0.0** - For web integration (provided scope)
- **Apache Commons Text/Codec** - String and encoding utilities
- **Vaadin Android JSON** - JSON processing
- **JUnit 5 + Mockito** - Testing framework

## Ruby Integration Testing

The `marshal.rb` script provides Ruby reference implementations for testing Marshal format compatibility. Tests use this script to generate expected outputs and validate cross-language compatibility.

## Key Features

- **Rails Session Compatibility** - Decrypt and validate Rails session cookies
- **Ruby Marshal Support** - Full Ruby object serialization format
- **Rate Limiting** - Servlet-based Rack::Attack implementation
- **Cryptographic Compatibility** - PBKDF2 key generation and HMAC message verification matching Rails behavior
- **Performance Optimized** - Concurrent caching and efficient byte buffer parsing