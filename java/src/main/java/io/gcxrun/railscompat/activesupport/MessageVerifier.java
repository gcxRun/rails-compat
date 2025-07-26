package io.gcxrun.railscompat.activesupport;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONException;
import org.json.JSONObject;

public class MessageVerifier {

  private final byte[] secret;

  /**
   * Creates a new MessageVerifier with the provided secret. It is only using the JSON serializer
   * and Digest SHA256
   *
   * <p><code>
   * ActiveSupport::MessageVerifier.new secret, digest: "SHA256", serializer: JSON
   * </code>
   *
   * @param secret
   */
  public MessageVerifier(byte[] secret) {
    // TODO: [HIGH] Add input validation - secret should not be null or empty
    // TODO: [HIGH] Add minimum secret length validation (e.g., >= 32 bytes)
    this.secret = secret;
  }

  private static String encode(byte[] data) {
    return Base64.getEncoder().encodeToString(data);
  }

  private static String encode(String data) {
    return encode(data.getBytes(StandardCharsets.UTF_8));
  }

  private static String decode(byte[] data) {
    // TODO: [MEDIUM] Add exception handling for invalid Base64 data
    return new String(Base64.getDecoder().decode(data), StandardCharsets.UTF_8);
  }

  private static String decode(String data) {
    return decode(data.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Generates a signed message for the provided value. The message is signed with the
   * MessageVerifier's secret. Returns Base64-encoded message joined with the generated signature.
   *
   * @param value
   * @param purpose
   * @return
   */
  public String generate(Object value, String purpose) {
    // TODO: [HIGH] Add input validation - purpose should not be null
    // TODO: [MEDIUM] Add value serialization validation
    String data = encode(Metadata.wrap(serialise(value), purpose).toJson());
    return String.format("%s--%s", data, generateDigest(data));
  }

  private String serialise(Object value) {
    // TODO: [HIGH] Add null check for value parameter
    // TODO: [MEDIUM] Consider proper JSON serialization instead of toString()
    return value.toString();
  }

  private String generateDigest(String data) {
    return generateDigest(data.getBytes(StandardCharsets.UTF_8));
  }

  private String generateDigest(byte[] data) {
    // TODO: [MEDIUM] Consider making Mac instance thread-local for better performance
    SecretKeySpec keySpec = new SecretKeySpec(secret, "HmacSHA256");
    Mac mac = null;
    try {
      mac = Mac.getInstance("HmacSHA256");
      mac.init(keySpec);
    } catch (InvalidKeyException | NoSuchAlgorithmException e) {
      // TODO: [HIGH] Don't expose internal exception details - create custom exception
      throw new RuntimeException(e);
    }
    byte[] digest = mac.doFinal(data);
    StringBuilder sb = new StringBuilder();
    for (byte b : digest) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  /**
   * Decodes the signed message using the MessageVerifier's secret. Returns the decoded message if
   * it was signed with the same secret, otherwise returns nil.
   *
   * @param signedMessage
   * @param purpose
   * @return
   */
  public Object verify(String signedMessage, String purpose) {
    // TODO: [HIGH] Add input validation - signedMessage and purpose should not be null
    // TODO: [HIGH] Validate splits array length to prevent IndexOutOfBoundsException
    String[] splits = signedMessage.split("--");
    var data = splits[0];
    var digest = splits[1];
    // TODO: [HIGH] Use timing-safe comparison to prevent timing attacks
    if (!digest.equals(generateDigest(data))) {
      return null;
    }

    return Metadata.verify(decode(data), purpose);
  }

  public static class Metadata {

    private final String message;
    private final String purpose;

    private Metadata(String message, String purpose) {
      this.message = message;
      this.purpose = purpose;
    }

    public static Metadata wrap(String message, String purpose) {
      return new Metadata(message, purpose);
    }

    public static Object verify(String data, String purpose) {
      return Metadata.fromJson(data).verify(purpose);
    }

    private static Metadata fromJson(String data) {
      // TODO: [HIGH] Add input validation - data should not be null
      try {
        JSONObject jsonObject = new JSONObject(data);
        String message = jsonObject.getJSONObject("_rails").get("message").toString();
        String purpose = jsonObject.getJSONObject("_rails").get("pur").toString();
        return new Metadata(decode(message), purpose);
      } catch (JSONException e) {
        // TODO: [HIGH] Don't expose internal exception details - create custom exception
        throw new RuntimeException(e);
      }
    }

    private Object verify(String purpose) {
      if (this.purpose.equals(purpose)) {
        return message;
      } else {
        return null;
      }
    }

    /**
     * Returns a JSON like string message is b64 encoded
     *
     * @return
     */
    public String toJson() {
      // TODO: [MEDIUM] Escape JSON strings properly to prevent injection
      // TODO: [LOW] Consider using a proper JSON library for construction
      return String.format(
          "{\"_rails\":{\"message\":\"%s\",\"exp\":null,\"pur\":\"%s\"}}",
          encode(message), purpose);
    }
  }
}