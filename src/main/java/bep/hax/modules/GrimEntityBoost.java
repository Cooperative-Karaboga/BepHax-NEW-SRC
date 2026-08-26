package bep.hax.modules;

import bep.hax.Bep;
import bep.hax.util.ViaProtocolUtil;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class GrimEntityBoost extends Module {
    private static final double PER_ENTITY = 0.08;
    private static final double GLIDE_VERTICAL = 0.05;
    private static final double ISSUE_VALUE = 0.06;
    private static final double GRIM_WIDTH = 0.6;
    private static final double GRIM_HEIGHT = 1.8;
    private static final double CONTACT_MARGIN = 0.2;
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgSafety = this.settings.createGroup("Safety");
    private final Setting<GrimEntityBoost.Mode> mode = this.sgGeneral
        .add(
            ((Builder)((Builder)((Builder)new Builder().name("mode"))
                        .description(
                            "Ride spends the whole budget Grim's own count grants. Issue is the report's fixed 0.06 per axis, kept to compare against."
                        ))
                    .defaultValue(GrimEntityBoost.Mode.Ride))
                .build()
        );
    private final Setting<GrimEntityBoost.Direction> direction = this.sgGeneral
        .add(
            ((Builder)((Builder)((Builder)((Builder)new Builder().name("direction"))
                            .description("Motion keeps pushing the way you are already going, which is what compounds. Look pushes along your yaw."))
                        .defaultValue(GrimEntityBoost.Direction.Motion))
                    .visible(() -> this.mode.get() == GrimEntityBoost.Mode.Ride))
                .build()
        );
    private final Setting<Double> safety = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("safety")
                .description(
                    "Fraction of the granted budget to actually spend. Sitting exactly on the boundary flags on any rounding, and Grim's count can drop a tick before ours does."
                )
                .defaultValue(0.8)
                .range(0.1, 1.0)
                .sliderRange(0.5, 1.0)
                .visible(() -> this.mode.get() == GrimEntityBoost.Mode.Ride)
                .build()
        );
    private final Setting<Boolean> vertical = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("vertical")
                .description("Also spend the 0.05 vertical budget the same method grants a glider next to an entity. Only does anything while gliding.")
                .defaultValue(false)
                .visible(() -> this.mode.get() == GrimEntityBoost.Mode.Ride)
                .build()
        );
    private final Setting<Boolean> airborneOnly = this.sgSafety
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("airborne-only")
                .description(
                    "Only boost while off the ground, as the report does. Ground friction eats most of the budget anyway, and walking into a mob at speed is how you get a horizontal-collision mismatch."
                )
                .defaultValue(true)
                .build()
        );
    private final Setting<Double> maxSpeed = this.sgSafety
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("max-speed")
                .description(
                    "Horizontal speed ceiling in blocks per tick. The budget compounds until friction balances it - one entity settles near 0.9 per axis, so this is what keeps a mob farm from launching you."
                )
                .defaultValue(1.0)
                .min(0.1)
                .max(10.0)
                .sliderRange(0.2, 3.0)
                .build()
        );
    private final Setting<Boolean> debug = this.sgSafety
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("debug")
                .description("Report once a second what the count and the spent budget were.")
                .defaultValue(false)
                .build()
        );
    private final int[] recentCounts = new int[3];
    private int countCursor;
    private int count;
    private int live;
    private double spent;
    private int debugTicks;
    private int appliedTicks;
    private Vec3 lastPos;

    public GrimEntityBoost() {
        super(Bep.CATEGORY, "grim-entity-boost", "Spends the 0.08-per-entity movement lenience Grim grants for entity pushing.");
    }

    @Override
    public void onActivate() {
        this.recentCounts[0] = this.recentCounts[1] = this.recentCounts[2] = 0;
        this.countCursor = 0;
        this.count = 0;
        this.live = 0;
        this.spent = 0.0;
        this.debugTicks = 0;
        this.appliedTicks = 0;
        this.lastPos = this.mc.player == null ? null : this.mc.player.position();
    }

    @Override
    public void onDeactivate() {
        this.lastPos = null;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.level != null) {
            this.live = this.countPushable();
            this.recentCounts[this.countCursor] = this.live;
            this.countCursor = (this.countCursor + 1) % this.recentCounts.length;
            this.count = Math.max(this.recentCounts[0], Math.max(this.recentCounts[1], this.recentCounts[2]));
            this.spent = 0.0;
            if (this.count > 0 && (!this.airborneOnly.get() || !this.mc.player.onGround())) {
                this.apply();
            }

            this.lastPos = this.mc.player.position();
            if (this.debug.get() && ++this.debugTicks >= 20) {
                double bpt = this.mc.player.getDeltaMovement().horizontalDistance();
                this.info(
                    "(highlight)%d(default) entities | %+.3f/axis on %d/%d ticks | %.2f b/t / %.0f m/s",
                    this.count,
                    this.spent,
                    this.appliedTicks,
                    this.debugTicks,
                    bpt,
                    bpt * 20.0
                );
                this.debugTicks = 0;
                this.appliedTicks = 0;
            }
        } else {
            this.lastPos = null;
        }
    }

    private void apply() {
        Vec3 velocity = this.mc.player.getDeltaMovement();
        double budget = this.mode.get() == GrimEntityBoost.Mode.Issue ? 0.06 : 0.08 * this.count * this.safety.get();
        if (!(budget <= 0.0)) {
            double dx;
            double dz;
            if (this.mode.get() == GrimEntityBoost.Mode.Issue) {
                dx = velocity.x <= 0.0 ? -budget : budget;
                dz = velocity.z <= 0.0 ? -budget : budget;
            } else {
                Vec3 heading = this.heading(velocity);
                double scale = budget / Math.max(Math.abs(heading.x), Math.abs(heading.z));
                dx = heading.x * scale;
                dz = heading.z * scale;
            }

            double cap = this.maxSpeed.get();
            double speed = Math.sqrt((velocity.x + dx) * (velocity.x + dx) + (velocity.z + dz) * (velocity.z + dz));
            if (speed > cap) {
                double current = velocity.horizontalDistance();
                if (current >= cap) {
                    return;
                }

                double room = (cap - current) / (speed - current);
                dx *= room;
                dz *= room;
            }

            double dy = 0.0;
            if (this.mode.get() == GrimEntityBoost.Mode.Ride && this.vertical.get() && this.mc.player.isFallFlying()) {
                dy = 0.05 * this.safety.get();
            }

            this.mc.player.setDeltaMovement(velocity.add(dx, dy, dz));
            this.spent = Math.max(Math.abs(dx), Math.abs(dz));
            this.appliedTicks++;
        }
    }

    private Vec3 heading(Vec3 velocity) {
        if (this.direction.get() == GrimEntityBoost.Direction.Motion && velocity.horizontalDistance() > 1.0E-4) {
            return new Vec3(velocity.x, 0.0, velocity.z).normalize();
        }

        double yaw = Math.toRadians(this.mc.player.getYRot());
        return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
    }

    private int countPushable() {
        if (!this.mc.player.isPassenger() && !this.mc.player.isSpectator()) {
            AABB box = this.contactBox();
            int found = 0;

            for (Entity entity : this.mc.level.entitiesForRendering()) {
                if (entity != this.mc.player && !entity.isSpectator() && this.isPushable(entity) && box.intersects(entity.getBoundingBox())) {
                    found++;
                }
            }

            return found;
        } else {
            return 0;
        }
    }

    private boolean isPushable(Entity entity) {
        return !(entity instanceof ArmorStand) && !(entity instanceof Bat) && !(entity instanceof Parrot)
            ? entity instanceof LivingEntity || entity instanceof AbstractBoat || entity instanceof AbstractMinecart
            : false;
    }

    private AABB contactBox() {
        Vec3 pos = this.mc.player.position();
        Vec3 previous = this.lastPos == null ? pos : this.lastPos;
        double threshold = this.movementThreshold();
        AABB current = this.grimBox(pos).inflate(threshold);
        AABB last = this.grimBox(previous);
        return new AABB(
                Math.min(current.minX, last.minX),
                Math.min(current.minY, last.minY),
                Math.min(current.minZ, last.minZ),
                Math.max(current.maxX, last.maxX),
                Math.max(current.maxY, last.maxY),
                Math.max(current.maxZ, last.maxZ)
            )
            .inflate(0.2);
    }

    private AABB grimBox(Vec3 pos) {
        double half = 0.3;
        return new AABB(pos.x - half, pos.y, pos.z - half, pos.x + half, pos.y + 1.8, pos.z + half);
    }

    private double movementThreshold() {
        return ViaProtocolUtil.isLegacyBand(ViaProtocolUtil.targetProtocol()) ? 0.03 : 2.0E-4;
    }

    @Override
    public String getInfoString() {
        if (this.count == 0) {
            return "no entities";
        }

        double budget = this.mode.get() == GrimEntityBoost.Mode.Issue ? 0.06 : 0.08 * this.count * this.safety.get();
        return this.live == 0 ? String.format("%d held | %+.3f/axis", this.count, budget) : String.format("%d entities | %+.3f/axis", this.live, budget);
    }

    public enum Direction {
        Motion("Motion"),
        Look("Look");

        private final String title;

        Direction(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return this.title;
        }
    }

    public enum Mode {
        Ride("Ride"),
        Issue("Issue");

        private final String title;

        Mode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return this.title;
        }
    }
}
