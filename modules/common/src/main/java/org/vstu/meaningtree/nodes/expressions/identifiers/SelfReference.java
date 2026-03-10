package org.vstu.meaningtree.nodes.expressions.identifiers;

import org.vstu.meaningtree.utils.InternalNode;

@InternalNode
public class SelfReference extends SimpleIdentifier {
    public SelfReference(String name) {
        super(name);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SelfReference)) {
            return false;
        }
        return super.equals(o);
    }
}
