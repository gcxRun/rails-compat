package io.gcxrun.railscompat.activesupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

public class TestMessageVerifier {

  static final String SIGNED_ID_VERIFIER_SECRET =
      "a3A2ytWxvvvo2MgLHwSRUEzrUM1aQ7mcsQBCSb4Jti3UNIvKyfSq18FCqLxT4DZBJPcdJ1K56044CYDFl75T2g==";
  static final String SIGNED_ID_VERIFIER_SECRET_BAD =
      "a3A2ytWxvvov2MgLHwSRUEzrUM1aQ7mcsQBCSb4Jti3UNIvKyfSq18FCqLxT4DZBJPcdJ1K56044CYDFl75T2g==";
  static final byte[] secret = Base64.getDecoder().decode(SIGNED_ID_VERIFIER_SECRET);
  static final byte[] secret_bad = Base64.getDecoder().decode(SIGNED_ID_VERIFIER_SECRET_BAD);

  @Test
  public void testMessageVerifierGenerate() {
    var messageVerifier = new MessageVerifier(secret);
    var actual = messageVerifier.generate(691661353, "appointment");

    final String expected =
        "eyJfcmFpbHMiOnsibWVzc2FnZSI6Ik5qa3hOall4TXpVeiIsImV4cCI6bnVsb"
            + "CwicHVyIjoiYXBwb2ludG1lbnQifX0=--69d80740fd5e7b65873e739907f3561c6d9772287b5277c103f885e5fd774fd4";
    assertEquals(expected, actual);
  }

  @Test
  void testMetadata() {
    // should like JSON
    var metadata = MessageVerifier.Metadata.wrap("218640951", "patient");
    assertEquals(
        "{\"_rails\":{\"message\":\"MjE4NjQwOTUx\",\"exp\":null,\"pur\":\"patient\"}}",
        metadata.toJson());
  }

  @Test
  public void testB64() {
    var s = "{\"_rails\":{\"message\":\"NjkxNjYxMzUz\",\"exp\":null,\"pur\":\"appointment\"}}";
    var actual = Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    assertEquals(
        "eyJfcmFpbHMiOnsibWVzc2FnZSI6Ik5qa3hOall4TXpVeiIsImV4cCI6bnVsbCwicHVyIjoiYXBwb2ludG1lbnQifX0=",
        actual);
  }

  @Test
  public void testMessageVerifierVerify() {
    var messageVerifier = new MessageVerifier(secret);

    final String signed_message =
        "eyJfcmFpbHMiOnsibWVzc2FnZSI6Ik5qSTFNelV6TlRRMiIsImV4cCI6bnVsbCwicHVyIjoiYXBwb2ludG1lbnQifX0=--6cd2bbc8d725e6c1d73"
            + "d8d9cae7ac981c5d0b4dd7ff3c6f257ffa61db7635929";
    var message = messageVerifier.verify(signed_message, "appointment");

    assertNotNull(message);
    assertEquals("625353546", message);
    // assertEquals(625353546, message); //TODO : handle wrapping of objects through the first json
    // serializer
  }

  @Test
  public void testMessageVerifierVerifyBadKey() {
    var messageVerifier = new MessageVerifier(secret_bad);

    final String signed_message =
        "eyJfcmFpbHMiOnsibWVzc2FnZSI6Ik5qSTFNelV6TlRRMiIsImV4cCI6bnVsbCwicHVyIjoiYXBwb2ludG1lbnQifX0=--6cd2bbc8d725e6c1d73"
            + "d8d9cae7ac981c5d0b4dd7ff3c6f257ffa61db7635929";
    var message = messageVerifier.verify(signed_message, "appointment");

    assertNull(message);
  }

  @Test
  public void testMessageVerifierVerifyTamperedMessage() {
    var messageVerifier = new MessageVerifier(secret_bad);

    final String signed_message =
        "eyJfcmFpbHMiOnsibWVzc2FnZSI6Ik5qSTFNelV6TlRRMiIsImV4cCI6bnVsbCwicHVyIjoiYXBwb2ludG1lbnQifX0=--6cd2bbc8d725e6c1d73"
            + "d8d9cae7ac981c5d0b4dd7ff3c6f257ffa61db7635992";
    var message = messageVerifier.verify(signed_message, "appointment");

    assertNull(message);
  }
}
