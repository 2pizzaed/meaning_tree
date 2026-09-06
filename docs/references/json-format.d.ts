/**
 * Схема JSON-формата Meaning Tree.
 *
 * Файл описывает то, что порождает
 * `modules/common/src/main/java/org/vstu/meaningtree/serializers/json/JsonSerializer.java`
 * и то, что ожидает на входе `JsonDeserializer.java`.
 *
 * Файл не компилируется и не участвует в сборке — это исполняемая документация:
 * его можно подключить в редакторе/TS-проекте, чтобы получить автодополнение и проверку
 * структуры дерева. При изменении `JsonSerializer` этот файл нужно править синхронно.
 *
 * Соглашения:
 *  - все имена узлов (`type`) — snake_case, задаются в `JsonNodeTypeClassMapper`;
 *    если класс узла там не зарегистрирован, имя получается транслитерацией
 *    `CamelCase -> camel_case` (`TransliterationUtils.camelToSnake`);
 *  - все значения enum сериализуются через `JsonSerializer.enumToValue`,
 *    т.е. `UPPER_SNAKE_CASE -> lower_snake_case` (исключение — `int_literal.repr`,
 *    он пишется как есть, в верхнем регистре);
 *  - `id` узлов и токенов — Java `long`, в JSON это число; при работе с большими деревьями
 *    учитывайте потерю точности в JS для значений > 2^53;
 *  - ВАЖНО про null: сериализатор кладёт в объекты `JsonNull`, но Gson по умолчанию
 *    (без `GsonBuilder.serializeNulls()`) выбрасывает null-члены объектов при печати.
 *    Поэтому такие поля описаны здесь как `field?: X | null`: в типичном выводе
 *    (в том числе у CLI `translate --serialize json`) они просто отсутствуют,
 *    а при включённом `serializeNulls()` присутствуют со значением `null`.
 *    На элементы массивов это не распространяется — там `null` печатается всегда;
 *  - поля, помеченные `?:` без `| null`, отсутствуют потому, что сериализатор
 *    вообще не добавляет их в этой ветке кода.
 */

/** Идентификатор узла AST (Java long). */
export type AstId = number;

/** Идентификатор области видимости. */
export type ScopeId = number;

/* =============================================================================
 * Корневые документы
 * ========================================================================== */

/** Результат `JsonSerializer.serialize(MeaningTree)`. */
export interface MeaningTreeDocument {
    type: "meaning_tree";
    /** `MeaningTree.hashCode()` — структурный хеш дерева. */
    unique_hash: number;
    root_node: AnyNode;
    /** Отсутствует, если у дерева нет меток. */
    labels?: Label[];
}

/** Результат `JsonSerializer.serialize(SourceMap)`. */
export interface SourceMapDocument {
    type: "source_map";
    /**
     * Дерево, к которому привязана карта. Это полноценный документ `meaning_tree`
     * (перегрузка `serialize(MeaningTree)`), а не голый узел.
     */
    origin: MeaningTreeDocument;
    source_code: string;
    /** Идентификатор языка-источника (см. `SupportedLanguage`). */
    language: string;
    /**
     * Соответствие `ast_id -> [offset, length]` в байтах исходного кода.
     * Ключ — строковое представление long-идентификатора узла.
     */
    byte_positions: Record<string, BytePosition>;
    /**
     * Области видимости напечатанного кода: дерево после оптимизаций целевого языка, разобранное
     * правилами целевого языка.
     *
     * Пришло на смену полю `scope_table`, которое несло смесь — исходное дерево с целевой
     * семантикой. Карты, записанные с прежним полем, читаются: `scope_table` понимается как
     * `render_scope_table`.
     */
    render_scope_table: ScopeTableDocument;
    /**
     * Области видимости входного дерева: то же дерево до оптимизаций, разобранное правилами
     * языка-источника. Отсутствует, если язык-источник вызывающему неизвестен — например, когда
     * дерево пришло из JSON или собрано программно.
     */
    origin_scope_table?: ScopeTableDocument;
    /** Произвольные числовые метрики разбора. */
    metrics: Record<string, number>;
    project_root_path?: string;
    project_file_rel_path?: string;
}

/** `[offset, length]` в байтах. */
export type BytePosition = [offset: number, length: number];

/* =============================================================================
 * Таблица областей видимости
 * ========================================================================== */

/**
 * Результат `JsonSerializer.serialize(ScopeTable)`.
 * Узлы здесь представлены не целиком, а «облегчёнными» ссылками (`ScopeNodeRef`).
 */
export interface ScopeTableDocument {
    type: "scope_table";
    current_scope_id: ScopeId;
    /**
     * Правило языка, по которому присваивание выбирает переменную:
     * `ENCLOSING` — связывается ближайшая видимая одноимённая (Java, C++),
     * `LOCAL` — переменная текущей области, а внешняя затеняется (Python).
     * Отсутствует в документах, записанных до появления правила; это означает `ENCLOSING`.
     */
    assignment_binding?: "ENCLOSING" | "LOCAL";
    symbols: {
        declarations: Array<{
            /** `Identifier.internalRepresentation()`; `null`, если идентификатора нет. */
            name?: string | null;
            declaration: ScopeNodeRef;
        }>;
        definitions: Array<{
            declaration: ScopeNodeRef;
            definition: ScopeNodeRef;
        }>;
        /**
         * Группы перегрузок. Отсутствует в документах, записанных до появления перегрузок;
         * пустой массив означает, что группы не строились (например, при `skipOptimizations`).
         */
        overload_groups?: OverloadGroupEntry[];
    };
    types: {
        declared_types: Array<{
            name?: string | null;
            /** Полностью сериализованный узел типа. */
            type_ref: AnyType;
        }>;
        type_declarations: Array<{
            type_ref: AnyType;
            declaration: ScopeNodeRef;
        }>;
        /** Иерархия пользовательских типов: тип -> список родителей. */
        hierarchy: Array<{
            type_ref: AnyType;
            parents: AnyType[];
        }>;
    };
    imports: {
        items: ScopeImportRef[];
    };
    scopes: ScopeEntry[];
}

/**
 * Одноимённые callable-сущности, между которыми выбирает разрешение вызова.
 * Группа существует и для неперегруженного имени — тогда в ней одна декларация.
 *
 * Индекс членов типа отдельно не сериализуется: он выводится из групп с непустым `owner`.
 */
export interface OverloadGroupEntry {
    /** Лексическая область группы; для методов и конструкторов — область владельца. */
    scope_id: ScopeId;
    /** `Identifier.internalRepresentation()` имени группы. */
    name: string;
    kind: "function" | "method" | "constructor";
    /** Владелец для методов и конструкторов; `null` для свободных функций. */
    owner?: AnyType | null;
    /** По одной канонической декларации на каждую уникальную сигнатуру, в порядке объявления. */
    declarations: ScopeNodeRef[];
}

/** Одна область видимости (`ScopeTableElement`). */
export interface ScopeEntry {
    id: ScopeId;
    parent_id?: ScopeId | null;
    /** `ast_id` узла-владельца области (например, `compound_statement`). */
    owner_ast_id?: AstId | null;
    variables: Array<{
        name?: string | null;
        type_ref: AnyType;
        /** Отсутствует, если объявление переменной неизвестно. */
        declaration?: ScopeNodeRef;
    }>;
    declarations: Array<{
        name?: string | null;
        declaration: ScopeNodeRef;
    }>;
    declared_types: Array<{
        name?: string | null;
        type_ref: AnyType;
    }>;
    type_declarations: Array<{
        type_ref: AnyType;
        declaration: ScopeNodeRef;
    }>;
    /**
     * Имена, объявленные в этой области связанными снаружи (`global` / `nonlocal`), и
     * области-цели. Цель — всегда строгий предок. Отсутствует или пусто, если привязок нет.
     */
    rebinds?: Array<{
        name?: string | null;
        scope_id: ScopeId;
    }>;
}

