package bep.hax.mixin.meteor;

import bep.hax.accessor.IHighwayBuilder;
import bep.hax.modules.BepMine;
import bep.hax.util.BaritoneHelper;
import bep.hax.util.HighwayBaritoneDriver;
import bep.hax.util.HighwayBuilderConfigHolder;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import java.lang.reflect.Field;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.HighwayBuilder;
import meteordevelopment.meteorclient.systems.modules.world.HighwayBuilder.DoubleMineBlock;
import meteordevelopment.meteorclient.utils.misc.HorizontalDirection;
import meteordevelopment.meteorclient.utils.misc.MBlockPos;
import meteordevelopment.meteorclient.utils.player.CustomPlayerInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = HighwayBuilder.class, remap = false)
public abstract class HighwayBuilderMixin implements IHighwayBuilder {
    @Shadow
    public Vec3 start;
    @Shadow
    private HorizontalDirection dir;
    @Shadow
    private boolean suspended;
    @Shadow
    private SettingGroup sgPaving;
    @Shadow
    private int placeTimer;
    @Shadow
    private CustomPlayerInput input;
    @Shadow
    @Final
    private Setting<Integer> width;
    @Shadow
    public DoubleMineBlock normalMining;
    @Shadow
    public DoubleMineBlock packetMining;
    @Unique
    private boolean bephax$onPath = false;
    @Unique
    private boolean bephax$pavingPlace = false;
    @Unique
    private boolean bephax$advancing = false;
    @Unique
    private boolean bephax$centerOrReLevel = false;
    @Unique
    private boolean bephax$baritoneEngaged = false;
    @Unique
    private int bephax$headX;
    @Unique
    private int bephax$headY;
    @Unique
    private int bephax$headZ;
    @Unique
    private Setting<Boolean> bephax$baritoneMovement;
    @Unique
    private static final double bephax$EPS = 0.001;
    @Unique
    private static final double bephax$MAX_STEP = 0.5;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void bephax$addPavingSettings(CallbackInfo ci) {
        Setting<Boolean> grimAirPlace = this.sgPaving
            .add(
                new Builder()
                    .name("grim-air-place")
                    .description(
                        "Place paving blocks (floor/railings/corners/liquids) with the AutoPortal method: smooth GrimAC rotation, the off-hand air-place exploit and per-block placement confirmation. Disable to use Meteor's normal placement."
                    )
                    .defaultValue(true)
                    .build()
            );
        HighwayBuilderConfigHolder.setGrimAirPlaceSetting(grimAirPlace);
        this.bephax$baritoneMovement = ((Module)(Object)this)
            .settings
            .getDefaultGroup()
            .add(
                new Builder()
                    .name("baritone-movement")
                    .description(
                        "Walk the highway with Baritone instead of Meteor's rigid input movement. Baritone follows the locked centerline, paces itself with the mining/placing, and re-paths back onto the line after mob knockback instead of snapping you in place. Needs Baritone installed; falls back to the default movement if it's missing."
                    )
                    .defaultValue(true)
                    .build()
            );
        this.bephax$widenWidth();
    }

    @Unique
    private void bephax$widenWidth() {
        if (this.width instanceof IntSetting is) {
            try {
                Field maxF = IntSetting.class.getField("max");
                Field sliderMaxF = IntSetting.class.getField("sliderMax");
                maxF.setAccessible(true);
                sliderMaxF.setAccessible(true);
                maxF.setInt(is, 8);
                sliderMaxF.setInt(is, 8);
            } catch (ReflectiveOperationException var4) {
            }
        }
    }

    @Inject(method = "getWidthLeft", at = @At("HEAD"), cancellable = true)
    private void bephax$widthLeft(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(this.width.get() / 2);
    }

