package bep.hax.mixin;

import bep.hax.modules.AutoCraft;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AutoCraftScreenMixin {
    @Shadow
    protected int leftPos;
    @Shadow
    protected int topPos;
    @Unique
    private AutoCraft autoCraft = null;
    @Unique
    private final List<Button> craftButtons = new ArrayList<>();

    @Inject(method = "init()V", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>)(Object)this;
        if (screen.getMenu() instanceof CraftingMenu || screen.getMenu() instanceof InventoryMenu) {
            Modules modules = Modules.get();
            if (modules != null) {
                this.autoCraft = modules.get(AutoCraft.class);
                if (this.autoCraft != null && this.autoCraft.isActive()) {
                    this.craftButtons.clear();
                    int startY = this.topPos + this.autoCraft.getButtonY();
                    int buttonX = this.leftPos + this.autoCraft.getButtonX();
                    int buttonWidth = this.autoCraft.getButtonWidth();
                    int buttonHeight = this.autoCraft.getButtonHeight();
                    int buttonSpacing = this.autoCraft.getButtonSpacing();
                    List<AutoCraft.CraftButton> buttons = this.autoCraft.getButtons();

                    for (int i = 0; i < buttons.size(); i++) {
                        AutoCraft.CraftButton craftBtn = buttons.get(i);
                        int yPos = startY + i * buttonSpacing;
                        Button btn = screen.addRenderableWidget(
                            Button.builder(Component.nullToEmpty(craftBtn.getName()), button -> this.autoCraft.activateButton(craftBtn))
                                .bounds(buttonX, yPos, buttonWidth, buttonHeight)
                                .tooltip(
                                    Tooltip.create(
                                        Component.nullToEmpty(
                                            "§7"
                                                + craftBtn.getName()
                                                + "\n§7Loop: §f"
                                                + (craftBtn.loop ? "Yes" : "No")
                                                + "\n§7Drop: §f"
                                                + (craftBtn.drop ? "Yes" : "No")
                                                + "\n§7Shift-Click: §f"
                                                + (craftBtn.shiftClick ? "Yes" : "No")
                                        )
                                    )
                                )
                                .build()
                        );
                        this.craftButtons.add(btn);
                    }
                }
            }
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>)(Object)this;
        if (screen.getMenu() instanceof CraftingMenu || screen.getMenu() instanceof InventoryMenu) {
            if (this.autoCraft != null && this.autoCraft.isActive()) {
                List<AutoCraft.CraftButton> buttons = this.autoCraft.getButtons();
                AutoCraft.CraftButton activeButton = this.autoCraft.getActiveButton();
                int startY = this.topPos + this.autoCraft.getButtonY();
                int buttonX = this.leftPos + this.autoCraft.getButtonX();
                int buttonWidth = this.autoCraft.getButtonWidth();
                int buttonHeight = this.autoCraft.getButtonHeight();
                int buttonSpacing = this.autoCraft.getButtonSpacing();
                Minecraft mc = Minecraft.getInstance();

                for (int i = 0; i < this.craftButtons.size() && i < buttons.size(); i++) {
                    Button widget = this.craftButtons.get(i);
                    AutoCraft.CraftButton btn = buttons.get(i);
                    int yPos = startY + i * buttonSpacing;
                    widget.visible = true;
                    widget.setX(buttonX);
                    widget.setY(yPos);

                    try {
                        Identifier id = Identifier.parse(btn.itemId);
                        Item item = BuiltInRegistries.ITEM.getValue(id);
                        ItemStack stack = new ItemStack(item);
                        boolean isActive = activeButton != null && activeButton.itemId.equals(btn.itemId);
                        int iconX = buttonX + buttonWidth / 2 - 8;
                        int iconY = yPos + buttonHeight / 2 - 8;
                        context.renderItem(stack, iconX, iconY);
                        if (isActive && btn.loop && btn.currentCrafts > 0) {
                            String count = String.valueOf(btn.currentCrafts);
                            context.drawString(mc.font, count, iconX + 16, iconY + 9, 16777215);
                        }
                    } catch (Exception var26) {
                    }
                }
            } else {
                for (Button btn : this.craftButtons) {
                    btn.visible = false;
                }
            }
        }
    }
}
