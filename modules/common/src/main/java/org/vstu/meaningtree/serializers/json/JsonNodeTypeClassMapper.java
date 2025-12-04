package org.vstu.meaningtree.serializers.json;

import org.vstu.meaningtree.nodes.Comment;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.ProgramEntryPoint;
import org.vstu.meaningtree.nodes.declarations.*;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.definitions.*;
import org.vstu.meaningtree.nodes.definitions.components.DefinitionArgument;
import org.vstu.meaningtree.nodes.expressions.ParenthesizedExpression;
import org.vstu.meaningtree.nodes.expressions.bitwise.*;
import org.vstu.meaningtree.nodes.expressions.calls.ConstructorCall;
import org.vstu.meaningtree.nodes.expressions.calls.FunctionCall;
import org.vstu.meaningtree.nodes.expressions.calls.MethodCall;
import org.vstu.meaningtree.nodes.expressions.comparison.*;
import org.vstu.meaningtree.nodes.expressions.comprehensions.ContainerBasedComprehension;
import org.vstu.meaningtree.nodes.expressions.comprehensions.RangeBasedComprehension;
import org.vstu.meaningtree.nodes.expressions.identifiers.*;
import org.vstu.meaningtree.nodes.expressions.literals.*;
import org.vstu.meaningtree.nodes.expressions.logical.*;
import org.vstu.meaningtree.nodes.expressions.math.*;
import org.vstu.meaningtree.nodes.expressions.newexpr.ObjectNewExpression;
import org.vstu.meaningtree.nodes.expressions.newexpr.PlacementNewExpression;
import org.vstu.meaningtree.nodes.expressions.other.*;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerMemberAccess;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerPackOp;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerUnpackOp;
import org.vstu.meaningtree.nodes.expressions.unary.*;
import org.vstu.meaningtree.nodes.io.*;
import org.vstu.meaningtree.nodes.memory.MemoryAllocationCall;
import org.vstu.meaningtree.nodes.memory.MemoryFreeCall;
import org.vstu.meaningtree.nodes.modules.*;
import org.vstu.meaningtree.nodes.statements.*;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.nodes.statements.assignments.CompoundAssignmentStatement;
import org.vstu.meaningtree.nodes.statements.assignments.MultipleAssignmentStatement;
import org.vstu.meaningtree.nodes.statements.conditions.IfStatement;
import org.vstu.meaningtree.nodes.statements.conditions.SwitchStatement;
import org.vstu.meaningtree.nodes.statements.conditions.components.BasicCaseBlock;
import org.vstu.meaningtree.nodes.statements.conditions.components.ConditionBranch;
import org.vstu.meaningtree.nodes.statements.conditions.components.DefaultCaseBlock;
import org.vstu.meaningtree.nodes.statements.conditions.components.FallthroughCaseBlock;
import org.vstu.meaningtree.nodes.statements.loops.*;
import org.vstu.meaningtree.nodes.statements.loops.control.BreakStatement;
import org.vstu.meaningtree.nodes.statements.loops.control.ContinueStatement;
import org.vstu.meaningtree.nodes.types.*;
import org.vstu.meaningtree.nodes.types.builtin.*;
import org.vstu.meaningtree.nodes.types.containers.*;
import org.vstu.meaningtree.nodes.types.containers.components.Shape;
import org.vstu.meaningtree.nodes.types.user.GenericClass;
import org.vstu.meaningtree.nodes.types.user.Structure;
import org.vstu.meaningtree.utils.TransliterationUtils;

import java.util.HashMap;
import java.util.Map;

public class JsonNodeTypeClassMapper {
    private static final Map<String, Class<? extends Node>> TYPE_TO_CLASS = new HashMap<>();
    private static final Map<Class<? extends Node>, String> CLASS_TO_TYPE = new HashMap<>();

