package bep.hax.mixin;

import bep.hax.accessor.InputAccessor;
import bep.hax.managers.SwapManager;
import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import bep.hax.modules.BepMine;
import bep.hax.util.InventoryManager;
import bep.hax.util.NoSlowConfigHolder;
import bep.hax.util.PushOutOfBlocksEvent;
import bep.hax.util.RotationUtils;
import bep.hax.util.prox.ProxTransport;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.NoSlow;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LocalPlayer.class, priority = 1001)
public abstract class ClientPlayerEntityMixin {
    @Shadow
    public ClientInput input;
    @Unique
    private boolean bephax$wasManuallyEating = false;
    @Unique
    private int bephax$lastManualEatingSlot = -1;
    @Unique
    private int bephax$lastItemUseTime = 0;

    @Shadow
    public abstract boolean isUsingItem();

    @Shadow
    public abstract boolean isShiftKeyDown();

    @Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
    private void onPushOutOfBlocks(double x, double z, CallbackInfo ci) {
        PushOutOfBlocksEvent event = new PushOutOfBlocksEvent();
        MeteorClient.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickStart(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer)(Object)this;
        if (player != null) {
            this.bephax$checkStartEating(player);
        }
    }

    @Inject(method = "drop", at = @At("HEAD"))
    private void bephax$onDrop(boolean fullStack, CallbackInfoReturnable<Boolean> cir) {
        SwapManager.getInstance().onUserAction();
    }