/**
 * Облегчённая ссылка на узел внутри таблицы областей видимости
 * (`JsonSerializer.serializeScopeNode`).
 *
 * Обратите внимание: имя узла здесь лежит в `node_type`, а не в `type`,
 * и полный набор дочерних узлов не сериализуется — только `ast_id` и краткие атрибуты.
 * Для `null`-узла возвращается пустой объект `{}`.
 */
export interface ScopeNodeRef {
    node_type?: NodeTypeName;
    ast_id?: AstId;

    /* Declaration / Definition */
    /** Имя объявления; для `Identifier` — его `internalRepresentation()`. */
    identifier?: string | null;
    /** Только для `NestedDeclaration` с родительским объявлением. */
    parent_declaration_ast_id?: AstId;
    /** Только для `Definition`. */
    declaration_ast_id?: AstId;

    /* Type */
    repr_name?: string;
    is_const?: boolean;
    is_safe_reference?: boolean;

    /* Метаданные выражения (см. withNodeMetadata) */
    value_estimate?: ExpressionValueEstimate;
}

/** Импорт в таблице областей видимости (`serializeScopeImport`). */
export interface ScopeImportRef extends ScopeNodeRef {
    /** Для `import_module` / `import_members_from_module`. */
    module_name?: string;
    /** Для `import_members_from_module` — имена членов. */
    members?: string[];
    /** Для `import_modules` — имена модулей. */
    modules?: string[];
    /** Для `include` — имя файла без экранирования. */
    file_name?: string;
    is_static?: boolean;
    /** `true` только для `include`. */
    all_content_include?: boolean;
}

/* =============================================================================
 * Метки (Label)
 * ========================================================================== */

/**
 * Метка узла или дерева. Числовые константы `id` определены в `utils/Label.java`:
 *  0 — VALUE, 1 — DUMMY, 2 — MUTATION_FLAG, 3 — ORIGIN, 4 — BYTEPOS_ANNOTATED,
 *  5 — REMAPPED, 32767 — UNKNOWN, -32768 — ERROR;
 *  диапазон [-32000, -1] зарезервирован под пользовательские метки.
 */
export interface Label {
    id: number;
    /**
     * Метка не участвует в сравнении содержимого узлов: она описывает происхождение или
     * служебное состояние узла, а не то, что узел значит. Поле пишется только когда оно
     * истинно; его отсутствие означает `false`.
     *
     * `REMAPPED` (id 5) невидима всегда, независимо от этого поля, поэтому файлы, записанные
     * до его появления, читаются без изменения смысла.
     */
    stealth?: boolean;
    /**
     * Атрибут метки. Допустимые типы ограничены `Label.ALLOWED_TYPES`:
     * строка, число, boolean, их массивы, массивы примитивов и произвольный JSON.
     * Отсутствует (или `null` при `serializeNulls()`), если атрибута нет.
     */
    attr?: LabelAttribute;
}

export type LabelAttribute =
    | null
    | string
    | number
    | boolean
    | Array<string | number | boolean>
    | Record<string, unknown>;

/* =============================================================================
 * Токены
 * ========================================================================== */

/** Результат `JsonSerializer.serialize(TokenList)`. */
export interface TokenListDocument {
    type: "tokens";
    items: TokenJson[];
}

/**
 * Токен. Поле `type` — транслитерация имени Java-класса токена
 * (`token`, `operator_token`, `operand_token`, `complex_operator_token`,
 * `pseudo_token`, `whitespace`, ...), поэтому оно намеренно типизировано как строка.
 */
export interface TokenJson {
    type: string;
    is_pseudo: boolean;

    /* PseudoToken */
    /** Произвольный атрибут псевдотокена, сериализованный Gson. */
    metadata?: unknown;
    /** Только для `Whitespace`. */
    has_newlines?: boolean;

    /* ComplexOperatorToken */
    /**
     * Индекс этой части внутри `token_values` (`ComplexOperatorToken.positionOfToken`).
     * Это число, а не значение перечисления `OperatorTokenPosition`.
     */
    token_position?: number;
    /** Все части составного оператора, например `["[", "]"]` или `["?", ":"]`. */
    token_values?: string[];

    /* OperatorToken */
    precedence?: number;
    associativity?: OperatorAssociativity;
    arity?: OperatorArity;
    is_strict_order?: boolean;
    first_evaluated_operand?: OperandPosition | null;
    optype_metadata?: OperatorType | null;

    /* OperandToken */
    /** Id оператора, операндом которого является токен. */
    operand_of?: AstId | null;
    operand_pos?: OperandPosition | null;

    token_type: TokenType;
    value: string;
    id: AstId;
    /** Строка с JSON-представлением присвоенного значения (`Gson.toJson`). */
    assigned_label: string;
    /** Id узла AST, которому принадлежит токен. */
    belongs_to?: AstId | null;
    byte_pos?: BytePosition | null;
}

/* =============================================================================
 * Перечисления
 * ========================================================================== */

export type TokenType =
    | "operator"
    | "const"
    | "callable_identifier"
    | "identifier"
    | "keyword"
    | "cast"
    | "comment"
    | "opening_brace"
    | "closing_brace"
    | "subscript_opening_brace"
    | "subscript_closing_brace"
    | "call_opening_brace"
    | "call_closing_brace"
    | "compound_opening_brace"
    | "compound_closing_brace"
    | "initializer_list_opening_brace"
    | "initializer_list_closing_brace"
    | "statement_token"
    | "separator"
    | "comma"
    | "unknown";

export type OperatorAssociativity = "left" | "right" | "non_assoc";

export type OperatorArity = "unary" | "binary" | "ternary";

export type OperandPosition = "left" | "center" | "right";

export type OperatorType =
    | "and"
    | "or"
    | "method_call"
    | "new_array"
    | "new"
    | "conditional"
    | "other";

/** `nodes/enums/AugmentedAssignmentOperator.java`. `none` соответствует обычному `=`. */
export type AugmentedAssignmentOperator =
    | "none"
    | "add"
    | "sub"
    | "mul"
    | "div"
    | "floor_div"
    | "bitwise_and"
    | "bitwise_or"
    | "bitwise_xor"
    | "bitwise_shift_left"
    | "bitwise_shift_right"
    | "mod"
    | "pow";

/** `nodes/enums/DeclarationModifier.java`. */
export type DeclarationModifier =
    | "public"
    | "private"
    | "protected"
    | "abstract"
    | "virtual"
    | "const"
    | "static";

/** `Range.Direction`. */
export type RangeDirection = "up" | "down" | "unknown";

/** `LoopIterationCount`. */
export type LoopIterationCount =
    | "zero"
    | "one"
    | "many"
    | "fixed"
    | "infinite"
    | "undefined";

/** `LoopType` — исходный вид цикла, свёрнутого в `infinite_loop`. */
export type LoopType = "for" | "while" | "do_while";

/** `Include.IncludeType`. */
export type IncludeType = "quoted_form" | "pointy_brackets_form";

/** `StringLiteral.Type`. */
export type StringLiteralType = "none" | "raw";

/** `IntegerLiteral.Representation` — пишется как `Enum.toString()`, без транслитерации. */
export type IntegerRepresentation = "DECIMAL" | "HEX" | "BINARY" | "OCTAL";

/* =============================================================================
 * Оценки (статический анализ)
 * ========================================================================== */

/** Оценка количества итераций цикла или диапазона (`LoopIterationEstimate`). */
export interface LoopIterationEstimate {
    kind: LoopIterationCount;
    /** Точное число итераций, если удалось вычислить. */
    exact_iterations?: number | null;
    reliable: boolean;
    direction: RangeDirection;
}

/** Оценка значения выражения (`ExpressionValueEstimate`). */
export interface ExpressionValueEstimate {
    exact_value?: EstimateValue;
    possible_values: Exclude<EstimateValue, null>[];
    reliable: boolean;
}

