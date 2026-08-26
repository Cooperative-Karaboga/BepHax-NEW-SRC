package bep.hax.modules;

import bep.hax.Bep;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.KeybindSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class GrimShulkerLaunch extends Module {
    private static final double PISTON = 0.51;
    private static final double PISTON_BONUS = 0.1;
    private static final double HARD_OFFSET = 1.2;
    private static final int HARD_WINDOW = 3;
    private static final int CLOSE_GRACE = 25;
    private static final double FEET_SIZE = 0.001;
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgOffset = this.settings.createGroup("Offset window");
    private final SettingGroup sgTest = this.settings.createGroup("Test");
    private final Setting<Keybind> launchKey = this.sgGeneral
        .add(
            new Builder()
                .name("launch-key")
                .description("Hold to spend the budget. Leave unbound and turn on auto to fire the moment a shulker box is in range.")
                .defaultValue(Keybind.none())
                .build()
        );
    private final Setting<Boolean> auto = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("auto")
                .description("Spend the budget on every tick it exists, without a key.")
                .defaultValue(false)
                .build()
        );
    private final Setting<GrimShulkerLaunch.Aim> aim = this.sgGeneral
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                            .name("aim"))
                        .description(
                            "Where to push. The budget only exists on the axis the box faces, so Up needs a box on the floor and Look or Motion need one on a wall."
                        ))
                    .defaultValue(GrimShulkerLaunch.Aim.Up))
                .build()
        );
    private final Setting<Double> safety = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("safety")
                .description(
                    "Fraction of the 0.51 to actually spend. The whole 0.51 is legal, but our tick and Grim's do not have to line up on the edges of the window."
                )
                .defaultValue(0.9)
                .range(0.1, 1.0)
                .sliderRange(0.5, 1.0)
                .build()
        );
    private final Setting<Integer> holdTicks = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("hold-ticks")
                .description(
                    "Extra ticks to keep spending after leaving range. Grim's queue holds a contact for about two more ticks; raise it to probe where the real edge is and watch for a setback."
                )
                .defaultValue(2)
                .range(0, 5)
                .sliderRange(0, 4)
                .build()
        );
    private final Setting<Boolean> bonus = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("piston-bonus")
                .description("Also spend the flat 0.1 horizontal and 0.1 vertical that any non-zero piston value adds on top.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> spendOffset = this.sgOffset
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("spend-offset")
                .description(
                    "Also spend the 1.2 blocks a tick that reduceOffset forgives near a shulker box, boat, shulker or happy ghast. Applied as position, not velocity - as velocity it flags the moment the window shuts."
                )
                .defaultValue(false)
                .build()
        );
    private final Setting<Double> offsetAmount = this.sgOffset
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("offset-amount")
                .description(
                    "Blocks per tick to move off-prediction. Grim's own figure is 1.2; past that the remainder is scored and 0.1 of it is an instant setback."
                )
                .defaultValue(1.0)
                .min(0.1)
                .max(2.0)
                .sliderRange(0.2, 1.2)
                .visible(this.spendOffset::get)
                .build()
        );
    private final Setting<Boolean> offsetCollide = this.sgOffset
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("respect-blocks")
                .description(
                    "Skip the position step when a block is in the way. Off means phasing, which the server's own movement checks will notice long before Grim does."
                )
                .defaultValue(true)
                .visible(this.spendOffset::get)
                .build()
        );
    private final Setting<Boolean> reportSetbacks = this.sgTest
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("report-setbacks")
                .description("Chat-log the shulker boxes Grim is tracking for us, and any teleport that follows a launch.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> debug = this.sgTest
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("debug")
                .description("Report the live budget every tick it changes.")
                .defaultValue(false)
                .build()
        );
    private final Map<BlockPos, Integer> openShulkers = new HashMap<>();
    private final int[] pistonTicks = new int[3];
    private int hardTicks;
    private String source = "nothing in range";
    private int launches;
    private int setbacks;
    private int contactTicks;
    private double peakVelocity;
    private boolean spending;
    private Vec3 lastPos;

    public GrimShulkerLaunch() {
        super(Bep.CATEGORY, "grim-shulker-launch", "Launches off the piston-grade uncertainty Grim grants next to an open shulker box.");
    }

    @Override
    public void onActivate() {
        this.openShulkers.clear();
        this.pistonTicks[0] = this.pistonTicks[1] = this.pistonTicks[2] = 0;
        this.hardTicks = 0;
        this.launches = 0;
        this.setbacks = 0;
        this.contactTicks = 0;
        this.peakVelocity = 0.0;
        this.spending = false;
        this.source = "nothing in range";
        this.lastPos = this.mc.player == null ? null : this.mc.player.position();
    }

    @Override
    public void onDeactivate() {
        this.openShulkers.clear();
        this.lastPos = null;
    }

    @EventHandler
    private void onPacketReceive(Receive event) {
        if (this.mc.level != null) {
            if (event.packet instanceof ClientboundBlockEventPacket packet) {
                if (packet.getBlock() instanceof ShulkerBoxBlock) {
                    BlockPos pos = packet.getPos().immutable();
                    if (packet.getB1() >= 1) {
                        if (this.openShulkers.put(pos, -1) == null && this.reportSetbacks.get()) {
                            this.info(
                                "Shulker box open at (highlight)%d %d %d(default) - Grim is tracking it.",
                                pos.getX(),
                                pos.getY(),
                                pos.getZ()
                            );
                        }
                    } else {
                        this.openShulkers.put(pos, 0);
                    }
                }
            } else {
                if (this.reportSetbacks.get() && this.spending && event.packet instanceof ClientboundPlayerPositionPacket) {
                    this.warning("Setback #%d during a launch - that budget was not there.", ++this.setbacks);
                }
            }
        }
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.level != null) {
            this.tickTracking();
            this.scanRange();
            boolean wasSpending = this.spending;
            this.spending = this.shouldSpend();
            if (this.spending) {
                this.spend();
            } else if (wasSpending) {
                this.reportLaunch();
            }

            this.lastPos = this.mc.player.position();
        } else {
            this.lastPos = null;
        }
    }

    private void reportLaunch() {
        if (this.contactTicks != 0) {
            boolean gliding = this.mc.player.isFallFlying();
            if (this.reportSetbacks.get() || this.debug.get()) {
                this.info(
                    "Launch: (highlight)%d(default) contact ticks, peak (highlight)%.2f(default) b/t, apex ~(highlight)%.0f(default) blocks%s.",
                    this.contactTicks,
                    this.peakVelocity,
                    this.estimateApex(this.peakVelocity, gliding),
                    gliding ? " (gliding)" : ""
                );
            }

            this.contactTicks = 0;
            this.peakVelocity = 0.0;
        }
    }

    private double estimateApex(double velocity, boolean gliding) {
        double gravity = gliding ? 0.02 : this.mc.player.getAttributeValue(Attributes.GRAVITY);
        double v = velocity;
        double rise = 0.0;

        for (int i = 0; i < 400 && v > 0.0; i++) {
            rise += v;
            v = (v - gravity) * 0.98;
        }

        return rise;
    }

    private void tickTracking() {
        Iterator<Entry<BlockPos, Integer>> iterator = this.openShulkers.entrySet().iterator();

        while (iterator.hasNext()) {
            Entry<BlockPos, Integer> entry = iterator.next();
            BlockState state = this.mc.level.getBlockState(entry.getKey());
            if (!(state.getBlock() instanceof ShulkerBoxBlock)) {
                iterator.remove();
            } else {
                int closing = entry.getValue();
                if (closing >= 0) {
                    if (closing >= 25) {
                        iterator.remove();
                    } else {
                        entry.setValue(closing + 1);
                    }
                }
            }
        }

        for (int axis = 0; axis < 3; axis++) {
            if (this.pistonTicks[axis] > 0) {
                this.pistonTicks[axis]--;
            }
        }

        if (this.hardTicks > 0) {
            this.hardTicks--;
        }
    }

    private void scanRange() {
        AABB feet = this.feetBox();
        String found = null;
        double modX = 0.0;
        double modY = 0.0;
        double modZ = 0.0;

        for (Entry<BlockPos, Integer> entry : this.openShulkers.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = this.mc.level.getBlockState(pos);
            Direction facing = state.hasProperty(ShulkerBoxBlock.FACING) ? state.getValue(ShulkerBoxBlock.FACING) : Direction.UP;
            AABB box = new AABB(pos).expandTowards(facing.getStepX(), facing.getStepY(), facing.getStepZ());
            if (feet.intersects(box)) {
                modX = Math.max(modX, Math.abs(facing.getStepX() * 0.51));
                modY = Math.max(modY, Math.abs(facing.getStepY() * 0.51));
                modZ = Math.max(modZ, Math.abs(facing.getStepZ() * 0.51));
                feet = feet.move(modX, modY, modZ);
                if (facing.getStepX() != 0) {
                    this.pistonTicks[0] = this.holdTicks.get() + 1;
                }

                if (facing.getStepY() != 0) {
                    this.pistonTicks[1] = this.holdTicks.get() + 1;
                }

                if (facing.getStepZ() != 0) {
                    this.pistonTicks[2] = this.holdTicks.get() + 1;
                }

                this.hardTicks = 3;
                found = "shulker box " + facing.getSerializedName();
            }
        }

        if (found == null && this.nearHardEntity()) {
            this.hardTicks = 3;
            found = "hard entity";
        }

        if (found != null) {
            this.source = found;
        } else if (this.pistonTicks[0] + this.pistonTicks[1] + this.pistonTicks[2] + this.hardTicks > 0) {
            this.source = "window closing";
        } else {
            this.source = "nothing in range";
        }
    }

    private boolean nearHardEntity() {
        AABB box = this.mc.player.getBoundingBox().inflate(1.0);

        for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity != this.mc.player
                && entity != this.mc.player.getVehicle()
                && (entity instanceof AbstractBoat || entity instanceof Shulker || entity instanceof HappyGhast)
                && box.intersects(entity.getBoundingBox())) {
                return true;
            }
        }

        return false;
    }

    private boolean shouldSpend() {
        if (this.mc.player.isSpectator() || this.mc.player.getAbilities().flying) {
            return false;
        } else {
            return !this.auto.get() && !this.launchKey.get().isPressed()
                ? false
                : this.pistonTicks[0] + this.pistonTicks[1] + this.pistonTicks[2] > 0 || this.spendOffset.get() && this.hardTicks > 0;
        }
    }

    private void spend() {
        Vec3 direction = this.aimVector();
        double[] allowance = new double[]{
            this.pistonTicks[0] > 0 ? 0.51 * this.safety.get() : 0.0,
            this.pistonTicks[1] > 0 ? 0.51 * this.safety.get() : 0.0,
            this.pistonTicks[2] > 0 ? 0.51 * this.safety.get() : 0.0
        };
        if (this.bonus.get() && (this.pistonTicks[0] > 0 || this.pistonTicks[1] > 0 || this.pistonTicks[2] > 0)) {
            allowance[0] += 0.1 * this.safety.get();
            allowance[1] += 0.1 * this.safety.get();
            allowance[2] += 0.1 * this.safety.get();
        }

        Vec3 push = this.fit(direction, allowance);
        if (push.lengthSqr() > 0.0) {
            this.mc.player.setDeltaMovement(this.mc.player.getDeltaMovement().add(push));
            this.launches++;
            this.contactTicks++;
            this.peakVelocity = Math.max(this.peakVelocity, this.mc.player.getDeltaMovement().y);
            if (this.debug.get()) {
                this.info("Spent (highlight)%.2f %.2f %.2f(default) from %s.", push.x, push.y, push.z, this.source);
            }
        }

        if (this.spendOffset.get() && this.hardTicks > 1) {
            this.blink(direction);
        }
    }

    private void blink(Vec3 direction) {
        Vec3 step = direction.scale(Math.min(this.offsetAmount.get(), 1.2));
        if (!(step.lengthSqr() <= 0.0)) {
            if (!this.offsetCollide.get() || this.mc.level.noCollision(this.mc.player, this.mc.player.getBoundingBox().expandTowards(step))) {
                Vec3 pos = this.mc.player.position();
                this.mc.player.setPos(pos.x + step.x, pos.y + step.y, pos.z + step.z);
            }
        }
    }

    private Vec3 fit(Vec3 direction, double[] allowance) {
        double scale = Double.MAX_VALUE;
        scale = Math.min(scale, this.axisScale(direction.x, allowance[0]));
        scale = Math.min(scale, this.axisScale(direction.y, allowance[1]));
        scale = Math.min(scale, this.axisScale(direction.z, allowance[2]));
        return scale != Double.MAX_VALUE && !(scale <= 0.0) ? direction.scale(scale) : Vec3.ZERO;
    }

    private double axisScale(double component, double allowance) {
        return Math.abs(component) < 1.0E-9 ? Double.MAX_VALUE : allowance / Math.abs(component);
    }

    private Vec3 aimVector() {
        switch ((GrimShulkerLaunch.Aim)this.aim.get()) {
            case Up:
                return new Vec3(0.0, 1.0, 0.0);
            case Look:
                return Vec3.directionFromRotation(this.mc.player.getXRot(), this.mc.player.getYRot());
            case Motion:
                Vec3 velocity = this.mc.player.getDeltaMovement();
                if (velocity.horizontalDistance() > 1.0E-4) {
                    return new Vec3(velocity.x, 0.0, velocity.z).normalize();
                }

                double yaw = Math.toRadians(this.mc.player.getYRot());
                return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
            default:
                return Vec3.ZERO;
        }
    }

    private AABB feetBox() {
        Vec3 pos = this.mc.player.position();
        Vec3 previous = this.lastPos == null ? pos : this.lastPos;
        double half = 5.0E-4;
        AABB box = new AABB(
            Math.min(pos.x, previous.x) - half,
            Math.min(pos.y, previous.y),
            Math.min(pos.z, previous.z) - half,
            Math.max(pos.x, previous.x) + half,
            Math.max(pos.y, previous.y) + 0.001,
            Math.max(pos.z, previous.z) + half
        );
        return box.inflate(1.0);
    }

    @Override
    public String getInfoString() {
        int ticks = Math.max(this.pistonTicks[1], Math.max(this.pistonTicks[0], this.pistonTicks[2]));
        return ticks == 0 && this.hardTicks == 0
            ? String.format("%d open | %s", this.openShulkers.size(), this.source)
            : String.format("%s | %d piston, %d offset | %d launches", this.source, ticks, this.hardTicks, this.launches);
    }

    public enum Aim {
        Up("Up"),
        Look("Look"),
        Motion("Motion");

        private final String title;

        Aim(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return this.title;
        }
    }
}
