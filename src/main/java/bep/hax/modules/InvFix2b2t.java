package bep.hax.modules;

import bep.hax.Bep;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

public class InvFix2b2t extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    public final Setting<Boolean> fixGhostItems = this.sgGeneral
        .add(
            new Builder()
                .name("fix-ghost-items")
                .description("Prevents ghost items from appearing when dragging items like shulker boxes, bundles, and filled maps.")
                .defaultValue(true)
                .build()
        );
    public final Setting<Boolean> fixBundles = this.sgGeneral
        .add(
            new Builder()
                .name("fix-bundles")
                .description("Fixes bundle contents being in reverse order on 2b2t, allowing you to select the correct item.")
                .defaultValue(true)
                .build()
        );

    public InvFix2b2t() {
        super(Bep.CATEGORY, "2b2tInvFix", "Fixes ghost items and broken bundles on 2b2t. Credit: Enderkill98");
    }
}
