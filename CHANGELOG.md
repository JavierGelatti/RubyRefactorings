### Version 0.1.1
#### Bug fixes
- For "open class instead of definining singleton method" refactoring:
    * The rescue/else/ensure blocks of the source method are preserved.
    * Indentation is maintained when a multi-line method is refactored.
    * Methods with symbol names can be refactored without producing errors.
    
### Version 0.1
#### Features
- Initial implementation for "open class instead of definining singleton method" refactoring.