    @Inject(
        method = "aiStep",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;input:Lnet/minecraft/client/player/ClientInput;", ordinal = 0, shift = Shift.AFTER)
    )
    private void bephax$multiplyInputAfterInputTick(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer)(Object)this;
        NoSlow noSlow = Modules.get().get(NoSlow.class);
        if (noSlow.isActive()) {
            if (this.bephax$isGrimV3Enabled(noSlow)) {
                if (player.isUsingItem() && this.bephax$checkGrimV3Timing()) {
                    float multiplier = this.bephax$getGrimV3Multiplier();
                    InputAccessor inputAccessor = (InputAccessor)this.input;
                    inputAccessor.setMovementForward(inputAccessor.getMovementForward() * multiplier);
                    inputAccessor.setMovementSideways(inputAccessor.getMovementSideways() * multiplier);
                }
            } else {
                if (this.bephax$shouldMultiplyInput(noSlow)) {
                    float multiplier = this.bephax$getInputMultiplier();
                    InputAccessor inputAccessor = (InputAccessor)this.input;
                    inputAccessor.setMovementForward(inputAccessor.getMovementForward() * multiplier);
                    inputAccessor.setMovementSideways(inputAccessor.getMovementSideways() * multiplier);
                }

                if (noSlow.sneaking() && this.isShiftKeyDown()) {
                    float sneakMultiplier = 3.3333333F;
                    InputAccessor inputAccessor = (InputAccessor)this.input;
                    inputAccessor.setMovementForward(inputAccessor.getMovementForward() * sneakMultiplier);
                    inputAccessor.setMovementSideways(inputAccessor.getMovementSideways() * sneakMultiplier);
                }
            }
        }
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void bephax$handleManualEatingAtTail(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer)(Object)this;
        this.bephax$handleManualEating(player);
    }

    @Unique
    private boolean bephax$shouldMultiplyInput(NoSlow noSlow) {
        LocalPlayer player = (LocalPlayer)(Object)this;
        return !player.isPassenger() && !this.isShiftKeyDown() ? this.isUsingItem() && noSlow.items() : false;
    }

    @Unique
    private boolean bephax$isGrimV3Enabled(NoSlow noSlow) {
        try {
            Field field = noSlow.getClass().getDeclaredField("bephax$grimV3Bypass");
            field.setAccessible(true);
            Object setting = field.get(noSlow);
            Method getMethod = setting.getClass().getMethod("get");
            Object value = getMethod.invoke(setting);
            return value instanceof Boolean && (Boolean)value;
        } catch (Exception e) {
            return false;
        }
    }

    @Unique
    private boolean bephax$checkGrimV3Timing() {
        LocalPlayer player = (LocalPlayer)(Object)this;
        return player == null
            ? false
            : !player.isShiftKeyDown() && !player.isPassenger() && player.getUseItemRemainingTicks() < 5 || player.getTicksUsingItem() > 1 && player.getTicksUsingItem() % 2 != 0;
    }

    @Unique
    private float bephax$getGrimV3Multiplier() {
        NoSlow noSlow = Modules.get().get(NoSlow.class);

        try {
            Field field = noSlow.getClass().getDeclaredField("bephax$grimV3Multiplier");
            field.setAccessible(true);
            Object setting = field.get(noSlow);
            Method valueMethod = setting.getClass().getMethod("get");
            Object value = valueMethod.invoke(setting);
            if (value instanceof Number) {
                return ((Number)value).floatValue();
            }
        } catch (Exception var6) {
        }

        return 5.0F;
    }

    @Unique
    private float bephax$getInputMultiplier() {
        NoSlow noSlow = Modules.get().get(NoSlow.class);

        try {
            Field field = noSlow.getClass().getDeclaredField("bephax$inputMultiplier");
            field.setAccessible(true);
            Object setting = field.get(noSlow);
            Method valueMethod = setting.getClass().getMethod("get");
            Object value = valueMethod.invoke(setting);
            if (value instanceof Number) {
                return ((Number)value).floatValue();
            }
        } catch (Exception var6) {
        }

        return 5.0F;
    }

    @Unique
    private void bephax$checkStartEating(LocalPlayer player) {
        if (player != null) {
            int currentUseTime = player.getTicksUsingItem();
            if (currentUseTime == 1 && this.bephax$lastItemUseTime == 0) {
                ItemStack activeStack = player.getUseItem();
                if (!activeStack.isEmpty() && activeStack.get(DataComponents.FOOD) != null) {
                    InventoryManager invManager = InventoryManager.getInstance();
                    int currentSlot = ((PlayerInventoryAccessor)player.getInventory()).getSelectedSlot();
                    int serverSlot = invManager.getServerSlot();
                    if (serverSlot != currentSlot) {
                        invManager.setSlotForced(currentSlot);
                    }

                    invManager.setEating(true);
                    this.bephax$wasManuallyEating = true;
                    this.bephax$lastManualEatingSlot = currentSlot;
                }
            }

            this.bephax$lastItemUseTime = currentUseTime;
        }
    }

    @Unique
    private void bephax$handleManualEating(LocalPlayer player) {
        if (player != null) {
            boolean isEatingNow = this.bephax$isManuallyEatingFood(player);
            if (!isEatingNow && this.bephax$wasManuallyEating) {
                this.bephax$wasManuallyEating = false;
                InventoryManager.getInstance().setEating(false);
                this.bephax$lastManualEatingSlot = -1;
            }
        }
    }

    @Unique
    private boolean bephax$isManuallyEatingFood(LocalPlayer player) {
        if (!player.isUsingItem()) {
            return false;
        }

        ItemStack stack = player.getUseItem();
        return stack.isEmpty() ? false : stack.get(DataComponents.FOOD) != null;
    }

    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void bephax$flushProxTransport(CallbackInfo ci) {
        ProxTransport.getInstance().flush();
    }

    @ModifyExpressionValue(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"))
    private float bephax$modifyGetYaw(float original) {
        RotationUtils rotUtils = RotationUtils.getInstance();
        return !rotUtils.isRotating() && !rotUtils.isWireFresh() ? original : rotUtils.getSentYaw();
    }

    @Inject(method = "isMovingSlowly", at = @At("HEAD"), cancellable = true)
    private void bephax$allowSprintWhileUsing(CallbackInfoReturnable<Boolean> cir) {
        if (NoSlowConfigHolder.sprintWhileUsing()) {
            cir.setReturnValue(false);
        }
    }

    @ModifyExpressionValue(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"))
    private float bephax$modifyGetPitch(float original) {
        RotationUtils rotUtils = RotationUtils.getInstance();
        return !rotUtils.isRotating() && !rotUtils.isWireFresh() ? original : rotUtils.getSentPitch();
    }

    @ModifyExpressionValue(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lengthSquared(DDD)D"))
    private double bephax$forceMiningTickPacket(double original) {
        BepMine bepMine = Modules.get().get(BepMine.class);
        return bepMine != null && bepMine.needsMiningTickPacket() ? Double.MAX_VALUE : original;
    }

    @ModifyExpressionValue(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/ClientInput;keyPresses:Lnet/minecraft/world/entity/player/Input;"))
    private Input bephax$rotateDeclaredInput(Input original) {
        return RotationUtils.getInstance().rotateDeclaredInput(original);
    }

    @WrapOperation(method = {"aiStep", "canStartSprinting", "shouldStopRunSprinting"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;hasForwardImpulse()Z"))
    private boolean bephax$blockIllegalSprint(ClientInput clientInput, Operation<Boolean> original) {
        return RotationUtils.getInstance().isSprintBlocked() ? false : original.call(clientInput);
    }
}