export type EstimateValue = null | boolean | number | string;

/* =============================================================================
 * Базовые интерфейсы узлов
 * ========================================================================== */

/**
 * Поля, которые `JsonSerializer.serialize(Node)` добавляет к любому узлу
 * уже после его специализированной сериализации.
 */
export interface NodeCommon {
    id: AstId;
    /** `Node.hashCode()` — структурный хеш поддерева. */
    unique_hash: number;
    /** Отсутствует, если меток нет. */
    labels?: Label[];
    /** Только для `Statement` с меткой перехода (цель для `goto`/`break`/`continue`). */
    jump_label?: JumpLabelNode;
    /**
     * Только для мест вызова (`function_call`, `method_call`, `constructor_call`,
     * `object_new_expression`): `id` декларации, которую вызов вызывает.
     *
     * Отсутствует, если вызов разрешить не удалось: вызываемая сущность вне разбираемого
     * фрагмента либо у перегруженного имени не нашлось единственного подходящего кандидата.
     * Оба случая штатные, поэтому отсутствие поля не означает потерю данных.
     */
    resolved_declaration_id?: AstId;
}

export interface NodeBase<T extends string = NodeTypeName> extends NodeCommon {
    type: T;
}

/** Бинарный оператор: два операнда, никаких дополнительных полей. */
export interface BinaryOpNode<T extends string> extends NodeBase<T> {
    left_operand: AnyNode;
    right_operand: AnyNode;
}

/** Унарный оператор. */
export interface UnaryOpNode<T extends string> extends NodeBase<T> {
    operand: AnyNode;
}

/* =============================================================================
 * Операторы
 * ========================================================================== */

export type AddOpNode = BinaryOpNode<"add_operator">;
export type SubOpNode = BinaryOpNode<"sub_operator">;
export type MulOpNode = BinaryOpNode<"mul_operator">;
export type DivOpNode = BinaryOpNode<"div_operator">;
export type ModOpNode = BinaryOpNode<"mod_operator">;
export type MatMulOpNode = BinaryOpNode<"matrix_mul_operator">;
export type FloorDivOpNode = BinaryOpNode<"floor_div_operator">;
export type PowOpNode = BinaryOpNode<"pow_operator">;

export type EqOpNode = BinaryOpNode<"eq_operator">;
export type GeOpNode = BinaryOpNode<"ge_operator">;
export type GtOpNode = BinaryOpNode<"gt_operator">;
export type LeOpNode = BinaryOpNode<"le_operator">;
export type LtOpNode = BinaryOpNode<"lt_operator">;
export type NotEqOpNode = BinaryOpNode<"not_eq_operator">;
export type ThreeWayComparisonOpNode = BinaryOpNode<"three_way_comparison">;

/** Сравнение ссылок (`is` / `==` для ссылок). `is_negative` — отрицание. */
export interface ReferenceEqOpNode extends BinaryOpNode<"reference_eq_operator"> {
    is_negative: boolean;
}

export type ShortCircuitAndOpNode = BinaryOpNode<"short_circuit_and_operator">;
export type ShortCircuitOrOpNode = BinaryOpNode<"short_circuit_or_operator">;
export type LongCircuitAndOpNode = BinaryOpNode<"long_circuit_and_operator">;
export type LongCircuitOrOpNode = BinaryOpNode<"long_circuit_or_operator">;

export type BitwiseAndOpNode = BinaryOpNode<"bitwise_and_operator">;
export type BitwiseOrOpNode = BinaryOpNode<"bitwise_or_operator">;
export type XorOpNode = BinaryOpNode<"xor_operator">;
export type LeftShiftOpNode = BinaryOpNode<"left_shift_operator">;
export type RightShiftOpNode = BinaryOpNode<"right_shift_operator">;

export type NotOpNode = UnaryOpNode<"not_operator">;
export type UnaryMinusOpNode = UnaryOpNode<"unary_minus_operator">;
export type UnaryPlusOpNode = UnaryOpNode<"unary_plus_operator">;
export type InversionOpNode = UnaryOpNode<"inversion_operator">;
export type PostfixIncrementOpNode = UnaryOpNode<"unary_postfix_inc_operator">;
export type PostfixDecrementOpNode = UnaryOpNode<"unary_postfix_dec_operator">;
export type PrefixIncrementOpNode = UnaryOpNode<"unary_prefix_inc_operator">;
export type PrefixDecrementOpNode = UnaryOpNode<"unary_prefix_dec_operator">;

/**
 * `expression instanceof type`.
 *
 * ВНИМАНИЕ: сериализатор кладёт узел проверяемого типа в ключ `type`, тем самым
 * перезаписывая имя узла. В выходном JSON `type` — это объект типа, а не строка
 * `"instance_of_operator"`. Опознавать такой узел следует по наличию `expression`
 * вместе с объектом в `type`.
 */
export interface InstanceOfOpNode extends NodeCommon {
    type: AnyType;
    expression: AnyNode;
}

/** `element in collection`; `is_negative` — для `not in`. */
export interface ContainsOpNode extends NodeBase<"contains_operator"> {
    element: AnyNode;
    collection: AnyNode;
    is_negative: boolean;
}

export interface TernaryOperatorNode extends NodeBase<"ternary_operator"> {
    condition: AnyNode;
    true_expression: AnyNode;
    false_expression: AnyNode;
}

/** Цепочка сравнений вида `a < b <= c` (Python). */
export interface CompoundComparisonNode extends NodeBase<"compound_comparison"> {
    comparisons: Array<{
        left: AnyNode;
        /** Имя узла-компаратора, например `lt_operator`. */
        operator: NodeTypeName;
        right: AnyNode;
    }>;
}

/* =============================================================================
 * Литералы
 * ========================================================================== */

export interface FloatLiteralNode extends NodeBase<"float_literal"> {
    value: number;
    is_double: boolean;
}

export interface IntegerLiteralNode extends NodeBase<"int_literal"> {
    value: number;
    repr: IntegerRepresentation;
    is_long: boolean;
    is_unsigned: boolean;
}

export interface StringLiteralNode extends NodeBase<"string_literal"> {
    /** Значение без экранирования, «как в памяти». */
    value: string;
}

export type NullLiteralNode = NodeBase<"null_literal">;

export interface BoolLiteralNode extends NodeBase<"bool_literal"> {
    value: boolean;
}

export interface CharacterLiteralNode extends NodeBase<"char_literal"> {
    /** Кодовая точка символа. */
    value: number;
}

/** Общая часть литералов-коллекций. */
export interface CollectionLiteralNode<T extends string> extends NodeBase<T> {
    elements: AnyNode[];
    /** Присутствует только если тип элементов известен. */
    type_hint?: AnyType;
}

export type ArrayLiteralNode = CollectionLiteralNode<"array_literal">;
export type ListLiteralNode = CollectionLiteralNode<"list_literal">;
export type SetLiteralNode = CollectionLiteralNode<"set_literal">;
export type UnmodifiableListLiteralNode = CollectionLiteralNode<"unmodifiable_list_literal">;

/**
 * Интерполированная строка.
 *
 * ВНИМАНИЕ: сериализатор перезаписывает поле `type` значением `StringLiteral.Type`,
 * поэтому в выходном JSON `type` равен `"none"`/`"raw"`, а не
 * `"interpolated_string_literal"`. Определять такой узел следует по наличию `components`.
 */
export interface InterpolatedStringLiteralNode extends NodeCommon {
    type: StringLiteralType;
    /** Части строки: литералы и подставляемые выражения. */
    components: AnyNode[];
}

export interface DictionaryLiteralNode extends NodeBase<"dictionary_literal"> {
    entries: Array<{ key: AnyNode; value: AnyNode }>;
    key_type_hint?: AnyType;
    value_type_hint?: AnyType;
}

