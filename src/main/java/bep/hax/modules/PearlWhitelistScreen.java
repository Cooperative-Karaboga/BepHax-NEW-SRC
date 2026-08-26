package bep.hax.modules;

import java.util.List;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;

public class PearlWhitelistScreen extends WindowScreen {
    private final PearlLoader module;
    private WTextBox addInput;

    public PearlWhitelistScreen(GuiTheme theme, PearlLoader module) {
        super(theme, "Pearl Loader Whitelist");
        this.module = module;
    }

    @Override
    public void initWidgets() {
        WVerticalList list = this.add(this.theme.verticalList()).expandX().widget();
        WHorizontalList addRow = list.add(this.theme.horizontalList()).expandX().widget();
        this.addInput = addRow.add(this.theme.textBox("", "Player name")).minWidth(220.0).expandX().widget();
        WButton addButton = addRow.add(this.theme.button("Add")).widget();
        addButton.action = this::addCurrent;
        this.enterAction = addButton.action;
        list.add(this.theme.horizontalSeparator()).expandX();
        List<String> players = this.module.getWhitelistedPlayers();
        if (players.isEmpty()) {
            list.add(this.theme.label("No whitelisted players. Add one above.")).expandX();
        } else {
            for (String player : players) {
                WHorizontalList row = list.add(this.theme.horizontalList()).expandX().widget();
                row.add(this.theme.label(player)).expandX();
                WButton remove = row.add(this.theme.button("Remove")).widget();
                remove.action = () -> {
                    this.module.removeWhitelistedPlayer(player);
                    this.reload();
                };
            }
        }
    }

    private void addCurrent() {
        this.module.addWhitelistedPlayer(this.addInput.get());
        this.reload();
    }
}