    static {
        // Operators - Math
        register("add_operator", AddOp.class);
        register("sub_operator", SubOp.class);
        register("mul_operator", MulOp.class);
        register("div_operator", DivOp.class);
        register("mod_operator", ModOp.class);
        register("matrix_mul_operator", MatMulOp.class);
        register("floor_div_operator", FloorDivOp.class);
        register("pow_operator", PowOp.class);

        // Operators - Comparison
        register("eq_operator", EqOp.class);
        register("ge_operator", GeOp.class);
        register("gt_operator", GtOp.class);
        register("le_operator", LeOp.class);
        register("lt_operator", LtOp.class);
        register("not_eq_operator", NotEqOp.class);
        register("reference_eq_operator", ReferenceEqOp.class);
        register("instance_of_operator", InstanceOfOp.class);
        register("contains_operator", ContainsOp.class);
        register("compound_comparison", CompoundComparison.class);
        register("three_way_comparison", ThreeWayComparisonOp.class);

        // Operators - Logical
        register("short_circuit_and_operator", ShortCircuitAndOp.class);
        register("short_circuit_or_operator", ShortCircuitOrOp.class);
        register("long_circuit_and_operator", LongCircuitAndOp.class);
        register("long_circuit_or_operator", LongCircuitOrOp.class);
        register("unary_operator", NotOp.class);

        // Operators - Unary
        register("unary_minus_operator", UnaryMinusOp.class);
        register("unary_plus_operator", UnaryPlusOp.class);
        register("unary_postfix_inc_operator", PostfixIncrementOp.class);
        register("unary_postfix_dec_operator", PostfixDecrementOp.class);
        register("unary_prefix_inc_operator", PrefixIncrementOp.class);
        register("unary_prefix_dec_operator", PrefixDecrementOp.class);

        // Operators - Bitwise
        register("bitwise_and_operator", BitwiseAndOp.class);
        register("bitwise_or_operator", BitwiseOrOp.class);
        register("xor_operator", XorOp.class);
        register("inversion_operator", InversionOp.class);
        register("left_shift_operator", LeftShiftOp.class);
        register("right_shift_operator", RightShiftOp.class);

        // Operators - Other
        register("ternary_operator", TernaryOperator.class);

        // Literals
        register("float_literal", FloatLiteral.class);
        register("int_literal", IntegerLiteral.class);
        register("string_literal", StringLiteral.class);
        register("null_literal", NullLiteral.class);
        register("bool_literal", BoolLiteral.class);
        register("char_literal", CharacterLiteral.class);
        register("array_literal", ArrayLiteral.class);
        register("list_literal", ListLiteral.class);
        register("set_literal", SetLiteral.class);
        register("unmodifiable_list_literal", UnmodifiableListLiteral.class);
        register("interpolated_string_literal", InterpolatedStringLiteral.class);

        // Expressions
        register("parenthesized_expression", ParenthesizedExpression.class);
        register("identifier", SimpleIdentifier.class);
        register("assignment_expression", AssignmentExpression.class);
        register("function_call", FunctionCall.class);
        register("index_expression", IndexExpression.class);
        register("range", Range.class);
        register("qualified_identifier", QualifiedIdentifier.class);
        register("scoped_identifier", ScopedIdentifier.class);
        register("self_reference", SelfReference.class);
        register("super_class_reference", SuperClassReference.class);
        register("alias", Alias.class);
        register("member_access", MemberAccess.class);

        // Pointers
        register("pointer_member_access", PointerMemberAccess.class);
        register("pointer_pack", PointerPackOp.class);
        register("pointer_unpack", PointerUnpackOp.class);

        // Calls
        register("constructor_call", ConstructorCall.class);
        register("method_call", MethodCall.class);

        // Other expressions
        register("array_initializer", ArrayInitializer.class);
        register("cast_type_expression", CastTypeExpression.class);
        register("comma_expression", CommaExpression.class);
        register("delete_expression", DeleteExpression.class);
        register("key_value_pair", KeyValuePair.class);
        register("sizeof_expression", SizeofExpression.class);
        register("expression_sequence", ExpressionSequence.class);
        register("placement_new_expression", PlacementNewExpression.class);
        register("object_new_expression", ObjectNewExpression.class);

        // Comprehensions
        register("container_based_comprehension", ContainerBasedComprehension.class);
        register("range_based_comprehension", RangeBasedComprehension.class);

        // Memory
        register("memory_allocation_call", MemoryAllocationCall.class);
        register("memory_free_call", MemoryFreeCall.class);

        // IO
        register("format_input", FormatInput.class);
        register("format_print", FormatPrint.class);
        register("pointer_input_command", PointerInputCommand.class);
        register("input_command", InputCommand.class);
        register("print_values", PrintValues.class);

        // Statements
        register("assignment_statement", AssignmentStatement.class);
        register("empty_statement", EmptyStatement.class);
        register("variable_declaration", VariableDeclaration.class);
        register("field_declaration", FieldDeclaration.class);
        register("compound_statement", CompoundStatement.class);
        register("expression_statement", ExpressionStatement.class);
        register("return_statement", ReturnStatement.class);
        register("compound_assignment_statement", CompoundAssignmentStatement.class);
        register("multiple_assignment_statement", MultipleAssignmentStatement.class);
        register("delete_statement", DeleteStatement.class);

        // Control flow
        register("if_statement", IfStatement.class);
        register("condition_branch", ConditionBranch.class);
        register("switch_statement", SwitchStatement.class);
        register("basic_case_block", BasicCaseBlock.class);
        register("default_case_block", DefaultCaseBlock.class);
        register("fallthrough_case_block", FallthroughCaseBlock.class);

        // Loops
        register("general_for_loop", GeneralForLoop.class);
        register("range_for_loop", RangeForLoop.class);
        register("while_loop", WhileLoop.class);
        register("do_while_loop", DoWhileLoop.class);
        register("infinite_loop", InfiniteLoop.class);
        register("break_statement", BreakStatement.class);
        register("continue_statement", ContinueStatement.class);

        // Types
        register("int_type", IntType.class);
        register("float_type", FloatType.class);
        register("boolean_type", BooleanType.class);
        register("pointer_type", PointerType.class);
        register("reference_type", ReferenceType.class);
        register("string_type", StringType.class);
        register("list_type", ListType.class);
        register("array_type", ArrayType.class);
        register("plain_collection_type", PlainCollectionType.class);
        register("shape", Shape.class);
        register("dictionary_type", DictionaryType.class);
        register("set_type", SetType.class);
        register("unmodifiable_list_type", UnmodifiableListType.class);
        register("generic_user_type", GenericUserType.class);
        register("generic_interface", GenericInterface.class);
        register("no_return", NoReturn.class);
        register("unknown_type", UnknownType.class);
        register("user_type", UserType.class);
        register("class_type", org.vstu.meaningtree.nodes.types.user.Class.class);
        register("enum_type", org.vstu.meaningtree.nodes.types.user.Enum.class);
        register("structure_type", Structure.class);
        register("generic_class_type", GenericClass.class);

        // Definitions
        register("class_definition", ClassDefinition.class);
        register("object_constructor_definition", ObjectConstructorDefinition.class);
        register("object_destructor_definition", ObjectDestructorDefinition.class);
        register("method_definition", MethodDefinition.class);
        register("function_definition", FunctionDefinition.class);
        register("definition_argument", DefinitionArgument.class);

        // Declarations
        register("declaration_argument", DeclarationArgument.class);
        register("annotation", Annotation.class);
        register("class_declaration", ClassDeclaration.class);
        register("object_constructor_declaration", ObjectConstructorDeclaration.class);
        register("object_destructor_declaration", ObjectDestructorDeclaration.class);
        register("separated_variable_declaration", SeparatedVariableDeclaration.class);
        register("method_declaration", MethodDeclaration.class);
        register("function_declaration", FunctionDeclaration.class);

        // Modules
        register("static_import_all", StaticImportAll.class);
        register("static_import_members_from_module", StaticImportMembersFromModule.class);
        register("import_all_from_module", ImportAllFromModule.class);
        register("import_members_from_module", ImportMembersFromModule.class);
        register("import_module", ImportModule.class);
        register("import_modules", ImportModules.class);
        register("package_declaration", PackageDeclaration.class);

        // Other
        register("program_entry_point", ProgramEntryPoint.class);
        register("comment", Comment.class);
    }

