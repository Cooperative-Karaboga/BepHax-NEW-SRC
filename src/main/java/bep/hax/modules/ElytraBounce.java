package bep.hax.modules;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import bep.hax.Bep;
import bep.hax.util.BaritoneHelper;
import bep.hax.util.BounceSolver;
import bep.hax.util.Utils;
import java.util.List;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.PlaySoundEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.systems.modules.player.ChestSwap;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class ElytraBounce extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgObstaclePasser = this.settings.createGroup("Obstacle Passer");
    private final Setting<Boolean> bounce = this.sgGeneral
        .add(new Builder().name("bounce").description("Automatically does bounce efly.").defaultValue(true).build());
    private final Setting<Boolean> lockPitch = this.sgGeneral
        .add(
            new Builder()
                .name("lock-pitch")
                .description("Whether to lock your pitch when bounce is enabled.")
                .defaultValue(true)
                .visible(this.bounce::get)
                .build()
        );
    private final Setting<Boolean> autoPitch = this.sgGeneral
        .add(
            new Builder()
                .name("auto-pitch")
                .description(
                    "Solves for the pitch that actually bounces fastest instead of using a fixed one. Speed comes from how often you land (each sprint-jump adds 0.2 while the elytra keeps 0.99 friction), so the best pitch depends on how much headroom is above you — worth up to +25% under a low ceiling."
                )
                .defaultValue(true)
                .visible(() -> this.bounce.get() && this.lockPitch.get())
                .build()
        );
    private final Setting<Double> pitch = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("pitch")
                .description("The pitch to set when bounce is enabled.")
                .defaultValue(90.0)
                .sliderRange(-90.0, 90.0)
                .visible(() -> this.bounce.get() && this.lockPitch.get() && !this.autoPitch.get())
                .build()
        );
    private final Setting<Integer> setbackPause = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("setback-pause")
                .description("Ticks to stop bouncing for after the server teleports you back, so the module doesn't fight a setback loop. 0 disables.")
                .defaultValue(5)
                .min(0)
                .sliderMax(40)
                .visible(this.bounce::get)
                .build()
        );
    private final Setting<Boolean> lockYaw = this.sgGeneral
        .add(
            new Builder()
                .name("lock-yaw")
                .description("Whether to lock your yaw when bounce is enabled.")
                .defaultValue(false)
                .visible(this.bounce::get)
                .build()
        );
    private final Setting<Boolean> freePitch = this.sgGeneral
        .add(
            new Builder()
                .name("free-pitch")
                .description(
                    "Allows you to freely move your camera pitch without affecting bounce. Server pitch stays locked for bouncing while camera pitch is independent, letting you look ahead."
                )
                .defaultValue(true)
                .visible(() -> this.bounce.get() && this.lockPitch.get())
                .build()
        );
    private final Setting<Boolean> useCustomYaw = this.sgGeneral
        .add(
            new Builder()
                .name("use-custom-yaw")
                .description(
                    "Enable this if you want to use a yaw that isn't a factor of 45. WARNING: This effects the baritone goal for obstacle passer, use the default Rotations module if you only want a different yawlock."
                )
                .defaultValue(false)
                .visible(this.bounce::get)
                .build()
        );
    private final Setting<Double> yaw = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("yaw")
                .description(
                    "The yaw to set when bounce is enabled. This is auto set to the closest 45 deg angle to you unless Use Custom Yaw is enabled. WARNING: This effects the baritone goal for obstacle passer, use the default Rotations module if you only want a different yawlock."
                )
                .defaultValue(0.0)
                .sliderRange(0.0, 359.0)
                .visible(() -> this.bounce.get() && this.useCustomYaw.get())
                .build()
        );
    private final Setting<Boolean> highwayObstaclePasser = this.sgObstaclePasser
        .add(new Builder().name("highway-obstacle-passer").description("Uses baritone to pass obstacles.").defaultValue(true).visible(this.bounce::get).build());
    private final Setting<Boolean> awayFromStartPos = this.sgObstaclePasser
        .add(
            new Builder()
                .name("away-from-start-position")
                .description(
                    "If true, will go away from the start position instead of towards it. The start position is automatically set to your position when the module is activated."
                )
                .defaultValue(true)
                .visible(() -> this.bounce.get() && this.highwayObstaclePasser.get())
                .build()
        );
    private final Setting<Double> distance = this.sgObstaclePasser
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("distance")
                .description("The distance to set the baritone goal for path realignment.")
                .defaultValue(10.0)
                .visible(() -> this.bounce.get() && this.highwayObstaclePasser.get())
                .build()
        );
    private final Setting<Boolean> avoidPortalTraps = this.sgObstaclePasser
        .add(
            new Builder()
                .name("avoid-portal-traps")
                .description("Will attempt to detect portal traps on chunk load and avoid them.")
                .defaultValue(false)
                .visible(() -> this.bounce.get() && this.highwayObstaclePasser.get())
                .build()
        );
    private final Setting<Double> portalAvoidDistance = this.sgObstaclePasser
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("portal-avoid-distance")
                .description("The distance to a portal trap where the obstacle passer will takeover and go around it.")
                .defaultValue(20.0)
                .min(0.0)
                .sliderMax(50.0)
                .visible(() -> this.bounce.get() && this.highwayObstaclePasser.get() && this.avoidPortalTraps.get())
                .build()
        );
    private final Setting<Integer> portalScanWidth = this.sgObstaclePasser
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("portal-scan-width")
                .description("The width on the axis of the highway that will be scanned for portal traps.")
                .defaultValue(5)
                .min(3)
                .sliderMax(10)
                .visible(() -> this.bounce.get() && this.highwayObstaclePasser.get() && this.avoidPortalTraps.get())
                .build()
        );
    private final Setting<Boolean> killAuraWhileWalking = this.sgObstaclePasser
        .add(
            new Builder()
                .name("killaura-while-walking")
                .description(
                    "Only enables KillAura while the bounce is stopped and Baritone is walking (obstacle passing), then disables it again once bouncing resumes so its rotations don't interfere. Restores KillAura's previous state on deactivate."
                )
                .defaultValue(false)
                .visible(() -> this.bounce.get() && this.highwayObstaclePasser.get())
                .build()
        );
    private final Setting<Boolean> fakeFly = this.sgGeneral
        .add(
            new Builder()
                .name("chestplate-fakefly")
                .description("Lets you fly using a chestplate to use almost 0 elytra durability. Must have elytra in hotbar.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> toggleElytra = this.sgGeneral
        .add(
            new Builder()
                .name("toggle-elytra")
                .description("Equips an elytra on activate, and a chestplate on deactivate.")
                .defaultValue(true)
                .visible(() -> !this.fakeFly.get())
                .build()
        );
    private boolean startSprinting;
    private boolean jumpKeyDown;
    private BlockPos portalTrap = null;
    private boolean paused = false;
    private boolean elytraToggled = false;
    private Vec3 lastUnstuckPos;
    private int stuckTimer = 0;
    private int targetY = 120;
    private BlockPos startPos = new BlockPos(0, 0, 0);
    public float cameraPitch;
    private KillAura killAura;
    private Boolean killAuraUserState = null;
    private float solvedPitch = 90.0F;
    private double solvedHeadroom = -1.0;
    private double predictedSpeed = 0.0;
    private int solveCooldown = 0;
    private int setbackTicks = 0;
    private final double maxDistance = 80.0;
    private BlockPos tempPath = null;
    private boolean waitingForChunksToLoad;

    public ElytraBounce() {
        super(Bep.HUNT_CATEGORY, "ElytraBounce", "Elytra fly with some more features.");
    }

    @Override
    public void onActivate() {
        if (this.mc.player != null && !this.mc.player.getAbilities().mayfly) {
            this.startSprinting = this.mc.player.isSprinting();
            this.jumpKeyDown = false;
            this.tempPath = null;
            this.portalTrap = null;
            this.paused = false;
            this.waitingForChunksToLoad = false;
            this.elytraToggled = false;
            this.lastUnstuckPos = this.mc.player.position();
            this.stuckTimer = 0;
            this.cameraPitch = this.mc.player.getXRot();
            this.solvedPitch = this.pitch.get().floatValue();
            this.solvedHeadroom = -1.0;
            this.predictedSpeed = 0.0;
            this.solveCooldown = 0;
            this.setbackTicks = 0;
            if (this.bounce.get() && this.mc.player.position().multiply(1.0, 0.0, 1.0).length() >= 100.0) {
                if (!BaritoneHelper.hasElytraProcess() || BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().currentDestination() == null) {
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoal(null);
                }

                if (this.highwayObstaclePasser.get()) {
                    this.startPos = this.mc.player.blockPosition();
                    this.targetY = this.mc.player.getBlockY();
                } else {
                    this.startPos = new BlockPos(0, 0, 0);
                }

                if (!this.useCustomYaw.get()) {
                    if (!(this.mc.player.blockPosition().distSqr(this.startPos) < 10000.0) && this.highwayObstaclePasser.get()) {
                        BlockPos directionVec = this.mc.player.blockPosition().subtract(this.startPos);
                        double angle = Math.toDegrees(Math.atan2(-directionVec.getX(), directionVec.getZ()));
                        double angleNormalized = Utils.angleOnAxis(angle);
                        if (!this.awayFromStartPos.get()) {
                            angleNormalized += 180.0;
                        }

                        this.yaw.set(angleNormalized);
                    } else {
                        double playerAngleNormalized = Utils.angleOnAxis(this.mc.player.getYRot());
                        this.yaw.set(playerAngleNormalized);
                    }
                }
            }
        }
    }

    @Override
    public void onDeactivate() {
        this.restoreKillAura();
        if (this.mc.player != null) {
            if (this.freePitch.get() && this.lockPitch.get() && this.bounce.get()) {
                this.mc.player.setXRot(this.cameraPitch);
            }

            if (this.bounce.get()
                && (!BaritoneHelper.hasElytraProcess() || BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().currentDestination() == null)) {
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoal(null);
            }

            this.mc.player.setSprinting(this.startSprinting);
            if (this.toggleElytra.get()
                && !this.fakeFly.get()
                && !this.mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem().toString().contains("chestplate")) {
                Modules.get().get(ChestSwap.class).swap();
            }
        }
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && !this.mc.player.getAbilities().mayfly) {
            if (!this.isFreePitchEnabled()) {
                this.cameraPitch = this.mc.player.getXRot();
            }

            if (this.setbackTicks > 0) {
                this.setbackTicks--;
            }

            this.updateAutoPitch();
            this.updateKillAura();
            this.updateJumpKey();
            if (this.toggleElytra.get() && !this.fakeFly.get() && !this.elytraToggled) {
                if (!this.mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem().equals(Items.ELYTRA)) {
                    Modules.get().get(ChestSwap.class).swap();
                } else {
                    this.elytraToggled = true;
                }
            }

            if (this.enabled() && this.mc.player.getFoodData().hasEnoughFood()) {
                this.mc.player.setSprinting(true);
            }

            if (this.bounce.get()) {
                if (this.tempPath != null && this.mc.player.blockPosition().distSqr(this.tempPath) < 500.0) {
                    this.tempPath = null;
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoal(null);
                } else if (this.tempPath != null) {
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(this.tempPath));
                    return;
                }

                if (this.highwayObstaclePasser.get() && BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().getGoal() != null) {
                    return;
                }

                if (this.mc.player.distanceToSqr(this.lastUnstuckPos) < 25.0) {
                    this.stuckTimer++;
                } else {
                    this.stuckTimer = 0;
                    this.lastUnstuckPos = this.mc.player.position();
                }

                if (this.highwayObstaclePasser.get()
                    && this.mc.player.position().length() > 100.0
                    && (
                        this.mc.player.getY() < this.targetY
                            || this.mc.player.getY() > this.targetY + 2
                            || this.mc.player.horizontalCollision && !this.mc.player.minorHorizontalCollision
                            || this.portalTrap != null
                                && this.portalTrap.distSqr(this.mc.player.blockPosition())
                                    < this.portalAvoidDistance.get() * this.portalAvoidDistance.get()
                            || this.waitingForChunksToLoad
                            || this.stuckTimer > 50
                    )) {
                    this.waitingForChunksToLoad = false;
                    this.paused = true;
                    BlockPos goal = this.mc.player.blockPosition();
                    double currDistance = this.distance.get();
                    if (this.portalTrap != null) {
                        currDistance += this.mc.player.position().distanceTo(this.portalTrap.getCenter());
                        this.portalTrap = null;
                        this.info("Pathing around portal.");
                    }

                    do {
                        if (currDistance > 80.0) {
                            this.tempPath = goal;
                            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(goal));
                            return;
                        }

                        Vec3 unitYawVec = Utils.yawToDirection(this.yaw.get());
                        Vec3 travelVec = this.mc.player.position().subtract(this.startPos.getCenter());
                        double parallelCurrPosDot = travelVec.multiply(new Vec3(1.0, 0.0, 1.0)).dot(unitYawVec);
                        Vec3 parallelCurrPosComponent = unitYawVec.scale(parallelCurrPosDot);
                        Vec3 pos = this.startPos.getCenter().add(parallelCurrPosComponent);
                        pos = Utils.positionInDirection(pos, this.yaw.get(), currDistance);
                        goal = new BlockPos((int)Math.floor(pos.x), this.targetY, (int)Math.floor(pos.z));
                        currDistance++;
                        if (this.mc.level.getBlockState(goal).getBlock() == Blocks.VOID_AIR) {
                            this.waitingForChunksToLoad = true;
                            return;
                        }
                    } while (
                        !this.mc.level.getBlockState(goal.below()).isRedstoneConductor(this.mc.level, goal.below())
                            || this.mc.level.getBlockState(goal).getBlock() == Blocks.NETHER_PORTAL
                            || !this.mc.level.getBlockState(goal).isAir()
                    );

                    BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(goal));
                } else {
                    this.paused = false;
                    if (!this.enabled()) {
                        return;
                    }

                    if (this.lockYaw.get()) {
                        this.mc.player.setYRot(this.yaw.get().floatValue());
                    }

                    if (this.lockPitch.get()) {
                        this.mc.player.setXRot(this.effectivePitch());
                    }
                }
            }

            if (this.enabled() && this.fakeFly.get()) {
                this.doGrimEflyStuff();
            }
        }
    }

    private void updateKillAura() {
        if (this.killAura == null) {
            this.killAura = Modules.get().get(KillAura.class);
        }

        if (this.killAura != null) {
            if (this.isActive() && this.killAuraWhileWalking.get() && this.bounce.get() && this.highwayObstaclePasser.get()) {
                if (this.killAuraUserState == null) {
                    this.killAuraUserState = this.killAura.isActive();
                }

                boolean walking = this.paused;
                if (walking != this.killAura.isActive()) {
                    this.killAura.toggle();
                }
            } else {
                this.restoreKillAura();
            }
        }
    }

    private void updateAutoPitch() {
        if (this.autoPitch.get() && this.bounce.get() && this.lockPitch.get() && this.mc.level != null) {
            if (this.solveCooldown > 0) {
                this.solveCooldown--;
            } else if (this.mc.player.onGround()) {
                this.solveCooldown = 10;
                double headroom = BounceSolver.measureHeadroom(this.mc.player, this.mc.level);
                if (!(this.solvedHeadroom >= 0.0) || !(Math.abs(headroom - this.solvedHeadroom) < 0.05)) {
                    this.solvedHeadroom = headroom;
                    this.solvedPitch = BounceSolver.solvePitch(headroom);
                    this.predictedSpeed = BounceSolver.simulateSpeed(this.solvedPitch, headroom);
                }
            }
        }
    }

    private float effectivePitch() {
        return this.autoPitch.get() ? this.solvedPitch : this.pitch.get().floatValue();
    }

    @Override
    public String getInfoString() {
        return this.bounce.get() && this.lockPitch.get() && this.autoPitch.get() && !(this.predictedSpeed <= 0.0)
            ? String.format("%.0f b/s @ %.0f°", this.predictedSpeed, this.solvedPitch)
            : null;
    }

    @EventHandler
    private void onPacketReceive(Receive event) {
        if (this.bounce.get() && this.setbackPause.get() > 0) {
            if (event.packet instanceof ClientboundPlayerPositionPacket) {
                this.setbackTicks = this.setbackPause.get();
            }
        }
    }

    private void restoreKillAura() {
        if (this.killAuraUserState != null) {
            if (this.killAura != null && this.killAura.isActive() != this.killAuraUserState) {
                this.killAura.toggle();
            }

            this.killAuraUserState = null;
        }
    }

    public boolean enabled() {
        return this.isActive()
            && !this.paused
            && this.setbackTicks == 0
            && this.mc.player != null
            && (this.fakeFly.get() || this.mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem().equals(Items.ELYTRA));
    }

    public boolean isFreePitchEnabled() {
        return this.enabled() && this.bounce.get() && this.lockPitch.get() && this.freePitch.get();
    }

    public boolean isBounceRenderStabilized() {
        return this.enabled() && this.bounce.get() && this.lockPitch.get();
    }

    public boolean isFakeFlyEnabled() {
        return this.fakeFly.get();
    }

    public boolean shouldAutoJump() {
        return this.bounce.get() && !this.fakeFly.get();
    }

    private void updateJumpKey() {
        boolean prev = this.jumpKeyDown;
        if (!this.enabled() || !this.shouldAutoJump()) {
            this.jumpKeyDown = false;
        } else if (this.mc.player.onGround()) {
            this.jumpKeyDown = true;
        } else if (this.mc.player.isFallFlying()) {
            this.jumpKeyDown = false;
        } else {
            this.jumpKeyDown = !prev;
        }
    }

    public boolean isJumpKeyForcedDown() {
        return this.jumpKeyDown;
    }

    private void doGrimEflyStuff() {
        if (this.bounce.get() && this.mc.player.onGround()) {
            this.mc.player.jumpFromGround();
        }
    }

    @EventHandler
    private void onPlaySound(PlaySoundEvent event) {
        if (this.fakeFly.get()) {
            for (Identifier identifier : List.of(
                Identifier.parse("minecraft:item.armor.equip_generic"),
                Identifier.parse("minecraft:item.armor.equip_netherite"),
                Identifier.parse("minecraft:item.armor.equip_elytra"),
                Identifier.parse("minecraft:item.armor.equip_diamond"),
                Identifier.parse("minecraft:item.armor.equip_gold"),
                Identifier.parse("minecraft:item.armor.equip_iron"),
                Identifier.parse("minecraft:item.armor.equip_chain"),
                Identifier.parse("minecraft:item.armor.equip_leather"),
                Identifier.parse("minecraft:item.elytra.flying")
            )) {
                if (identifier.equals(event.sound.getIdentifier())) {
                    event.cancel();
                    break;
                }
            }
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (this.avoidPortalTraps.get() && this.highwayObstaclePasser.get()) {
            ChunkPos pos = event.chunk().getPos();
            BlockPos centerPos = pos.getMiddleBlockPosition(this.targetY);
            Vec3 moveDir = Utils.yawToDirection(this.yaw.get());
            double distanceToHighway = Utils.distancePointToDirection(Vec3.atLowerCornerOf(centerPos), moveDir, this.mc.player.position());
            if (!(distanceToHighway > 21.0)) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = this.targetY; y < this.targetY + 3; y++) {
                            BlockPos position = new BlockPos(pos.x * 16 + x, y, pos.z * 16 + z);
                            if (!(
                                    Utils.distancePointToDirection(Vec3.atLowerCornerOf(position), moveDir, this.mc.player.position())
                                        > this.portalScanWidth.get().intValue()
                                )
                                && this.mc.level.getBlockState(position).getBlock().equals(Blocks.NETHER_PORTAL)) {
                                BlockPos posBehind = new BlockPos(
                                    (int)Math.floor(position.getX() + moveDir.x),
                                    position.getY(),
                                    (int)Math.floor(position.getZ() + moveDir.z)
                                );
                                if ((
                                        this.mc.level.getBlockState(posBehind).isRedstoneConductor(this.mc.level, posBehind)
                                            || this.mc.level.getBlockState(posBehind).getBlock() == Blocks.NETHER_PORTAL
                                    )
                                    && (
                                        this.portalTrap == null
                                            || this.portalTrap.distSqr(posBehind) > 100.0
                                                && this.mc.player.blockPosition().distSqr(posBehind)
                                                    < this.mc.player.blockPosition().distSqr(this.portalTrap)
                                    )) {
                                    this.portalTrap = posBehind;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
