package bep.hax.mixin.meteor;

import bep.hax.managers.SwapManager;
import bep.hax.util.PlacementUtils;
import bep.hax.util.RotationUtils;
import bep.hax.util.SurroundPlaceMode;
import bep.hax.util.printer.AirPlaceExecutor;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.combat.Surround;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Surround.class, remap = false)
public abstract class SurroundMixin extends Module {
    @Shadow
    @Final
    private Setting<Boolean> rotate;
    @Shadow
    @Final
    private Setting<Boolean> swing;
    @Shadow
    @Final
    private Setting<Boolean> airPlace;
    @Unique
    private static final double BEPHAX$REACH = 4.5;
    @Unique
    private static final int BEPHAX$RELEASE_TICKS = 4;
    @Unique
    private Setting<SurroundPlaceMode> bephax$placeMode;
    @Unique
    private Setting<Double> bephax$turnSpeed;
    @Unique
    private Setting<Double> bephax$alignTolerance;
    @Unique
    private Setting<Boolean> bephax$silentSwap;
    @Unique
    private Setting<AirPlaceExecutor.Method> bephax$airPlaceMethod;
    @Unique
    private BlockPos bephax$pending;
    @Unique
    private boolean bephax$aimedThisTick;
    @Unique
    private int bephax$realPlacesThisTick;

