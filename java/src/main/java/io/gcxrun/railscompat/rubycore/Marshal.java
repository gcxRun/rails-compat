package io.gcxrun.railscompat.rubycore;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ruby Marshal format parser for cross-language compatibility.
 * 
 * Handles Ruby objects, arrays, hashes, symbols and other Ruby data types
 * for cross-language compatibility with Rails applications.
 * 
 * @see <a href="https://docs.ruby-lang.org/en/3.0/Marshal.html">Ruby Marshal documentation</a>
 */
// TODO: [HIGH] Add comprehensive input validation for malformed Marshal data
// TODO: [MEDIUM] Consider adding limits to prevent DoS attacks (max objects, max depth)
public class Marshal {

  private final ArrayList<Object> symbols = new ArrayList<>();
  private final ByteBuffer bb;

  private Marshal(byte[] bytes) {
    // TODO: [HIGH] Add input validation - bytes should not be null or empty
    this.bb = ByteBuffer.wrap(bytes);
  }

  public static Object load(byte[] bytes) {
    // TODO: [HIGH] Add input validation and size limits
    // TODO: [MEDIUM] Add exception handling for corrupted data
    return (new Marshal(bytes)).load();
  }

  public static Object load(String b64EncodedBytes) {
    // TODO: [HIGH] Add input validation - b64EncodedBytes should not be null
    // TODO: [MEDIUM] Add exception handling for invalid Base64 data
    return (new Marshal(Base64.getDecoder().decode(b64EncodedBytes))).load();
  }

  private byte readByte() {
    // TODO: [HIGH] Add bounds checking to prevent BufferUnderflowException
    return this.bb.get();
  }

  private Object read() {
    // TODO: [MEDIUM] Add recursion depth limit to prevent stack overflow

    byte c = readByte();
    RubyType type = RubyType.valueOf(c);
    if (type == null) {
      // TODO: [HIGH] Don't expose internal exception details - create custom exception
      throw new RuntimeException("Not a managed type: " + c);
    }

    return switch (type) {
      case NIL -> // 0
      null;
      case TRUE -> // T
      true;
      case FALSE -> // F
      false;
      case INT -> // i
      readInt();
      case BIGNUM -> readBigNum();
      case HASH -> // {
      readHash();
      case ARRAY -> // {
      readArray();
      case STRING -> readString();
      case IVARS -> // 49
      readIVar();
      case SYMBOL -> // 49
      readSymbol();
      case SYMBOL_LINK -> // 49
      readSymbolLink();
      case USR_MARSHAL -> readUsrMarshal();
      case EXTENDED -> readExtended();
      case USERDEF -> readUserDef();
      case LINK -> readLink();
      case OBJECT -> readRObject();
      default -> throw new RuntimeException("Not a managed type: " + c);
    };
  }

  private Object readRObject() {
    var c = readByte();
    var type = RubyType.valueOf(c);

    var symbol =
        switch (type) {
          case SYMBOL -> readSymbol();
          case SYMBOL_LINK -> readSymbolLink();
          default -> throw new RuntimeException("Expecting SYMBOL but got : " + c);
        };

    var wrapper = ObjectWrapper.wrap(RubyType.OBJECT, symbol);

    var size = readInt();
    for (int i = 0; i < size; i++) {
      wrapper.add(read());
      wrapper.add(read());
    }
    return wrapper;
  }

  private Object readLink() {
    var link = readInt();
    return ObjectWrapper.wrap(RubyType.LINK, link);
  }

  private Object readUserDef() {
    var c = readByte();
    var type = RubyType.valueOf(c);

    Object symbol =
        switch (type) {
          case SYMBOL -> readSymbol();
          case SYMBOL_LINK -> readSymbolLink();
          default -> throw new RuntimeException("Expecting SYMBOL but got : " + c);
        };

    var size = readInt();

    var tmpBytes = new byte[size.intValue()];
    this.bb.get(tmpBytes);

    var wrapper = ObjectWrapper.wrap(RubyType.USERDEF, symbol);
    wrapper.add(new String(tmpBytes));
    return wrapper;
  }

  private Object readExtended() {
    return ObjectWrapper.wrap(RubyType.EXTENDED);
  }

  private Object readUsrMarshal() {
    var c = readByte();
    var type = RubyType.valueOf(c);
    Object symbol =
        switch (type) {
          case SYMBOL -> readSymbol();
          case SYMBOL_LINK -> readSymbolLink();
          default -> throw new RuntimeException("Expecting SYMBOL but got : " + c);
        };

    var o = read();
    var wrapper = ObjectWrapper.wrap(RubyType.USR_MARSHAL, symbol);
    wrapper.add(o);
    return wrapper;
  }

  private Object readBigNum() {
    readByte(); // == 45 ? -1 : 1; // but why ?
    long l = readInt();
    var tmpBytes = new byte[(int) l * 2];
    this.bb.get(tmpBytes);

    long result = 0;
    for (int i = 0; i < tmpBytes.length; i++) {
      result += (long) tmpBytes[i] << (i * 8);
    }
    return result;
  }

