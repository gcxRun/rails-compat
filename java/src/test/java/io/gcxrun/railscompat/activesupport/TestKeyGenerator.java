package io.gcxrun.railscompat.activesupport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

public class TestKeyGenerator {

  private static final String secretKeyBase =
      "6894a355142c571fc6d5c5bcfeb7e35c7b0e143d3c98277bc4111d04bd6aa249c6b0bca"
          + "97124d943e6eeaba1b5ee6d56d3b1b5a42502201b1b5d38e98de861ee";

  byte[] derived_key = {
    98, 32, -32, 99, -33, 119, 42, -20, -55, -83, 88, -59, 32, 51, 113, 127, 104, 9, -7, -37, -14,
    -45, 52, 14, -93, -98, 17, -99, -100, -37, -40, 35
  };

  @Test
  public void testGenerateKey() {
    final var salt = "authenticated encrypted cookie";
    final var keyGen = new KeyGenerator(secretKeyBase, 1000, false);
    final var key = keyGen.generateKey(salt, 32 * 8);

    assertEquals(32, key.length);
    // assert equality
    assertArrayEquals(derived_key, key);

    final var key2 = keyGen.generateKey(salt, 32 * 8);

    assertEquals(32, key.length);
    // assert identity on purpose to check that the cache is working, not working here
    assertNotSame(key, key2);
  }

  @Test
  public void testCachingKeyGenerator() {
    final var salt = "authenticated encrypted cookie";
    final var keyGen = new KeyGenerator(secretKeyBase, 1000, true);
    final var key = keyGen.generateKey(salt, 32 * 8);

    assertEquals(32, key.length);
    assertArrayEquals(derived_key, key);

    final var key2 = keyGen.generateKey(salt, 32 * 8);

    assertEquals(32, key.length);
    // assert identity on purpose to check that the cache is working
    assertSame(key, key2);
  }
}
