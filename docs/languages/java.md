# JavaLanguage

Класс `JavaLanguage` реализует преобразование дерева, разобранного Tree‑sitter, в `MeaningTree`.

## Основные возможности

- **Инициализация**
  - `public JavaLanguage()`

- **Построение дерева**
  - `public synchronized MeaningTree getMeaningTree(String code)`
    - Конвертирует исходнный код на языке `Java` в `MeaningTree`.

- **Использование:**
```java
LanguageParser javaLanguage = new JavaLanguage();
MeaningTree meaningTree = javaLanguage.getMeaningTree("String a = \"Hello, world!\"");
```

## Поддерживаемые классы результирующего дерева

- `Error`
- `ArrayAccess`
- `ArrayCreationExpression`
- `ArrayInitializer`
- `AssignmentExpression`
- `BinaryExpression`
- `Block`
- `BreakStatement`
- `CastExpression`
- `CharacterLiteral`
- `ClassDeclaration`
- `ClassLiteral`
- `Condition`
- `ConstructorDeclaration`
- `ContinueStatement`
- `DecimalFloatingPointLiteral`
- `DecimalIntegerLiteral`
- `DoStatement`
- `EnhancedForStatement`
- `ExpressionStatement`
- `FieldAccess`
- `FieldDeclaration`
- `ForStatement`
- `Identifier`
- `IfStatement`
- `ImportDeclaration`
- `InstanceofExpression`
- `LineComment`
- `LocalVariableDeclaration`
- `MethodDeclaration`
- `MethodInvocation`
- `NullLiteral`
- `ObjectCreationExpression`
- `PackageDeclaration`
- `ParenthesizedExpression`
- `Program`
- `ReturnStatement`
- `ScopedIdentifier`
- `Statement`
- `StringLiteral`
- `SwitchExpression`
- `TernaryExpression`
- `This`
- `True`
- `UnaryExpression`
- `UpdateExpression`
- `VoidType`
- `WhileStatement`


# JavaViewer

Класс `JavaViewer` выполняет преобразование внутреннего дерева (`MeaningTree`) в корректно отформатированный Java‑код с учётом заданных параметров стиля.

## Поддерживаемые классы
- `ListLiteral`
- `SetLiteral`
- `DictionaryLiteral`
- `PlainCollectionLiteral`
- `InterpolatedStringLiteral`
- `FloatLiteral`
- `IntegerLiteral`
- `QualifiedIdentifier`
- `StringLiteral`
- `UserType`
- `ReferenceType`
- `PointerType`
- `MemoryAllocationCall`
- `MemoryFreeCall`
- `Type`
- `SelfReference`
- `UnaryMinusOp`
- `UnaryPlusOp`
- `AddOp`
- `SubOp`
- `MulOp`
- `DivOp`
- `ModOp`
- `MatMulOp`
- `FloorDivOp`
- `EqOp`
- `GeOp`
- `GtOp`
- `LeOp`
- `LtOp`
- `InstanceOfOp`
- `NotEqOp`
- `ShortCircuitAndOp`
- `ShortCircuitOrOp`
- `NotOp`
- `ParenthesizedExpression`
- `AssignmentExpression`
- `AssignmentStatement`
- `FieldDeclaration`
- `VariableDeclaration`
- `CompoundStatement`
- `ExpressionStatement`
- `SimpleIdentifier`
- `IfStatement`
- `GeneralForLoop`
- `CompoundComparison`
- `RangeForLoop`
- `ProgramEntryPoint`
- `MethodCall`
- `FormatPrint`
- `PrintValues`
- `FunctionCall`
- `WhileLoop`
- `ScopedIdentifier`
- `PostfixIncrementOp`
- `PostfixDecrementOp`
- `PrefixIncrementOp`
- `PrefixDecrementOp`
- `PowOp`
- `PackageDeclaration`
- `ClassDeclaration`
- `ClassDefinition`
- `Comment`
- `BreakStatement`
- `ContinueStatement`
- `ObjectConstructorDefinition`
- `MethodDefinition`
- `SwitchStatement`
- `NullLiteral`
- `StaticImportAll`
- `StaticImportMembers`
- `ImportAll`
- `ImportMembers`
- `ObjectNewExpression`
- `BoolLiteral`
- `MemberAccess`
- `ArrayNewExpression`
- `ArrayInitializer`
- `ReturnStatement`
- `CastTypeExpression`
- `IndexExpression`
- `TernaryOperator`
- `BitwiseAndOp`
- `BitwiseOrOp`
- `XorOp`
- `InversionOp`
- `LeftShiftOp`
- `RightShiftOp`
- `MultipleAssignmentStatement`
- `InfiniteLoop`
- `ExpressionSequence`
- `CharacterLiteral`
- `DoWhileLoop`
- `PointerPackOp`
- `DefinitionArgument`
- `PointerUnpackOp`
- `ContainsOp`
- `ReferenceEqOp`


**Использование:**
```java
LanguageViewer javaViewer = new JavaViewer();
String code = javaViewer.toString(meaningTree);
```

## Конструкторы

```java
// По умолчанию: 4 пробела, открывающая скобка на той же строке, без скобок вокруг case, без авто‑декларации
public JavaViewer()

// Указание всех параметров стиля
public JavaViewer(
    int indentSpaceCount,
    boolean openBracketOnSameLine,
    boolean bracketsAroundCaseBranches,
    boolean autoVariableDeclaration
)

// Инициализация через токенизатор (использует жёстко закодированные значения) из конструктора по умолчанию
public JavaViewer(LanguageTokenizer tokenizer)
```

| Параметр                       | Описание                                                                                         |
|--------------------------------|--------------------------------------------------------------------------------------------------|
| `indentSpaceCount`             | сколько пробелов использовать для одного уровня вложенности (`" ".repeat(indentSpaceCount)`)     |
| `openBracketOnSameLine`        | `true` — `{` сразу после заголовка блока; `false` — на новой строке с отступом                   |
| `bracketsAroundCaseBranches`   | `true` — всегда оборачивать содержимое `case` в `{…}`; `false` — только при объявлении переменных|
| `autoVariableDeclaration`      | `true` — при первом присваивании автоматически генерировать `type name = …;`                      |

> TODO:
> Перенести в configs. 