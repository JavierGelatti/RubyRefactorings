### Version 0.1.6
#### New features
- Added "replace conditional with guard clause" refactoring.

#### Enhancements
- Improve precision of spot locations in "remove braces from last argument hash" refactoring documentation.

#### Bug fixes
- The "remove braces from last hash argument" refactoring no longer generates an error when trying to refactor a
  hash-to-arguments conversion (`**`) that's not applied to a literal hash.
