"""Ruby Marshal format parser/deserializer for cross-language compatibility.

See: https://docs.ruby-lang.org/en/3.0/Marshal.html
"""

import base64
from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Dict, List, Optional, Union


# TODO: [HIGH] Add comprehensive input validation for malformed Marshal data
# TODO: [MEDIUM] Consider adding limits to prevent DoS attacks (max objects, max depth)
class Marshal:
    """Ruby Marshal format parser.
    
    Handles Ruby objects, arrays, hashes, symbols and other Ruby data types
    for cross-language compatibility with Rails applications.
    
    See: https://docs.ruby-lang.org/en/3.0/Marshal.html
    """

    def __init__(self, data: bytes) -> None:
        """Initialize Marshal parser.
        
        Args:
            data: Raw Marshal data bytes
        """
        # TODO: [HIGH] Add input validation - bytes should not be null or empty
        self._data = data
        self._position = 0
        self._symbols: List[Any] = []

    @classmethod
    def load(cls, data: bytes) -> Any:
        """Load and parse Marshal data from bytes.
        
        Args:
            data: Marshal data as bytes
            
        Returns:
            Parsed Ruby object
        """
        # TODO: [HIGH] Add input validation and size limits
        # TODO: [MEDIUM] Add exception handling for corrupted data
        return cls(data)._load()

    @classmethod
    def load_b64(cls, b64_data: str) -> Any:
        """Load and parse Marshal data from base64 string.
        
        Args:
            b64_data: Base64 encoded Marshal data
            
        Returns:
            Parsed Ruby object
        """
        # TODO: [HIGH] Add input validation - b64EncodedBytes should not be null
        # TODO: [MEDIUM] Add exception handling for invalid Base64 data
        data = base64.b64decode(b64_data)
        return cls(data)._load()

    def _read_byte(self) -> int:
        """Read a single byte from the data stream.
        
        Returns:
            Byte value as signed integer (-128 to 127)
        """
        # TODO: [HIGH] Add bounds checking to prevent IndexError
        if self._position >= len(self._data):
            raise RuntimeError(f"Unexpected end of data at position {self._position}")
            
        byte_val = self._data[self._position]
        self._position += 1
        
        # Convert unsigned byte to signed byte (like Java's byte type)
        return byte_val if byte_val < 128 else byte_val - 256

    def _read(self) -> Any:
        """Read and parse the next Ruby object from the stream.
        
        Returns:
            Parsed Ruby object
        """
        # TODO: [MEDIUM] Add recursion depth limit to prevent stack overflow
        
        type_byte = self._read_byte()
        ruby_type = RubyType.from_byte(type_byte)
        
        if ruby_type is None:
            # TODO: [HIGH] Don't expose internal exception details - create custom exception
            raise RuntimeError(f"Not a managed type: {type_byte}")
        
        if ruby_type == RubyType.NIL:
            return None
        elif ruby_type == RubyType.TRUE:
            return True
        elif ruby_type == RubyType.FALSE:
            return False
        elif ruby_type == RubyType.INT:
            return self._read_int()
        elif ruby_type == RubyType.BIGNUM:
            return self._read_bignum()
        elif ruby_type == RubyType.HASH:
            return self._read_hash()
        elif ruby_type == RubyType.ARRAY:
            return self._read_array()
        elif ruby_type == RubyType.STRING:
            return self._read_string()
        elif ruby_type == RubyType.IVARS:
            return self._read_ivar()
        elif ruby_type == RubyType.SYMBOL:
            return self._read_symbol()
        elif ruby_type == RubyType.SYMBOL_LINK:
            return self._read_symbol_link()
        elif ruby_type == RubyType.USR_MARSHAL:
            return self._read_usr_marshal()
        elif ruby_type == RubyType.EXTENDED:
            return self._read_extended()
        elif ruby_type == RubyType.USERDEF:
            return self._read_userdef()
        elif ruby_type == RubyType.LINK:
            return self._read_link()
        elif ruby_type == RubyType.OBJECT:
            return self._read_object()
        else:
            raise RuntimeError(f"Not a managed type: {type_byte}")

    def _read_object(self) -> "ObjectWrapper":
        """Read Ruby object with attributes."""
        type_byte = self._read_byte()
        symbol_type = RubyType.from_byte(type_byte)
        
        if symbol_type == RubyType.SYMBOL:
            symbol = self._read_symbol()
        elif symbol_type == RubyType.SYMBOL_LINK:
            symbol = self._read_symbol_link()
        else:
            raise RuntimeError(f"Expecting SYMBOL but got: {type_byte}")
        
        wrapper = ObjectWrapper.wrap(RubyType.OBJECT, symbol)
        
        size = self._read_int()
        for _ in range(size):
            wrapper.add(self._read())  # key
            wrapper.add(self._read())  # value
            
        return wrapper

    def _read_link(self) -> "ObjectWrapper":
        """Read object link reference."""
        link = self._read_int()
        return ObjectWrapper.wrap(RubyType.LINK, link)

    def _read_userdef(self) -> "ObjectWrapper":
        """Read user-defined object."""
        type_byte = self._read_byte()
        symbol_type = RubyType.from_byte(type_byte)
        
        if symbol_type == RubyType.SYMBOL:
            symbol = self._read_symbol()
        elif symbol_type == RubyType.SYMBOL_LINK:
            symbol = self._read_symbol_link()
        else:
            raise RuntimeError(f"Expecting SYMBOL but got: {type_byte}")
        
        size = self._read_int()
        data_bytes = self._read_bytes(size)
        
        wrapper = ObjectWrapper.wrap(RubyType.USERDEF, symbol)
        wrapper.add(data_bytes.decode('utf-8'))
        return wrapper

    def _read_extended(self) -> "ObjectWrapper":
        """Read extended object."""
        return ObjectWrapper.wrap(RubyType.EXTENDED)

    def _read_usr_marshal(self) -> "ObjectWrapper":
        """Read user marshal object."""
        type_byte = self._read_byte()
        symbol_type = RubyType.from_byte(type_byte)
        
        if symbol_type == RubyType.SYMBOL:
            symbol = self._read_symbol()
        elif symbol_type == RubyType.SYMBOL_LINK:
            symbol = self._read_symbol_link()
        else:
            raise RuntimeError(f"Expecting SYMBOL but got: {type_byte}")
        
        obj = self._read()
        wrapper = ObjectWrapper.wrap(RubyType.USR_MARSHAL, symbol)
        wrapper.add(obj)
        return wrapper

    def _read_bignum(self) -> int:
        """Read Ruby Bignum as Python int."""
        self._read_byte()  # Sign byte (== 45 ? -1 : 1; but why ?)
        length = self._read_int()
        data_bytes = self._read_bytes(length * 2)
        
        result = 0
        for i, byte_val in enumerate(data_bytes):
            # Convert unsigned byte and shift
            unsigned_byte = byte_val if byte_val >= 0 else byte_val + 256
            result += unsigned_byte << (i * 8)
            
        return result

    def _read_array(self) -> List[Any]:
        """Read Ruby Array as Python list."""
        size = self._read_int()
        result = []
        for _ in range(size):
            result.append(self._read())
        return result

    def _read_symbol_link(self) -> str:
        """Read symbol link reference."""
        link = self._read_int()
        if link >= len(self._symbols):
            raise RuntimeError(f"Invalid symbol link: {link}")
        return self._symbols[link]

    def _read_symbol(self) -> str:
        """Read Ruby Symbol as string with ':' prefix."""
        size = self._read_int()
        if size == 0:
            symbol = ":"
        else:
            data_bytes = self._read_bytes(size)
            symbol = ":" + data_bytes.decode('utf-8')
            
        self._symbols.append(symbol)
        return symbol

    def _read_int(self) -> int:
        """Read Ruby integer using Ruby's sophisticated packing system.
        
        Ruby uses a sophisticated system to pack integers: first `code` byte either 
        determines packing scheme or carries encoded immediate value (thus allowing 
        smaller values from -123 to 122 (inclusive) to take only one byte. 
        There are 11 encoding schemes in total.
        
        Returns:
            Parsed integer value
        """
        c = self._read_byte()
        
        # Special case for 0
        if c == 0:  # 1
            return 0
        
        # Immediate positive values 1..122
        if c > 4:  # 2
            return c - 5
        
        # Immediate negative values -123..-1
        if c < -4:  # 3
            return c + 5
        
        # Multi-byte positive numbers
        if c > 0:
            x = 0
            for i in range(c):
                byte_val = self._read_byte()
                # Convert to unsigned byte
                unsigned_byte = byte_val if byte_val >= 0 else byte_val + 256
                x |= unsigned_byte << (8 * i)
            return x
        else:
            # Multi-byte negative numbers
            x = -1
            for i in range(-c):
                a = ~(0xFF << (8 * i))  # wtf is this magic (preserved from Java)
                b = self._read_byte()
                b = b << (8 * i)
                x = (x & a) | b
            return x

    def _read_hash(self) -> Dict[Any, Any]:
        """Read Ruby Hash as Python dict."""
        size = self._read_int()
        result = {}
        for _ in range(size):
            key = self._read()
            value = self._read()
            result[key] = value
        return result

    def _read_string(self) -> str:
        """Read Ruby String as Python str."""
        size = self._read_int()
        data_bytes = self._read_bytes(size)
        return data_bytes.decode('utf-8')

    def _read_ivar(self) -> Any:
        """Read Ruby object with instance variables."""
        result = self._read()
        size = self._read_int()
        
        attachments = []
        for _ in range(size):
            sym = self._read()
            attachments.append(sym)
            attachments.append(self._read())
        
        return result

    def _read_bytes(self, count: int) -> bytes:
        """Read multiple bytes from the data stream.
        
        Args:
            count: Number of bytes to read
            
        Returns:
            Bytes data
        """
        if self._position + count > len(self._data):
            raise RuntimeError(f"Not enough data: need {count} bytes, have {len(self._data) - self._position}")
        
        result = self._data[self._position:self._position + count]
        self._position += count
        return result

    def _load(self) -> Any:
        """Load and parse the Marshal data.
        
        Returns:
            Parsed Ruby object
        """
        # Check Marshal version bytes
        if not (self._read_byte() == 4 and self._read_byte() == 8):
            raise RuntimeError("Unsupported Marshal version")
        
        return self._read()


