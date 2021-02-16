package mindustry.client.ui;

import arc.func.Boolp;
import arc.func.Prov;
import arc.scene.Element;
import arc.scene.ui.Label;
import mindustry.ui.Styles;

public class Hint {

    private final Boolp visible;
    private final Prov<CharSequence> message;

    public Hint(Boolp visible, Prov<CharSequence> message) {
        this.visible = visible;
        this.message = message;
    }

    public Element label() {
        Label label = new Label(message);
        label.visible(visible);
        label.setStyle(Styles.outlineLabel);
        return label;
    }
}
