### Version 0.1.20

#### New features
- Added "Use Self-Assignment" refactoring, to simplify an assignment of the result of operating with
  the same variable that is being assigned.
- Added "Move Into Preceding Conditional" refactoring, to move code that is after a conditional to be
  inside all branches of the conditional.

#### Enhancements
- Detect the available refactoring intentions taking into account the program elements at both ends of
  the caret.
