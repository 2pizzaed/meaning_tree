# CppLanguage

Класс `CppLanguage` реализует преобразование дерева, разобранного Tree‑sitter, в `MeaningTree`.

## Основные возможности

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

## Текущие ограничения

- **Нет значений по умолчанию** для параметров функций (всегда `null`).
- **Отсутствие полноценной таблицы символов** и разрешения имён (scope).
- **Пока не поддерживаются аннотации** (annotations всегда пуст).
- **ExpressionMode**: допускается только одно выражение в теле `main`.
- **Частичная поддержка параметров шаблонов** и пользовательских типов (TODO).
- **Расширения C++** (например, `parameter_pack_expansion`) обрабатываются упрощённо через рекурсию.

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