class RubyType(Enum):
    """Ruby Marshal type constants."""
    
    NONE = 0
    HASH = 123      # {
    ARRAY = 91      # [
    SYMBOL = 58     # :
    SYMBOL_LINK = 59  # ;
    IVARS = 73      # I
    STRING = 34     # "
    INT = 105       # i
    BIGNUM = 108    # l
    NIL = 48        # 0
    TRUE = 84       # T
    FALSE = 70      # F
    USR_MARSHAL = 85  # U
    EXTENDED = 101   # e
    USERDEF = 117   # u
    LINK = 64       # @
    OBJECT = 111    # o

    @classmethod
    def from_byte(cls, byte_val: int) -> Optional["RubyType"]:
        """Get RubyType from byte value.
        
        Args:
            byte_val: Byte value
            
        Returns:
            RubyType if found, None otherwise
        """
        for ruby_type in cls:
            if ruby_type.value == byte_val:
                return ruby_type
        return None


@dataclass
class ObjectWrapper:
    """Wrapper for complex Ruby objects."""
    
    ruby_type: RubyType
    obj: Any = None
    children: List[Any] = field(default_factory=list)

    @classmethod
    def wrap(cls, ruby_type: RubyType, obj: Any = None) -> "ObjectWrapper":
        """Create ObjectWrapper.
        
        Args:
            ruby_type: Ruby type
            obj: Object data
            
        Returns:
            ObjectWrapper instance
        """
        return cls(ruby_type=ruby_type, obj=obj)

    def add(self, obj: Any) -> None:
        """Add child object.
        
        Args:
            obj: Object to add
        """
        self.children.append(obj)

    def __str__(self) -> str:
        """String representation."""
        return self.ruby_type.name