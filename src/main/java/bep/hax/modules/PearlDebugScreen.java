package bep.hax.modules;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;

public class PearlDebugScreen extends WindowScreen {
    private final PearlLoader module;
    private final List<WLabel> statusLabels = new ArrayList<>();

    public PearlDebugScreen(GuiTheme theme, PearlLoader module) {
        super(theme, "Pearl Loader Debug");
        this.module = module;
    }

    @Override
    public void initWidgets() {
        this.statusLabels.clear();
        WVerticalList list = this.add(this.theme.verticalList()).expandX().widget();
        WHorizontalList toggleRow = list.add(this.theme.horizontalList()).expandX().widget();
        toggleRow.add(this.theme.label("Debug logging:"));
        WCheckbox debugToggle = toggleRow.add(this.theme.checkbox(this.module.isDebugEnabled())).widget();
        debugToggle.action = () -> this.module.setDebugEnabled(debugToggle.checked);
        list.add(this.theme.horizontalSeparator("Live status")).expandX();

        for (String line : this.module.debugStatusLines()) {
            this.statusLabels.add(list.add(this.theme.label(line)).expandX().widget());
        }
    }

    @Override
    public void tick() {
        super.tick();
        List<String> lines = this.module.debugStatusLines();
        if (lines.size() != this.statusLabels.size()) {
            this.reload();
        } else {
            for (int i = 0; i < lines.size(); i++) {
                this.statusLabels.get(i).set(lines.get(i));
            }
        }
    }
}
