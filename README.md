<a href="https://plugins.jetbrains.com/plugin/15312-rubyrefactorings">
<img
  src="https://raw.githubusercontent.com/JavierGelatti/RubyRefactorings/main/src/main/resources/META-INF/pluginIcon.svg"
  width="120"
  align="right"
  alt="Icon"
/>
</a>

# RubyRefactorings
[![Build](https://github.com/JavierGelatti/RubyRefactorings/workflows/Scala%20CI/badge.svg?branch=main)](https://github.com/JavierGelatti/RubyRefactorings/actions)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/15312)](https://plugins.jetbrains.com/plugin/15312-rubyrefactorings)

A RubyMine plugin that provides additional refactorings for the Ruby language, implemented using code intentions.

Some refactorings that are available when installing the plugin:

- Replace singleton method by opening singleton class:

  ![Replace singleton method by opening singleton class example](https://plugins.jetbrains.com/files/15312/screenshot_23578.png)

- Remove unnecessary braces from hash argument:

  ![Remove unnecessary braces from hash argument example](https://plugins.jetbrains.com/files/15312/screenshot_23630.png)
  
- Introduce string interpolation:

  ![Introduce interpolation example](https://plugins.jetbrains.com/files/15312/screenshot_23649.png)

- Convert single-quoted string to double-quoted string:

  ![Change a single-quoted string literal to have double quotes example](https://plugins.jetbrains.com/files/15312/screenshot_23693.png)
  
- Replace conditional with guard clause:

  ![Simplify if chain by introducing guard clauses](https://plugins.jetbrains.com/files/15312/screenshot_23749.png)

  ![Introduce guard clause in conditional spanning a whole method](https://plugins.jetbrains.com/files/15312/screenshot_23750.png)

  ![Introduce guard clase in block](https://plugins.jetbrains.com/files/15312/screenshot_23751.png)
  
  ![Introduce guard clase replacing elsifs that include exceptions](https://plugins.jetbrains.com/files/15312/screenshot_23752.png)

- Extract method object (a.k.a. *Replace Function with Command*):

  ![Extract method object example](https://plugins.jetbrains.com/files/15312/screenshot_595a7859-f347-4542-a67e-0e00645ba4b2)
  
- Convert string/symbol word list (using `%w`/`%i` syntax) to array (using `[]` syntax):

  ![Convert %w and %i literals to square bracket array](https://plugins.jetbrains.com/files/15312/screenshot_0ef20c09-71cf-4740-8f56-c8b19cff2e1e)

- Split map/collect/each by introducing chained map:

  ![Split map in two different ways](https://plugins.jetbrains.com/files/15312/screenshot_0f1ebb00-34a7-45d7-8402-5bc69a5c2ff7)

  ![Split each, and collect with brace block](https://plugins.jetbrains.com/files/15312/screenshot_d2dc373b-8c34-40c8-92cd-09790cd0f3e0)
