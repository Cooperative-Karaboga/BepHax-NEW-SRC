package bep.hax.mixin;

import bep.hax.util.InventoryManager;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerboundInteractPacket.class)
public class PlayerInteractEntityC2SPacketMixin implements InventoryManager.IPlayerInteractEntityC2SPacket {
    @Shadow
    @Final
    private int entityId;
    @Unique
    private boolean bepHax$isAttackPacket = false;

    @Inject(method = "createAttackPacket", at = @At("RETURN"))
    private static void onAttack(Entity entity, boolean sneaking, CallbackInfoReturnable<ServerboundInteractPacket> cir) {
        ServerboundInteractPacket packet = cir.getReturnValue();
        if (packet != null) {
            ((PlayerInteractEntityC2SPacketMixin)(Object)packet).bepHax$isAttackPacket = true;
        }
    }

    @Override
    public boolean isAttackPacket() {
        return this.bepHax$isAttackPacket;
    }

    @Override
    public int getTargetEntityId() {
        return this.entityId;
    }
}
