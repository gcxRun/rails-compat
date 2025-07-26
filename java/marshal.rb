
require 'base64'


# Read the input string from stdin
input_string = gets.chomp

# Evaluate the input string as a Ruby literal and store the resulting object
ruby_object = eval(input_string)

# Marshal the object and encode it in Base64
serialized_object = Base64.strict_encode64(Marshal.dump(ruby_object))

# Print the serialized and encoded object to stdout
print serialized_object