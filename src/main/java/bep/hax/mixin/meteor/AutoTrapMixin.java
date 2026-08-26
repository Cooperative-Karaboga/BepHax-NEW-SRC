package bep.hax.mixin.meteor;

import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import bep.hax.util.InventoryManager;
import bep.hax.util.RotationUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.combat.AutoTrap;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AutoTrap.class, remap = false)
public abstract class AutoTrapMixin extends Module {
    @Shadow
    @Final
    private SettingGroup sgGeneral;
    @Shadow
    @Final
    private Setting<Boolean> rotate;
    @Shadow
    private List<BlockPos> placePositions;
    @Shadow
    private Player target;
    @Unique
    private SettingGroup bephax$sgTrapMode;
    @Unique
    private Setting<Boolean> bephax$useExpandPattern;
    @Unique
    private Setting<Boolean> bephax$feetOnly;
    @Unique
    private Setting<Integer> bephax$blocksPerTick;
    @Unique
    private SettingGroup bephax$sgGrimPlace;
    @Unique
    private Setting<Boolean> bephax$grimPlace;
    @Unique
    private Setting<Boolean> bephax$grimRotate;
    @Unique
    private Setting<Boolean> bephax$yawStep;
    @Unique
    private Setting<Integer> bephax$yawStepLimit;
    @Unique
    private RotationUtils bephax$rotationManager;
    @Unique
    private InventoryManager bephax$inventoryManager;
    @Unique
    private Vec3 bephax$targetRotation = null;
    @Unique
    private boolean bephax$rotated = true;
    @Unique
    private int bephax$blocksPlacedThisTick = 0;
    @Unique
    private final Set<BlockPos> bephax$placedPositions = new HashSet<>();

    public AutoTrapMixin(Category category, String name, String description) {
        super(category, name, description);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        this.bephax$rotationManager = RotationUtils.getInstance();
        this.bephax$inventoryManager = InventoryManager.getInstance();
        this.bephax$sgTrapMode = this.settings.createGroup("Trap Mode");
        this.bephax$useExpandPattern = this.bephax$sgTrapMode
            .add(
                new Builder()
                    .name("use-expand-pattern")
                    .description("Use custom expand pattern (handles boundaries correctly). Disable to use Meteor's original patterns.")
                    .defaultValue(false)
                    .build()
            );
        this.bephax$feetOnly = this.bephax$sgTrapMode
            .add(
                new Builder()
                    .name("feet-only")
                    .description("Only traps the feet level (no roof or head blocks)")
                    .defaultValue(false)
                    .visible(this.bephax$useExpandPattern::get)
                    .build()
            );
        this.bephax$blocksPerTick = this.bephax$sgTrapMode
            .add(
                new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                    .name("blocks-per-tick")
                    .description("Maximum blocks to place per tick")
                    .defaultValue(3)
                    .min(1)
                    .sliderRange(1, 20)
                    .build()
            );
        this.bephax$sgGrimPlace = this.settings.createGroup("Grim Place");
        this.bephax$grimPlace = this.bephax$sgGrimPlace
            .add(
                new Builder()
                    .name("grim-place")
                    .description("Uses GrimAirPlace exploit for block placement (bypass 2b2t anti-cheat)")
                    .defaultValue(true)
                    .build()
            );
        this.bephax$grimRotate = this.bephax$sgGrimPlace
            .add(
                new Builder()
                    .name("grim-rotate")
                    .description("Rotation system with yaw stepping")
                    .defaultValue(false)
                    .visible(this.bephax$grimPlace::get)
                    .build()
            );
        this.bephax$yawStep = this.bephax$sgGrimPlace
            .add(
                new Builder()
                    .name("yaw-step")
                    .description("Rotates over multiple ticks (45-90° for GrimAC)")
                    .defaultValue(false)
                    .visible(() -> this.bephax$grimPlace.get() && this.bephax$grimRotate.get())
                    .build()
            );
        this.bephax$yawStepLimit = this.bephax$sgGrimPlace
            .add(
                new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                    .name("yaw-step-limit")
                    .description("Max yaw rotation per tick")
                    .defaultValue(134)
                    .min(1)
                    .max(180)
                    .sliderRange(1, 180)
                    .visible(() -> this.bephax$grimPlace.get() && this.bephax$grimRotate.get() && this.bephax$yawStep.get())
                    .build()
            );
    }