    private static void register(String type, Class<? extends Node> clazz) {
        TYPE_TO_CLASS.put(type, clazz);
        CLASS_TO_TYPE.put(clazz, type);
    }

    /**
     * Get the Node class corresponding to a JSON type string
     * @param type JSON type string (e.g., "add_operator")
     * @return corresponding Node class, or null if not found
     */
    public static Class<? extends Node> getClassForType(String type) {
        return TYPE_TO_CLASS.get(type);
    }

    /**
     * Get the JSON type string for a Node class
     * @param clazz Node class
     * @return corresponding JSON type string, or null if not found
     */
    public static String getTypeForClass(Class<? extends Node> clazz, boolean noTransliteration) {
       String type = CLASS_TO_TYPE.getOrDefault(clazz, null);
       if (type == null && !noTransliteration) {
           return TransliterationUtils.camelToSnake(clazz.getSimpleName());
       }
       return type;
    }

    /**
     * Get the JSON type string for a Node instance
     * @param node Node instance
     * @param strict don't use transliteration for node name transformation
     * @return corresponding JSON type string, or null if not found
     */
    public static String getTypeForNode(Node node, boolean strict) {
        if (node == null) {
            return null;
        }
        return getTypeForClass(node.getClass(), strict);
    }

    /**
     * Get the JSON type string for a Node instance
     * @param node Node instance
     * @return corresponding JSON type string, or null if not found
     */
    public static String getTypeForNode(Node node) {
        return getTypeForNode(node, false);
    }

    /**
     * Check if a JSON type string is registered
     * @param type JSON type string
     * @return true if registered, false otherwise
     */
    public static boolean isTypeRegistered(String type) {
        return TYPE_TO_CLASS.containsKey(type);
    }

    /**
     * Check if a Node class is registered
     * @param clazz Node class
     * @return true if registered, false otherwise
     */
    public static boolean isClassRegistered(Class<? extends Node> clazz) {
        return CLASS_TO_TYPE.containsKey(clazz);
    }
}