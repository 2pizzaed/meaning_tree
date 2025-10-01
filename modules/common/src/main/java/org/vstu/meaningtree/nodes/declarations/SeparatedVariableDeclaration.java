package org.vstu.meaningtree.nodes.declarations;

import org.vstu.meaningtree.exceptions.MeaningTreeException;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Declaration;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.components.VariableDeclarator;

import java.util.ArrayList;
import java.util.List;

public class SeparatedVariableDeclaration extends Declaration {
    /***
     * Подходит для множественных объявлений переменных с разными типами, в поддерживаемых языках (C++)
     * Неподдерживаемые языки должны разбивать это на разные statements
     */

    @TreeNode private List<VariableDeclaration> declarations;

    public SeparatedVariableDeclaration(VariableDeclaration decl1, VariableDeclaration ... decl) {
        declarations = new ArrayList<>();
        declarations.add(decl1);
        declarations.addAll(List.of(decl));
    }

    public List<VariableDeclaration> getDeclarations() {
        return List.copyOf(declarations);
    }

    public SeparatedVariableDeclaration(List<VariableDeclaration> decl) {
        if (decl == null || decl.isEmpty()) {
            throw new MeaningTreeException("Required at least one argument at separate variable declaration");
        }
        declarations = new ArrayList<>(decl);
    }

    public boolean canBeReduced() {
        Type t = declarations.getFirst().getType();
        for (VariableDeclaration decl : declarations) {
            if (!t.equals(decl.getType())) {
                return false;
            }
        }
        return true;
    }

    public VariableDeclaration reduce() {
        if (!canBeReduced()) {
            return null;
        }
        Type t = declarations.getFirst().getType();
        List<VariableDeclarator> decls = new ArrayList<>();
        for (VariableDeclaration decl : this.declarations) {
            decls.addAll(List.of(decl.getDeclarators()));
        }
        return new VariableDeclaration(t, decls);
    }
}
