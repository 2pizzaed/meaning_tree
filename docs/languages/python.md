# PythonLanguage

Класс `PythonLanguage` реализует преобразование дерева, разобранного Tree‑sitter, в `MeaningTree`.

## Основные возможности

- **Инициализация**
  - `public PythonLanguage()`
    - Создает пустой парсер, инициализирует структуру для пользовательских типов.
- **Построение дерева**
  - `public synchronized MeaningTree getMeaningTree(String code)`
    - Конвертирует исходнный код на языке `Python` в `MeaningTree`.

- **Использование:**
```java
LanguageParser pythonLanguage = new PythonLanguage();
MeaningTree meaningTree = pythonLanguage.getMeaningTree("a = 10");
```

## Поддерживаемые классы результирующего дерева

- `Error`
- `AliasedImport`
- `AssertStatement`
- `Assignment`
- `Attribute`
- `BinaryOperator`
- `Block`
- `BooleanOperator`
- `BreakStatement`
- `Call`
- `ClassDefinition`
- `Comment`
- `ComparisonOperator`
- `ConditionalExpression`
- `ContinueStatement`
- `DecoratedDefinition`
- `DeleteStatement`
- `Dictionary`
- `DictionarySplat`
- `DottedName`
- `ExpressionStatement`
- `False`
- `Float`
- `ForStatement`
- `FunctionDefinition`
- `Identifier`
- `IfStatement`
- `ImportStatement`
- `Integer`
- `Interpolation`
- `KeywordArgument`
- `List`
- `ListSplat`
- `MatchStatement`
- `Module`
- `NamedExpression`
- `None`
- `NotOperator`
- `ParenthesizedExpression`
- `PassStatement`
- `ReturnStatement`
- `SetComprehension`
- `Slice`
- `String`
- `Subscript`
- `True`
- `Type`
- `UnaryOperator`
- `WhileStatement`


# PythonViewer

Класс `PythonViewer` выполняет преобразование дерева `MeaningTree` в корректно отформатированный Python‑код.

## Основные возможности

- **Переопределение `toString` для различных узлов** через `switch`.

- **Управление табуляцией** с помощью класса `Tab`.

- **Использование:**
```java
LanguageViewer pythonViewer = new PythonViewer();
String code = pythonViewer.toString(meaningTree);
```

- **Поддерживаемые классы узлов**:

  - `Arrayinitializer`
  - `Arraynewexpression`
  - `Assignmentexpression`
  - `Assignmentstatement`
  - `Binarycomparison`
  - `Binaryexpression`
  - `Breakstatement`
  - `Casttypeexpression`
  - `Classdeclaration`
  - `Classdefinition`
  - `Commaexpression`
  - `Comment`
  - `Compoundcomparison`
  - `Compoundstatement`
  - `Comprehension`
  - `Constructorcall`
  - `Continuestatement`
  - `Definitionargument`
  - `Deleteexpression`
  - `Deletestatement`
  - `Dowhileloop`
  - `Expressionsequence`
  - `Expressionstatement`
  - `Forloop`
  - `Formatinput`
  - `Formatprint`
  - `Functioncall`
  - `Functiondeclaration`
  - `Functiondefinition`
  - `Identifier`
  - `Ifstatement`
  - `Import`
  - `Include`
  - `Indexexpression`
  - `Infiniteloop`
  - `Literal`
  - `Memberaccess`
  - `Memoryallocationcall`
  - `Memoryfreecall`
  - `Methoddefinition`
  - `Multipleassignmentstatement`
  - `Objectnewexpression`
  - `Packagedeclaration`
  - `Parenthesizedexpression`
  - `Pointerpackop`
  - `Pointerunpackop`
  - `Programentrypoint`
  - `Range`
  - `Returnstatement`
  - `Sizeofexpression`
  - `Switchstatement`
  - `Ternaryoperator`
  - `Type`
  - `Unaryexpression`
  - `Variabledeclaration`
  - `Whileloop`
  - `Null`

## Текущие ограничения

- Форматирование пользовательских генераторов и comprehensions может быть упрощено.

- Поддержка условных выражений и логических операторов базовая.
