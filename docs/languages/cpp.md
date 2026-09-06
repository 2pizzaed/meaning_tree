# CppLanguage

Класс `CppLanguage` реализует преобразование дерева, разобранного Tree‑sitter, в `MeaningTree`.

## Основные возможности

### Режим предпочтения C

Конфигурация `preferC=true` включает C-представление для поддерживаемого подмножества языка:
`int main(void)`, `char *` вместо `std::string`, C-массивы, `typedef struct` и
`malloc`/`calloc`/`free` вместо представимых `new`/`delete`. STL-контейнеры и структуры с
ООП-возможностями в этом режиме не поддерживаются. C++-ссылки и подсистема I/O пока выводятся
по прежним правилам и могут сохранять C++-синтаксис.

### Пространство имён std

Конфигурация `useDefaultNamespace=true` (по умолчанию `false`, область — viewer) печатает
программу под `using namespace std;`: директива ставится после всех `#include`, а префикс
`std::` не выводится нигде — ни у имён, вставленных генератором (`cout`, `endl`, `vector`,
`string`, `format`), ни у квалифицированных идентификаторов из дерева (`std::sqrt` → `sqrt`).
Флаг действует только в режимах трансляции `full` и `procedural`: в `simple` и `expression`
у программы нет шапки, директиве негде встать, и вывод остаётся квалифицированным. На C-режим
(`preferC=true`) флаг не влияет.

Разбор от флага не зависит и принимает оба написания. `using namespace std;` в исходнике
разбирается, узла в дереве не оставляет и включает распознавание неквалифицированных
`vector`/`list`/`array`/`map`/`unordered_map`/`set` как STL-контейнеров. Другие формы
using-объявления (`using namespace foo;`, `using std::vector;`) не поддерживаются и
останавливают разбор.

- **Инициализация**
  - `public CppLanguage()`
    - Создает пустой парсер, инициализирует структуру для пользовательских типов.
- **Построение дерева**
  - `public synchronized MeaningTree getMeaningTree(String code)`
    - Конвертирует исходнный код на языке `C++` в `MeaningTree`.

- **Использование:**
```java
LanguageParser cppLanguage = new CppLanguage();
MeaningTree meaningTree = cppLanguage.getMeaningTree("int main() { int a = 10; }");
```

## Поддерживаемые классы результирующего дерева

### Корневой объект
- `MeaningTree`

### Точка входа
- `ProgramEntryPoint`

### Функции и параметры
- `FunctionDeclaration`
- `FunctionDefinition`
- `DeclarationArgument`

### Объектно-ориентированные конструкции
- `ClassDeclaration`
- `ClassDefinition`
- `FieldDeclaration`
- `MethodDefinition`
- `ObjectConstructorDefinition`
- `ObjectDestructorDefinition`
- базовые классы и спецификаторы доступа `public` / `protected` / `private`

### Блоки
- `CompoundStatement`

### Литералы и идентификаторы
- `IntegerLiteral`
- `CharacterLiteral`
- `StringLiteral`
- `BoolLiteral`
- `NullLiteral`
- `ArrayLiteral`
- `SimpleIdentifier`
- `QualifiedIdentifier`
- `Identifier`

### Выражения и операции
- `AssignmentExpression`
- `BinaryExpression`
- `UnaryExpression`
- `CallExpression`
- `ConditionalExpression`
- `CommaExpression`
- `SubscriptExpression`
- `UpdateExpression`
- `CastTypeExpression`
- `SizeofExpression`

### Операторы new / delete
- `ObjectNewExpression`
- `PlacementNewExpression`
- `ArrayNewExpression`
- `DeleteExpression`

### Типы
- `IntType`
- `FloatType`
- `CharacterType`
- `StringType`
- `BooleanType`
- `NoReturn`
- `PointerType`
- `ReferenceType`
- `GenericClass`
- `Class`
- `DictionaryType`
- `ListType`
- `SetType`
- `UnknownType`

### Управляющие конструкции
- `IfStatement`
- `WhileLoop`
- `GeneralForLoop`
- `RangeForLoop`
- `InfiniteLoop`
- `SwitchStatement`
- `BasicCaseBlock`
- `FallthroughCaseBlock`
- `DefaultCaseBlock`
- `BreakStatement`
- `ContinueStatement`
- `ReturnStatement`
- `ExceptionCatchStatement`
- `CatchClause`
- `RaiseExceptionStatement`
- `ResourceContextStatement`

## Текущие ограничения

- **Нет значений по умолчанию** для параметров функций (всегда `null`).
- **Отсутствие полноценной таблицы символов** и разрешения имён (scope).
- **Пока не поддерживаются аннотации** (annotations всегда пуст).
- **ExpressionMode**: допускается только одно выражение в теле `main`.
- **Частичная поддержка параметров шаблонов** и пользовательских типов (TODO).
- **Расширения C++** (например, `parameter_pack_expansion`) обрабатываются упрощённо через рекурсию.
- **Нет ветви `finally`**: вывод такой конструкции отвергается (`TryFinallyFeature`), а ветвь,
  перехватывающая несколько типов, размножается по одной на тип. Подробности — в
  [docs/references/exception-handling.md](../references/exception-handling.md).
- **Нет владения ресурсами**: java-try-with-resources и python-`with` разворачиваются в плоский
  блок с объявлением и `delete`; обратный разбор такого блока в узел не делается. Подробности —
  в [docs/references/resource-context.md](../references/resource-context.md).