    @Inject(method = "onActivate", at = @At("TAIL"))
    private void onActivateInject(CallbackInfo ci) {
        this.bephax$targetRotation = null;
        this.bephax$rotated = true;
        this.bephax$placedPositions.clear();
    }

    @Inject(method = "onDeactivate", at = @At("TAIL"))
    private void onDeactivateInject(CallbackInfo ci) {
        this.bephax$targetRotation = null;
        this.bephax$rotated = true;
        this.bephax$placedPositions.clear();
        if (this.bephax$inventoryManager != null) {
            this.bephax$inventoryManager.syncToClient();
        }
    }

    @Unique
    @EventHandler(priority = 200)
    private void onPreTickHighPriority(Pre event) {
        this.bephax$blocksPlacedThisTick = 0;
        if (this.isActive() && this.mc.player != null) {
            if (this.bephax$grimRotate.get()) {
                if (this.rotate.get()) {
                    this.rotate.set(false);
                }

                if (this.bephax$targetRotation != null && !this.bephax$rotated && this.bephax$yawStep.get()) {
                    this.bephax$continueYawStep();
                }
            }
        } else {
            this.bephax$targetRotation = null;
            this.bephax$rotated = true;
        }
    }

    @Inject(method = "fillPlaceArray", at = @At("HEAD"), cancellable = true)
    private void replaceFillPlaceArray(Player targetPlayer, CallbackInfo ci) {
        if (this.bephax$useExpandPattern.get()) {
            ci.cancel();
            this.placePositions.clear();
            if (targetPlayer != null) {
                this.bephax$improveTrapping(targetPlayer);
            }
        }
    }

