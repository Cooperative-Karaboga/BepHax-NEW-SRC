package bep.hax.mixin.meteor;

import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import bep.hax.util.InventoryManager;
import bep.hax.util.NoSlowConfigHolder;
import bep.hax.util.RotationUtils;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.movement.NoSlow;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = NoSlow.class, remap = false)
public abstract class NoSlowMixin {
    @Shadow
    @Final
    protected SettingGroup sgGeneral;
    @Unique
    private Setting<NoSlowConfigHolder.Mode> bephax$grimMode;
    @Unique
    private Setting<Boolean> bephax$grimWebBypass;
    @Unique
    private Setting<Boolean> bephax$strictMode;
    @Unique
    private Setting<Boolean> bephax$disableOnElytra;
    @Unique
    private Setting<Boolean> bephax$sprintWhileUsing;
    @Unique
    private Setting<Integer> bephax$unslowedTicks;
    @Unique
    private int bephax$sequenceId = 0;
    @Unique
    private int bephax$dutyTick = 1;
    @Unique
    private boolean bephax$unslowed = false;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        this.bephax$grimMode = this.sgGeneral
            .add(
                ((Builder)((Builder)((Builder)((Builder)new Builder().name("grim-mode"))
                                .description(
                                    "Which generation of Grim to bypass. HandSwap and V3 claim the other hand is the one using an item; current Grim resyncs that from server entity metadata, so only DutyCycle still works on an up-to-date server."
                                ))
                            .defaultValue(NoSlowConfigHolder.Mode.DutyCycle))
                        .onChanged(v -> NoSlowConfigHolder.setModeSetting(this.bephax$grimMode)))
                    .build()
            );
        NoSlowConfigHolder.setModeSetting(this.bephax$grimMode);
        this.bephax$grimWebBypass = this.sgGeneral
            .add(
                new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                    .name("grim-web-bypass")
                    .description("Bypasses GrimAC web slowdown using block break packets")
                    .defaultValue(false)
                    .build()
            );
        this.bephax$strictMode = this.sgGeneral
            .add(
                new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                    .name("strict-mode")
                    .description("Strict NCP bypass for ground slowdowns")
                    .defaultValue(true)
                    .build()
            );
        this.bephax$disableOnElytra = this.sgGeneral
            .add(
                new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                    .name("disable-on-elytra")
                    .description("Disables NoSlow while flying with an elytra")
                    .defaultValue(true)
                    .build()
            );
        this.bephax$unslowedTicks = this.sgGeneral
            .add(
                new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                    .name("unslowed-ticks")
                    .description(
                        "Unslowed ticks between each slowed one. 1 is the only value Grim cannot flag; above that you are betting on the violation decay outrunning the setback threshold."
                    )
                    .defaultValue(1)
                    .range(1, 5)
                    .sliderRange(1, 5)
                    .visible(() -> this.bephax$grimMode.get() == NoSlowConfigHolder.Mode.DutyCycle)
                    .build()
            );
        this.bephax$sprintWhileUsing = this.sgGeneral
            .add(
                new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                    .name("sprint-while-using")
                    .description(
                        "Let a sprint start while an item is in use. Vanilla only blocks starting one - an existing sprint already survives eating - and Grim's SprintC only flags this in water, so it is 1.3x on both halves of the cycle for free."
                    )
                    .defaultValue(true)
                    .onChanged(v -> NoSlowConfigHolder.setSprintWhileUsingSetting(this.bephax$sprintWhileUsing))
                    .build()
            );
        NoSlowConfigHolder.setSprintWhileUsingSetting(this.bephax$sprintWhileUsing);
    }

    @Unique
    @EventHandler
    private void bephax$onDutyTick(Pre event) {
        NoSlow noSlow = (NoSlow)(Object)this;
        int span = this.bephax$unslowedTicks.get();
        if (MeteorClient.mc.player != null
            && noSlow.isActive()
            && this.bephax$grimMode.get() == NoSlowConfigHolder.Mode.DutyCycle
            && MeteorClient.mc.player.isUsingItem()) {
            this.bephax$unslowed = this.bephax$dutyTick < span;
            this.bephax$dutyTick = this.bephax$dutyTick >= span ? 0 : this.bephax$dutyTick + 1;
        } else {
            this.bephax$dutyTick = span;
            this.bephax$unslowed = false;
        }
    }

    @Inject(method = "items", at = @At("HEAD"), cancellable = true, require = 0)
    private void bephax$items(CallbackInfoReturnable<Boolean> cir) {
        if (this.bephax$grimMode.get() == NoSlowConfigHolder.Mode.DutyCycle) {
            if (MeteorClient.mc.player != null && MeteorClient.mc.player.isUsingItem()) {
                if (!this.bephax$unslowed) {
                    cir.setReturnValue(false);
                }
            }
        }
    }

    @EventHandler
    @Inject(method = "onPreTick", at = @At("HEAD"), cancellable = true, require = 0)
    private void bephax$onPreTick(Pre event, CallbackInfo ci) {
        if (MeteorClient.mc.player != null && MeteorClient.mc.level != null) {
            NoSlow noSlow = (NoSlow)(Object)this;
            if (noSlow.isActive()) {
                if (!this.bephax$disableOnElytra.get() || !MeteorClient.mc.player.isFallFlying()) {
                    float sentYaw = RotationUtils.getInstance().getSentYaw();
                    float sentPitch = RotationUtils.getInstance().getSentPitch();
                    NoSlowConfigHolder.Mode mode = this.bephax$grimMode.get();
                    if (mode == NoSlowConfigHolder.Mode.HandSwap && MeteorClient.mc.player.isUsingItem() && !MeteorClient.mc.player.isShiftKeyDown()) {
                        if (MeteorClient.mc.player.getUsedItemHand() == InteractionHand.OFF_HAND
                            && this.bephax$checkStack(MeteorClient.mc.player.getMainHandItem())) {
                            MeteorClient.mc.getConnection().send(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, this.bephax$sequenceId++, sentYaw, sentPitch));
                        } else if (this.bephax$checkStack(MeteorClient.mc.player.getOffhandItem())) {
                            MeteorClient.mc.getConnection().send(new ServerboundUseItemPacket(InteractionHand.OFF_HAND, this.bephax$sequenceId++, sentYaw, sentPitch));
                        }
                    }

                    if (mode == NoSlowConfigHolder.Mode.V3
                        && MeteorClient.mc.player.isUsingItem()
                        && !MeteorClient.mc.player.isShiftKeyDown()
                        && MeteorClient.mc.player.getUsedItemHand() == InteractionHand.OFF_HAND) {
                        MeteorClient.mc.getConnection().send(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, this.bephax$sequenceId++, sentYaw, sentPitch));
                    }

                    if (mode != NoSlowConfigHolder.Mode.None && this.bephax$grimWebBypass.get()) {
                        AABB bb = mode == NoSlowConfigHolder.Mode.HandSwap
                            ? MeteorClient.mc.player.getBoundingBox().inflate(1.0)
                            : MeteorClient.mc.player.getBoundingBox();

                        for (BlockPos pos : this.bephax$getIntersectingWebs(bb)) {
                            MeteorClient.mc.getConnection().send(new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, pos, Direction.DOWN));
                        }
                    }
                }
            }
        }
    }

    @Unique
    private boolean bephax$checkStack(ItemStack stack) {
        return !stack.getComponents().has(DataComponents.FOOD)
            && stack.getItem() != Items.BOW
            && stack.getItem() != Items.CROSSBOW
            && stack.getItem() != Items.SHIELD;
    }

    @Unique
    private boolean bephax$checkSlowed() {
        if (MeteorClient.mc.player == null) {
            return false;
        }

        NoSlowConfigHolder.Mode mode = this.bephax$grimMode.get();
        return mode == NoSlowConfigHolder.Mode.V3 && !this.bephax$checkGrimNew()
            ? false
            : !MeteorClient.mc.player.isHandsBusy()
                && !MeteorClient.mc.player.isShiftKeyDown()
                && (
                    MeteorClient.mc.player.isUsingItem()
                        || MeteorClient.mc.player.isBlocking() && mode != NoSlowConfigHolder.Mode.V3 && mode != NoSlowConfigHolder.Mode.HandSwap
                );
    }

    @Unique
    private boolean bephax$checkGrimNew() {
        return MeteorClient.mc.player == null
            ? true
            : !MeteorClient.mc.player.isShiftKeyDown()
                && !MeteorClient.mc.player.isVisuallyCrawling()
                && !MeteorClient.mc.player.isHandsBusy()
                && (
                    MeteorClient.mc.player.getUseItemRemainingTicks() < 5
                        || MeteorClient.mc.player.getTicksUsingItem() > 1 && MeteorClient.mc.player.getTicksUsingItem() % 2 != 0
                );
    }

    @Unique
    private List<BlockPos> bephax$getIntersectingWebs(AABB boundingBox) {
        List<BlockPos> blocks = new ArrayList<>();
        if (MeteorClient.mc.level == null) {
            return blocks;
        }

        int minX = (int)Math.floor(boundingBox.minX);
        int minY = (int)Math.floor(boundingBox.minY);
        int minZ = (int)Math.floor(boundingBox.minZ);
        int maxX = (int)Math.ceil(boundingBox.maxX);
        int maxY = (int)Math.ceil(boundingBox.maxY);
        int maxZ = (int)Math.ceil(boundingBox.maxZ);

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = MeteorClient.mc.level.getBlockState(pos);
                    if (state.getBlock() instanceof WebBlock) {
                        blocks.add(pos);
                    }
                }
            }
        }

        return blocks;
    }

    @Unique
    @EventHandler
    private void onPacketSend(Send event) {
        if (MeteorClient.mc.player != null && MeteorClient.mc.level != null) {
            NoSlow noSlow = (NoSlow)(Object)this;
            if (noSlow.isActive()) {
                if (!this.bephax$disableOnElytra.get() || !MeteorClient.mc.player.isFallFlying()) {
                    if (this.bephax$strictMode.get() && event.packet instanceof ServerboundMovePlayerPacket packet) {
                        if (!packet.hasPosition()) {
                            return;
                        }

                        if (!this.bephax$checkSlowed()) {
                            return;
                        }

                        InventoryManager.getInstance().setSlotForced(((PlayerInventoryAccessor)MeteorClient.mc.player.getInventory()).getSelectedSlot());
                    }
                }
            }
        }
    }
}
