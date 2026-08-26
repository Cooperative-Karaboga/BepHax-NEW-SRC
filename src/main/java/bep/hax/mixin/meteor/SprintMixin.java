package bep.hax.mixin.meteor;

import bep.hax.managers.SwapManager;
import bep.hax.util.InventoryManager;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.movement.Sprint;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Sprint.class, remap = false)
public abstract class SprintMixin extends Module {
    @Shadow
    @Final
    private Setting<Boolean> keepSprint;
    @Unique
    private Setting<Boolean> bephax$cooldownReset;
    @Unique
    private Setting<Boolean> bephax$slowWhenBlocked;
    @Unique
    private boolean bephax$bypassed;

    public SprintMixin(Category category, String name, String description) {
        super(category, name, description);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void bephax$init(CallbackInfo ci) {
        SettingGroup sg = this.settings.createGroup("Grim");
        this.bephax$cooldownReset = sg.add(
            new Builder()
                .name("grim-cooldown-reset")
                .description(
                    "Bounces the server-held hotbar slot through a different item and straight back before each attack packet, resetting Grim's attack-cooldown view so it stops requiring the post-hit slowdown that keep-sprint skips. Only fires while sprinting, and only when the two extra held-item packets are safe to send."
                )
                .defaultValue(true)
                .visible(this.keepSprint::get)
                .build()
        );
        this.bephax$slowWhenBlocked = sg.add(
            new Builder()
                .name("slow-when-blocked")
                .description(
                    "When the bounce cannot be sent (mid item use, dirty packet burst, after the tick's movement packet), take the vanilla sprint reset for that hit instead of desyncing from Grim's prediction. Also holds back unsprint-on-hit's stop-sprint packet on those hits, since Grim reads the sprint state from the last movement packet and would demand the slowdown anyway."
                )
                .defaultValue(true)
                .visible(() -> this.keepSprint.get() && this.bephax$cooldownReset.get())
                .build()
        );
    }

    @Unique
    @EventHandler(priority = 200)
    private void bephax$onPacketSend(Send event) {
        if (!event.isCancelled() && event.packet instanceof ServerboundInteractPacket packet) {
            InventoryManager.IPlayerInteractEntityC2SPacket accessor = (InventoryManager.IPlayerInteractEntityC2SPacket)packet;
            if (accessor.isAttackPacket()) {
                this.bephax$bypassed = this.bephax$resetCooldownView(accessor.getTargetEntityId());
            }
        }
    }

    @Inject(method = "onPacketSend", at = @At("HEAD"), cancellable = true)
    private void bephax$blockUnsprintOnHit(Send event, CallbackInfo ci) {
        if (event.packet instanceof ServerboundInteractPacket packet && ((InventoryManager.IPlayerInteractEntityC2SPacket)packet).isAttackPacket()) {
            if (this.bephax$shouldTakeVanillaSlow()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "stopSprinting", at = @At("HEAD"), cancellable = true)
    private void bephax$stopSprinting(CallbackInfoReturnable<Boolean> cir) {
        if (this.bephax$shouldTakeVanillaSlow()) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private boolean bephax$shouldTakeVanillaSlow() {
        return !this.bephax$bypassed && this.bephax$cooldownReset.get() && this.bephax$slowWhenBlocked.get() && this.keepSprint.get();
    }

    @Unique
    private boolean bephax$resetCooldownView(int entityId) {
        if (this.mc.player == null || this.mc.level == null || this.mc.getConnection() == null) {
            return false;
        }

        if (!this.keepSprint.get() || !this.bephax$cooldownReset.get()) {
            return false;
        }

        if (!this.mc.player.isSprinting()) {
            return false;
        }

        Entity target = this.mc.level.getEntity(entityId);
        if (target != null && (!(target instanceof LivingEntity) || target instanceof Player)) {
            SwapManager swap = SwapManager.getInstance();
            int held = swap.getServerSlot();
            if (held >= 0 && held <= 8) {
                int bounce = this.bephax$bounceSlot(held, swap.referenceSlot());
                return bounce != -1 && swap.bounce(bounce);
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    @Unique
    private int bephax$bounceSlot(int held, int preferred) {
        ItemStack stack = this.mc.player.getInventory().getItem(held);
        int last = InventoryManager.getInstance().getLastSentSlot();
        if (preferred != held && preferred != last && this.bephax$differs(preferred, stack)) {
            return preferred;
        }

        int fallback = -1;

        for (int i = 0; i < 9; i++) {
            if (i != held && this.bephax$differs(i, stack)) {
                if (i != last) {
                    return i;
                }

                fallback = i;
            }
        }

        return fallback;
    }

    @Unique
    private boolean bephax$differs(int slot, ItemStack stack) {
        return slot >= 0 && slot <= 8 ? !ItemStack.isSameItem(this.mc.player.getInventory().getItem(slot), stack) : false;
    }
}
