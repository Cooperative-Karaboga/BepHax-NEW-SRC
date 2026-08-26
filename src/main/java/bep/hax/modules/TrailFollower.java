package bep.hax.modules;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalXZ;
import bep.hax.Bep;
import bep.hax.util.BaritoneHelper;
import bep.hax.util.Utils;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.ArrayDeque;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import xaeroplus.XaeroPlus;
import xaeroplus.event.ChunkDataEvent;
import xaeroplus.module.ModuleManager;
import xaeroplus.module.impl.OldChunks;
import xaeroplus.module.impl.PaletteNewChunks;

public class TrailFollower extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    public final Setting<Integer> maxTrailLength = this.sgGeneral
        .add(
            new Builder()
                .name("max-trail-length")
                .description(
                    "The number of trail points to keep for the average. Adjust to change how quickly the average will change. More does not necessarily equal better because if the list is too long it will contain chunks behind you."
                )
                .defaultValue(20)
                .sliderRange(1, 100)
                .build()
        );
    public final Setting<Integer> chunksBeforeStarting = this.sgGeneral
        .add(
            new Builder()
                .name("chunks-before-starting")
                .description("Useful for afking looking for a trail. The amount of chunks before it gets detected as a trail.")
                .defaultValue(10)
                .sliderRange(1, 50)
                .build()
        );
    public final Setting<Integer> chunkConsiderationWindow = this.sgGeneral
        .add(
            new Builder()
                .name("chunk-timeframe")
                .description("The amount of time in seconds that the chunks must be found in before starting.")
                .defaultValue(5)
                .sliderRange(1, 20)
                .build()
        );
    public final Setting<TrailFollower.TrailEndBehavior> trailEndBehavior = this.sgGeneral
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                            .name("trail-end-behavior"))
                        .description("What to do when the trail ends."))
                    .defaultValue(TrailFollower.TrailEndBehavior.DISABLE))
                .build()
        );
    public final Setting<Double> trailEndYaw = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("trail-end-yaw")
                .description("The direction to go after the trail is abandoned.")
                .defaultValue(0.0)
                .sliderRange(0.0, 359.9)
                .visible(() -> this.trailEndBehavior.get() == TrailFollower.TrailEndBehavior.FLY_TOWARDS_YAW)
                .build()
        );
    public final Setting<TrailFollower.OverworldFlightMode> overworldFlightMode = this.sgGeneral
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                            .name("overworld-flight-mode"))
                        .description(
                            "Choose how TrailFollower flies in Overworld. If other is selected then nothing will be automatically enabled, instead just your yaw will be changed to point towards the trail."
                        ))
                    .defaultValue(TrailFollower.OverworldFlightMode.PITCH40))
                .build()
        );
    public final Setting<TrailFollower.NetherPathMode> netherPathMode = this.sgGeneral
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                            .name("nether-path-mode"))
                        .description(
                            "Choose how TrailFollower does baritone pathing in Nether. If other is selected then nothing will be automatically enabled, instead just your yaw will be changed to point towards the trail."
                        ))
                    .defaultValue(TrailFollower.NetherPathMode.AVERAGE))
                .build()
        );
    public final Setting<Boolean> pitch40Firework = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("auto-firework")
                .description("Uses a firework automatically if your velocity is too low.")
                .defaultValue(true)
                .visible(() -> this.overworldFlightMode.get() == TrailFollower.OverworldFlightMode.PITCH40)
                .build()
        );
    public final Setting<Double> rotateScaling = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("rotate-scaling")
                .description("Scaling of how fast the yaw changes. 1 = instant, 0 = doesn't change")
                .defaultValue(0.15)
                .sliderRange(0.0, 1.0)
                .build()
        );
    public final Setting<Boolean> oppositeDimension = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("opposite-dimension")
                .description("Follows trails from the opposite dimension (Requires that you've already loaded the other dimension with XP).")
                .defaultValue(false)
                .build()
        );
    public final Setting<Boolean> autoElytra = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("auto-start-baritone-elytra")
                .description("Starts baritone elytra for you.")
                .defaultValue(true)
                .build()
        );
    private final SettingGroup sgAdvanced = this.settings.createGroup("Advanced", false);
    public final Setting<Double> pathDistance = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("path-distance")
                .description("The distance to add trail positions in the direction the player is facing. (Ignored when following overworld from nether)")
                .defaultValue(500.0)
                .sliderRange(100.0, 2000.0)
                .onChanged(value -> this.pathDistanceActual = value)
                .build()
        );
    public final Setting<TrailFollower.FollowMode> flightMethod = this.sgAdvanced
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                            .name("flight-method"))
                        .description("Decided how the goals will be used. Leave this on AUTO unless you want to use yaw lock in the nether for example."))
                    .defaultValue(TrailFollower.FollowMode.AUTO))
                .build()
        );
    public final Setting<Double> startDirectionWeighting = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("start-direction-weight")
                .description(
                    "Initial bias toward the direction you're facing when enabling. Decays as trail becomes established. 0 = no bias, 1 = strong bias."
                )
                .defaultValue(0.7)
                .min(0.0)
                .sliderMax(1.0)
                .build()
        );
    public final Setting<Double> forwardConeAngle = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("forward-cone-angle")
                .description(
                    "During initial detection, only consider chunks within this angle of your facing direction. 90 = hemisphere ahead, 180 = all around."
                )
                .defaultValue(120.0)
                .min(45.0)
                .sliderMax(180.0)
                .build()
        );
    public final Setting<Double> forwardWeightStrength = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("forward-weight-strength")
                .description("How much to favor chunks aligned with current direction in the average. 0 = equal weight, 1 = strong forward preference.")
                .defaultValue(0.6)
                .min(0.0)
                .sliderMax(1.0)
                .build()
        );
    public final Setting<TrailFollower.DirectionWeighting> directionWeighting = this.sgAdvanced
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                            .name("direction-weighting"))
                        .description(
                            "How the chunks found should be weighted. Useful for path splits. Left will weight chunks to the left of the player higher, right will weigh chunks to the right higher, and none will be in the middle/random. "
                        ))
                    .defaultValue(TrailFollower.DirectionWeighting.NONE))
                .build()
        );
    public final Setting<Integer> directionWeightingMultiplier = this.sgAdvanced
        .add(
            new Builder()
                .name("direction-weighting-multiplier")
                .description(
                    "The multiplier for how much weight should be given to chunks in the direction specified. Values are capped to be in the range [2, maxTrailLength]."
                )
                .defaultValue(2)
                .min(2)
                .sliderMax(10)
                .visible(() -> this.directionWeighting.get() != TrailFollower.DirectionWeighting.NONE)
                .build()
        );
    public final Setting<Boolean> only112 = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("follow-only-1.12")
                .description("Will only follow 1.12 chunks and will ignore other ones.")
                .defaultValue(false)
                .build()
        );
    public final Setting<Double> chunkFoundTimeout = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("chunk-found-timeout")
                .description(
                    "The amount of MS without a trail chunk ahead of you before weaving side to side to let the world load. Raise on laggy servers so normal chunk droughts don't trigger searching."
                )
                .defaultValue(8000.0)
                .min(1000.0)
                .sliderMax(20000.0)
                .build()
        );
    public final Setting<TrailFollower.SearchBehavior> searchBehavior = this.sgAdvanced
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                            .name("search-behavior"))
                        .description(
                            "What to do when chunks stop arriving. SWEEP weaves around the trail direction keeping forward progress, CIRCLE spins in place (legacy), STRAIGHT holds the trail direction. Baritone mode treats CIRCLE as STRAIGHT."
                        ))
                    .defaultValue(TrailFollower.SearchBehavior.SWEEP))
                .build()
        );
    public final Setting<Double> sweepAngle = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("sweep-angle")
                .description(
                    "Starting sweep amplitude off the trail direction. Widens up to 90 degrees the longer no forward chunks arrive, slowing forward progress so slow-loading chunks can catch up."
                )
                .defaultValue(35.0)
                .min(10.0)
                .sliderMax(90.0)
                .visible(() -> this.searchBehavior.get() == TrailFollower.SearchBehavior.SWEEP)
                .build()
        );
    public final Setting<Double> circlingDegPerTick = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("Circling-degrees-per-tick")
                .description("The amount of degrees to change per tick while searching (circle spin rate / sweep speed).")
                .defaultValue(2.0)
                .min(1.0)
                .sliderMax(20.0)
                .build()
        );
    public final Setting<Double> trailTimeout = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("trail-timeout")
                .description("The amount of MS without a chunk found to stop following the trail.")
                .defaultValue(30000.0)
                .min(10000.0)
                .sliderMax(180000.0)
                .build()
        );
    public final Setting<Double> maxTrailDeviation = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("max-trail-deviation")
                .description("Maximum allowed angle (in degrees) from the committed trail direction. Helps avoid switching to intersecting trails.")
                .defaultValue(180.0)
                .min(1.0)
                .sliderMax(270.0)
                .build()
        );
    public final Setting<Boolean> reverseLock = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("reverse-lock")
                .description(
                    "Ignores chunks too far off the committed trail direction, so chunks loading late behind you (server lag) can't turn you back the way you came."
                )
                .defaultValue(true)
                .build()
        );
    public final Setting<Double> reverseLockAngle = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("reverse-lock-angle")
                .description("Chunks more than this many degrees off the committed trail direction are ignored.")
                .defaultValue(110.0)
                .min(60.0)
                .sliderMax(180.0)
                .visible(this.reverseLock::get)
                .build()
        );
    public final Setting<Double> maxTurnRate = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("max-turn-rate")
                .description(
                    "Maximum degrees per second the committed trail direction can change. Stops lag bursts of sideways chunks from whipping the heading around."
                )
                .defaultValue(45.0)
                .min(5.0)
                .sliderMax(180.0)
                .build()
        );
    public final Setting<Integer> chunkCacheLength = this.sgAdvanced
        .add(
            new Builder()
                .name("chunk-cache-length")
                .description("The amount of chunks to keep in the cache. (Won't be applied until deactivating)")
                .defaultValue(100000)
                .sliderRange(0, 10000000)
                .build()
        );
    public final Setting<String> webhookLink = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("webhook-link")
                .description("Will send all updates to the webhook link. Leave blank to disable.")
                .defaultValue("")
                .build()
        );
    public final Setting<Integer> baritoneUpdateTicks = this.sgAdvanced
        .add(
            new Builder()
                .name("baritone-path-update-ticks")
                .description("The amount of ticks between updates to the baritone goal. Low values may cause high instability.")
                .defaultValue(100)
                .sliderRange(20, 600)
                .build()
        );
    public final Setting<Boolean> debug = this.sgAdvanced
        .add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("debug").description("Debug mode.").defaultValue(false).build());
    private boolean oldAutoFireworkValue;
    private boolean oldAutoBoundAdjustValue;
    private TrailFollower.FollowMode followMode;
    private boolean followingTrail = false;
    private ArrayDeque<Vec3> trail = new ArrayDeque<>();
    private ArrayDeque<Vec3> possibleTrail = new ArrayDeque<>();
    private long lastFoundTrailTime;
    private long lastSteerChunkTime;
    private long lastFoundPossibleTrailTime;
    private double pathDistanceActual = this.pathDistance.get();
    private boolean started = false;
    private double initialYaw = 0.0;
    private boolean hasInitialDirection = false;
    private int chunksFoundSinceStart = 0;
    private Cache<Long, Byte> seenChunksCache = Caffeine.newBuilder()
        .maximumSize(this.chunkCacheLength.get().intValue())
        .expireAfterWrite(Duration.ofMinutes(5L))
        .build();
    private double targetYaw;
    private double desiredYaw;
    private double committedYaw;
    private double searchPhase;
    private boolean wasSearching;
    private int baritoneSetGoalTicks = 0;
    Vec3 posDebug;

    public TrailFollower() {
        super(Bep.HUNT_CATEGORY, "TrailFollower", "Automatically follows trails in all dimensions.");
    }

    void resetTrail() {
        this.baritoneSetGoalTicks = 0;
        this.followingTrail = false;
        this.trail = new ArrayDeque<>();
        this.possibleTrail = new ArrayDeque<>();
        this.hasInitialDirection = false;
        this.chunksFoundSinceStart = 0;
        this.wasSearching = false;
        this.searchPhase = 0.0;
    }

    @Override
    public void onActivate() {
        this.resetTrail();
        XaeroPlus.EVENT_BUS.register(this);
        if (this.started) {
            if (this.mc.player != null && this.mc.level != null) {
                ResourceKey<Level> currentDimension = this.mc.level.dimension();
                if (this.oppositeDimension.get()) {
                    if (currentDimension.equals(Level.END)) {
                        this.info("There is no opposite dimension to the end. Disabling TrailFollower");
                        this.toggle();
                        return;
                    }

                    if (currentDimension.equals(Level.NETHER)) {
                        this.info("Following overworld trails from the nether is not supported yet, sorry. Disabling TrailFollower");
                        this.toggle();
                        return;
                    }
                }

                if (this.flightMethod.get() != TrailFollower.FollowMode.AUTO) {
                    this.followMode = this.flightMethod.get();
                } else if (!currentDimension.equals(Level.NETHER)) {
                    this.followMode = TrailFollower.FollowMode.YAWLOCK;
                    this.info("You are in the overworld or end, basic yaw mode will be used.");
                } else {
                    try {
                        Class.forName("baritone.api.BaritoneAPI");
                        this.followMode = TrailFollower.FollowMode.BARITONE;
                        this.info("You are in the nether, baritone mode will be used.");
                    } catch (ClassNotFoundException e) {
                        this.info("Baritone is required to trail follow in the nether. Disabling TrailFollower");
                        this.toggle();
                        return;
                    }
                }

                if (this.followMode == TrailFollower.FollowMode.YAWLOCK && !this.mc.level.dimension().equals(Level.NETHER)) {
                    if (this.overworldFlightMode.get() == TrailFollower.OverworldFlightMode.PITCH40) {
                        Class<? extends Module> pitch40Util = Pitch40.class;
                        Module pitch40UtilModule = Modules.get().get(pitch40Util);
                        if (!pitch40UtilModule.isActive()) {
                            pitch40UtilModule.toggle();
                            if (this.pitch40Firework.get()) {
                                Setting<Boolean> setting = (Setting<Boolean>)pitch40UtilModule.settings.get("auto-firework");
                                if (setting != null) {
                                    this.info(
                                        "Auto Firework enabled, if you want to change the velocity threshold or the firework cooldown check the settings under Pitch40."
                                    );
                                    this.oldAutoFireworkValue = setting.get();
                                    setting.set(true);
                                }
                            }

                            Setting<Boolean> autoBoundAdjustSetting = (Setting<Boolean>)pitch40UtilModule.settings.get("auto-bound-adjust");
                            if (autoBoundAdjustSetting != null) {
                                this.oldAutoBoundAdjustValue = autoBoundAdjustSetting.get();
                                autoBoundAdjustSetting.set(false);
                            }
                        }
                    } else if (this.overworldFlightMode.get() == TrailFollower.OverworldFlightMode.ROCKETS) {
                        RocketFly rocketFly = Modules.get().get(RocketFly.class);
                        if (!rocketFly.isActive()) {
                            rocketFly.toggle();
                        }
                    }
                }

                this.initialYaw = this.getActualYaw(this.mc.player.getYRot());
                this.hasInitialDirection = true;
                Vec3 offset = new Vec3(
                        Math.sin(-this.mc.player.getYRot() * Math.PI / 180.0), 0.0, Math.cos(-this.mc.player.getYRot() * Math.PI / 180.0)
                    )
                    .normalize()
                    .scale(this.pathDistance.get());
                Vec3 targetPos = this.mc.player.position().add(offset);

                for (int i = 0; i < this.maxTrailLength.get().intValue() * this.startDirectionWeighting.get(); i++) {
                    this.trail.add(targetPos);
                }

                this.targetYaw = this.initialYaw;
            } else {
                this.toggle();
            }
        }
    }

    @Override
    public void onDeactivate() {
        this.started = false;
        this.seenChunksCache = Caffeine.newBuilder().maximumSize(this.chunkCacheLength.get().intValue()).expireAfterWrite(Duration.ofMinutes(5L)).build();
        XaeroPlus.EVENT_BUS.unregister(this);
        this.trail.clear();
        if (this.followMode != null) {
            switch (this.followMode) {
                case BARITONE:
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("cancel");
                    break;
                case YAWLOCK:
                    if (this.mc.level == null || this.mc.level.dimension().equals(Level.NETHER)) {
                        return;
                    }

                    if (this.overworldFlightMode.get() == TrailFollower.OverworldFlightMode.ROCKETS) {
                        RocketFly rocketFly = Modules.get().get(RocketFly.class);
                        if (rocketFly != null) {
                            rocketFly.resetYLock();
                            if (rocketFly.isActive()) {
                                rocketFly.toggle();
                            }
                        }
                    } else if (this.overworldFlightMode.get() == TrailFollower.OverworldFlightMode.PITCH40) {
                        Class<? extends Module> pitch40Util = Pitch40.class;
                        Module pitch40UtilModule = Modules.get().get(pitch40Util);
                        if (pitch40UtilModule != null) {
                            if (pitch40UtilModule.isActive()) {
                                pitch40UtilModule.toggle();
                            }

                            Setting<Boolean> autoFireworkSetting = (Setting<Boolean>)pitch40UtilModule.settings.get("auto-firework");
                            if (autoFireworkSetting != null) {
                                autoFireworkSetting.set(this.oldAutoFireworkValue);
                            }

                            Setting<Boolean> autoBoundAdjustSetting = (Setting<Boolean>)pitch40UtilModule.settings.get("auto-bound-adjust");
                            if (autoBoundAdjustSetting != null) {
                                autoBoundAdjustSetting.set(this.oldAutoBoundAdjustValue);
                            }
                        }
                    }
            }
        }
    }

    private void searchForTrail() {
        if (!this.wasSearching) {
            this.wasSearching = true;
            this.searchPhase = 0.0;
            this.targetYaw = this.committedYaw;
        }

        TrailFollower.SearchBehavior behavior = this.searchBehavior.get();
        if (this.followMode == TrailFollower.FollowMode.BARITONE && behavior == TrailFollower.SearchBehavior.CIRCLE) {
            behavior = TrailFollower.SearchBehavior.STRAIGHT;
        }

        switch (behavior) {
            case SWEEP:
                this.searchPhase = this.searchPhase + this.circlingDegPerTick.get();
                double starvedMs = Math.max(0.0, System.currentTimeMillis() - this.lastSteerChunkTime - this.chunkFoundTimeout.get());
                double growWindowMs = Math.max(1000.0, this.trailTimeout.get() - this.chunkFoundTimeout.get());
                double amplitude = this.sweepAngle.get() + (90.0 - this.sweepAngle.get()) * Math.min(1.0, starvedMs / growWindowMs);
                this.targetYaw = this.wrapYaw(this.committedYaw + amplitude * Math.sin(Math.toRadians(this.searchPhase)));
                break;
            case CIRCLE:
                this.targetYaw = this.wrapYaw(this.targetYaw + this.circlingDegPerTick.get());
                break;
            case STRAIGHT:
                this.targetYaw = this.committedYaw;
        }

        if (this.mc.player.tickCount % 100 == 0) {
            this.log(
                "Searching for trail chunks, abandoning trail in "
                    + (this.trailTimeout.get() - (System.currentTimeMillis() - this.lastFoundTrailTime)) / 1000.0
                    + " seconds."
            );
        }
    }

    @EventHandler
    private void onTick(Post event) {
        if (this.mc.player != null && this.mc.level != null) {
            if (!this.started) {
                this.started = true;
                this.onActivate();
                if (!this.isActive()) {
                    return;
                }
            }

            long sinceLastChunk = System.currentTimeMillis() - this.lastFoundTrailTime;
            if (this.followingTrail && sinceLastChunk > this.trailTimeout.get()) {
                this.resetTrail();
                this.log("Trail timed out, stopping.");
                switch ((TrailFollower.TrailEndBehavior)this.trailEndBehavior.get()) {
                    case DISABLE:
                        this.toggle();
                        break;
                    case FLY_TOWARDS_YAW:
                        this.targetYaw = this.trailEndYaw.get();
                        break;
                    case DISCONNECT:
                        this.mc.player.connection.handleDisconnect(new ClientboundDisconnectPacket(Component.literal("[TrailFollower] Trail timed out.")));
                }

                if (!this.isActive()) {
                    return;
                }
            }

            if (this.followingTrail && System.currentTimeMillis() - this.lastSteerChunkTime > this.chunkFoundTimeout.get()) {
                this.searchForTrail();
            } else {
                this.wasSearching = false;
                if (this.followingTrail) {
                    this.committedYaw = this.approachYaw(this.committedYaw, this.desiredYaw, this.maxTurnRate.get() / 20.0);
                    this.targetYaw = this.committedYaw;
                }
            }

            switch (this.followMode) {
                case BARITONE:
                    if (this.baritoneSetGoalTicks > 0) {
                        this.baritoneSetGoalTicks--;
                    } else if (this.baritoneSetGoalTicks == 0) {
                        this.baritoneSetGoalTicks = this.baritoneUpdateTicks.get();
                        if (this.mc.level.dimension().equals(Level.NETHER)) {
                            if (!this.trail.isEmpty()) {
                                Vec3 baritoneTarget;
                                if (this.netherPathMode.get() == TrailFollower.NetherPathMode.AVERAGE) {
                                    baritoneTarget = Utils.positionInDirection(this.mc.player.position(), this.targetYaw, this.pathDistanceActual);
                                } else {
                                    baritoneTarget = this.trail.getLast();
                                }

                                BaritoneAPI.getProvider()
                                    .getPrimaryBaritone()
                                    .getCustomGoalProcess()
                                    .setGoalAndPath(new GoalXZ((int)baritoneTarget.x, (int)baritoneTarget.z));
                            }
                        } else {
                            Vec3 targetPos = Utils.positionInDirection(this.mc.player.position(), this.targetYaw, this.pathDistanceActual);
                            BaritoneAPI.getProvider()
                                .getPrimaryBaritone()
                                .getCustomGoalProcess()
                                .setGoalAndPath(new GoalXZ((int)targetPos.x, (int)targetPos.z));
                        }

                        if (this.autoElytra.get()
                            && BaritoneHelper.hasElytraProcess()
                            && BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().currentDestination() == null) {
                            BaritoneAPI.getSettings().elytraTermsAccepted.value = true;
                            BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("elytra");
                        }
                    }
                    break;
                case YAWLOCK:
                    this.mc
                        .player
                        .setYRot(Utils.smoothRotation(this.getActualYaw(this.mc.player.getYRot()), this.targetYaw, this.rotateScaling.get()));
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.debug.get()) {
            Vec3 targetPos = Utils.positionInDirection(this.mc.player.position(), this.targetYaw, 10.0);
            event.renderer
                .line(
                    this.mc.player.getX(),
                    this.mc.player.getY(),
                    this.mc.player.getZ(),
                    targetPos.x,
                    targetPos.y,
                    targetPos.z,
                    new Color(255, 0, 0)
                );
            if (this.posDebug != null) {
                event.renderer
                    .line(
                        this.mc.player.getX(),
                        this.mc.player.getY(),
                        this.mc.player.getZ(),
                        this.posDebug.x,
                        targetPos.y,
                        this.posDebug.z,
                        new Color(0, 0, 255)
                    );
            }
        }
    }

    @net.lenni0451.lambdaevents.EventHandler(priority = -1)
    public void onChunkData(ChunkDataEvent event) {
        if (!event.seenChunk()) {
            if (this.mc.player != null && this.mc.level != null) {
                ResourceKey<Level> currentDimension = this.mc.level.dimension();
                LevelChunk chunk = event.chunk();
                ChunkPos chunkPos = chunk.getPos();
                long chunkLong = chunkPos.toLong();
                if (this.seenChunksCache.getIfPresent(chunkLong) == null) {
                    ChunkPos chunkDelta = new ChunkPos(
                        chunkPos.x - this.mc.player.chunkPosition().x, chunkPos.z - this.mc.player.chunkPosition().z
                    );
                    if (this.oppositeDimension.get()) {
                        if (currentDimension.equals(Level.OVERWORLD)) {
                            chunkPos = new ChunkPos(
                                this.mc.player.chunkPosition().x / 8 + chunkDelta.x,
                                this.mc.player.chunkPosition().z / 8 + chunkDelta.z
                            );
                            currentDimension = Level.NETHER;
                        } else if (currentDimension.equals(Level.NETHER)) {
                            chunkPos = new ChunkPos(
                                this.mc.player.chunkPosition().x * 8 + chunkDelta.x,
                                this.mc.player.chunkPosition().z * 8 + chunkDelta.z
                            );
                            currentDimension = Level.OVERWORLD;
                        }
                    }

                    if (this.isValidChunk(chunkPos, currentDimension)) {
                        this.seenChunksCache.put(chunkLong, (byte)127);
                        Vec3 pos = chunk.getPos().getMiddleBlockPosition(0).getCenter();
                        this.posDebug = pos;
                        if (!this.followingTrail) {
                            if (System.currentTimeMillis() - this.lastFoundPossibleTrailTime > this.chunkConsiderationWindow.get() * 1000) {
                                this.possibleTrail.clear();
                            }

                            if (this.hasInitialDirection) {
                                double chunkAngleToPlayer = Rotations.getYaw(pos);
                                double angleDiffFromInitial = Utils.angleDifference(this.initialYaw, chunkAngleToPlayer);
                                if (Math.abs(angleDiffFromInitial) > this.forwardConeAngle.get()) {
                                    return;
                                }
                            }

                            this.possibleTrail.add(pos);
                            this.lastFoundPossibleTrailTime = System.currentTimeMillis();
                            if (this.possibleTrail.size() > this.chunksBeforeStarting.get()) {
                                this.log("Trail found, starting to follow.");
                                this.followingTrail = true;
                                this.lastFoundTrailTime = System.currentTimeMillis();
                                this.lastSteerChunkTime = this.lastFoundTrailTime;
                                this.chunksFoundSinceStart = this.possibleTrail.size();
                                if (!this.hasInitialDirection) {
                                    this.initialYaw = this.targetYaw;
                                    this.hasInitialDirection = true;
                                }

                                this.committedYaw = this.wrapYaw(this.targetYaw);
                                this.desiredYaw = this.committedYaw;
                                this.wasSearching = false;
                                this.trail.addAll(this.possibleTrail);
                                this.possibleTrail.clear();
                            }
                        } else {
                            double chunkAngle = Rotations.getYaw(pos);
                            double angleDiff = Utils.angleDifference(this.committedYaw, chunkAngle);
                            if (!(Math.abs(angleDiff) > this.maxTrailDeviation.get())) {
                                this.lastFoundTrailTime = System.currentTimeMillis();
                                if (!this.reverseLock.get() || !(Math.abs(angleDiff) > this.reverseLockAngle.get())) {
                                    if (Math.abs(angleDiff) <= 90.0) {
                                        this.lastSteerChunkTime = System.currentTimeMillis();
                                    }

                                    while (this.trail.size() >= this.maxTrailLength.get()) {
                                        this.trail.pollFirst();
                                    }

                                    if (angleDiff > 0.0 && angleDiff < 90.0 && this.directionWeighting.get() == TrailFollower.DirectionWeighting.LEFT) {
                                        for (int i = 0; i < this.directionWeightingMultiplier.get() - 1; i++) {
                                            this.trail.pollFirst();
                                            this.trail.add(pos);
                                        }

                                        this.trail.add(pos);
                                    } else if (angleDiff < 0.0 && angleDiff > -90.0 && this.directionWeighting.get() == TrailFollower.DirectionWeighting.RIGHT) {
                                        for (int i = 0; i < this.directionWeightingMultiplier.get() - 1; i++) {
                                            this.trail.pollFirst();
                                            this.trail.add(pos);
                                        }

                                        this.trail.add(pos);
                                    } else {
                                        this.trail.add(pos);
                                    }

                                    this.chunksFoundSinceStart++;
                                    if (!this.trail.isEmpty()) {
                                        Vec3 averagePos = this.calculateForwardWeightedAverage(this.trail);
                                        if (averagePos != null) {
                                            Vec3 positionVec = averagePos.subtract(this.mc.player.position()).normalize();
                                            Vec3 targetPos = this.mc.player.position().add(positionVec.scale(10.0));
                                            double calculatedYaw = Rotations.getYaw(targetPos);
                                            double decayedWeight = this.getDecayedInitialWeight();
                                            if (decayedWeight > 0.01) {
                                                this.desiredYaw = this.blendYaw(calculatedYaw, this.initialYaw, decayedWeight);
                                            } else {
                                                this.desiredYaw = this.wrapYaw(calculatedYaw);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isValidChunk(ChunkPos chunkPos, ResourceKey<Level> currentDimension) {
        PaletteNewChunks paletteNewChunks = ModuleManager.getModule(PaletteNewChunks.class);
        boolean is119NewChunk = paletteNewChunks.isNewChunk(chunkPos.x, chunkPos.z, currentDimension);
        boolean is112OldChunk = ModuleManager.getModule(OldChunks.class).isOldChunk(chunkPos.x, chunkPos.z, currentDimension);
        boolean isHighlighted = is119NewChunk || paletteNewChunks.isInverseNewChunk(chunkPos.x, chunkPos.z, currentDimension);
        return isHighlighted && (!is119NewChunk && !this.only112.get() || is112OldChunk);
    }

    private Vec3 calculateForwardWeightedAverage(ArrayDeque<Vec3> positions) {
        if (positions.isEmpty()) {
            return null;
        }

        double sumX = 0.0;
        double sumZ = 0.0;
        double totalWeight = 0.0;
        Vec3 playerPos = this.mc.player.position();

        for (Vec3 pos : positions) {
            double chunkYaw = Rotations.getYaw(pos);
            double angleDiff = Math.abs(Utils.angleDifference(this.committedYaw, chunkYaw));
            if (!(angleDiff > 90.0)) {
                double finalWeight;
                if (this.forwardWeightStrength.get() <= 0.01) {
                    finalWeight = 1.0;
                } else {
                    double alignmentFactor = 1.0 - angleDiff / 180.0;
                    double weight = 1.0 - this.forwardWeightStrength.get() + this.forwardWeightStrength.get() * alignmentFactor;
                    double dist = playerPos.distanceTo(pos);
                    double distWeight = 1.0 / (1.0 + dist / 256.0);
                    finalWeight = weight * (0.5 + 0.5 * distWeight);
                }

                sumX += pos.x * finalWeight;
                sumZ += pos.z * finalWeight;
                totalWeight += finalWeight;
            }
        }

        return totalWeight <= 0.0 ? null : new Vec3(sumX / totalWeight, 0.0, sumZ / totalWeight);
    }

    private double getDecayedInitialWeight() {
        if (this.hasInitialDirection && !(this.startDirectionWeighting.get() <= 0.0)) {
            int decayChunks = this.maxTrailLength.get() * 2;
            double decayFactor = Math.max(0.0, 1.0 - (double)this.chunksFoundSinceStart / decayChunks);
            return this.startDirectionWeighting.get() * decayFactor;
        } else {
            return 0.0;
        }
    }

    private float getActualYaw(float yaw) {
        return (yaw % 360.0F + 360.0F) % 360.0F;
    }

    private double wrapYaw(double yaw) {
        return (yaw % 360.0 + 360.0) % 360.0;
    }

    private double approachYaw(double current, double target, double maxStep) {
        double diff = Utils.angleDifference(target, current);
        return Math.abs(diff) <= maxStep ? this.wrapYaw(target) : this.wrapYaw(current + Math.copySign(maxStep, diff));
    }

    private double blendYaw(double yaw1, double yaw2, double weight) {
        yaw1 = (yaw1 % 360.0 + 360.0) % 360.0;
        yaw2 = (yaw2 % 360.0 + 360.0) % 360.0;
        double diff = yaw2 - yaw1;
        if (diff > 180.0) {
            diff -= 360.0;
        }

        if (diff < -180.0) {
            diff += 360.0;
        }

        double result = yaw1 + diff * weight;
        return (result % 360.0 + 360.0) % 360.0;
    }

    private void log(String message) {
        this.info(message);
        if (!this.webhookLink.get().isEmpty()) {
            Utils.sendWebhook(this.webhookLink.get(), "TrailFollower", message, null, this.mc.player.getGameProfile().name());
        }
    }

    public enum DirectionWeighting {
        LEFT,
        NONE,
        RIGHT;
    }

    public enum FollowMode {
        AUTO,
        BARITONE,
        YAWLOCK;
    }

    public enum NetherPathMode {
        AVERAGE,
        OTHER;
    }

    public enum OverworldFlightMode {
        ROCKETS,
        PITCH40,
        OTHER;
    }

    public enum SearchBehavior {
        SWEEP,
        CIRCLE,
        STRAIGHT;
    }

    public enum TrailEndBehavior {
        DISABLE,
        FLY_TOWARDS_YAW,
        DISCONNECT;
    }
}
