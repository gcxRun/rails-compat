"""Tests for MessageVerifier module."""

import pytest
from railscompat.activesupport import MessageVerifier


class TestMessageVerifier:
    """Test MessageVerifier HMAC message signing and verification."""

    def test_generate_and_verify(self) -> None:
        """Test basic message generation and verification."""
        # TODO: [HIGH] Add input validation - secret should not be null or empty
        # TODO: [HIGH] Add minimum secret length validation (e.g., >= 32 bytes)
        secret = b"test-secret-key-that-is-long-enough"
        verifier = MessageVerifier(secret)
        
        # TODO: [HIGH] Add input validation - purpose should not be null
        # TODO: [MEDIUM] Add value serialization validation
        signed_message = verifier.generate("test-value", "test-purpose")
        
        assert isinstance(signed_message, str)
        assert "--" in signed_message
        
        # Verify the signed message
        # TODO: [HIGH] Add input validation - signedMessage and purpose should not be null
        result = verifier.verify(signed_message, "test-purpose")
        assert result == "test-value"

    def test_verify_with_wrong_purpose_returns_none(self) -> None:
        """Test that verification fails with wrong purpose."""
        secret = b"test-secret-key-that-is-long-enough"
        verifier = MessageVerifier(secret)
        
        signed_message = verifier.generate("test-value", "correct-purpose")
        result = verifier.verify(signed_message, "wrong-purpose")
        
        assert result is None

    def test_verify_with_wrong_secret_returns_none(self) -> None:
        """Test that verification fails with wrong secret."""
        secret1 = b"test-secret-key-that-is-long-enough-1"
        secret2 = b"test-secret-key-that-is-long-enough-2"
        
        verifier1 = MessageVerifier(secret1)
        verifier2 = MessageVerifier(secret2)
        
        signed_message = verifier1.generate("test-value", "test-purpose")
        result = verifier2.verify(signed_message, "test-purpose")
        
        assert result is None

    def test_verify_malformed_message_returns_none(self) -> None:
        """Test that verification of malformed messages returns None."""
        secret = b"test-secret-key-that-is-long-enough"
        verifier = MessageVerifier(secret)
        
        # Test various malformed messages
        # TODO: [HIGH] Validate splits array length to prevent IndexOutOfBoundsException
        assert verifier.verify("no-separator", "test-purpose") is None
        assert verifier.verify("too--many--separators", "test-purpose") is None
        assert verifier.verify("", "test-purpose") is None

    def test_different_values_produce_different_signatures(self) -> None:
        """Test that different values produce different signatures."""
        secret = b"test-secret-key-that-is-long-enough"
        verifier = MessageVerifier(secret)
        
        message1 = verifier.generate("value1", "test-purpose")
        message2 = verifier.generate("value2", "test-purpose")
        
        assert message1 != message2
        
        # But both should verify correctly
        assert verifier.verify(message1, "test-purpose") == "value1"
        assert verifier.verify(message2, "test-purpose") == "value2"

    def test_serialization_of_different_types(self) -> None:
        """Test serialization of different value types."""
        secret = b"test-secret-key-that-is-long-enough"
        verifier = MessageVerifier(secret)
        
        # TODO: [HIGH] Add null check for value parameter
        # TODO: [MEDIUM] Consider proper JSON serialization instead of toString()
        
        # Test string
        result = verifier.verify(
            verifier.generate("string-value", "test"), "test"
        )
        assert result == "string-value"
        
        # Test number (will be converted to string)
        result = verifier.verify(
            verifier.generate(42, "test"), "test"
        )
        assert result == "42"
        
        # Test boolean (will be converted to string)
        result = verifier.verify(
            verifier.generate(True, "test"), "test"
        )
        assert result == "True"