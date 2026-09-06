package org.vstu.meaningtree.serializers.model;


import org.vstu.meaningtree.utils.Label;

import java.util.HashMap;
import java.util.Map;

public class SerializedLabel extends AbstractSerializedNode {
    public SerializedLabel(Label label) {
        super(new HashMap<>());
        values.put("id", label.getId());
        if (label.isStealth()) {
            values.put("stealth", true);
        }
        if (label.hasAttribute()) {
            values.put("attr", label.getAttribute());
        }
    }

    public SerializedLabel(Map<String, Object> valueMap) {
        super(new HashMap<>());
        values.put("id", valueMap.getOrDefault("id", Short.MAX_VALUE));
        if (Boolean.TRUE.equals(valueMap.get("stealth"))) {
            values.put("stealth", true);
        }
        if (valueMap.containsKey("attr")) {
            values.put("attr", valueMap.get("attr"));
        }
    }

    @Override
    public boolean hasManyNodes() {
        return false;
    }

    public Label toObject() {
        // Number, а не Integer: конструктор от Label кладёт сюда short, а разбор внешнего
        // представления — Integer, и приведение к одному из двух ломало вторую половину.
        Number val = (Number) values.getOrDefault("id", Short.MAX_VALUE);
        boolean stealth = Boolean.TRUE.equals(values.get("stealth"));
        Object attr = values.getOrDefault("attr", null);
        Label label = attr == null
                ? new Label(val.shortValue())
                : new Label(val.shortValue(), attr);
        return stealth ? label.setStealth() : label;
    }
}