    @Inject(method = "getWidthRight", at = @At("HEAD"), cancellable = true)
    private void bephax$widthRight(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue((this.width.get() - 1) / 2);
    }

    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true)
    private void bephax$gateToSurvival(Pre event, CallbackInfo ci) {
        if (MeteorClient.mc.gameMode == null || MeteorClient.mc.gameMode.getPlayerMode() != GameType.SURVIVAL) {
            this.normalMining = null;
            this.packetMining = null;
            ci.cancel();
        }
    }

    @Inject(method = "error", at = @At("HEAD"), cancellable = true)
    private void bephax$preventErrorToggleRecursion(String message, Object[] args, CallbackInfo ci) {
        if (!((Module)(Object)this).isActive()) {
            ci.cancel();
        }
    }

    @Inject(
        method = "setState(Lmeteordevelopment/meteorclient/systems/modules/world/HighwayBuilder$State;Lmeteordevelopment/meteorclient/systems/modules/world/HighwayBuilder$State;)V",
        at = @At("HEAD")
    )
    private void bephax$trackPathState(@Coerce Enum<?> newState, @Coerce Enum<?> lastState, CallbackInfo ci) {
        String name = newState.name();
        this.bephax$onPath = bephax$isForwardState(name);
        this.bephax$pavingPlace = bephax$isPavingState(name);
        this.bephax$advancing = name.equals("Forward");
        this.bephax$centerOrReLevel = name.equals("Center") || name.equals("ReLevel");
    }

    @Override
    public boolean bephax$isPavingPlace() {
        return this.bephax$pavingPlace;
    }

    @Override
    public void bephax$stallPlacement(int ticks) {
        if (this.placeTimer < ticks) {
            this.placeTimer = ticks;
        }
    }

    @Override
    public boolean bephax$shouldLockLine() {
        return this.bephax$onPath && this.start != null && this.dir != null;
    }

    @Override
    public void bephax$snapToLine(MBlockPos pos) {
        if (this.start != null && this.dir != null) {
            int laneX = Mth.floor(this.start.x);
            int laneZ = Mth.floor(this.start.z);
            if (!this.dir.diagonal) {
                if (this.dir.offsetX == 0) {
                    pos.x = laneX;
                } else {
                    pos.z = laneZ;
                }
            } else {
                int dx = this.dir.offsetX;
                int dz = this.dir.offsetZ;
                int t = Math.round(((pos.x - laneX) * dx + (pos.z - laneZ) * dz) / 2.0F);
                pos.x = laneX + t * dx;
                pos.z = laneZ + t * dz;
            }
        }
    }

    @WrapWithCondition(
        method = "tickDoubleMine",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V", remap = true)
    )
    private boolean bephax$skipSwingWhileDelegating(LocalPlayer player, InteractionHand hand) {
        BepMine bepMine = Modules.get().get(BepMine.class);
        return bepMine == null || !bepMine.canDelegateMining();
    }

    @Inject(method = "onTick", at = @At("TAIL"))
    private void bephax$keepOnLine(Pre event, CallbackInfo ci) {
        if (MeteorClient.mc.player != null && MeteorClient.mc.level != null) {
            if (this.bephax$baritoneMovement != null && this.bephax$baritoneMovement.get() && BaritoneHelper.isAvailable()) {
                this.bephax$driveWithBaritone();
            } else {
                if (BaritoneHelper.isAvailable()) {
                    HighwayBaritoneDriver.disengage();
                }

                if (this.bephax$baritoneEngaged) {
                    if (this.input != null) {
                        MeteorClient.mc.player.input = this.input;
                    }

                    this.bephax$baritoneEngaged = false;
                }

                if (!this.suspended && this.bephax$onPath && this.start != null && this.dir != null) {
                    Vec3 p = MeteorClient.mc.player.position();
                    Vec3 v = MeteorClient.mc.player.getDeltaMovement();
                    double laneX = Math.floor(this.start.x) + 0.5;
                    double laneZ = Math.floor(this.start.z) + 0.5;
                    if (!this.dir.diagonal) {
                        if (this.dir.offsetX == 0) {
                            this.bephax$lockStraight(p, v, true, laneX);
                        } else {
                            this.bephax$lockStraight(p, v, false, laneZ);
                        }
                    } else {
                        this.bephax$lockDiagonal(p, v, laneX, laneZ);
                    }
                }
            }
        }
    }

    @Unique
    private void bephax$driveWithBaritone() {
        boolean drivable = !this.suspended && this.bephax$onPath && !this.bephax$centerOrReLevel && this.start != null && this.dir != null;
        if (!drivable) {
            if (this.bephax$baritoneEngaged) {
                HighwayBaritoneDriver.pause();
                if (this.input != null) {
                    MeteorClient.mc.player.input = this.input;
                }

                this.bephax$baritoneEngaged = false;
            }
        } else {
            double px = MeteorClient.mc.player.getX();
            double pz = MeteorClient.mc.player.getZ();
            int laneX = Mth.floor(this.start.x);
            int laneZ = Mth.floor(this.start.z);
            int hx;
            int hz;
            if (!this.dir.diagonal) {
                if (this.dir.offsetX == 0) {
                    hx = laneX;
                    hz = Mth.floor(pz);
                } else {
                    hx = Mth.floor(px);
                    hz = laneZ;
                }
            } else {
                int dx = this.dir.offsetX;
                int dz = this.dir.offsetZ;
                int t = Math.round(((Mth.floor(px) - laneX) * dx + (Mth.floor(pz) - laneZ) * dz) / 2.0F);
                hx = laneX + t * dx;
                hz = laneZ + t * dz;
            }

            if (this.bephax$advancing || !this.bephax$baritoneEngaged) {
                this.bephax$headX = hx;
                this.bephax$headZ = hz;
                this.bephax$headY = Mth.floor(MeteorClient.mc.player.getY());
            }

            this.bephax$baritoneEngaged = true;
            if (this.input != null) {
                this.input.stop();
            }

            HighwayBaritoneDriver.drive(
                this.dir.offsetX, this.dir.offsetZ, this.bephax$advancing, this.bephax$headX, this.bephax$headY, this.bephax$headZ, px, pz
            );
        }
    }

    @Inject(method = "onDeactivate", at = @At("HEAD"))
    private void bephax$baritoneDeactivate(CallbackInfo ci) {
        if (BaritoneHelper.isAvailable()) {
            HighwayBaritoneDriver.disengage();
        }

        this.bephax$baritoneEngaged = false;
    }

    @Unique
    private void bephax$lockStraight(Vec3 p, Vec3 v, boolean lockX, double lane) {
        double cur = lockX ? p.x : p.z;
        double diff = lane - cur;
        double vp = lockX ? v.x : v.z;
        if (!(Math.abs(diff) <= 0.001) || !(Math.abs(vp) <= 0.001)) {
            double step = Math.max(-0.5, Math.min(0.5, diff));
            double newPos = cur + step;
            if (lockX) {
                MeteorClient.mc.player.setPos(newPos, p.y, p.z);
                MeteorClient.mc.player.setDeltaMovement(0.0, v.y, v.z);
            } else {
                MeteorClient.mc.player.setPos(p.x, p.y, newPos);
                MeteorClient.mc.player.setDeltaMovement(v.x, v.y, 0.0);
            }
        }
    }

    @Unique
    private void bephax$lockDiagonal(Vec3 p, Vec3 v, double sx, double sz) {
        int dx = this.dir.offsetX;
        int dz = this.dir.offsetZ;
        double rx = p.x - sx;
        double rz = p.z - sz;
        double k = (rx * dz - rz * dx) / 2.0;
        double vk = (v.x * dz - v.z * dx) / 2.0;
        if (!(Math.abs(k) <= 0.001) || !(Math.abs(vk) <= 0.001)) {
            double kStep = Math.max(-0.5, Math.min(0.5, k));
            double nx = p.x - kStep * dz;
            double nz = p.z + kStep * dx;
            double nvx = v.x - vk * dz;
            double nvz = v.z + vk * dx;
            MeteorClient.mc.player.setPos(nx, p.y, nz);
            MeteorClient.mc.player.setDeltaMovement(nvx, v.y, nvz);
        }
    }

    @Unique
    private static boolean bephax$isForwardState(String name) {
        return switch (name) {
            case "Center", "Forward", "MineFront", "MineFloor", "MineRailings", "MineAboveRailings", "FillLiquids", "PlaceFloor", "PlaceRailings", "PlaceCornerBlock", "ReLevel", "DefuseCrystalTraps" -> true;
            default -> false;
        };
    }

    @Unique
    private static boolean bephax$isPavingState(String name) {
        return switch (name) {
            case "FillLiquids", "PlaceFloor", "PlaceRailings", "PlaceCornerBlock" -> true;
            default -> false;
        };
    }
}
