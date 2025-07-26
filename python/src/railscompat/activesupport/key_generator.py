"""PBKDF2 key generation compatible with Rails' ActiveSupport::KeyGenerator."""

from typing import Optional
import hashlib
from threading import Lock


class KeyGenerator:
    """PBKDF2 key derivation with caching support.
    
    Compatible with Rails' ActiveSupport::KeyGenerator implementation.
    """

    def __init__(self, secret: str, iterations: int, with_cache: bool) -> None:
        """Initialize KeyGenerator.
        
        Args:
            secret: The secret key for PBKDF2 derivation
            iterations: Number of PBKDF2 iterations
            with_cache: Whether to enable key caching
        """
        # TODO: [HIGH] Add input validation - secret should not be null or empty
        # TODO: [HIGH] Add validation - iterations should be >= 1000 for security
        self._secret = secret
        self._iterations = iterations
        
        if with_cache:
            self._key_cache: Optional[dict[str, bytes]] = {}
            self._cache_lock = Lock()
        else:
            self._key_cache = None
            self._cache_lock = Lock()  # Still need lock for type safety

    def generate_key(self, salt: str, key_size: int) -> bytes:
        """Generate a key using PBKDF2.
        
        Args:
            salt: Salt string for key derivation
            key_size: Desired key size in bits
            
        Returns:
            Generated key as bytes
        """
        # TODO: [HIGH] Add input validation - salt should not be null
        # TODO: [MEDIUM] Add validation - keySize should be reasonable (e.g., 128-512 bits)
        
        if self._key_cache is not None:
            cache_key = f"{salt}|{key_size}"
            
            # Check cache first
            with self._cache_lock:
                if cache_key in self._key_cache:
                    return self._key_cache[cache_key]
            
            # Generate key and cache it
            # TODO: [MEDIUM] Consider cache size limits to prevent memory exhaustion
            key = self._generate_key(salt, key_size)
            
            with self._cache_lock:
                self._key_cache[cache_key] = key
            
            return key
        
        return self._generate_key(salt, key_size)

    def _generate_key(self, salt: str, key_size: int) -> bytes:
        """Internal key generation using PBKDF2.
        
        Args:
            salt: Salt string for key derivation
            key_size: Desired key size in bits
            
        Returns:
            Generated key as bytes
        """
        try:
            # TODO: [HIGH] Use StandardCharsets.UTF_8 instead of default charset for salt.getBytes()
            salt_bytes = salt.encode('utf-8')
            password_bytes = self._secret.encode('utf-8')
            
            # TODO: [MEDIUM] Consider upgrading to PBKDF2WithHmacSHA256 for better security
            key = hashlib.pbkdf2_hmac(
                'sha1',
                password_bytes,
                salt_bytes,
                self._iterations,
                key_size // 8  # Convert bits to bytes
            )
            
            return key
            
        except Exception as e:
            # TODO: [HIGH] Don't expose internal exception details - create custom exception
            raise RuntimeError(f"Key generation failed: {e}") from e
        finally:
            # TODO: [HIGH] Clear password from memory (Python limitation - strings are immutable)
            pass