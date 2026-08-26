package bep.hax.mixin.meteor;

import bep.hax.modules.ItemSearchBar;
import bep.hax.util.PeekNavigationConfig;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.PeekScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PeekScreen.class, remap = false)
public abstract class PeekScreenMixin extends ShulkerBoxScreen {
    @Unique
    @Nullable
    private Screen bephax$parentScreen = null;
    @Unique
    private EditBox bephax$searchField;
    @Unique
    private ItemSearchBar bephax$searchModule;

    public PeekScreenMixin(ShulkerBoxMenu handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(ItemStack storageBlock, ItemStack[] contents, CallbackInfo ci) {
        this.bephax$searchModule = Modules.get().get(ItemSearchBar.class);
        this.bephax$parentScreen = MeteorClient.mc.screen;
    }

    @Override
    protected void init() {
        super.init();
        if (this.bephax$searchModule != null && this.bephax$searchModule.isActive() && this.bephax$searchModule.shouldShowSearchField()) {
            this.bephax$searchField = new EditBox(
                Minecraft.getInstance().font,
                this.leftPos + this.bephax$searchModule.getOffsetX(),
                this.topPos + this.bephax$searchModule.getOffsetY(),
                this.bephax$searchModule.getFieldWidth(),
                this.bephax$searchModule.getFieldHeight(),
                Component.nullToEmpty("Search items...")
            );
            this.bephax$searchField.setHint(Component.nullToEmpty("Search items..."));
            this.bephax$searchField.setMaxLength(100);
            String currentQuery = this.bephax$searchModule.searchQuery.get();
            if (currentQuery != null && !currentQuery.isEmpty()) {
                this.bephax$searchField.setValue(currentQuery);
            }

            this.bephax$searchField.setResponder(text -> {
                if (this.bephax$searchModule != null) {
                    this.bephax$searchModule.updateSearchQuery(text);
                }
            });
            this.bephax$searchField.setFocused(false);
            this.bephax$searchField.setEditable(true);
            this.bephax$searchField.setVisible(true);
            this.addRenderableWidget(this.bephax$searchField);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = true)
    private void onMouseClicked(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (this.bephax$searchModule != null && this.bephax$searchModule.isActive() && this.bephax$searchModule.shouldShowSearchField()) {
            if (this.bephax$searchField != null) {
                double mouseX = click.x();
                double mouseY = click.y();
                boolean clickedOnField = mouseX >= this.bephax$searchField.getX()
                    && mouseX < this.bephax$searchField.getX() + this.bephax$searchField.getWidth()
                    && mouseY >= this.bephax$searchField.getY()
                    && mouseY < this.bephax$searchField.getY() + this.bephax$searchField.getHeight();
                if (clickedOnField) {
                    this.bephax$searchField.setFocused(true);
                    if (this.bephax$searchField.mouseClicked(click, doubled)) {
                        cir.setReturnValue(true);
                        return;
                    }
                } else {
                    this.bephax$searchField.setFocused(false);
                }
            }
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, remap = true)
    private void onKeyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        int keyCode = input.key();
        if (this.bephax$searchModule != null
            && this.bephax$searchModule.isActive()
            && this.bephax$searchModule.shouldShowSearchField()
            && this.bephax$searchField != null) {
            if (keyCode == 258) {
                this.bephax$searchField.setFocused(true);
                cir.setReturnValue(true);
                return;
            }

            if (keyCode == 256 && this.bephax$searchField.isFocused()) {
                this.bephax$searchField.setFocused(false);
                cir.setReturnValue(true);
                return;
            }

            if (this.bephax$searchField.isFocused()) {
                this.bephax$searchField.keyPressed(input);
                if (keyCode != 256) {
                    cir.setReturnValue(true);
                }

                return;
            }
        }
    }

    @Override
    public void onClose() {
        if (PeekNavigationConfig.isEnabled()) {
            Screen parent = PeekNavigationConfig.popScreen();
            if (parent != null) {
                PeekNavigationConfig.setNavigatingBack(true);
                MeteorClient.mc.setScreen(parent);
                PeekNavigationConfig.setNavigatingBack(false);
                return;
            }
        }

        PeekNavigationConfig.clearStack();
        super.onClose();
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        return this.bephax$searchModule != null
                && this.bephax$searchModule.isActive()
                && this.bephax$searchModule.shouldShowSearchField()
                && this.bephax$searchField != null
                && this.bephax$searchField.isFocused()
                && this.bephax$searchField.charTyped(input)
            ? true
            : super.charTyped(input);
    }

    @Override
    protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
        super.renderBg(context, delta, mouseX, mouseY);
        if (this.bephax$searchModule != null && this.bephax$searchModule.isActive() && this.bephax$searchModule.shouldShowSearchField()) {
            if (this.bephax$searchField != null) {
                this.bephax$searchField.setX(this.leftPos + this.bephax$searchModule.getOffsetX());
                this.bephax$searchField.setY(this.topPos + this.bephax$searchModule.getOffsetY());
            }
        }
    }
}
