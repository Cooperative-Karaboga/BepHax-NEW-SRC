package bep.hax.mixin;

import bep.hax.modules.GhostContainer;
import bep.hax.modules.ItemSearchBar;
import bep.hax.modules.ShulkerOverviewModule;
import bep.hax.util.InventoryTweaksConfigHolder;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenMixin extends Screen {
    @Shadow
    protected int leftPos;
    @Shadow
    protected int topPos;
    @Shadow
    protected int imageWidth;
    @Shadow
    protected int imageHeight;
    @Shadow
    protected Slot hoveredSlot;
    @Unique
    private EditBox itemSearchField;
    @Unique
    private ItemSearchBar itemSearchModule;
    @Unique
    private Button bephax$ghostButton;
    @Unique
    private boolean bephax$fadeInit;
    @Unique
    private boolean bephax$renderMainPushed;
    @Unique
    private float bephax$hoverAnim;
    @Unique
    private int bephax$hoverLastSlot = -1;
    @Unique
    private long bephax$hoverNanos;

    protected HandledScreenMixin(Component title) {
        super(title);
    }

    @Shadow
    public abstract AbstractContainerMenu getMenu();

    @Inject(method = "init()V", at = @At("TAIL"))
    private void bephax$addGhostButton(CallbackInfo ci) {
        GhostContainer ghost = Modules.get().get(GhostContainer.class);
        if (ghost != null && ghost.isActive() && ghost.canGhost()) {
            int bx = this.leftPos + ghost.offsetX.get();
            int by = this.topPos + ghost.offsetY.get();
            this.bephax$ghostButton = Button.builder(Component.nullToEmpty(ghost.buttonText.get()), b -> {
                GhostContainer g = Modules.get().get(GhostContainer.class);
                if (g != null && g.isActive()) {
                    g.ghostClose();
                }
            }).bounds(bx, by, ghost.buttonWidth.get(), 20).build();
            this.addRenderableWidget(this.bephax$ghostButton);
        }
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        this.itemSearchModule = Modules.get().get(ItemSearchBar.class);
        if (this.itemSearchModule != null && this.itemSearchModule.isActive() && this.itemSearchModule.shouldShowSearchField()) {
            this.itemSearchField = new EditBox(
                Minecraft.getInstance().font,
                this.leftPos + this.itemSearchModule.getOffsetX(),
                this.topPos + this.itemSearchModule.getOffsetY(),
                this.itemSearchModule.getFieldWidth(),
                this.itemSearchModule.getFieldHeight(),
                Component.nullToEmpty("Search items...")
            );
            this.itemSearchField.setHint(Component.nullToEmpty("Search items..."));
            this.itemSearchField.setMaxLength(100);
            String currentQuery = this.itemSearchModule.searchQuery.get();
            if (currentQuery != null && !currentQuery.isEmpty()) {
                this.itemSearchField.setValue(currentQuery);
            }

            this.itemSearchField.setResponder(text -> {
                if (this.itemSearchModule != null) {
                    this.itemSearchModule.updateSearchQuery(text);
                }
            });
            this.itemSearchField.setFocused(false);
            this.itemSearchField.setEditable(true);
            this.itemSearchField.setVisible(true);
            this.addRenderableWidget(this.itemSearchField);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.itemSearchModule != null && this.itemSearchModule.isActive() && this.itemSearchModule.shouldShowSearchField()) {
            if (this.itemSearchField != null) {
                this.itemSearchField.setX(this.leftPos + this.itemSearchModule.getOffsetX());
                this.itemSearchField.setY(this.topPos + this.itemSearchModule.getOffsetY());
                this.itemSearchField.setVisible(true);
            }
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        if (this.itemSearchModule != null && this.itemSearchModule.isActive() && this.itemSearchModule.shouldShowSearchField()) {
            if (this.itemSearchField != null) {
                int keyCode = input.key();
                if (keyCode == 258) {
                    this.setFocused(this.itemSearchField);
                    this.itemSearchField.setFocused(true);
                    cir.setReturnValue(true);
                } else {
                    if (this.itemSearchField.isFocused()) {
                        if (keyCode == 256) {
                            this.setFocused(null);
                            this.itemSearchField.setFocused(false);
                            cir.setReturnValue(true);
                            return;
                        }

                        this.itemSearchField.keyPressed(input);
                        cir.setReturnValue(true);
                    }
                }
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent click, boolean pressed, CallbackInfoReturnable<Boolean> cir) {
        if (this.itemSearchModule != null && this.itemSearchModule.isActive() && this.itemSearchModule.shouldShowSearchField()) {
            if (this.itemSearchField != null) {
                double mouseX = click.x();
                double mouseY = click.y();
                boolean clickedOnField = mouseX >= this.itemSearchField.getX()
                    && mouseX < this.itemSearchField.getX() + this.itemSearchField.getWidth()
                    && mouseY >= this.itemSearchField.getY()
                    && mouseY < this.itemSearchField.getY() + this.itemSearchField.getHeight();
                if (clickedOnField) {
                    this.setFocused(this.itemSearchField);
                    this.itemSearchField.setFocused(true);
                    if (this.itemSearchField.mouseClicked(click, pressed)) {
                        cir.setReturnValue(true);
                        return;
                    }
                } else {
                    if (this.getFocused() == this.itemSearchField) {
                        this.setFocused(null);
                    }

                    this.itemSearchField.setFocused(false);
                }
            }
        }
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        return this.itemSearchModule != null
                && this.itemSearchModule.isActive()
                && this.itemSearchModule.shouldShowSearchField()
                && this.itemSearchField != null
                && this.itemSearchField.isFocused()
                && this.itemSearchField.charTyped(input)
            ? true
            : super.charTyped(input);
    }

    @Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
    private void onDrawSlotHead(GuiGraphics context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (InventoryTweaksConfigHolder.hoverEffectEnabled() && this.hoveredSlot == slot && slot.hasItem()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSlot", at = @At("TAIL"))
    private void onDrawSlotTail(GuiGraphics context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        ShulkerOverviewModule shulkerModule = Modules.get().get(ShulkerOverviewModule.class);
        if (shulkerModule != null && shulkerModule.isActive()) {
            shulkerModule.renderShulkerOverlay(context, slot.x, slot.y, slot.getItem());
        }
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void bephax$initFade(CallbackInfo ci) {
        if (!this.bephax$fadeInit) {
            this.bephax$fadeInit = true;
            if (InventoryTweaksConfigHolder.isFadeActive()) {
                InventoryTweaksConfigHolder.resetFade();
            }
        }
    }

    @Inject(method = "renderContents", at = @At("HEAD"))
    private void bephax$fadeScaleBegin(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        this.bephax$renderMainPushed = false;
        if (InventoryTweaksConfigHolder.isFadeActive()) {
            InventoryTweaksConfigHolder.updateFade(true);
            float a = InventoryTweaksConfigHolder.getFadeAlpha();
            if (!(a >= 0.999F)) {
                float eased = 1.0F - (1.0F - a) * (1.0F - a) * (1.0F - a);
                float scale = 0.6F + 0.4F * eased;
                float cx = this.leftPos + this.imageWidth / 2.0F;
                float cy = this.topPos + this.imageHeight / 2.0F;
                Matrix3x2fStack matrices = context.pose();
                matrices.pushMatrix();
                matrices.translate(cx, cy);
                matrices.scale(scale, scale);
                matrices.translate(-cx, -cy);
                this.bephax$renderMainPushed = true;
            }
        }
    }

    @Inject(method = "renderContents", at = @At("TAIL"))
    private void bephax$afterContainer(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.bephax$renderMainPushed) {
            context.pose().popMatrix();
            this.bephax$renderMainPushed = false;
        }

        this.bephax$drawHoverEffect(context);
    }

    @Unique
    private void bephax$drawHoverEffect(GuiGraphics context) {
        if (InventoryTweaksConfigHolder.hoverEffectEnabled() && this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            int id = this.hoveredSlot.index;
            long now = System.nanoTime();
            float dt = this.bephax$hoverNanos == 0L ? 0.0F : (float)(now - this.bephax$hoverNanos) / 1.0E9F;
            this.bephax$hoverNanos = now;
            dt = Math.max(0.0F, Math.min(0.1F, dt));
            if (id != this.bephax$hoverLastSlot) {
                this.bephax$hoverAnim = 0.0F;
                this.bephax$hoverLastSlot = id;
            }

            this.bephax$hoverAnim = Math.min(1.0F, this.bephax$hoverAnim + dt / 0.12F);
            float t = 1.0F - (1.0F - this.bephax$hoverAnim) * (1.0F - this.bephax$hoverAnim);
            float s = 1.0F + t * (InventoryTweaksConfigHolder.hoverEffectScale() - 1.0F);
            int sx = this.leftPos + this.hoveredSlot.x;
            int sy = this.topPos + this.hoveredSlot.y;
            ItemStack stack = this.hoveredSlot.getItem();
            Matrix3x2fStack m = context.pose();
            m.pushMatrix();
            m.translate(sx + 8.0F, sy + 8.0F);
            m.scale(s, s);
            m.translate(-8.0F, -8.0F);
            context.renderItem(stack, 0, 0);
            context.renderItemDecorations(Minecraft.getInstance().font, stack, 0, 0);
            ShulkerOverviewModule shulkerModule = Modules.get().get(ShulkerOverviewModule.class);
            if (shulkerModule != null && shulkerModule.isActive()) {
                shulkerModule.renderShulkerOverlay(context, 0, 0, stack);
            }

            m.popMatrix();
        } else {
            this.bephax$hoverLastSlot = -1;
            this.bephax$hoverAnim = 0.0F;
        }
    }
}
