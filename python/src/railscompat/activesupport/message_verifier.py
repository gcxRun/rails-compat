"""Message signing and verification with HMAC-SHA256 compatible with Rails."""

import base64
import hashlib
import hmac
import json
from typing import Any, Optional
from dataclasses import dataclass


class MessageVerifier:
    """Message verification using HMAC signatures.
    
    Creates a new MessageVerifier with the provided secret. It is only using the JSON serializer
    and Digest SHA256.
    
    Compatible with: ActiveSupport::MessageVerifier.new secret, digest: "SHA256", serializer: JSON
    """

    def __init__(self, secret: bytes) -> None:
        """Initialize MessageVerifier.
        
        Args:
            secret: Secret key for HMAC signing
        """
        # TODO: [HIGH] Add input validation - secret should not be null or empty
        # TODO: [HIGH] Add minimum secret length validation (e.g., >= 32 bytes)
        self._secret = secret

    @staticmethod
    def _encode_bytes(data: bytes) -> str:
        """Encode bytes to base64 string."""
        return base64.b64encode(data).decode('utf-8')

    @staticmethod
    def _encode_string(data: str) -> str:
        """Encode string to base64 string."""
        return MessageVerifier._encode_bytes(data.encode('utf-8'))

    @staticmethod
    def _decode_bytes(data: bytes) -> str:
        """Decode base64 bytes to string."""
        # TODO: [MEDIUM] Add exception handling for invalid Base64 data
        return base64.b64decode(data).decode('utf-8')

    @staticmethod
    def _decode_string(data: str) -> str:
        """Decode base64 string to string."""
        return MessageVerifier._decode_bytes(data.encode('utf-8'))

    def generate(self, value: Any, purpose: str) -> str:
        """Generate a signed message for the provided value.
        
        The message is signed with the MessageVerifier's secret. Returns Base64-encoded
        message joined with the generated signature.
        
        Args:
            value: Value to sign
            purpose: Purpose string for the message
            
        Returns:
            Signed message in format "data--signature"
        """
        # TODO: [HIGH] Add input validation - purpose should not be null
        # TODO: [MEDIUM] Add value serialization validation
        metadata = Metadata.wrap(self._serialize(value), purpose)
        data = self._encode_string(metadata.to_json())
        signature = self._generate_digest_string(data)
        return f"{data}--{signature}"

    def _serialize(self, value: Any) -> str:
        """Serialize value to string.
        
        Args:
            value: Value to serialize
            
        Returns:
            Serialized string
        """
        # TODO: [HIGH] Add null check for value parameter
        # TODO: [MEDIUM] Consider proper JSON serialization instead of toString()
        return str(value)

    def _generate_digest_string(self, data: str) -> str:
        """Generate HMAC digest for string data."""
        return self._generate_digest_bytes(data.encode('utf-8'))

    def _generate_digest_bytes(self, data: bytes) -> str:
        """Generate HMAC digest for byte data.
        
        Args:
            data: Data to sign
            
        Returns:
            Hexadecimal digest string
        """
        try:
            # TODO: [MEDIUM] Consider making HMAC instance thread-local for better performance
            digest = hmac.new(
                self._secret,
                data,
                hashlib.sha256
            ).digest()
            
            return digest.hex()
            
        except Exception as e:
            # TODO: [HIGH] Don't expose internal exception details - create custom exception
            raise RuntimeError(f"Digest generation failed: {e}") from e

    def verify(self, signed_message: str, purpose: str) -> Optional[Any]:
        """Decode the signed message using the MessageVerifier's secret.
        
        Returns the decoded message if it was signed with the same secret, otherwise returns None.
        
        Args:
            signed_message: Message to verify in format "data--signature"
            purpose: Expected purpose string
            
        Returns:
            Decoded message if valid, None otherwise
        """
        # TODO: [HIGH] Add input validation - signedMessage and purpose should not be null
        # TODO: [HIGH] Validate splits array length to prevent IndexOutOfBoundsException
        splits = signed_message.split("--")
        if len(splits) != 2:
            return None
            
        data, digest = splits[0], splits[1]
        
        # TODO: [HIGH] Use timing-safe comparison to prevent timing attacks
        expected_digest = self._generate_digest_string(data)
        if digest != expected_digest:
            return None

        return Metadata.verify(self._decode_string(data), purpose)


@dataclass
class Metadata:
    """Metadata wrapper for Rails-compatible message format."""
    
    message: str
    purpose: str

    @classmethod
    def wrap(cls, message: str, purpose: str) -> "Metadata":
        """Create metadata wrapper.
        
        Args:
            message: Message content
            purpose: Purpose string
            
        Returns:
            Metadata instance
        """
        return cls(message=message, purpose=purpose)

    @classmethod
    def verify(cls, data: str, purpose: str) -> Optional[Any]:
        """Verify metadata from JSON data.
        
        Args:
            data: JSON data string
            purpose: Expected purpose
            
        Returns:
            Message if valid, None otherwise
        """
        return cls.from_json(data).verify_purpose(purpose)

    @classmethod
    def from_json(cls, data: str) -> "Metadata":
        """Parse metadata from JSON string.
        
        Args:
            data: JSON string
            
        Returns:
            Metadata instance
        """
        # TODO: [HIGH] Add input validation - data should not be null
        try:
            json_obj = json.loads(data)
            rails_data = json_obj["_rails"]
            message = MessageVerifier._decode_string(rails_data["message"])
            purpose = rails_data["pur"]
            return cls(message=message, purpose=purpose)
        except (json.JSONDecodeError, KeyError) as e:
            # TODO: [HIGH] Don't expose internal exception details - create custom exception
            raise RuntimeError(f"Invalid JSON metadata: {e}") from e

    def verify_purpose(self, purpose: str) -> Optional[str]:
        """Verify the purpose matches.
        
        Args:
            purpose: Expected purpose
            
        Returns:
            Message if purpose matches, None otherwise
        """
        if self.purpose == purpose:
            return self.message
        else:
            return None

    def to_json(self) -> str:
        """Convert metadata to JSON string.
        
        Returns:
            JSON string with Rails-compatible format
        """
        # TODO: [MEDIUM] Escape JSON strings properly to prevent injection
        # TODO: [LOW] Consider using a proper JSON library for construction
        return json.dumps({
            "_rails": {
                "message": MessageVerifier._encode_string(self.message),
                "exp": None,
                "pur": self.purpose
            }
        }, separators=(',', ':'))