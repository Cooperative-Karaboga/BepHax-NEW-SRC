package bep.hax.mixin.meteor;

import bep.hax.modules.BepMine;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.HighwayBuilder;
import meteordevelopment.meteorclient.systems.modules.world.HighwayBuilder.DoubleMineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DoubleMineBlock.class, remap = false)
public abstract class HighwayBuilderDoubleMineBlockMixin {
    @Shadow
    @Final
    public BlockPos blockPos;
    @Shadow
    @Final
    private Direction direction;
    @Shadow
    @Final
    private HighwayBuilder b;
    @Shadow
    @Final
    private Block block;
    @Unique
    private static boolean bephax$warnedNoBepMine = false;
    @Unique
    private static final int bephax$RETRY_BUFFER_TICKS = 6;
    @Unique
    private int bephax$retryTimer = -1;

    @Inject(method = "startDestroying", at = @At("HEAD"), cancellable = true)
    private void bephax$delegateBreakToBepMine(CallbackInfoReturnable<DoubleMineBlock> cir) {
        BepMine bepMine = Modules.get().get(BepMine.class);
        if (bepMine != null && bepMine.canDelegateMining()) {
            bepMine.mineBlock(this.blockPos, this.direction);
            cir.setReturnValue((DoubleMineBlock)(Object)this);
        } else if (!bephax$warnedNoBepMine) {
            bephax$warnedNoBepMine = true;
            this.b
                .warning(
                    "Double Mine delegates breaking to BepMine - enable BepMine in Packet mode for this to work on 2b2t. Falling back to Meteor's breaker for now."
                );
        }
    }

    @Inject(method = "stopDestroying", at = @At("HEAD"), cancellable = true)
    private void bephax$skipMeteorStop(CallbackInfoReturnable<DoubleMineBlock> cir) {
        BepMine bepMine = Modules.get().get(BepMine.class);
        if (bepMine != null && bepMine.canDelegateMining()) {
            cir.setReturnValue((DoubleMineBlock)(Object)this);
        }
    }

    @ModifyExpressionValue(method = "shouldRemove", at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/utils/Utils;distance(DDDDDD)D"))
    private double bephax$dropReachRestraint(double original) {
        return 0.0;
    }

    @Inject(method = "shouldRemove", at = @At("HEAD"), cancellable = true)
    private void bephax$dontTimeoutWhileBepMineBreaks(CallbackInfoReturnable<Boolean> cir) {
        BepMine bepMine = Modules.get().get(BepMine.class);
        if (bepMine != null && bepMine.canDelegateMining() && MeteorClient.mc.level != null) {
            BlockState state = MeteorClient.mc.level.getBlockState(this.blockPos);
            if (state.isAir() || state.getBlock() != this.block) {
                this.bephax$retryTimer = -1;
            } else if (bepMine.isMiningBlock(this.blockPos)) {
                this.bephax$retryTimer = -1;
            } else if (this.bephax$retryTimer < 0) {
                this.bephax$retryTimer = 6;
            } else if (this.bephax$retryTimer == 0) {
                bepMine.mineBlock(this.blockPos, this.direction);
                this.bephax$retryTimer = -1;
            } else {
                this.bephax$retryTimer--;
            }

            cir.setReturnValue(false);
        }
    }
}
