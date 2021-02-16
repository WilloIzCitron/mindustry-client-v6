package mindustry.client.ui;

import arc.func.Boolp;
import arc.func.Prov;
import arc.scene.ui.Label;

public class Hint {

    private final Boolp visible;
    private final Prov<CharSequence> message;

    public Hint(Boolp visible, Prov<CharSequence> message) {
        this.visible = visible;
        this.message = message;
    }

    public Label label() {
        Label label = new Label(message);
        label.visible(visible);
        return label;
    }
}
