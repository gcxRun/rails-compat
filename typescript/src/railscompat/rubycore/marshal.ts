/**
 * Ruby Marshal format parser for cross-language compatibility.
 * 
 * Handles Ruby objects, arrays, hashes, symbols and other Ruby data types
 * for cross-language compatibility with Rails applications.
 * 
 * @see https://docs.ruby-lang.org/en/3.0/Marshal.html
 */

/**
 * Exception thrown when Marshal parsing fails.
 */
export class MarshalException extends Error {
  public readonly cause?: Error | undefined;

  constructor(message: string, cause?: Error) {
    super(message);
    this.name = 'MarshalException';
    if (cause) {
      this.cause = cause;
    }
  }
}

/**
 * Ruby Marshal type constants.
 */
export enum RubyType {
  NONE = 0,
  HASH = 123,        // {
  ARRAY = 91,        // [
  SYMBOL = 58,       // :
  SYMBOL_LINK = 59,  // ;
  IVARS = 73,        // I
  STRING = 34,       // "
  INT = 105,         // i
  BIGNUM = 108,      // l
  NIL = 48,          // 0
  TRUE = 84,         // T
  FALSE = 70,        // F
  USR_MARSHAL = 85,  // U
  EXTENDED = 101,    // e
  USERDEF = 117,     // u
  LINK = 64,         // @
  OBJECT = 111,      // o
}

/**
 * Wrapper for complex Ruby objects.
 */
export class ObjectWrapper {
  public readonly rubyType: RubyType;
  public readonly obj: unknown;
  public readonly children: unknown[] = [];

  constructor(rubyType: RubyType, obj?: unknown) {
    this.rubyType = rubyType;
    this.obj = obj;
  }

  public static wrap(rubyType: RubyType, obj?: unknown): ObjectWrapper {
    return new ObjectWrapper(rubyType, obj);
  }

  public add(obj: unknown): void {
    this.children.push(obj);
  }

  public toString(): string {
    return RubyType[this.rubyType];
  }
}

/**
 * Ruby Marshal format parser.
 */
export class Marshal {
  private static readonly MAX_RECURSION_DEPTH = 1000;
  private static readonly MAX_DATA_SIZE = 100 * 1024 * 1024; // 100MB limit

  private readonly data: Uint8Array;
  private position = 0;
  private readonly symbols: unknown[] = [];
  private recursionDepth = 0;

  private constructor(data: Uint8Array) {
    if (!data) {
      throw new MarshalException('Input data cannot be null or undefined');
    }
    if (data.length === 0) {
      throw new MarshalException('Input data cannot be empty');
    }
    if (data.length > Marshal.MAX_DATA_SIZE) {
      throw new MarshalException(
        `Input data too large: ${data.length} bytes (max: ${Marshal.MAX_DATA_SIZE})`
      );
    }
    this.data = data;
  }

  /**
   * Load and parse Marshal data from Uint8Array.
   */
  public static load(data: Uint8Array): unknown {
    try {
      return new Marshal(data).load();
    } catch (error) {
      if (error instanceof MarshalException) {
        throw error;
      }
      throw new MarshalException('Failed to parse Marshal data', error as Error);
    }
  }

  /**
   * Load and parse Marshal data from base64 string.
   */
  public static loadB64(b64Data: string): unknown {
    if (!b64Data) {
      throw new MarshalException('Base64 encoded data cannot be null or undefined');
    }
    try {
      const binaryString = atob(b64Data);
      const data = new Uint8Array(binaryString.length);
      for (let i = 0; i < binaryString.length; i++) {
        data[i] = binaryString.charCodeAt(i);
      }
      return new Marshal(data).load();
    } catch (error) {
      if (error instanceof MarshalException) {
        throw error;
      }
      throw new MarshalException('Invalid Base64 data', error as Error);
    }
  }

  private readByte(): number {
    if (this.position >= this.data.length) {
      throw new MarshalException(`Unexpected end of data at position ${this.position}`);
    }
    const byte = this.data[this.position++];
    if (byte === undefined) {
      throw new MarshalException(`Unexpected end of data at position ${this.position - 1}`);
    }
    // Convert unsigned byte to signed byte (like Java's byte type)
    return byte > 127 ? byte - 256 : byte;
  }

  private readBytes(count: number): Uint8Array {
    if (this.position + count > this.data.length) {
      throw new MarshalException(
        `Not enough data: need ${count} bytes, have ${this.data.length - this.position}`
      );
    }
    const result = this.data.slice(this.position, this.position + count);
    this.position += count;
    return result;
  }