  private Object readArray() {
    var size = readInt().intValue();
    List<Object> result = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      result.add(read());
    }
    return result;
  }

  private Object readSymbolLink() {
    var link = readInt();

    return symbols.get(link.intValue());
  }

  private Object readSymbol() {
    var size = readInt().intValue();
    if (size == 0) {
      return "";
    }
    var tmpBytes = new byte[size];
    this.bb.get(tmpBytes);
    var result = new String(tmpBytes);
    var sym = ":" + result;
    symbols.add(sym);
    return sym;
  }

  /**
   * Ruby uses sophisticated system to pack integers: first `code` byte either determines packing
   * scheme or carries encoded immediate value (thus allowing smaller values from -123 to 122
   * (inclusive) to take only one byte. There are 11 encoding schemes in total:
   *
   * <p>* 1/ 0 is encoded specially (as 0) * 2/ 1..122 are encoded as immediate value with a shift *
   * 4/ 123..255 are encoded with code of 0x01 and 1 extra byte * 6/ 0x100..0xffff are encoded with
   * code of 0x02 and 2 extra bytes * 7/ 0x10000..0xffffff are encoded with code of 0x03 and 3 extra
   * bytes * 8/ 0x1000000..0xffffffff are encoded with code of 0x04 and 4 extra bytes * 3/ -123..-1
   * are encoded as immediate value with another shift * 5/ -256..-124 are encoded with code of 0xff
   * and 1 extra byte * -0x10000..-257 are encoded with code of 0xfe and 2 extra bytes *
   * -0x1000000..0x10001 are encoded with code of 0xfd and 3 extra bytes * -0x40000000..-0x1000001
   * are encoded with code of 0xfc and 4 extra bytes
   */
  private Long readInt() {

    int c = readByte();

    if (c == 0) // 1
    {
      return (long) 0;
    }

    if (c > 4) // 2
    {
      return (long) (c - 5);
    }

    if (c < -4) // 3
    {
      return (long) (c + 5);
    }

    if (c > 0) {
      int x = 0;
      for (int i = 0; i < c; i++) {
        int n = Byte.toUnsignedInt(bb.get());
        n = n << (8 * i);
        x |= n;
      }
      return (long) x;
    } else {
      int x = -1;
      for (int i = 0; i < -c; i++) {
        int a = ~(0xFF << (8 * i)); // wtf is this magic
        int b = bb.get();
        b = b << (8 * i);
        x = (x & a) | b;
      }
      return (long) x;
    }
  }

  private Map<Object, Object> readHash() {
    int size = readInt().intValue();
    Map<Object, Object> map = new HashMap<>();
    for (int i = 0; i < size; i++) {
      Object key = read();
      Object value = read();
      map.put(key, value);
    }
    return map;
  }

  private String readString() {
    var size = readInt().intValue();
    var tmpBytes = new byte[size];
    this.bb.get(tmpBytes);
    return new String(tmpBytes, StandardCharsets.UTF_8);
  }

  private Object readIVar() {
    var result = read();
    var size = readInt();

    var attachements = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      var sym = read();
      attachements.add(sym);
      attachements.add(read());
    }

    return result;
  }

  private Object load() {
    if (!(readByte() == 4 && readByte() == 8)) {
      throw new RuntimeException("Unsupported version:");
    }
    return read();
  }

  enum RubyType {
    NONE(0),
    HASH(123),
    ARRAY(91),
    SYMBOL(58),
    SYMBOL_LINK(59),
    IVARS(73),
    STRING(34),
    INT(105),
    BIGNUM(108), // l
    NIL(48),
    TRUE(84),
    FALSE(70), // ),

    USR_MARSHAL(85), // U
    EXTENDED(101), // e
    USERDEF(117), // u
    LINK(64), // @
    OBJECT(111), // o
    ; // U

    private static final Map<Byte, RubyType> byCode = new HashMap<>(13);

    static {
      for (RubyType e : RubyType.values()) {
        byCode.put(e.code, e);
      }
    }

    private final byte code;

    RubyType(int c) {
      this.code = (byte) c;
    }

    public static RubyType valueOf(byte code) {
      return byCode.get(code);
    }
  }

  static class ObjectWrapper {

    final Object object;

    final RubyType type;

    final ArrayList<Object> children = new ArrayList<>();

    private ObjectWrapper(Object object, RubyType type) {
      this.object = object;
      this.type = type;
    }

    static ObjectWrapper wrap(RubyType type) {
      return wrap(type, null);
    }

    static ObjectWrapper wrap(RubyType type, Object o) {
      return new ObjectWrapper(o, type);
    }

    public void add(Object o) {
      children.add(o);
    }

    @Override
    public String toString() {
      return type.toString();
    }
  }
}
