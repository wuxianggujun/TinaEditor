; Log file highlights (Android logcat format)

; Date and time
(date) @number
(time) @number

; Process and thread IDs
(pid) @number
(tid) @number
(thread_name) @string

; Tag
(tag) @label

; Message content
(message) @string

; Priority levels - different colors for different log levels
(priority) @keyword

; Verbose - gray/dim
((priority) @comment
  (#any-of? @comment "V" "VERBOSE"))

; Debug - blue
((priority) @function
  (#any-of? @function "D" "DEBUG"))

; Info - green
((priority) @string
  (#any-of? @string "I" "INFO"))

; Fine - cyan
((priority) @type
  (#any-of? @type "F" "FINE"))

; Trace - magenta
((priority) @attribute
  (#any-of? @attribute "T" "TRACE"))

; Warning - yellow
((priority) @constant
  (#any-of? @constant "W" "WARN"))

; Error - red
((priority) @keyword.exception
  (#any-of? @keyword.exception "E" "ERROR"))

; Silent - special
((priority) @comment
  (#any-of? @comment "S" "SILENT"))

; Punctuation
[
  ","
  "-"
  "."
  ":"
  "["
  "]"
] @punctuation.delimiter

; Header
(begin_header) @comment