- **Нет объявлений привязки имён**: python-`global` снимается перед выводом
  (`ScopeDeclarationLowerer.dropGlobals`) — в C++ присваивание имени, объявленному снаружи, и
  так уходит наружу, поэтому объявление ничего не добавляет. Python-`nonlocal` отвергается
  (`NonlocalBindingFeature`): он указывает на промежуточную область, которой в C++
  соответствовать нечему.

# CppViewer

Класс `CppViewer` выполняет преобразование внутреннего дерева `MeaningTree` в корректно отформатированный C++‑код с учётом заданных параметров стиля.

---

## Основные возможности

- **Использование:**
```java
LanguageViewer cppViewer = new CppViewer();
String code = cppViewer.toString(meaningTree);
```
---

## Конструкторы

```java
// По умолчанию: 4 пробела, открывающая скобка на той же строке, без скобок вокруг case, без авто‑декларации
public CppViewer()

// Указание всех параметров стиля
public CppViewer(
    int indentSpaceCount,
    boolean openBracketOnSameLine,
    boolean bracketsAroundCaseBranches,
    boolean autoVariableDeclaration
)

// Инициализация через токенизатор (использует жёстко закодированные значения) из конструктора по умолчанию
public CppViewer(LanguageTokenizer tokenizer)
```

| Параметр                       | Описание                                                                                         |
|--------------------------------|--------------------------------------------------------------------------------------------------|
| `indentSpaceCount`             | сколько пробелов использовать для одного уровня вложенности (`" ".repeat(indentSpaceCount)`)     |
| `openBracketOnSameLine`        | `true` — `{` сразу после заголовка блока; `false` — на новой строке с отступом                   |
| `bracketsAroundCaseBranches`   | `true` — всегда оборачивать содержимое `case` в `{…}`; `false` — только при объявлении переменных|
| `autoVariableDeclaration`      | `true` — при первом присваивании автоматически генерировать `type name = …;`                      |

> TODO:
> Перенести в configs. 
---

## Управление отступами

- **`increaseIndentLevel()`**  
  Увеличивает внутренний счётчик вложенности.
- **`decreaseIndentLevel()`**  
  Уменьшает счётчик; при попытке уйти ниже 0 бросает `UnsupportedViewingException`.
- **`indent(String s)`**  
  Добавляет к строке `s` нужное количество повторений строки отступа.

---

## Поддерживаемые конструкции

- **ProgramEntryPoint**
- **ExpressionStatement**
- **VariableDeclaration**
- **IndexExpression**
- **ExpressionSequence** (комма‑выражение)
- **TernaryOperator**
- **MemoryAllocationCall**
- **MemoryFreeCall**
- **InputCommand**
  - _включая подклассы `FormatInput`_
- **PrintCommand**
  - _включая подклассы `FormatPrint` и `PrintValues`_
- **FunctionCall**
  - _включая `MethodCall`_
- **ParenthesizedExpression**
- **AssignmentExpression**
- **AssignmentStatement**
- **Type** (все реализации: `IntType`, `FloatType`, `CharacterType`, `BooleanType`, `NoReturn`, `UnknownType`, `PointerType`, `ReferenceType`, `DictionaryType`, `ArrayType`, `UnmodifiableListType`, `SetType`, `PlainCollectionType`, `StringType`, `GenericUserType`, `UserType`)
- **Identifier**
  - _включая `SimpleIdentifier`, `ScopedIdentifier`, `QualifiedIdentifier`_
- **NumericLiteral**
  - _включая `IntegerLiteral`, `FloatLiteral`_
- **FloorDivOp**
- **UnaryExpression**
  - _включая `NotOp`, `InversionOp`, `UnaryMinusOp`, `UnaryPlusOp`, `PostfixIncrementOp`, `PrefixIncrementOp`, `PostfixDecrementOp`, `PrefixDecrementOp`, `PointerPackOp`, `PointerUnpackOp`_
- **BinaryExpression**
  - _включая арифметические, логические, битовые, сравнительные операции, `PowOp`, `MatMulOp`, `ContainsOp`, `ReferenceEqOp`, `InstanceOfOp`, `FloorDivOp`_
- **NullLiteral**
- **StringLiteral**
- **CharacterLiteral**
- **BoolLiteral**
- **PlainCollectionLiteral**
- **DictionaryLiteral**
- **CastTypeExpression**
- **SizeofExpression**
- **NewExpression**
  - `ArrayNewExpression`
  - `PlacementNewExpression`
  - `ObjectNewExpression`
- **DeleteExpression**
- **DeleteStatement**
- **MemberAccess**
  - _включая `PointerMemberAccess`_
- **CompoundComparison**
- **DefinitionArgument**
- **Comment**
- **InterpolatedStringLiteral**
- **MultipleAssignmentStatement**
- **Нет генераторов и итераторов**: `GeneratorDefinition`, `IteratorDefinition` и
  `YieldStatement` объявлены неподдерживаемыми в `CppViewer`, и вывод такого дерева отвергается
  до генерации кода. Запрет объявлен явно узлами, а не оставлен на умолчание: генератор наследует
  `FunctionDefinition`, а итератор — `ClassDefinition`, и подъём по надклассу молча отрисовал бы
  их обычной функцией и обычным классом, потеряв весь смысл конструкции. Сопрограммы C++20
  (`co_yield`, `co_await`, `co_return`) не разбираются и не порождаются. Подробности — в
  [docs/session-handoff/plans/generators-and-iterators.md](../session-handoff/plans/generators-and-iterators.md).
