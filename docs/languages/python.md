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
- `GlobalStatement` / `NonlocalStatement` (`ScopeDeclarationStatement`)


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
  - `Resourcecontextstatement`
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

- `raise ... from ...` и `except*` не поддерживаются: у узла нет поля причины, а групп
  исключений в модели нет. Подробности — в
  [docs/references/exception-handling.md](../references/exception-handling.md).

- `async with` и распаковка кортежа в `as`-цели (`with a() as (x, y)`) не поддерживаются:
  признака асинхронности у узла нет, а имя ресурса — один идентификатор. Подробности — в
  [docs/references/resource-context.md](../references/resource-context.md).

- `global` и `nonlocal` разбираются в `ScopeDeclarationStatement` и выводятся обратно как есть.
  Присваивание в Python объявляет локальное имя, поэтому `x = 2` внутри функции затеняет
  одноимённую внешнюю переменную, а не меняет её; писать во внешнее имя можно только объявив
  его `global` или `nonlocal`. Само перенаправление хранит таблица областей видимости
  (`ScopeTableElement.rebinds`), а правило связывания задаёт `PythonParser.languageBehavior()`
  (`AssignmentBinding.LOCAL`, см.
  [docs/references/language-behavior.md](../references/language-behavior.md)).

- Аннотация типа у имени, объявленного `global` или `nonlocal`, в Python запрещена
  (`SyntaxError`), поэтому такой формы разбор не ожидает.

- Генераторы разбираются и выводятся как есть: функция с `yield` в теле становится
  `GeneratorDefinition`, `yield from` сохраняется дословно, а аннотация `Iterator[T]` /
  `Iterable[T]` / `Generator[T, ...]` разворачивается в тип элемента и надевается обратно при
  выводе. Не поддерживаются: `yield` в позиции выражения (`x = yield v` — это двусторонний
  протокол `send`/`throw`, которого в модели нет) и генератор-метод. Класс-итератор, разобранный
  из другого языка, выводится как обычный класс плюс сгенерированные `__iter__` и `__next__`;
  обратный разбор python-класса с `__next__` в `IteratorDefinition` не делается — у него нет
  отдельного «есть ли следующий». Подробности — в
  [docs/session-handoff/plans/generators-and-iterators.md](../session-handoff/plans/generators-and-iterators.md).

- Выражение-генератор `(x for x in y)` по-прежнему разбирается как comprehension и теряет
  ленивость: выводится списочным включением, а не генераторным выражением.