/* =============================================================================
 * Идентификаторы
 * ========================================================================== */

export interface SimpleIdentifierNode extends NodeBase<"identifier"> {
    name: string;
    /** `Identifier.internalRepresentation()` — каноническое имя для сопоставления. */
    repr_name: string;
}

/** `JumpLabel` — метка перехода; имя получено транслитерацией класса. */
export interface JumpLabelNode extends NodeBase<"jump_label"> {
    name: string;
    repr_name: string;
}

/** Доступ вида `scope.member`. */
export interface QualifiedIdentifierNode extends NodeBase<"qualified_identifier"> {
    scope: AnyNode;
    member: AnyNode;
    repr_name: string;
}

/** Разрешение области видимости, например `a::b::c`. */
export interface ScopedIdentifierNode extends NodeBase<"scoped_identifier"> {
    identifiers: AnyNode[];
    repr_name: string;
}

export interface SelfReferenceNode extends NodeBase<"self_reference"> {
    /** `this`, `self` и т.п. */
    name: string;
}

export interface SuperClassReferenceNode extends NodeBase<"super_class_reference"> {
    name: string;
}

/** Импорт/объявление с переименованием: `realName as alias`. */
export interface AliasNode extends NodeBase<"alias"> {
    realName: AnyNode;
    alias: AnyNode;
}

/* =============================================================================
 * Выражения
 * ========================================================================== */

export interface ParenthesizedExpressionNode extends NodeBase<"parenthesized_expression"> {
    expression: AnyNode;
}

export interface AssignmentExpressionNode extends NodeBase<"assignment_expression"> {
    target: AnyNode;
    value: AnyNode;
    augmented_operator: AugmentedAssignmentOperator;
    /** Выведенный тип присваивания; `null`, если тип неизвестен. */
    real_type?: AnyType | null;
}

export interface FunctionCallNode extends NodeBase<"function_call"> {
    function: AnyNode;
    arguments: AnyNode[];
}

export interface MethodCallNode extends NodeBase<"method_call"> {
    receiver: AnyNode;
    method_name: AnyNode;
    arguments: AnyNode[];
}

export interface ConstructorCallNode extends NodeBase<"constructor_call"> {
    owner: AnyNode;
    /** Вызов конструктора базового класса (`super(...)`). */
    base_class_call: boolean;
    arguments: AnyNode[];
}

export interface DestructorCallNode extends NodeBase<"destructor_call"> {
    owner: AnyNode;
}

export interface IndexExpressionNode extends NodeBase<"index_expression"> {
    expr: AnyNode;
    index: AnyNode;
}

export interface MemberAccessNode extends NodeBase<"member_access"> {
    expression: AnyNode;
    member: AnyNode;
}

export interface PointerMemberAccessNode extends NodeBase<"pointer_member_access"> {
    expression: AnyNode;
    member: AnyNode;
}

/** Взятие адреса (`&x`). */
export type PointerPackOpNode = UnaryOpNode<"pointer_pack">;
/** Разыменование (`*p`). */
export type PointerUnpackOpNode = UnaryOpNode<"pointer_unpack">;

/** Диапазон `start..stop` с шагом. */
export interface RangeNode extends NodeBase<"range"> {
    start?: AnyNode | null;
    stop?: AnyNode | null;
    step?: AnyNode | null;
    isExcludingStart: boolean;
    isExcludingEnd: boolean;
    direction: RangeDirection;
    /** Присутствует, только если оценка вычислена. */
    iteration_estimate?: LoopIterationEstimate;
}

export interface ArrayInitializerNode extends NodeBase<"array_initializer"> {
    elements: AnyNode[];
}

export interface CastTypeExpressionNode extends NodeBase<"cast_type_expression"> {
    target_type: AnyType;
    value: AnyNode;
}

export interface CommaExpressionNode extends NodeBase<"comma_expression"> {
    expressions: AnyNode[];
}

export interface ExpressionSequenceNode extends NodeBase<"expression_sequence"> {
    expressions: AnyNode[];
}

export interface DeleteExpressionNode extends NodeBase<"delete_expression"> {
    expr: AnyNode;
}

export interface KeyValuePairNode extends NodeBase<"key_value_pair"> {
    key: AnyNode;
    value: AnyNode;
}

export interface SizeofExpressionNode extends NodeBase<"sizeof_expression"> {
    value: AnyNode;
}

/** Форматирование строки: шаблон + подстановки. */
export interface StringFormatNode extends NodeBase<"string_format"> {
    template: AnyNode;
    substitutions: AnyNode[];
}

/* --- Выражения создания объектов --------------------------------------- */

export interface ObjectNewExpressionNode extends NodeBase<"object_new_expression"> {
    target_type: AnyType;
    arguments: AnyNode[];
    is_stack_allocated: boolean;
}

export interface PlacementNewExpressionNode extends NodeBase<"placement_new_expression"> {
    target_type: AnyType;
    arguments: AnyNode[];
    is_stack_allocated: boolean;
}

export interface ArrayNewExpressionNode extends NodeBase<"array_new_expression"> {
    target_type: AnyType;
    shape: ShapeNode;
    initializer?: AnyNode | null;
    is_stack_allocated: boolean;
}

/* --- Работа с памятью --------------------------------------------------- */

export interface MemoryAllocationCallNode extends NodeBase<"memory_allocation_call"> {
    /** `calloc` вместо `malloc`. */
    is_clear: boolean;
    function: AnyNode;
    arguments: AnyNode[];
}

export interface MemoryFreeCallNode extends NodeBase<"memory_free_call"> {
    value: AnyNode;
}

/* --- Comprehensions ----------------------------------------------------- */

/**
 * Элемент comprehension: либо `key_value_pair` (для словарей),
 * либо `list_comprehension_item` / `set_comprehension_item`.
 */
export type ComprehensionItem =
    | KeyValuePairNode
    | {
          type: "list_comprehension_item" | "set_comprehension_item";
          expression: AnyNode;
      };

export interface ContainerBasedComprehensionNode
    extends NodeBase<"container_based_comprehension"> {
    condition?: AnyNode | null;
    /** Объявление переменной элемента контейнера. */
    container_item: AnyNode;
    container: AnyNode;
    item: ComprehensionItem;
}

export interface RangeBasedComprehensionNode extends NodeBase<"range_based_comprehension"> {
    condition?: AnyNode | null;
    item: ComprehensionItem;
    range: RangeNode;
    identifier: AnyNode;
}

/* --- Ввод-вывод --------------------------------------------------------- */

export interface FormatInputNode extends NodeBase<"format_input"> {
    format_string: AnyNode;
    arguments: AnyNode[];
}

export interface FormatPrintNode extends NodeBase<"format_print"> {
    format_string: AnyNode;
    separator?: AnyNode | null;
    end?: AnyNode | null;
    arguments: AnyNode[];
}

export interface InputCommandNode extends NodeBase<"input_command"> {
    arguments: AnyNode[];
}

export interface PointerInputCommandNode extends NodeBase<"pointer_input_command"> {
    arguments: AnyNode[];
    target: AnyNode;
}

export interface PrintValuesNode extends NodeBase<"print_values"> {
    separator?: AnyNode | null;
    end?: AnyNode | null;
    arguments: AnyNode[];
}

/* =============================================================================
 * Инструкции
 * ========================================================================== */

export interface AssignmentStatementNode extends NodeBase<"assignment_statement"> {
    target: AnyNode;
    value: AnyNode;
    augmented_operator: AugmentedAssignmentOperator;
    real_type?: AnyType | null;
}

/** `a = b = c = value`. */
export interface ChainedAssignmentStatementNode
    extends NodeBase<"chained_assignment_statement"> {
    targets: AnyNode[];
    value: AnyNode;
    variable_declarations: AnyNode[];
    real_type?: AnyType | null;
}

