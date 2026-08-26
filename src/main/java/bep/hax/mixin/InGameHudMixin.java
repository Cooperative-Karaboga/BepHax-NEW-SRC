package bep.hax.mixin;

import bep.hax.modules.NoHurtCam;
import bep.hax.modules.ShulkerOverviewModule;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class InGameHudMixin {
    @Shadow
    private ItemStack lastToolHighlight;

    @Inject(method = "renderItemHotbar", at = @At("TAIL"))
    private void onRenderHotbar(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        ShulkerOverviewModule module = Modules.get().get(ShulkerOverviewModule.class);
        if (module != null && module.isActive()) {
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            if (player != null) {
                int scaledWidth = mc.getWindow().getGuiScaledWidth();
                int scaledHeight = mc.getWindow().getGuiScaledHeight();
                int center = scaledWidth / 2;
                int hotbarY = scaledHeight - 19;

                for (int i = 0; i < 9; i++) {
                    int posX = center - 90 + i * 20 + 2;
                    ItemStack stack = player.getInventory().getItem(i);
                    if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock) {
                        module.renderShulkerOverlay(context, posX, hotbarY, stack);
                    }
                }

                ItemStack offhandStack = player.getOffhandItem();
                if (!offhandStack.isEmpty() && offhandStack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock) {
                    int offY = scaledHeight - 23;
                    int offX;
                    if (player.getMainArm() == HumanoidArm.LEFT) {
                        offX = center + 91 + 9;
                    } else {
                        offX = center - 91 - 29;
                    }

                    module.renderShulkerOverlay(context, offX + 3, offY + 3, offhandStack);
                }
            }
        }
    }

    @Inject(method = "renderTextureOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderOverlay(GuiGraphics context, Identifier texture, float opacity, CallbackInfo ci) {
        Modules modules = Modules.get();
        if (modules != null) {
            NoHurtCam noHurtCam = modules.get(NoHurtCam.class);
            if (noHurtCam != null && noHurtCam.shouldDisableRedOverlay()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && mc.player.hurtTime > 0) {
                    ci.cancel();
                }
            }
        }
    }
}
