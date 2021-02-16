package mindustry.client.ui;

import arc.func.Cons;
import arc.scene.Element;
import arc.scene.ui.layout.Table;

public class CustomHint extends Hint {
    private final Table table1;

    public CustomHint(Cons<Table> table) {
        super(null, null);
        table1 = new Table();
        table1.table(table);
    }

    @Override
    public Element label() {
        return table1;
    }
}
