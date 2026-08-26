package bep.hax.mixin.meteor;

import bep.hax.accessor.IHighwayBuilder;
import bep.hax.modules.BepMine;
import bep.hax.util.HighwayBuilderConfigHolder;
import bep.hax.util.PlacementUtils;
import bep.hax.util.RotationUtils;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.HighwayBuilder;
import meteordevelopment.meteorclient.systems.modules.world.HighwayBuilder.DoubleMineBlock;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "meteordevelopment/meteorclient/systems/modules/world/HighwayBuilder$State", remap = false)
public abstract class HighwayBuilderStateMixin {
    @Unique
    private static final int bephax$AIRPLACE_RETRY_TICKS = 10;
    @Unique
    private static final int bephax$MAX_AIRPLACE_ATTEMPTS = 3;
    @Unique
    private static final Map<BlockPos, int[]> bephax$pendingAirPlace = new LinkedHashMap<BlockPos, int[]>() {
        @Override
        protected boolean removeEldestEntry(Entry<BlockPos, int[]> eldest) {
            return this.size() > 64;
        }
    };

    @Inject(method = "doubleMine", at = @At("HEAD"), cancellable = true)
    private void bephax$doubleMineBothViaBepMine(HighwayBuilder b, ArrayDeque<BlockPos> blocks, CallbackInfo ci) {
        BepMine bepMine = Modules.get().get(BepMine.class);
        if (bepMine != null && bepMine.canDelegateMining()) {
            ci.cancel();
            if (b.normalMining == null && !blocks.isEmpty()) {
                b.normalMining = new DoubleMineBlock(b, blocks.pop()).startDestroying();
            }

            if (b.packetMining == null && !blocks.isEmpty()) {
                b.packetMining = new DoubleMineBlock(b, blocks.pop()).startDestroying().packetMine();
            }
        }
    }

    @WrapWithCondition(method = "mine", at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/utils/player/InvUtils;swap(IZ)Z"))
    private boolean bephax$skipToolSwapWhileDelegating(int slot, boolean swapBack, @Local(argsOnly = true) HighwayBuilder b) {
        BepMine bepMine = Modules.get().get(BepMine.class);
        return bepMine != null && bepMine.canDelegateMining() ? b.normalMining == null && b.packetMining == null : true;
    }

    @WrapOperation(
        method = "place",
        at = @At(
            value = "INVOKE",
            target = "Lmeteordevelopment/meteorclient/utils/world/BlockUtils;place(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/InteractionHand;IZIZZZ)Z",
            remap = true
        )
    )
    private boolean bephax$grimAirPlacePaving(
        BlockPos pos,
        InteractionHand hand,
        int slot,
        boolean rotate,
        int priority,
        boolean swing,
        boolean checkEntities,
        boolean swapBack,
        Operation<Boolean> original,
        @Local(argsOnly = true) HighwayBuilder b
    ) {
        if (MeteorClient.mc.player != null
            && MeteorClient.mc.level != null
            && HighwayBuilderConfigHolder.grimAirPlaceEnabled()
            && b instanceof IHighwayBuilder ihb
            && ihb.bephax$isPavingPlace()) {
            BlockPos placePos = pos.immutable();
            if (!MeteorClient.mc.level.getBlockState(placePos).canBeReplaced()) {
                bephax$pendingAirPlace.remove(placePos);
                return false;
            }

            ihb.bephax$stallPlacement(1);
            int now = MeteorClient.mc.player.tickCount;
            int[] entry = bephax$pendingAirPlace.get(placePos);
            int attempts = entry == null ? 0 : entry[1];
            if (attempts >= 3) {
                return original.call(pos, hand, slot, rotate, priority, swing, checkEntities, swapBack);
            }

            if (entry != null && now - entry[0] < 10) {
                return false;
            }

            BlockHitResult hit = PlacementUtils.getAirPlaceHit(placePos, MeteorClient.mc.player.blockInteractionRange() + 1.0);
            if (hit == null) {
                return original.call(pos, hand, slot, rotate, priority, swing, checkEntities, swapBack);
            }

            if (rotate) {
                float[] rotations = RotationUtils.getRotationsTo(MeteorClient.mc.player.getEyePosition(), hit.getLocation());
                RotationUtils.getInstance().setRotationSilent(rotations[0], rotations[1]);
                if (!RotationUtils.getInstance().isAligned()) {
                    return false;
                }
            }

            if (slot >= 0 && slot <= 8 && InvUtils.swap(slot, false)) {
                MeteorClient.mc.player.connection.send(new ServerboundPlayerActionPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
                MeteorClient.mc
                    .player
                    .connection
                    .send(new ServerboundUseItemOnPacket(InteractionHand.OFF_HAND, hit, MeteorClient.mc.player.containerMenu.getStateId() + 2));
                MeteorClient.mc.player.connection.send(new ServerboundPlayerActionPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
                MeteorClient.mc.player.swing(InteractionHand.MAIN_HAND);
                bephax$pendingAirPlace.put(placePos, new int[]{now, attempts + 1});
                return false;
            } else {
                return false;
            }
        } else {
            return original.call(pos, hand, slot, rotate, priority, swing, checkEntities, swapBack);
        }
    }
}