  private read(): unknown {
    this.recursionDepth++;
    if (this.recursionDepth > Marshal.MAX_RECURSION_DEPTH) {
      throw new MarshalException(`Maximum recursion depth exceeded: ${Marshal.MAX_RECURSION_DEPTH}`);
    }

    try {
      const typeByte = this.readByte();
      const rubyType = this.getRubyType(typeByte);

      if (rubyType === null) {
        throw new MarshalException(`Unsupported Marshal type: 0x${typeByte.toString(16).padStart(2, '0')}`);
      }

      switch (rubyType) {
        case RubyType.NIL:
          return null;
        case RubyType.TRUE:
          return true;
        case RubyType.FALSE:
          return false;
        case RubyType.INT:
          return this.readInt();
        case RubyType.BIGNUM:
          return this.readBignum();
        case RubyType.HASH:
          return this.readHash();
        case RubyType.ARRAY:
          return this.readArray();
        case RubyType.STRING:
          return this.readString();
        case RubyType.IVARS:
          return this.readIVar();
        case RubyType.SYMBOL:
          return this.readSymbol();
        case RubyType.SYMBOL_LINK:
          return this.readSymbolLink();
        case RubyType.USR_MARSHAL:
          return this.readUsrMarshal();
        case RubyType.EXTENDED:
          return this.readExtended();
        case RubyType.USERDEF:
          return this.readUserDef();
        case RubyType.LINK:
          return this.readLink();
        case RubyType.OBJECT:
          return this.readObject();
        default:
          throw new MarshalException(`Unsupported Marshal type: 0x${typeByte.toString(16).padStart(2, '0')}`);
      }
    } finally {
      this.recursionDepth--;
    }
  }

  private getRubyType(typeByte: number): RubyType | null {
    const numericValues = Object.values(RubyType).filter(v => typeof v === 'number') as number[];
    return numericValues.includes(typeByte) ? (typeByte as RubyType) : null;
  }

  private readObject(): ObjectWrapper {
    const typeByte = this.readByte();
    const symbolType = this.getRubyType(typeByte);

    let symbol: unknown;
    if (symbolType === RubyType.SYMBOL) {
      symbol = this.readSymbol();
    } else if (symbolType === RubyType.SYMBOL_LINK) {
      symbol = this.readSymbolLink();
    } else {
      throw new MarshalException(`Expected SYMBOL but got: 0x${typeByte.toString(16).padStart(2, '0')}`);
    }

    const wrapper = ObjectWrapper.wrap(RubyType.OBJECT, symbol);
    const size = Number(this.readInt());
    for (let i = 0; i < size; i++) {
      wrapper.add(this.read()); // key
      wrapper.add(this.read()); // value
    }
    return wrapper;
  }

  private readLink(): ObjectWrapper {
    const link = this.readInt();
    return ObjectWrapper.wrap(RubyType.LINK, link);
  }

  private readUserDef(): ObjectWrapper {
    const typeByte = this.readByte();
    const symbolType = this.getRubyType(typeByte);

    let symbol: unknown;
    if (symbolType === RubyType.SYMBOL) {
      symbol = this.readSymbol();
    } else if (symbolType === RubyType.SYMBOL_LINK) {
      symbol = this.readSymbolLink();
    } else {
      throw new MarshalException(`Expected SYMBOL but got: 0x${typeByte.toString(16).padStart(2, '0')}`);
    }

    const size = Number(this.readInt());
    if (size < 0 || size > Marshal.MAX_DATA_SIZE) {
      throw new MarshalException(`Invalid size for USERDEF: ${size}`);
    }

    const dataBytes = this.readBytes(size);
    const wrapper = ObjectWrapper.wrap(RubyType.USERDEF, symbol);
    wrapper.add(new TextDecoder('utf-8').decode(dataBytes));
    return wrapper;
  }

  private readExtended(): ObjectWrapper {
    return ObjectWrapper.wrap(RubyType.EXTENDED);
  }

  private readUsrMarshal(): ObjectWrapper {
    const typeByte = this.readByte();
    const symbolType = this.getRubyType(typeByte);

    let symbol: unknown;
    if (symbolType === RubyType.SYMBOL) {
      symbol = this.readSymbol();
    } else if (symbolType === RubyType.SYMBOL_LINK) {
      symbol = this.readSymbolLink();
    } else {
      throw new MarshalException(`Expected SYMBOL but got: 0x${typeByte.toString(16).padStart(2, '0')}`);
    }

    const obj = this.read();
    const wrapper = ObjectWrapper.wrap(RubyType.USR_MARSHAL, symbol);
    wrapper.add(obj);
    return wrapper;
  }