/** Несколько независимых присваиваний в одной инструкции. */
export interface MultipleAssignmentStatementNode
    extends NodeBase<"multiple_assignment_statement"> {
    targets: AnyNode[];
}

/** `a, b = value`. Имя типа получено транслитерацией. */
export interface ListUnpackingAssignmentStatementNode
    extends NodeBase<"list_unpacking_assignment_statement"> {
    variable_names: AnyNode[];
    value: AnyNode;
}

/** Объявление с распаковкой: `a, b = value`. Имя типа получено транслитерацией. */
export interface ListUnpackingVariableDeclarationNode
    extends NodeBase<"list_unpacking_variable_declaration"> {
    variable_names: AnyNode[];
    value: AnyNode;
}

export type EmptyStatementNode = NodeBase<"empty_statement">;

/** Блок инструкций. `scope_id` присутствует, если блок образует область видимости. */
export interface CompoundStatementNode extends NodeBase<"compound_statement"> {
    scope_id?: ScopeId;
    statements: AnyNode[];
}

export interface ExpressionStatementNode extends NodeBase<"expression_statement"> {
    expression: AnyNode;
}

export interface ReturnStatementNode extends NodeBase<"return_statement"> {
    /** Отсутствует для `return;`. */
    expression?: AnyNode;
}

/**
 * Выдача значения из генератора: `yield e`, `yield` и `yield from e`.
 * Оператор, а не выражение: `yield` в позиции значения (`x = yield v`) моделью не выражается.
 */
export interface YieldStatementNode extends NodeBase<"yield_statement"> {
    /** Отсутствует у голого `yield`. У делегирующей выдачи — источник последовательности. */
    value?: AnyNode;
    /** `true` для `yield from e`. */
    delegated: boolean;
}

export interface DeleteStatementNode extends NodeBase<"delete_statement"> {
    expr: AnyNode;
}

/** Объявление переменной (`variable_declaration`) или поля (`field_declaration`). */
export interface VariableDeclarationNode
    extends NodeBase<"variable_declaration" | "field_declaration"> {
    /** Присутствует только у `field_declaration`. */
    modifiers?: DeclarationModifier[];
    declarators: VariableDeclarator[];
    var_type: AnyType;
    annotations: AnnotationNode[];
}

/** Один объявляемый идентификатор внутри `variable_declaration`. */
export interface VariableDeclarator {
    type: "variable_declarator";
    id: AstId;
    identifier: AnyNode;
    /** Отсутствует, если инициализатора нет. */
    rvalue?: AnyNode;
    real_type?: AnyType | null;
}

/** Несколько объявлений переменных разных типов, записанные вместе. */
export interface SeparatedVariableDeclarationNode
    extends NodeBase<"separated_variable_declaration"> {
    declarations: AnyNode[];
}

/* --- Ветвления ---------------------------------------------------------- */

export interface IfStatementNode extends NodeBase<"if_statement"> {
    branches: ConditionBranchNode[];
    /** Обратите внимание: ключ в camelCase. */
    elseBranch?: AnyNode;
}

export interface ConditionBranchNode extends NodeBase<"condition_branch"> {
    /** Отсутствует у безусловной ветви. */
    condition?: AnyNode;
    body: AnyNode;
}

export interface SwitchStatementNode extends NodeBase<"switch_statement"> {
    expression: AnyNode;
    cases: CaseBlockNode[];
    /** Присутствует, если есть ветвь по умолчанию. */
    default?: DefaultCaseBlockNode;
}

export interface BasicCaseBlockNode extends NodeBase<"basic_case_block"> {
    body: AnyNode;
    match_value: AnyNode;
}

export interface FallthroughCaseBlockNode extends NodeBase<"fallthrough_case_block"> {
    match_value: AnyNode;
    body: AnyNode;
}

export interface DefaultCaseBlockNode extends NodeBase<"default_case_block"> {
    body: AnyNode;
}

export type CaseBlockNode =
    | BasicCaseBlockNode
    | FallthroughCaseBlockNode
    | DefaultCaseBlockNode;

/* --- Исключения --------------------------------------------------------- */

export interface ExceptionCatchStatementNode extends NodeBase<"exception_catch_statement"> {
    /** Тело `try`. */
    body: AnyNode;
    /**
     * Ресурсы java-try-with-resources в порядке захвата; пустой массив — обычный `try`.
     * Устройство элементов — как у `ResourceContextStatementNode.resources`.
     */
    resources: AnyNode[];
    catch_clauses: CatchClauseNode[];
    /** Ветвь `else` (Python). Ключ в camelCase. */
    elseBranch?: AnyNode;
    /** Ветвь `finally`. Ключ в camelCase. */
    finallyBranch?: AnyNode;
}

export interface CatchClauseNode extends NodeBase<"catch_clause"> {
    /**
     * Перехватываемые типы. Несколько — это Java multi-catch (`catch (A | B e)`)
     * или python-кортеж; пустой массив — `except:` / `catch (...)`.
     */
    exception_types: AnyType[];
    /** Отсутствует, если исключение не связывается с переменной. */
    name?: AnyNode;
    body: AnyNode;
}

export interface RaiseExceptionStatementNode extends NodeBase<"raise_exception_statement"> {
    /** Отсутствует у повторного возбуждения (`raise` в Python, `throw;` в C++). */
    exception?: AnyNode;
}

/**
 * Java try-with-resources без `catch` и `finally` и python-`with`. Та же конструкция с
 * ветвями — это `exception_catch_statement` с непустым `resources`.
 */
export interface ResourceContextStatementNode extends NodeBase<"resource_context_statement"> {
    /**
     * Ресурсы в порядке захвата, минимум один. Каждый элемент — либо
     * `variable_declaration` (`try (R r = e)`, `with e as r`), либо выражение без имени
     * (`try (existing)`, `with lock:`).
     */
    resources: AnyNode[];
    body: AnyNode;
}

/**
 * Объявление о том, что имена связаны не в этой области, а снаружи: python-`global x, y`
 * и `nonlocal a`. Само перенаправление лежит не в дереве, а в `rebinds` области видимости.
 */
export interface ScopeDeclarationStatementNode extends NodeBase<"scope_declaration_statement"> {
    /** `GLOBAL` — корневая область программы, `NONLOCAL` — ближайшая объемлющая функция. */
    kind: "GLOBAL" | "NONLOCAL";
    /** Имена, минимум одно. */
    names: AnyNode[];
}

/* --- Циклы -------------------------------------------------------------- */

/** Общие для всех циклов поля. */
export interface LoopCommon {
    /** Ветвь `else` (Python). Ключ в camelCase. */
    elseBranch?: AnyNode;
    /** Присутствует, только если оценка вычислена. */
    iteration_estimate?: LoopIterationEstimate;
}

export interface GeneralForLoopNode extends NodeBase<"general_for_loop">, LoopCommon {
    initializer?: AnyNode;
    condition?: AnyNode;
    update?: AnyNode;
    body: AnyNode;
}

export interface RangeForLoopNode extends NodeBase<"range_for_loop">, LoopCommon {
    identifier: AnyNode;
    range: RangeNode;
    body: AnyNode;
}

export interface ForEachLoopNode extends NodeBase<"for_each_loop">, LoopCommon {
    item: AnyNode;
    container: AnyNode;
    body: AnyNode;
}

export interface WhileLoopNode extends NodeBase<"while_loop">, LoopCommon {
    condition: AnyNode;
    body: AnyNode;
}

export interface DoWhileLoopNode extends NodeBase<"do_while_loop">, LoopCommon {
    body: AnyNode;
    condition: AnyNode;
}

/** Бесконечный цикл; `original_loop_type` хранит исходную форму записи. */
export interface InfiniteLoopNode extends NodeBase<"infinite_loop">, LoopCommon {
    body: AnyNode;
    original_loop_type: LoopType;
}

