
package org.vstu.meaningtree.utils.analysis;

import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.iterators.utils.NodeIterable;
import org.vstu.meaningtree.nodes.expressions.logical.LongCircuitAndOp;
import org.vstu.meaningtree.nodes.expressions.logical.LongCircuitOrOp;
import org.vstu.meaningtree.nodes.expressions.logical.ShortCircuitAndOp;
import org.vstu.meaningtree.nodes.expressions.logical.ShortCircuitOrOp;
import org.vstu.meaningtree.nodes.expressions.other.TernaryOperator;
import org.vstu.meaningtree.nodes.statements.Loop;
import org.vstu.meaningtree.nodes.statements.conditions.IfStatement;
import org.vstu.meaningtree.nodes.statements.conditions.SwitchStatement;

import java.util.Objects;

public class CyclomaticComplexityAnalyzer {
    public int analyze(NodeIterable root) {
        Objects.requireNonNull(root, "root must not be null");

        int complexity = 1;
        for (NodeInfo info : root) {
            if (info.node() instanceof IfStatement ifStatement) {
                complexity += ifStatement.getBranches().size();
            } else if (info.node() instanceof SwitchStatement switchStatement) {
                complexity += switchStatement.getCases().size();
            } else if (info.node() instanceof Loop) {
                complexity += 1;
            } else if (info.node() instanceof TernaryOperator) {
                complexity += 1;
            } else if (info.node() instanceof ShortCircuitAndOp
                    || info.node() instanceof ShortCircuitOrOp
                    || info.node() instanceof LongCircuitAndOp
                    || info.node() instanceof LongCircuitOrOp) {
                complexity += 1;
            }
        }
        return complexity;
    }
}
