"""Tests for KeyGenerator module."""

import pytest
from railscompat.activesupport import KeyGenerator


class TestKeyGenerator:
    """Test KeyGenerator PBKDF2 key generation."""

    def test_key_generation_without_cache(self) -> None:
        """Test basic key generation without caching."""
        # TODO: [HIGH] Add input validation - secret should not be null or empty
        # TODO: [HIGH] Add validation - iterations should be >= 1000 for security
        generator = KeyGenerator("test-secret", 1000, False)
        
        # TODO: [HIGH] Add input validation - salt should not be null
        # TODO: [MEDIUM] Add validation - keySize should be reasonable (e.g., 128-512 bits)
        key = generator.generate_key("salt", 256)
        
        assert isinstance(key, bytes)
        assert len(key) == 32  # 256 bits = 32 bytes

    def test_key_generation_with_cache(self) -> None:
        """Test key generation with caching enabled."""
        generator = KeyGenerator("test-secret", 1000, True)
        
        # Generate same key twice
        key1 = generator.generate_key("salt", 256)
        key2 = generator.generate_key("salt", 256)
        
        # Should be identical due to caching
        assert key1 == key2
        assert isinstance(key1, bytes)
        assert len(key1) == 32

    def test_different_salts_produce_different_keys(self) -> None:
        """Test that different salts produce different keys."""
        generator = KeyGenerator("test-secret", 1000, False)
        
        key1 = generator.generate_key("salt1", 256)
        key2 = generator.generate_key("salt2", 256)
        
        assert key1 != key2

    def test_different_key_sizes(self) -> None:
        """Test generation of different key sizes."""
        generator = KeyGenerator("test-secret", 1000, False)
        
        key128 = generator.generate_key("salt", 128)
        key256 = generator.generate_key("salt", 256)
        key512 = generator.generate_key("salt", 512)
        
        assert len(key128) == 16  # 128 bits = 16 bytes
        assert len(key256) == 32  # 256 bits = 32 bytes
        assert len(key512) == 64  # 512 bits = 64 bytes

    def test_cache_isolation(self) -> None:
        """Test that cache properly isolates different salt/size combinations."""
        generator = KeyGenerator("test-secret", 1000, True)
        
        # TODO: [MEDIUM] Consider cache size limits to prevent memory exhaustion
        key1 = generator.generate_key("salt1", 256)
        key2 = generator.generate_key("salt2", 256)
        key3 = generator.generate_key("salt1", 512)
        
        # All should be different
        assert key1 != key2
        assert key1 != key3
        assert key2 != key3
        
        # But regenerating with same params should return cached results
        assert key1 == generator.generate_key("salt1", 256)
        assert key2 == generator.generate_key("salt2", 256)
        assert key3 == generator.generate_key("salt1", 512)