export interface BreakStatementNode extends NodeBase<"break_statement"> {
    /** Отсутствует у безметочного `break`. */
    jump_destination?: JumpLabelNode;
}

export interface ContinueStatementNode extends NodeBase<"continue_statement"> {
    jump_destination?: JumpLabelNode;
}

/** `goto`. Имя типа получено транслитерацией. */
export interface GotoStatementNode extends NodeBase<"goto_statement"> {
    jump_destination: JumpLabelNode;
}

/* =============================================================================
 * Типы
 * ========================================================================== */

/** Общие поля любого узла типа. */
export interface TypeBase<T extends string> extends NodeBase<T> {
    is_const: boolean;
    is_safe_reference: boolean;
}

/** `size` — разрядность; `unsigned` присутствует только у `int_type`. */
export interface IntTypeNode extends TypeBase<"int_type"> {
    size: number;
    unsigned: boolean;
}

export interface FloatTypeNode extends TypeBase<"float_type"> {
    size: number;
}

/**
 * Символьный тип. `CharacterType` не зарегистрирован в `JsonNodeTypeClassMapper`,
 * поэтому имя получено транслитерацией. Сериализуется как `NumericType`, т.е. с `size`.
 */
export interface CharacterTypeNode extends TypeBase<"character_type"> {
    size: number;
}

export type BooleanTypeNode = TypeBase<"boolean_type">;
export type NoReturnTypeNode = TypeBase<"no_return">;
export type UnknownTypeNode = TypeBase<"unknown_type">;

export interface StringTypeNode extends TypeBase<"string_type"> {
    char_size: number;
    /** String is represented as `char *` / `char[]` rather than a string class. */
    c_style: boolean;
    /** The string value itself is immutable (Java `String`, Python `str`). */
    immutable: boolean;
    /** Capacity of a fixed-size buffer (`char buf[64]`); absent for pointers and string classes. */
    max_length?: AnyNode;
}

export interface PointerTypeNode extends TypeBase<"pointer_type"> {
    target_type: AnyType;
}

export interface ReferenceTypeNode extends TypeBase<"reference_type"> {
    target_type: AnyType;
}

export interface ArrayTypeNode extends TypeBase<"array_type"> {
    target_type: AnyType;
    shape: ShapeNode;
}

/** `list_type`, `set_type`, `unmodifiable_list_type`, `plain_collection_type`. */
export interface PlainCollectionTypeNode
    extends TypeBase<"list_type" | "set_type" | "unmodifiable_list_type" | "plain_collection_type"> {
    target_type: AnyType;
}

export interface DictionaryTypeNode extends TypeBase<"dictionary_type"> {
    key_type: AnyType;
    value_type: AnyType;
}

/** Форма массива: число измерений и, при наличии, их размеры. */
export interface ShapeNode extends NodeBase<"shape"> {
    dimension_count: number;
    /** Элемент может быть `null`, если размер измерения неизвестен. */
    dimensions: Array<AnyNode | null>;
}

/**
 * Пользовательский тип: `user_type`, `class_type`, `interface_type`, `enum_type`,
 * `structure_type`.
 */
export interface UserTypeNode
    extends TypeBase<
        "user_type" | "class_type" | "interface_type" | "enum_type" | "structure_type"
    > {
    name: AnyNode;
}

/**
 * Обобщённый пользовательский тип: `generic_user_type`, `generic_interface`,
 * `generic_class_type`, `generic_structure_type`.
 */
export interface GenericUserTypeNode
    extends TypeBase<
        "generic_user_type" | "generic_interface" | "generic_class_type" | "generic_structure_type"
    > {
    name: AnyNode;
    /** Параметры-шаблоны. */
    templates: AnyType[];
}

/** `Optional<T>`. Имя типа получено транслитерацией. */
export interface OptionalTypeNode extends TypeBase<"optional_type"> {
    target: AnyType;
}

/** Тип-литерал. Имя типа получено транслитерацией. */
export interface LiteralTypeNode extends TypeBase<"literal_type"> {
    literal: AnyNode;
}

/** Объединение типов (`A | B`). Имя типа получено транслитерацией. */
export interface TypeAlternativesNode extends TypeBase<"type_alternatives"> {
    alternatives: AnyType[];
}

/** Кортеж. Имя типа получено транслитерацией. */
export interface TupleTypeNode extends TypeBase<"tuple_type"> {
    elements: AnyType[];
}

export type AnyType =
    | IntTypeNode
    | FloatTypeNode
    | CharacterTypeNode
    | BooleanTypeNode
    | NoReturnTypeNode
    | UnknownTypeNode
    | StringTypeNode
    | PointerTypeNode
    | ReferenceTypeNode
    | ArrayTypeNode
    | PlainCollectionTypeNode
    | DictionaryTypeNode
    | UserTypeNode
    | GenericUserTypeNode
    | OptionalTypeNode
    | LiteralTypeNode
    | TypeAlternativesNode
    | TupleTypeNode
    /** Любой другой тип, сериализованный базовым `serializeType`. */
    | TypeBase<string>;

/* =============================================================================
 * Объявления и определения
 * ========================================================================== */

/** Общая форма определения: объявление + тело. */
export interface DefinitionNode<T extends string> extends NodeBase<T> {
    declaration: AnyNode;
    body: AnyNode;
}

export type ClassDefinitionNode = DefinitionNode<"class_definition">;
/** Определение интерфейса. */
export interface InterfaceDefinitionNode extends DefinitionNode<"interface_definition"> {
    declaration: InterfaceDeclarationNode;
    body: CompoundStatementNode;
}
export type StructureDefinitionNode = DefinitionNode<"structure_definition">;
export type ObjectConstructorDefinitionNode = DefinitionNode<"object_constructor_definition">;
export type ObjectDestructorDefinitionNode = DefinitionNode<"object_destructor_definition">;
/**
 * Определение метода. Внутри `InterfaceDefinitionNode` наличие этого узла означает
 * default-метод; отдельного значения `default` в `DeclarationModifier` нет.
 */
export type MethodDefinitionNode = DefinitionNode<"method_definition">;
export type FunctionDefinitionNode = DefinitionNode<"function_definition">;
/**
 * Функция-генератор. `return_type` её объявления — тип **элемента** последовательности,
 * а не тип самого генератора.
 */
export type GeneratorDefinitionNode = DefinitionNode<"generator_definition">;
/** Класс-итератор: обычный класс, два метода которого исполняют роли протокола обхода. */
export interface IteratorDefinitionNode extends DefinitionNode<"iterator_definition"> {
    declaration: ClassDeclarationNode;
    body: CompoundStatementNode;
    /** Тип элемента последовательности. */
    element_type: AnyNode;
    /** Имя метода «есть ли ещё элемент». */
    has_next_method: SimpleIdentifierNode;
    /** Имя метода «взять следующий элемент». */
    next_method: SimpleIdentifierNode;
}

/** Фактический аргумент вызова (в определении вызова). */
export interface DefinitionArgumentNode extends NodeBase<"definition_argument"> {
    /** `null`, если имя аргумента не задано (позиционный аргумент). */
    name?: string | null;
    name_id?: AstId | null;
    initial?: AnyNode | null;
    /** `**kwargs`. */
    is_dict_unpacking: boolean;
    /** `*args`. */
    is_list_unpacking: boolean;
}

/** Формальный параметр функции/метода. */
export interface DeclarationArgumentNode extends NodeBase<"declaration_argument"> {
    target_type: AnyType;
    /** Значение по умолчанию. */
    initial?: AnyNode | null;
    is_dict_unpacking: boolean;
    is_list_unpacking: boolean;
    name?: string | null;
    name_id?: AstId | null;
    annotations: AnnotationNode[];
}

/** Аннотация/декоратор. */
export interface AnnotationNode extends NodeBase<"annotation"> {
    function: AnyNode;
    arguments: AnyNode[];
}