  private readBignum(): bigint {
    const signByte = this.readByte(); // Sign byte: '+' (43) for positive, '-' (45) for negative
    const length = Number(this.readInt());
    if (length < 0 || length > Marshal.MAX_DATA_SIZE / 2) {
      throw new MarshalException(`Invalid size for BIGNUM: ${length}`);
    }

    const dataBytes = this.readBytes(length * 2);

    // Build BigInt from little-endian bytes
    let result = 0n;
    for (let i = 0; i < dataBytes.length; i++) {
      const byte = dataBytes[i];
      if (byte === undefined) {
        throw new MarshalException(`Invalid byte at position ${i} in bignum data`);
      }
      const unsignedByte = BigInt(byte);
      result += unsignedByte << (BigInt(i) * 8n);
    }

    // Apply sign
    if (signByte === 45) { // '-'
      result = -result;
    }

    return result;
  }

  private readArray(): unknown[] {
    const size = Number(this.readInt());
    if (size < 0 || size > Marshal.MAX_DATA_SIZE / 100) {
      throw new MarshalException(`Invalid array size: ${size}`);
    }

    const result: unknown[] = [];
    for (let i = 0; i < size; i++) {
      result.push(this.read());
    }
    return result;
  }

  private readSymbolLink(): string {
    const link = Number(this.readInt());
    if (link < 0 || link >= this.symbols.length) {
      throw new MarshalException(`Invalid symbol link: ${link} (available: ${this.symbols.length})`);
    }
    return this.symbols[link] as string;
  }

  private readSymbol(): string {
    const size = Number(this.readInt());
    if (size < 0 || size > Marshal.MAX_DATA_SIZE / 10) {
      throw new MarshalException(`Invalid symbol size: ${size}`);
    }

    let symbol: string;
    if (size === 0) {
      symbol = ':';
    } else {
      const dataBytes = this.readBytes(size);
      const symbolName = new TextDecoder('utf-8').decode(dataBytes);
      symbol = ':' + symbolName;
    }

    this.symbols.push(symbol);
    return symbol;
  }

  /**
   * Ruby uses sophisticated system to pack integers: first `code` byte either determines packing
   * scheme or carries encoded immediate value (thus allowing smaller values from -123 to 122
   * (inclusive) to take only one byte. There are 11 encoding schemes in total.
   */
  private readInt(): bigint {
    const c = this.readByte();

    // Special case for 0
    if (c === 0) {
      return 0n;
    }

    // Immediate positive values 1..122
    if (c > 4) {
      return BigInt(c - 5);
    }

    // Immediate negative values -123..-1
    if (c < -4) {
      return BigInt(c + 5);
    }

    // Multi-byte positive numbers
    if (c > 0) {
      let x = 0;
      for (let i = 0; i < c; i++) {
        const byte = this.readByte();
        const unsignedByte = byte < 0 ? byte + 256 : byte;
        x |= unsignedByte << (8 * i);
      }
      return BigInt(x);
    } else {
      // Multi-byte negative numbers
      let x = -1;
      for (let i = 0; i < -c; i++) {
        const a = ~(0xFF << (8 * i));
        let b = this.readByte();
        b = b << (8 * i);
        x = (x & a) | b;
      }
      return BigInt(x);
    }
  }

  private readHash(): Map<unknown, unknown> {
    const size = Number(this.readInt());
    if (size < 0 || size > Marshal.MAX_DATA_SIZE / 100) {
      throw new MarshalException(`Invalid hash size: ${size}`);
    }

    const result = new Map<unknown, unknown>();
    for (let i = 0; i < size; i++) {
      const key = this.read();
      const value = this.read();
      result.set(key, value);
    }
    return result;
  }

  private readString(): string {
    const size = Number(this.readInt());
    if (size < 0 || size > Marshal.MAX_DATA_SIZE) {
      throw new MarshalException(`Invalid string size: ${size}`);
    }

    const dataBytes = this.readBytes(size);
    return new TextDecoder('utf-8').decode(dataBytes);
  }

  private readIVar(): unknown {
    const result = this.read();
    const size = Number(this.readInt());

    const attachments: unknown[] = [];
    for (let i = 0; i < size; i++) {
      const sym = this.read();
      attachments.push(sym);
      attachments.push(this.read());
    }

    return result;
  }

  private load(): unknown {
    // Check Marshal version bytes (4.8)
    const major = this.readByte();
    const minor = this.readByte();
    if (major !== 4 || minor !== 8) {
      throw new MarshalException(`Unsupported Marshal version (expected 4.8, got ${major}.${minor})`);
    }

    return this.read();
  }
}