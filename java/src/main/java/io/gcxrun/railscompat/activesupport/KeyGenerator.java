package io.gcxrun.railscompat.activesupport;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class KeyGenerator {

  private final String secret;
  private final int iterations;
  private final ConcurrentHashMap<String, byte[]> keyCache;

  public KeyGenerator(String secret, int iterations, boolean withCache) {
    // TODO: [HIGH] Add input validation - secret should not be null or empty
    // TODO: [HIGH] Add validation - iterations should be >= 1000 for security
    this.secret = secret;
    this.iterations = iterations;
    if (withCache) {
      this.keyCache = new ConcurrentHashMap<>();
    } else {
      keyCache = null;
    }
  }

  public byte[] generateKey(String salt, int keySize) {
    // TODO: [HIGH] Add input validation - salt should not be null
    // TODO: [MEDIUM] Add validation - keySize should be reasonable (e.g., 128-512 bits)
    if (keyCache != null) {
      // TODO: [MEDIUM] Consider cache size limits to prevent memory exhaustion
      return keyCache.computeIfAbsent(salt + '|' + keySize, s -> _generateKey(salt, keySize));
    }
    return _generateKey(salt, keySize);
  }

  private byte[] _generateKey(String salt, int keySize) {
    // TODO: [HIGH] Use StandardCharsets.UTF_8 instead of default charset for salt.getBytes()
    final var spec = new PBEKeySpec(secret.toCharArray(), salt.getBytes(), iterations, keySize);
    try {
      // TODO: [MEDIUM] Consider upgrading to PBKDF2WithHmacSHA256 for better security
      final var factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
      return factory.generateSecret(spec).getEncoded();
    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      // TODO: [HIGH] Don't expose internal exception details - create custom exception
      throw new RuntimeException(e);
    } finally {
      // TODO: [HIGH] Clear PBEKeySpec to remove secret from memory
      // spec.clearPassword();
    }
  }
}