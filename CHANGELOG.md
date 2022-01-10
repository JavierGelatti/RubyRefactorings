### Version 0.1.17

#### Enhancements
- Disable remove braces from last hash argument refactoring when using Ruby 3.0 or later (since it's no longer 
  a refactoring in that case).

#### Bugfixes
- Correctly remove useless if statements when they are inside parentheses.
