package org.vstu.meaningtree.languages;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.languages.helpers.JumpTargets;
import org.vstu.meaningtree.nodes.expressions.identifiers.JumpLabel;
import org.vstu.meaningtree.nodes.statements.Loop;
import org.vstu.meaningtree.nodes.statements.conditions.SwitchStatement;
import org.vstu.meaningtree.nodes.statements.loops.control.BreakStatement;

import java.util.ArrayList;
import java.util.List;

/**
 * break внутри match в Python выходит из цикла, а break без метки в дереве — из ближайшего
 * цикла или switch, как в C. Такой break получает метку своего цикла, и дерево значит то же,
 * что исходник.
 */
final class PythonMatchBreakLabeler {
    private record Fix(BreakStatement jump, Loop loop) {
    }

    private PythonMatchBreakLabeler() {
    }

    static void label(MeaningTree tree) {
        // Сначала собрать, потом менять: метки — дети узлов, обход не переживает их подмену
        List<Fix> fixes = new ArrayList<>();
        for (NodeInfo info : tree) {
            if (info.node() instanceof BreakStatement jump && jump.getJumpDestination() == null
                    && JumpTargets.target(info, true) instanceof SwitchStatement
                    && JumpTargets.target(info, false) instanceof Loop loop) {
                fixes.add(new Fix(jump, loop));
            }
        }
        int count = 0;
        for (Fix fix : fixes) {
            if (fix.loop().getJumpLabel() == null) {
                fix.loop().setJumpLabel(new JumpLabel("loop" + ++count));
            }
            fix.jump().setJumpDestination(new JumpLabel(fix.loop().getJumpLabel().getName()));
        }
    }
}
