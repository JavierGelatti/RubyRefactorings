### Version 0.1.7
#### Enhancements
- Consider `fail` as an exception-raising method for "replace conditional with guard clause" refactoring.

#### Bug fixes
- Fix issue of "introduce interpolation" intention crashing when trying to get code intentions for strings with no
  quote-beginning (e.g. heredocs).
