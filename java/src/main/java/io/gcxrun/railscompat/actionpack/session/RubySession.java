package io.gcxrun.railscompat.actionpack.session;

import io.gcxrun.railscompat.activesupport.KeyGenerator;
import io.gcxrun.railscompat.rubycore.Marshal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONException;
import org.json.JSONObject;

// TODO: [HIGH] Add comprehensive input validation for all cookie operations
// TODO: [MEDIUM] Consider thread safety for concurrent cookie decryption
public class RubySession {

  static final String COOKIE_SALT = "authenticated encrypted cookie";
  private String cookieValue;
  private KeyGenerator keyGen;

  private RubySession() {}

  public static RubySession fromCookieValue(String cookieValue, String secretKeyBase) {
    // TODO: [HIGH] Add input validation - cookieValue and secretKeyBase should not be null
    // TODO: [HIGH] Validate cookieValue format before processing
    var session = new RubySession();
    session.cookieValue = cookieValue;
    session.keyGen = new KeyGenerator(secretKeyBase, 1000, true);
    return session;
  }

  public Map<Object, Object> decrypt() {
    try {

      return decryptInternal();
    } catch (NoSuchAlgorithmException
        | InvalidKeySpecException
        | NoSuchPaddingException
        | InvalidAlgorithmParameterException
        | InvalidKeyException
        | IllegalBlockSizeException
        | BadPaddingException
        | JSONException e) {
      // TODO: [HIGH] Replace printStackTrace with proper logging
      // TODO: [HIGH] Don't expose internal exception details to caller
      e.printStackTrace();
    }
    // TODO: [MEDIUM] Consider returning Optional<Map> instead of empty map
    return Collections.emptyMap();
  }

  public Map<Object, Object> decryptInternal()
      throws NoSuchAlgorithmException,
          InvalidKeySpecException,
          InvalidAlgorithmParameterException,
          InvalidKeyException,
          NoSuchPaddingException,
          IllegalBlockSizeException,
          BadPaddingException,
          JSONException {
    // TODO: [HIGH] Add exception handling for URL decode operations
    var cookie_value = URLDecoder.decode(cookieValue, StandardCharsets.UTF_8);

    final var key = keyGen.generateKey(COOKIE_SALT, 32 * 8);

    // TODO: [HIGH] Validate params array length to prevent IndexOutOfBoundsException
    String[] params = cookie_value.split("--");
    // TODO: [HIGH] Add exception handling for Base64 decode operations
    // TODO: [MEDIUM] Validate IV length for GCM mode (should be 12 bytes)
    byte[] encrypted_data = Base64.getDecoder().decode(params[0]);
    byte[] iv = Base64.getDecoder().decode(params[1]);
    byte[] auth_tag = Base64.getDecoder().decode(params[2]);

    SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
    // int tLen, byte[] src, int offset, int len

    GCMParameterSpec parameterSpec = new GCMParameterSpec(auth_tag.length * 8, iv);

    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);

    cipher.updateAAD(new byte[0]);

    cipher.update(encrypted_data);
    cipher.update(auth_tag);
    byte[] clear_data = cipher.doFinal();

    // TODO : retrofit MessageVerifier.Metadata with purpose here
    JSONObject jsonObject = new JSONObject(new String(clear_data, StandardCharsets.UTF_8));
    String message = jsonObject.getJSONObject("_rails").get("message").toString();

    byte[] data = Base64.getDecoder().decode(message);

    var hash = Marshal.load(data);

    //noinspection unchecked
    return (Map<Object, Object>) hash;
  }
}