export interface ClassDeclarationNode<
    T extends "class_declaration" | "structure_declaration" | "interface_declaration" =
        "class_declaration" | "structure_declaration"
> extends NodeBase<T> {
    modifiers: DeclarationModifier[];
    name: AnyNode;
    /** Базовые классы/интерфейсы. */
    parents: AnyType[];
    generic_type_params: AnyType[];
    /** Узел типа, соответствующий объявляемому классу. */
    type_node: AnyType;
    annotations: AnnotationNode[];
}

/** Объявление интерфейса; по структуре наследует формат объявления класса. */
export type InterfaceDeclarationNode = ClassDeclarationNode<"interface_declaration">;

export interface EnumDeclarationNode extends NodeBase<"enum_declaration"> {
    modifiers: DeclarationModifier[];
    name: AnyNode;
    constants: Array<{
        name: AnyNode;
        /** `null`, если константе не задано значение. */
        value?: AnyNode | null;
    }>;
    /** `enum class` в C++. */
    scoped: boolean;
    type_node: AnyType;
    annotations: AnnotationNode[];
}

/**
 * Объявление метода. У `object_constructor_declaration` и
 * `object_destructor_declaration` поле `return_type` отсутствует. Непосредственный
 * `MethodDeclarationNode` в теле интерфейса является абстрактным методом.
 */
export interface MethodDeclarationNode
    extends NodeBase<
        "method_declaration" | "object_constructor_declaration" | "object_destructor_declaration"
    > {
    return_type?: AnyType;
    /** Тип-владелец метода. */
    owner?: AnyType | null;
    name: AnyNode;
    annotations: AnnotationNode[];
    modifiers: DeclarationModifier[];
    arguments: DeclarationArgumentNode[];
    parent_decl_id?: AstId | null;
    /**
     * Методы предков, которые этот метод переопределяет или реализует. Список, а не одна ссылка:
     * метод может реализовывать одинаковую сигнатуру нескольких интерфейсов, а в C++ её могут
     * объявлять несколько прямых баз — более одного элемента означает, что выбора между ними
     * язык не даёт.
     *
     * Пришло на смену полю `overridden_from_id`; прежнее поле читается как список из одного
     * элемента.
     */
    overridden_from_ids?: AstId[];
}

export interface FunctionDeclarationNode extends NodeBase<"function_declaration"> {
    return_type?: AnyType | null;
    name: AnyNode;
    annotations: AnnotationNode[];
    arguments: DeclarationArgumentNode[];
}

/* =============================================================================
 * Модули и импорты
 * ========================================================================== */

/** `ImportResolverMetadata.ImportKind`. */
export type ImportResolverKind =
    | "local_resolved_exact"
    | "local_resolved_fallback"
    /**
     * Перебор по проекту нашёл несколько файлов с подходящим хвостом пути; `resolved_file`
     * отсутствует.
     */
    | "local_ambiguous"
    | "local_unresolved"
    | "library"
    /**
     * Одна строка импорта называет несколько модулей, и разрешились они по-разному
     * (`import math, local_module`). Одного вердикта у такого узла нет; `resolved_file`
     * отсутствует.
     */
    | "mixed";

/**
 * Результат резолвинга импорта (`ImportResolverMetadata`).
 *
 * Присутствует только когда у транслятора был задан контекст проекта — иначе
 * `resolver_metadata` в узле импорта отсутствует вовсе, а не содержит выдуманных данных.
 */
export interface ImportResolverMetadataRef {
    kind: ImportResolverKind;
    /**
     * Путь к найденному файлу **относительно корня проекта**; отсутствует для `library`,
     * `local_unresolved`, `local_ambiguous` и `mixed`.
     *
     * Относительный, а не абсолютный: абсолютный путь бесполезен на другой машине и раскрывает
     * её структуру каталогов. Файл вне корня проекта результатом не считается вовсе.
     */
    resolved_file: string | null;
}

export interface ImportModuleNode
    extends NodeBase<"import_module" | "import_all_from_module" | "static_import_all"> {
    module_name: AnyNode;
    resolver_metadata?: ImportResolverMetadataRef;
}

export interface ImportMembersNode
    extends NodeBase<"import_members_from_module" | "static_import_members_from_module"> {
    module_name: AnyNode;
    members: AnyNode[];
    resolver_metadata?: ImportResolverMetadataRef;
}

export interface ImportModulesNode extends NodeBase<"import_modules"> {
    modules: AnyNode[];
    resolver_metadata?: ImportResolverMetadataRef;
}

export interface IncludeNode extends NodeBase<"include"> {
    file_name: StringLiteralNode;
    include_type: IncludeType;
    resolver_metadata?: ImportResolverMetadataRef;
}

export interface PackageDeclarationNode extends NodeBase<"package_declaration"> {
    name: AnyNode;
}

/* =============================================================================
 * Прочее
 * ========================================================================== */

/**
 * Точка входа программы — обычно корень дерева.
 *
 * `main_class` / `entry_point_node` присутствуют только тогда, когда
 * соответствующий узел не входит в `body` (иначе достаточно ссылки по id).
 */
export interface ProgramEntryPointNode extends NodeBase<"program_entry_point"> {
    body: AnyNode[];
    main_class_id?: AstId;
    main_class?: AnyNode;
    entry_point_node_id?: AstId;
    entry_point_node?: AnyNode;
}

export interface CommentNode extends NodeBase<"comment"> {
    /** Текст без символов комментария. */
    content: string;
    is_multiline: boolean;
}

/* =============================================================================
 * Общие объединения
 * ========================================================================== */

