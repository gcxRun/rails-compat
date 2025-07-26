"""ActiveSupport compatibility module.

Rails framework utilities compatible with ActiveSupport.
"""

from .key_generator import KeyGenerator
from .message_verifier import MessageVerifier

__all__ = ["KeyGenerator", "MessageVerifier"]