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

- Remove useless conditional statement

  ![Replace if statement with true condition by its then block](https://plugins.jetbrains.com/files/15312/screenshot_0464bf73-2c18-4e71-8f2a-6298b5caefa2)
  
  ![Replace if expression with false condition by its else block](https://plugins.jetbrains.com/files/15312/screenshot_31ed006b-399f-45e5-8c23-9d1ab76ac1ba)
  
  ![Replace if expression with false condition and no else block by nil](https://plugins.jetbrains.com/files/15312/screenshot_b827d543-44bf-4736-a580-942b5f45ea21)
  
  ![Replace unless expression with multiple statements by block](https://plugins.jetbrains.com/files/15312/screenshot_cab24c5c-9300-42fc-bd3d-cc04fc8cf237)

- Move into conditional above

  ![Move a statement into all branches of a conditional that is just before it](https://i.imgur.com/5MBPvGM.gif)

- Use self-assignment

  ![Replace assignment to increment numeric variable by self-assignment](https://i.imgur.com/CpAoYtE.gif)
  
  ![Replace short-circuit or to memoize result of computation by self-assignment](https://i.imgur.com/ZE3q0Vl.gif)
  
  ![Replace hash access to increment value by self-assignment](https://i.imgur.com/ywwIQ5h.gif)