    public SurroundMixin(Category category, String name, String description) {
        super(category, name, description);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void bephax$init(CallbackInfo ci) {
        SettingGroup sg = this.settings.createGroup("Grim Place");
        this.bephax$placeMode = sg.add(
            ((Builder)((Builder)((Builder)new Builder().name("place-mode"))
                        .description(
                            "Meteor: unchanged. Grim: clicks a real neighbouring face with a face-plane cursor, one place per tick (forces air-place off so support blocks get placed first). AirPlace: Grim, plus an air-place for positions with no real face. AirPlaceAll: every block air-placed - no rotation, no line of sight, full surround as fast as blocks-per-tick allows."
                        ))
                    .defaultValue(SurroundPlaceMode.AirPlace))
                .build()
        );
        this.bephax$airPlaceMethod = sg.add(
            ((Builder)((Builder)((Builder)((Builder)new Builder().name("air-place-method"))
                            .description(
                                "Default: the ordinary vanilla interact, which 2b2t accepts. Grim: wrap it in the off-hand swap so the anticheat cannot see which item placed the block - only needed on servers that cancel air places."
                            ))
                        .defaultValue(AirPlaceExecutor.Method.Default))
                    .visible(() -> this.bephax$placeMode.get() == SurroundPlaceMode.AirPlace || this.bephax$placeMode.get() == SurroundPlaceMode.AirPlaceAll))
                .build()
        );
        this.bephax$turnSpeed = sg.add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("turn-speed")
                .description("Max degrees per tick of the silent rotation towards the block being placed.")
                .defaultValue(180.0)
                .min(10.0)
                .sliderRange(45.0, 360.0)
                .visible(() -> this.bephax$placeMode.get() != SurroundPlaceMode.Meteor && this.bephax$placeMode.get() != SurroundPlaceMode.AirPlaceAll)
                .build()
        );
        this.bephax$alignTolerance = sg.add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("align-tolerance")
                .description(
                    "Degrees of aim error tolerated before clicking. Surround blocks are adjacent, so a loose value still raytraces onto the face and places sooner."
                )
                .defaultValue(5.0)
                .min(0.5)
                .sliderRange(1.0, 15.0)
                .visible(() -> this.bephax$placeMode.get() != SurroundPlaceMode.Meteor && this.bephax$placeMode.get() != SurroundPlaceMode.AirPlaceAll)
                .build()
        );
        this.bephax$silentSwap = sg.add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("silent-swap")
                .description(
                    "Holds the surround block server-side only - you keep seeing (and holding) your current item. Off: the hotbar slot visibly switches, still through the shared swap arbiter."
                )
                .defaultValue(true)
                .visible(() -> this.bephax$placeMode.get() != SurroundPlaceMode.Meteor)
                .build()
        );
    }

    @Unique
    @EventHandler(priority = 200)
    private void bephax$onTickPre(Pre event) {
        this.bephax$aimedThisTick = false;
        this.bephax$realPlacesThisTick = 0;
        if (this.isActive() && this.mc.player != null && this.mc.level != null) {
            if (this.bephax$placeMode.get() != SurroundPlaceMode.Meteor) {
                if (this.bephax$placeMode.get() == SurroundPlaceMode.Grim && this.airPlace.get()) {
                    this.airPlace.set(false);
                }

                if (this.bephax$pending != null && !this.mc.level.getBlockState(this.bephax$pending).canBeReplaced()) {
                    this.bephax$dropAim();
                }
            }
        } else {
            this.bephax$dropAim();
        }
    }

    @Inject(method = "onDeactivate", at = @At("TAIL"))
    private void bephax$onDeactivate(CallbackInfo ci) {
        this.bephax$dropAim();
        SwapManager.getInstance().releaseNow(this);
    }

    @Redirect(
        method = "place",
        at = @At(
            value = "INVOKE",
            target = "Lmeteordevelopment/meteorclient/utils/world/BlockUtils;place(Lnet/minecraft/core/BlockPos;Lmeteordevelopment/meteorclient/utils/player/FindItemResult;ZIZZ)Z"
        ),
        remap = true
    )
    private boolean bephax$place(BlockPos pos, FindItemResult item, boolean rotateArg, int rotationPriority, boolean swingArg, boolean checkEntities) {
        SurroundPlaceMode mode = this.bephax$placeMode.get();
        return mode == SurroundPlaceMode.Meteor
            ? BlockUtils.place(pos, item, rotateArg, rotationPriority, swingArg, checkEntities)
            : this.bephax$grimPlace(pos, item, mode);
    }

    @Unique
    private boolean bephax$grimPlace(BlockPos pos, FindItemResult item, SurroundPlaceMode mode) {
        if (this.mc.player == null || this.mc.level == null) {
            return false;
        }

        if (item.found() && BlockUtils.canPlace(pos)) {
            ItemStack stack = item.isOffhand() ? this.mc.player.getOffhandItem() : this.mc.player.getInventory().getItem(item.slot());
            if (!(stack.getItem() instanceof BlockItem blockItem)) {
                return false;
            } else {
                BlockState var11 = blockItem.getBlock().defaultBlockState();
                BlockHitResult support = PlacementUtils.getSupportHit(pos, 4.5);
                boolean exploit = mode == SurroundPlaceMode.AirPlaceAll || mode == SurroundPlaceMode.AirPlace && support == null;
                if (exploit && !item.isOffhand()) {
                    BlockHitResult hit = PlacementUtils.getAirPlaceHit(pos, 4.5);
                    if (hit != null) {
                        if (!this.bephax$hold(item)) {
                            return false;
                        }

                        AirPlaceExecutor.airPlace(hit, var11, this.bephax$airPlaceMethod.get());
                        return true;
                    }
                }

                if (support == null) {
                    return false;
                }

                if (this.bephax$realPlacesThisTick >= 1) {
                    return false;
                }

                if (this.rotate.get()) {
                    RotationUtils rot = RotationUtils.getInstance();
                    if (!this.bephax$aimedThisTick) {
                        float[] aim = RotationUtils.getRotationsTo(this.mc.player.getEyePosition(), support.getLocation());
                        if (!rot.setRotationSilent(this, 25, aim[0], aim[1], this.bephax$turnSpeed.get())) {
                            return false;
                        }

                        this.bephax$pending = pos;
                        this.bephax$aimedThisTick = true;
                    } else if (!pos.equals(this.bephax$pending)) {
                        return false;
                    }

                    if (!rot.isRotating() || !rot.isAlignedFor(this, this.bephax$alignTolerance.get())) {
                        return false;
                    }
                }

                if (!this.bephax$hold(item)) {
                    return false;
                }

                InteractionHand hand = item.isOffhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                AirPlaceExecutor.silentPlace(support, pos, var11, hand, this.swing.get());
                this.bephax$realPlacesThisTick++;
                this.bephax$pending = null;
                return true;
            }
        } else {
            return false;
        }
    }

    @Unique
    private boolean bephax$hold(FindItemResult item) {
        if (item.isOffhand()) {
            return true;
        } else {
            return !item.isHotbar() ? false : SwapManager.getInstance().hold(this, item.slot(), 25, this.bephax$silentSwap.get(), 4);
        }
    }

    @Unique
    private void bephax$dropAim() {
        RotationUtils.getInstance().release(this);
        this.bephax$pending = null;
    }
}