/** Все имена узлов, зарегистрированные в `JsonNodeTypeClassMapper`. */
export type NodeTypeName =
    // Операторы
    | "add_operator"
    | "sub_operator"
    | "mul_operator"
    | "div_operator"
    | "mod_operator"
    | "matrix_mul_operator"
    | "floor_div_operator"
    | "pow_operator"
    | "eq_operator"
    | "ge_operator"
    | "gt_operator"
    | "le_operator"
    | "lt_operator"
    | "not_eq_operator"
    | "reference_eq_operator"
    | "instance_of_operator"
    | "contains_operator"
    | "compound_comparison"
    | "three_way_comparison"
    | "short_circuit_and_operator"
    | "short_circuit_or_operator"
    | "long_circuit_and_operator"
    | "long_circuit_or_operator"
    | "not_operator"
    | "unary_minus_operator"
    | "unary_plus_operator"
    | "unary_postfix_inc_operator"
    | "unary_postfix_dec_operator"
    | "unary_prefix_inc_operator"
    | "unary_prefix_dec_operator"
    | "bitwise_and_operator"
    | "bitwise_or_operator"
    | "xor_operator"
    | "inversion_operator"
    | "left_shift_operator"
    | "right_shift_operator"
    | "ternary_operator"
    // Литералы
    | "float_literal"
    | "int_literal"
    | "string_literal"
    | "null_literal"
    | "bool_literal"
    | "char_literal"
    | "array_literal"
    | "list_literal"
    | "set_literal"
    | "unmodifiable_list_literal"
    | "interpolated_string_literal"
    | "dictionary_literal"
    // Выражения
    | "parenthesized_expression"
    | "identifier"
    | "assignment_expression"
    | "function_call"
    | "index_expression"
    | "range"
    | "qualified_identifier"
    | "scoped_identifier"
    | "self_reference"
    | "super_class_reference"
    | "alias"
    | "member_access"
    | "pointer_member_access"
    | "pointer_pack"
    | "pointer_unpack"
    | "constructor_call"
    | "destructor_call"
    | "method_call"
    | "array_initializer"
    | "cast_type_expression"
    | "comma_expression"
    | "delete_expression"
    | "key_value_pair"
    | "sizeof_expression"
    | "expression_sequence"
    | "placement_new_expression"
    | "object_new_expression"
    | "array_new_expression"
    | "string_format"
    | "container_based_comprehension"
    | "range_based_comprehension"
    | "memory_allocation_call"
    | "memory_free_call"
    // Ввод-вывод
    | "format_input"
    | "format_print"
    | "pointer_input_command"
    | "input_command"
    | "print_values"
    // Инструкции
    | "assignment_statement"
    | "empty_statement"
    | "variable_declaration"
    | "field_declaration"
    | "compound_statement"
    | "expression_statement"
    | "return_statement"
    | "yield_statement"
    | "chained_assignment_statement"
    | "multiple_assignment_statement"
    | "delete_statement"
    | "if_statement"
    | "condition_branch"
    | "switch_statement"
    | "basic_case_block"
    | "default_case_block"
    | "fallthrough_case_block"
    | "exception_catch_statement"
    | "catch_clause"
    | "raise_exception_statement"
    | "resource_context_statement"
    | "scope_declaration_statement"
    | "general_for_loop"
    | "range_for_loop"
    | "for_each_loop"
    | "while_loop"
    | "do_while_loop"
    | "infinite_loop"
    | "break_statement"
    | "continue_statement"
    // Типы
    | "int_type"
    | "float_type"
    | "boolean_type"
    | "pointer_type"
    | "reference_type"
    | "string_type"
    | "list_type"
    | "array_type"
    | "plain_collection_type"
    | "shape"
    | "dictionary_type"
    | "set_type"
    | "unmodifiable_list_type"
    | "generic_user_type"
    | "generic_interface"
    | "no_return"
    | "unknown_type"
    | "user_type"
    | "class_type"
    | "interface_type"
    | "enum_type"
    | "structure_type"
    | "generic_class_type"
    | "generic_structure_type"
    // Определения
    | "class_definition"
    | "interface_definition"
    | "structure_definition"
    | "object_constructor_definition"
    | "object_destructor_definition"
    | "method_definition"
    | "function_definition"
    | "generator_definition"
    | "iterator_definition"
    | "definition_argument"
    // Объявления
    | "declaration_argument"
    | "annotation"
    | "class_declaration"
    | "interface_declaration"
    | "structure_declaration"
    | "enum_declaration"
    | "object_constructor_declaration"
    | "object_destructor_declaration"
    | "separated_variable_declaration"
    | "method_declaration"
    | "function_declaration"
    // Модули
    | "static_import_all"
    | "static_import_members_from_module"
    | "import_all_from_module"
    | "import_members_from_module"
    | "import_module"
    | "import_modules"
    | "include"
    | "package_declaration"
    // Прочее
    | "program_entry_point"
    | "comment"
    // Незарегистрированные классы: имя получается транслитерацией CamelCase -> snake_case
    | "jump_label"
    | "goto_statement"
    | "list_unpacking_assignment_statement"
    | "list_unpacking_variable_declaration"
    | "character_type"
    | "optional_type"
    | "literal_type"
    | "type_alternatives"
    | "tuple_type"
    // Любое другое имя, полученное транслитерацией имени Java-класса
    | (string & {});

/** Любой узел дерева. */
export type AnyNode =
    // Операторы
    | AddOpNode
    | SubOpNode
    | MulOpNode
    | DivOpNode
    | ModOpNode
    | MatMulOpNode
    | FloorDivOpNode
    | PowOpNode
    | EqOpNode
    | GeOpNode
    | GtOpNode
    | LeOpNode
    | LtOpNode
    | NotEqOpNode
    | ReferenceEqOpNode
    | InstanceOfOpNode
    | ContainsOpNode
    | CompoundComparisonNode
    | ThreeWayComparisonOpNode
    | ShortCircuitAndOpNode
    | ShortCircuitOrOpNode
    | LongCircuitAndOpNode
    | LongCircuitOrOpNode
    | NotOpNode
    | UnaryMinusOpNode
    | UnaryPlusOpNode
    | PostfixIncrementOpNode
    | PostfixDecrementOpNode
    | PrefixIncrementOpNode
    | PrefixDecrementOpNode
    | BitwiseAndOpNode
    | BitwiseOrOpNode
    | XorOpNode
    | InversionOpNode
    | LeftShiftOpNode
    | RightShiftOpNode
    | TernaryOperatorNode
    // Литералы
    | FloatLiteralNode
    | IntegerLiteralNode
    | StringLiteralNode
    | NullLiteralNode
    | BoolLiteralNode
    | CharacterLiteralNode
    | ArrayLiteralNode
    | ListLiteralNode
    | SetLiteralNode
    | UnmodifiableListLiteralNode
    | InterpolatedStringLiteralNode
    | DictionaryLiteralNode
    // Идентификаторы
    | SimpleIdentifierNode
    | JumpLabelNode
    | QualifiedIdentifierNode
    | ScopedIdentifierNode
    | SelfReferenceNode
    | SuperClassReferenceNode
    | AliasNode
    // Выражения
    | ParenthesizedExpressionNode
    | AssignmentExpressionNode
    | FunctionCallNode
    | MethodCallNode
    | ConstructorCallNode
    | DestructorCallNode
    | IndexExpressionNode
    | MemberAccessNode
    | PointerMemberAccessNode
    | PointerPackOpNode
    | PointerUnpackOpNode
    | RangeNode
    | ArrayInitializerNode
    | CastTypeExpressionNode
    | CommaExpressionNode
    | ExpressionSequenceNode
    | DeleteExpressionNode
    | KeyValuePairNode
    | SizeofExpressionNode
    | StringFormatNode
    | ObjectNewExpressionNode
    | PlacementNewExpressionNode
    | ArrayNewExpressionNode
    | MemoryAllocationCallNode
    | MemoryFreeCallNode
    | ContainerBasedComprehensionNode
    | RangeBasedComprehensionNode
    // Ввод-вывод
    | FormatInputNode
    | FormatPrintNode
    | InputCommandNode
    | PointerInputCommandNode
    | PrintValuesNode
    // Инструкции
    | AssignmentStatementNode
    | ChainedAssignmentStatementNode
    | MultipleAssignmentStatementNode
    | ListUnpackingAssignmentStatementNode
    | ListUnpackingVariableDeclarationNode
    | EmptyStatementNode
    | CompoundStatementNode
    | ExpressionStatementNode
    | ReturnStatementNode
    | YieldStatementNode
    | DeleteStatementNode
    | VariableDeclarationNode
    | SeparatedVariableDeclarationNode
    | IfStatementNode
    | ConditionBranchNode
    | SwitchStatementNode
    | CaseBlockNode
    | ExceptionCatchStatementNode
    | ResourceContextStatementNode
    | ScopeDeclarationStatementNode
    | CatchClauseNode
    | RaiseExceptionStatementNode
    | GeneralForLoopNode
    | RangeForLoopNode
    | ForEachLoopNode
    | WhileLoopNode
    | DoWhileLoopNode
    | InfiniteLoopNode
    | BreakStatementNode
    | ContinueStatementNode
    | GotoStatementNode
    // Типы
    | AnyType
    | ShapeNode
    // Объявления и определения
    | ClassDefinitionNode
    | InterfaceDefinitionNode
    | StructureDefinitionNode
    | ObjectConstructorDefinitionNode
    | ObjectDestructorDefinitionNode
    | MethodDefinitionNode
    | FunctionDefinitionNode
    | GeneratorDefinitionNode
    | IteratorDefinitionNode
    | DefinitionArgumentNode
    | DeclarationArgumentNode
    | AnnotationNode
    | ClassDeclarationNode
    | InterfaceDeclarationNode
    | EnumDeclarationNode
    | MethodDeclarationNode
    | FunctionDeclarationNode
    // Модули
    | ImportModuleNode
    | ImportMembersNode
    | ImportModulesNode
    | IncludeNode
    | PackageDeclarationNode
    // Прочее
    | ProgramEntryPointNode
    | CommentNode;