    @Unique
    private void bephax$improveTrapping(Player target) {
        Set<BlockPos> newPositions = new HashSet<>();
        AABB box = target.getBoundingBox();
        int minX = (int)Math.floor(box.minX);
        int maxX = (int)Math.floor(box.maxX - 1.0E-4);
        int minZ = (int)Math.floor(box.minZ);
        int maxZ = (int)Math.floor(box.maxZ - 1.0E-4);
        int footY = target.getBlockY();
        Set<BlockPos> footBlocks = new HashSet<>();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                footBlocks.add(new BlockPos(x, footY, z));
            }
        }

        for (BlockPos foot : footBlocks) {
            BlockPos floor = foot.below();
            if (BlockUtils.canPlace(floor)) {
                newPositions.add(floor);
            }
        }

        for (BlockPos foot : footBlocks) {
            newPositions.add(foot.north());
            newPositions.add(foot.south());
            newPositions.add(foot.east());
            newPositions.add(foot.west());
        }

        newPositions.removeAll(footBlocks);
        if (!this.bephax$feetOnly.get()) {
            for (BlockPos foot : footBlocks) {
                BlockPos up = foot.above();
                newPositions.add(up.north());
                newPositions.add(up.south());
                newPositions.add(up.east());
                newPositions.add(up.west());
            }

            for (BlockPos foot : footBlocks) {
                newPositions.remove(foot.above());
            }

            for (BlockPos foot : footBlocks) {
                newPositions.add(foot.above(2));
            }
        }

        newPositions.removeIf(pos -> !BlockUtils.canPlace(pos));
        this.placePositions.clear();
        this.placePositions.addAll(newPositions);
    }

    @Unique
    @EventHandler
    private void onSendPacket(Send event) {
        if (this.isActive() && Utils.canUpdate() && this.mc.player != null) {
            if (event.packet instanceof ServerboundUseItemOnPacket packet) {
                InteractionHand hand = packet.getHand();
                if (hand == null) {
                    return;
                }

                if (this.mc.player.getItemInHand(hand).getItem() instanceof BlockItem) {
                    BlockPos targetPos = packet.getHitResult().getBlockPos();
                    if (this.bephax$placedPositions.contains(targetPos)) {
                        event.cancel();
                        return;
                    }

                    if (this.bephax$blocksPlacedThisTick >= this.bephax$blocksPerTick.get()) {
                        event.cancel();
                        return;
                    }

                    if (this.bephax$grimPlace.get()) {
                        event.cancel();
                        this.bephax$placeGrimBlock(packet);
                        this.bephax$blocksPlacedThisTick++;
                        this.bephax$placedPositions.add(targetPos);
                    } else {
                        this.bephax$blocksPlacedThisTick++;
                        this.bephax$placedPositions.add(targetPos);
                    }
                }
            }
        }
    }

    @Unique
    private void bephax$placeGrimBlock(ServerboundUseItemOnPacket packet) {
        BlockHitResult hitResult = packet.getHitResult();
        int currentSlot = ((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot();
        this.bephax$inventoryManager.setSlot(currentSlot);
        if (this.bephax$grimRotate.get()) {
            Vec3 blockPos = Vec3.atCenterOf(hitResult.getBlockPos());
            this.bephax$applyRotation(blockPos);
        }

        this.mc.player.connection.send(new ServerboundPlayerActionPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
        this.mc.player.connection.send(new ServerboundUseItemOnPacket(InteractionHand.OFF_HAND, hitResult, this.mc.player.containerMenu.getStateId() + 2));
        this.mc.player.connection.send(new ServerboundPlayerActionPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
        this.mc.player.swing(InteractionHand.MAIN_HAND);
    }

    @Unique
    private void bephax$applyRotation(Vec3 target) {
        if (this.mc.player != null) {
            this.bephax$targetRotation = target;
            float[] rotation = RotationUtils.getRotationsTo(this.mc.player.getEyePosition(), target);
            if (this.bephax$yawStep.get()) {
                float serverYaw = this.bephax$rotationManager.getWrappedYaw();
                float targetYaw = rotation[0];
                float diff = serverYaw - targetYaw;

                while (diff > 180.0F) {
                    diff -= 360.0F;
                }

                while (diff < -180.0F) {
                    diff += 360.0F;
                }

                float diff1 = Math.abs(diff);
                int stepLimit = this.bephax$yawStepLimit.get();
                if (diff1 > stepLimit) {
                    float deltaYaw = diff > 0.0F ? -stepLimit : stepLimit;
                    float yaw = serverYaw + deltaYaw;
                    this.bephax$rotationManager.setRotationSilent(yaw, rotation[1]);
                    this.bephax$rotated = false;
                } else {
                    this.bephax$rotationManager.setRotationSilent(targetYaw, rotation[1]);
                    this.bephax$rotated = true;
                    this.bephax$targetRotation = null;
                }
            } else {
                this.bephax$rotationManager.setRotationSilent(rotation[0], rotation[1]);
                this.bephax$rotated = true;
                this.bephax$targetRotation = null;
            }
        }
    }

    @Unique
    private void bephax$continueYawStep() {
        if (this.mc.player != null && this.bephax$targetRotation != null) {
            float[] rotation = RotationUtils.getRotationsTo(this.mc.player.getEyePosition(), this.bephax$targetRotation);
            float serverYaw = this.bephax$rotationManager.getWrappedYaw();
            float targetYaw = rotation[0];
            float diff = serverYaw - targetYaw;

            while (diff > 180.0F) {
                diff -= 360.0F;
            }

            while (diff < -180.0F) {
                diff += 360.0F;
            }

            float diff1 = Math.abs(diff);
            int stepLimit = this.bephax$yawStepLimit.get();
            if (diff1 > stepLimit) {
                float deltaYaw = diff > 0.0F ? -stepLimit : stepLimit;
                float yaw = serverYaw + deltaYaw;
                this.bephax$rotationManager.setRotationSilent(yaw, rotation[1]);
                this.bephax$rotated = false;
            } else {
                this.bephax$rotationManager.setRotationSilent(targetYaw, rotation[1]);
                this.bephax$rotated = true;
                this.bephax$targetRotation = null;
            }
        } else {
            this.bephax$rotated = true;
        }
    }
}
