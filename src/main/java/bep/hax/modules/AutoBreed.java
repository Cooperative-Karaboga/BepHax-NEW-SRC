package bep.hax.modules;

import bep.hax.managers.SwapManager;
import bep.hax.mixin.accessor.AllayAccessor;
import bep.hax.util.RotationUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Donkey;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.Ocelot;
import net.minecraft.world.entity.animal.nautilus.AbstractNautilus;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public class AutoBreed extends Module {
    private static final int LOVE_TICKS = 600;
    private static final int PARENT_COOLDOWN_TICKS = 6000;
    private static final double MATE_RANGE = 8.0;
    private static final double BABY_SCAN_RANGE = 16.0;
    private static final byte EVENT_IN_LOVE = 18;
    private static final int SWAP_RELEASE_TICKS = 3;
    private static final int ENGAGE_TIMEOUT = 40;
    private static final int NOTIFY_INTERVAL = 200;
    private static final Set<EntityType<?>> BREEDABLE = Set.of(
        EntityType.COW,
        EntityType.MOOSHROOM,
        EntityType.SHEEP,
        EntityType.PIG,
        EntityType.CHICKEN,
        EntityType.RABBIT,
        EntityType.TURTLE,
        EntityType.HORSE,
        EntityType.DONKEY,
        EntityType.LLAMA,
        EntityType.TRADER_LLAMA,
        EntityType.WOLF,
        EntityType.CAT,
        EntityType.OCELOT,
        EntityType.PANDA,
        EntityType.FOX,
        EntityType.BEE,
        EntityType.GOAT,
        EntityType.AXOLOTL,
        EntityType.STRIDER,
        EntityType.CAMEL,
        EntityType.SNIFFER,
        EntityType.FROG,
        EntityType.HOGLIN,
        EntityType.ARMADILLO,
        EntityType.NAUTILUS,
        EntityType.ALLAY
    );
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgMobs = this.settings.createGroup("Mobs");
    private final SettingGroup sgRotation = this.settings.createGroup("Rotation");
    private final SettingGroup sgTiming = this.settings.createGroup("Timing");
    private final Setting<Set<EntityType<?>>> entities = this.sgMobs
        .add(
            new Builder()
                .name("entities")
                .description("Which mobs to breed. Only mobs that can actually be bred are listed.")
                .defaultValue(BREEDABLE.toArray(new EntityType[0]))
                .filter(BREEDABLE::contains)
                .build()
        );
    private final Setting<Double> range = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("range")
                .description("Distance from your eyes to the mob's hitbox. The server rejects entity interactions past 3 blocks.")
                .defaultValue(3.0)
                .min(1.0)
                .max(3.0)
                .sliderRange(1.0, 3.0)
                .build()
        );
    private final Setting<Boolean> requireMate = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("require-mate")
                .description(
                    "Only feed a mob that has a valid partner within 8 blocks of itself - the range its breeding goal searches. Allays duplicate alone and ignore this."
                )
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> finishPairs = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("finish-pairs")
                .description("Feed the partner of an already loving mob first, so both love windows overlap instead of expiring one after the other.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> lineOfSight = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("line-of-sight")
                .description("Skip mobs with a block between you and the point you would click.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> feedBabies = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("feed-babies")
                .description("Also feed babies to speed up their growth. Costs one item per feed and never breeds anything.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> pandaBamboo = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("panda-bamboo")
                .description(
                    "Only feed pandas that have bamboo within 8 blocks - their breeding goal refuses to run without it and they just eat the bamboo instead."
                )
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> notify = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("notify")
                .description("Warn in chat when a mob is skipped for a reason you can fix (scared armadillo, panda without bamboo).")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> debug = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("debug")
                .description("Log every target, feed and refusal.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> rotate = this.sgRotation
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("rotate")
                .description(
                    "Aim at the mob with the shared server-side rotation before clicking. With this off, only mobs your crosshair is already on get fed."
                )
                .defaultValue(true)
                .build()
        );
    private final Setting<Double> turnSpeed = this.sgRotation
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("turn-speed")
                .description("Degrees rotated per tick towards the mob.")
                .defaultValue(120.0)
                .min(10.0)
                .max(180.0)
                .sliderRange(10.0, 180.0)
                .visible(this.rotate::get)
                .build()
        );
    private final Setting<Integer> feedDelay = this.sgTiming
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("feed-delay")
                .description("Ticks between two feeds.")
                .defaultValue(6)
                .min(1)
                .max(60)
                .sliderRange(1, 20)
                .build()
        );
    private final Setting<Integer> confirmTicks = this.sgTiming
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("confirm-timeout")
                .description("Ticks to wait for the server's love hearts before treating a feed as refused. Raise it on a high ping server.")
                .defaultValue(12)
                .min(4)
                .max(60)
                .sliderRange(4, 30)
                .build()
        );
    private final Queue<ClientboundEntityEventPacket> loveEvents = new ConcurrentLinkedQueue<>();
    private final Map<UUID, AutoBreed.Tracked> tracked = new HashMap<>();
    private final Map<String, Long> notified = new HashMap<>();
    private final Map<UUID, AutoBreed.BambooCheck> bambooChecks = new HashMap<>();
    private Set<Integer> knownBabies = new HashSet<>();
    private long tick;
    private AutoBreed.State state = AutoBreed.State.IDLE;
    private Mob target;
    private int delay;
    private int settleTicks;
    private int engageTicks;
    private int confirmWaited;
    private int feedSlot = -1;
    private Item feedItem;
    private int feedCount;

    public AutoBreed() {
        super(Categories.World, "auto-breed", "Feeds every breedable mob in reach the moment it can actually breed.");
    }

    @Override
    public void onActivate() {
        this.reset();
        this.tracked.clear();
        this.notified.clear();
        this.bambooChecks.clear();
        this.loveEvents.clear();
        this.knownBabies = this.currentBabies();
    }

    @Override
    public void onDeactivate() {
        this.reset();
        SwapManager.getInstance().releaseNow(this);
        RotationUtils.getInstance().release(this);
    }

    private void reset() {
        this.state = AutoBreed.State.IDLE;
        this.target = null;
        this.delay = 0;
        this.settleTicks = 0;
        this.engageTicks = 0;
        this.confirmWaited = 0;
        this.feedSlot = -1;
        this.feedItem = null;
        this.feedCount = 0;
    }

    @EventHandler
    private void onPacketReceive(Receive event) {
        if (event.packet instanceof ClientboundEntityEventPacket packet && packet.getEventId() == 18) {
            this.loveEvents.add(packet);
        }
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.level != null) {
            this.tick++;
            this.drainLoveEvents();
            this.trackBabies();
            this.prune();
            switch (this.state) {
                case IDLE:
                    this.tickIdle();
                    break;
                case ENGAGE:
                    this.tickEngage();
                    break;
                case CONFIRM:
                    this.tickConfirm();
            }
        } else {
            this.loveEvents.clear();
            this.reset();
        }
    }

    private void tickIdle() {
        if (this.delay > 0) {
            this.delay--;
        } else {
            Mob picked = this.pickTarget();
            if (picked == null) {
                RotationUtils.getInstance().release(this);
            } else {
                this.target = picked;
                this.state = AutoBreed.State.ENGAGE;
                this.settleTicks = 0;
                this.engageTicks = 0;
                if (this.debug.get()) {
                    this.info("Targeting %s.", this.name(picked));
                }
            }
        }
    }

    private void tickEngage() {
        Mob mob = this.target;
        if (mob != null && this.isViable(mob)) {
            if (++this.engageTicks > 40) {
                this.skip(mob, 40);
                this.abort();
            } else {
                int slot = this.foodSlot(mob);
                if (slot == -1) {
                    this.skip(mob, 100);
                    this.abort();
                } else if (SwapManager.getInstance().hold(this, slot, 2, 3)) {
                    if (this.canRotate()) {
                        Vec3 aim = this.aimPoint(mob);
                        float[] rotation = RotationUtils.getRotationsTo(this.mc.player.getEyePosition(), aim);
                        if (!RotationUtils.getInstance().setRotationSilent(this, 2, rotation[0], rotation[1], this.turnSpeed.get())) {
                            return;
                        }

                        if (!RotationUtils.getInstance().isAlignedFor(this)) {
                            this.settleTicks = 0;
                            return;
                        }

                        if (this.settleTicks++ < 1) {
                            return;
                        }
                    }

                    Vec3 hit = this.aimHit(mob);
                    if (hit == null) {
                        if (!this.canRotate()) {
                            this.skip(mob, 40);
                            this.abort();
                        }
                    } else {
                        ItemStack food = this.mc.player.getInventory().getItem(slot);
                        this.feedSlot = slot;
                        this.feedItem = food.getItem();
                        this.feedCount = food.getCount();
                        if (this.interact(mob, hit)) {
                            this.state = AutoBreed.State.CONFIRM;
                            this.confirmWaited = 0;
                            this.delay = this.feedDelay.get();
                            if (this.debug.get()) {
                                this.info("Fed %s with %s.", this.name(mob), food.getHoverName().getString());
                            }
                        }
                    }
                }
            }
        } else {
            this.abort();
        }
    }

    private void tickConfirm() {
        Mob mob = this.target;
        if (mob != null && mob.isAlive()) {
            if (mob.isBaby()) {
                ItemStack now = this.mc.player.getInventory().getItem(this.feedSlot);
                if (now.getItem() != this.feedItem || now.getCount() < this.feedCount) {
                    this.succeed(mob);
                    return;
                }
            } else {
                AutoBreed.Tracked entry = this.tracked.get(mob.getUUID());
                if (entry != null && entry.loveUntil > this.tick) {
                    this.succeed(mob);
                    return;
                }
            }

            if (++this.confirmWaited >= this.confirmTicks.get()) {
                AutoBreed.Tracked entry = this.entry(mob.getUUID());
                entry.failures++;

                entry.readyAt = this.tick + switch (entry.failures) {
                    case 1 -> 100L;
                    case 2 -> 600L;
                    default -> 6000L;
                };
                if (this.debug.get()) {
                    this.info("%s refused the food (attempt %d).", this.name(mob), entry.failures);
                }

                this.endCycle();
            }
        } else {
            this.abort();
        }
    }

    private void succeed(Mob mob) {
        this.entry(mob.getUUID()).failures = 0;
        this.endCycle();
    }

    private void abort() {
        this.delay = Math.max(this.delay, this.feedDelay.get());
        this.endCycle();
    }

    private void endCycle() {
        this.target = null;
        this.state = AutoBreed.State.IDLE;
        this.settleTicks = 0;
        this.engageTicks = 0;
        this.confirmWaited = 0;
        this.feedSlot = -1;
        this.feedItem = null;
        RotationUtils.getInstance().release(this);
    }

    private boolean canRotate() {
        return this.rotate.get() && !this.mc.player.isFallFlying() && !this.mc.player.isSwimming() && !this.mc.player.isPassenger();
    }

    private boolean interact(Mob mob, Vec3 hit) {
        if (this.mc.getConnection() == null) {
            return false;
        }

        Vec3 location = hit.subtract(mob.getX(), mob.getY(), mob.getZ());
        boolean sneaking = this.mc.player.isShiftKeyDown();
        this.mc.getConnection().send(ServerboundInteractPacket.createInteractionPacket(mob, sneaking, InteractionHand.MAIN_HAND, location));
        this.mc.getConnection().send(ServerboundInteractPacket.createInteractionPacket(mob, sneaking, InteractionHand.MAIN_HAND));
        return true;
    }

    private Vec3 aimPoint(Mob mob) {
        Vec3 eye = this.mc.player.getEyePosition();
        AABB box = mob.getBoundingBox();
        if (box.contains(eye)) {
            return box.getCenter();
        }

        AABB inner = box.deflate(
            Math.min(0.25, box.getXsize() * 0.3), Math.min(0.25, box.getYsize() * 0.3), Math.min(0.25, box.getZsize() * 0.3)
        );
        Vec3 centre = box.getCenter();
        Vec3 closest = new Vec3(
            Mth.clamp(eye.x, inner.minX, inner.maxX),
            Mth.clamp(eye.y, inner.minY, inner.maxY),
            Mth.clamp(eye.z, inner.minZ, inner.maxZ)
        );
        if (closest.distanceToSqr(eye) < 0.01) {
            return centre;
        }

        if (!this.lineOfSight.get()) {
            return closest;
        }

        Vec3 head = new Vec3(centre.x, inner.maxY, centre.z);

        for (Vec3 candidate : new Vec3[]{closest, head, centre}) {
            if (!this.blocked(eye, candidate)) {
                return candidate;
            }
        }

        return closest;
    }

    private Vec3 aimHit(Mob mob) {
        RotationUtils rotations = RotationUtils.getInstance();
        Vec3 eye = this.mc.player.getEyePosition();
        double reach = Math.min(this.range.get(), this.mc.player.entityInteractionRange());
        AABB box = mob.getBoundingBox();
        if (box.contains(eye)) {
            return eye;
        }

        Vec3 hit = this.clip(box, eye, reach, rotations.getServerYaw(), rotations.getServerPitch());
        if (hit == null && rotations.isRotating()) {
            hit = this.clip(box, eye, reach, rotations.getSentYaw(), rotations.getSentPitch());
        }

        return hit != null && (!this.lineOfSight.get() || !this.blocked(eye, hit)) ? hit : null;
    }

    private Vec3 clip(AABB box, Vec3 eye, double reach, float yaw, float pitch) {
        Vec3 end = eye.add(RotationUtils.getRotationVector(pitch, yaw).scale(reach));
        Optional<Vec3> hit = box.clip(eye, end);
        return hit.orElse(null);
    }

    private boolean blocked(Vec3 eye, Vec3 hit) {
        return this.mc.level.clip(new ClipContext(eye, hit, Block.COLLIDER, Fluid.NONE, this.mc.player)).getType()
            != Type.MISS;
    }

    private Mob pickTarget() {
        Mob best = null;
        int bestScore = Integer.MIN_VALUE;
        double bestDistance = Double.MAX_VALUE;
        Vec3 eye = this.mc.player.getEyePosition();
        double reach = Math.min(this.range.get(), this.mc.player.entityInteractionRange());

        for (Entity entity : this.mc
            .level
            .getEntities(
                this.mc.player,
                this.mc.player.getBoundingBox().inflate(reach + 1.0),
                e -> e instanceof Mob mobx && this.isViable(mobx) && this.foodSlot(mobx) != -1
            )) {
            Mob mob = (Mob)entity;
            int score = this.score(mob);
            if (score >= 0) {
                double distance = mob.getBoundingBox().distanceToSqr(eye);
                if (score > bestScore || score == bestScore && distance < bestDistance) {
                    best = mob;
                    bestScore = score;
                    bestDistance = distance;
                }
            }
        }

        return best;
    }

    private int score(Mob mob) {
        if (mob.isBaby()) {
            return 0;
        }

        if (mob instanceof Allay) {
            return 3;
        }

        boolean partnerInLove = false;
        boolean partnerReady = false;

        for (Mob other : this.mates(mob)) {
            AutoBreed.Tracked entry = this.tracked.get(other.getUUID());
            if (entry != null && entry.loveUntil > this.tick) {
                partnerInLove = true;
                break;
            }

            if (!partnerReady && this.canBreedNow(other) && this.foodSlot(other) != -1) {
                partnerReady = true;
            }
        }

        if (partnerInLove) {
            return this.finishPairs.get() ? 4 : 2;
        } else if (partnerReady) {
            return 2;
        } else {
            return this.requireMate.get() ? -1 : 1;
        }
    }

    private List<Mob> mates(Mob mob) {
        List<Mob> result = new ArrayList<>();
        Class<?> group = this.mateGroup(mob);

        for (Entity entity : this.mc
            .level
            .getEntities(
                mob,
                mob.getBoundingBox().inflate(8.0),
                e -> {
                    if (!(e instanceof Mob other && !other.isBaby() && other.isAlive())) {
                        return false;
                    } else {
                        return this.mateGroup(other) == group && !(other.distanceToSqr(mob) > 64.0)
                            ? !(other instanceof TamableAnimal tameable && (!tameable.isTame() || tameable.isInSittingPose()))
                            : false;
                    }
                }
            )) {
            result.add((Mob)entity);
        }

        return result;
    }

    private Class<?> mateGroup(Mob mob) {
        if (mob instanceof Horse || mob instanceof Donkey) {
            return AbstractHorse.class;
        } else {
            return mob instanceof Llama ? Llama.class : mob.getClass();
        }
    }

    private boolean isViable(Mob mob) {
        if (mob.isAlive() && mob != this.mc.player.getVehicle()) {
            if (!this.entities.get().contains(mob.getType())) {
                return false;
            }

            Vec3 eye = this.mc.player.getEyePosition();
            double reach = Math.min(this.range.get(), this.mc.player.entityInteractionRange());
            if (mob.getBoundingBox().distanceToSqr(eye) > reach * reach) {
                return false;
            }

            AutoBreed.Tracked entry = this.tracked.get(mob.getUUID());
            return entry == null || entry.loveUntil <= this.tick && entry.readyAt <= this.tick ? this.canBreedNow(mob) : false;
        } else {
            return false;
        }
    }

    private boolean canBreedNow(Mob mob) {
        if (mob instanceof Allay allay) {
            return allay.isDancing() && ((AllayAccessor)allay).invokeCanDuplicate();
        } else if (!(mob instanceof Animal animal)) {
            return false;
        } else {
            if (animal.isBaby()) {
                return this.feedBabies.get();
            }

            if (!animal.canFallInLove()) {
                if (animal instanceof Armadillo armadillo && armadillo.isScaredBy(this.mc.player)) {
                    this.warn("armadillo", "An armadillo rolled up because you are sprinting or riding - it will not eat until it unrolls.");
                }

                return false;
            } else if (animal instanceof TamableAnimal tameable && (!tameable.isTame() || tameable.isInSittingPose())) {
                return false;
            } else if (animal instanceof Ocelot ocelot && !ocelot.isTrusting()) {
                return false;
            } else {
                if ((animal instanceof Wolf || animal instanceof Cat || animal instanceof AbstractNautilus)
                    && animal.getHealth() < animal.getMaxHealth()) {
                    return false;
                }

                if (!(
                    animal instanceof AbstractHorse horse
                        && (!horse.isTamed() || horse.isVehicle() || horse.isPassenger() || horse.getHealth() < horse.getMaxHealth())
                )) {
                    if (animal instanceof Sniffer sniffer) {
                        net.minecraft.world.entity.animal.sniffer.Sniffer.State sniffing = sniffer.getState();
                        if (sniffing != net.minecraft.world.entity.animal.sniffer.Sniffer.State.IDLING && sniffing != net.minecraft.world.entity.animal.sniffer.Sniffer.State.SCENTING && sniffing != net.minecraft.world.entity.animal.sniffer.Sniffer.State.FEELING_HAPPY) {
                            return false;
                        }
                    }

                    if (animal instanceof Panda panda) {
                        if (panda.isSitting() || panda.isInWater() || panda.getUnhappyCounter() > 0) {
                            return false;
                        }

                        if (this.pandaBamboo.get() && !this.bambooNear(panda)) {
                            this.warn("panda", "No bamboo within 8 blocks of a panda - it eats the bamboo instead of breeding.");
                            return false;
                        }
                    }

                    return !this.mc.player.isShiftKeyDown() || !(animal instanceof AbstractHorse) && !(animal instanceof AbstractNautilus);
                } else {
                    return false;
                }
            }
        }
    }

    private boolean bambooNear(Panda panda) {
        BlockPos origin = panda.blockPosition();
        AutoBreed.BambooCheck cached = this.bambooChecks.get(panda.getUUID());
        if (cached != null && cached.expiresAt() > this.tick && cached.pos().equals(origin)) {
            return cached.found();
        }

        boolean found = false;
        MutableBlockPos pos = new MutableBlockPos();

        label42:
        for (int y = 0; y < 3 && !found; y++) {
            for (int x = -7; x <= 7; x++) {
                for (int z = -7; z <= 7; z++) {
                    pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (this.mc.level.getBlockState(pos).is(Blocks.BAMBOO)) {
                        found = true;
                        break label42;
                    }
                }
            }
        }

        this.bambooChecks.put(panda.getUUID(), new AutoBreed.BambooCheck(origin, this.tick + 40L, found));
        return found;
    }

    private int foodSlot(Mob mob) {
        for (int slot = 0; slot < 9; slot++) {
            if (this.isBreedingItem(mob, this.mc.player.getInventory().getItem(slot))) {
                return slot;
            }
        }

        return -1;
    }

    private boolean isBreedingItem(Mob mob, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        } else if (mob instanceof Allay) {
            return stack.is(ItemTags.DUPLICATES_ALLAYS);
        } else if (mob instanceof Llama) {
            return stack.is(Items.HAY_BLOCK);
        } else if (mob instanceof Camel) {
            return stack.is(Items.CACTUS);
        } else if (!(mob instanceof AbstractHorse)) {
            return !(mob instanceof Animal animal && animal.isFood(stack))
                ? false
                : !(animal instanceof Bee)
                    || !(stack.getItem() instanceof BlockItem item && item.getBlock() instanceof FlowerBlock flower && flower.getBeeInteractionEffect() != null);
        } else {
            return stack.is(Items.GOLDEN_CARROT) || stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE);
        }
    }

    private void drainLoveEvents() {
        ClientboundEntityEventPacket packet;
        while ((packet = this.loveEvents.poll()) != null) {
            if (packet.getEntity(this.mc.level) instanceof Mob mob) {
                AutoBreed.Tracked entry = this.entry(mob.getUUID());
                entry.loveUntil = this.tick + 600L;
                entry.failures = 0;
                if (this.debug.get()) {
                    this.info("%s is in love.", this.name(mob));
                }
            }
        }
    }

    private void trackBabies() {
        Set<Integer> babies = new HashSet<>();
        List<Mob> fresh = new ArrayList<>();

        for (Entity entity : this.nearbyBabies()) {
            Mob mob = (Mob)entity;
            babies.add(mob.getId());
            if (!this.knownBabies.contains(mob.getId())) {
                fresh.add(mob);
            }
        }

        this.knownBabies = babies;

        for (Mob baby : fresh) {
            Class<?> group = this.mateGroup(baby);
            List<Mob> parents = new ArrayList<>();

            for (Entity entity : this.mc.level.getEntities(baby, baby.getBoundingBox().inflate(8.0), e -> {
                if (!(e instanceof Mob adult && !adult.isBaby() && this.mateGroup(adult) == group)) {
                    return false;
                } else {
                    if (adult.distanceToSqr(baby) > 64.0) {
                        return false;
                    }

                    AutoBreed.Tracked entryx = this.tracked.get(adult.getUUID());
                    return entryx != null && entryx.loveUntil > this.tick;
                }
            })) {
                parents.add((Mob)entity);
            }

            parents.sort((a, b) -> Double.compare(a.distanceToSqr(baby), b.distanceToSqr(baby)));

            for (int i = 0; i < Math.min(2, parents.size()); i++) {
                AutoBreed.Tracked entry = this.entry(parents.get(i).getUUID());
                entry.loveUntil = 0L;
                entry.readyAt = this.tick + 6000L;
                if (this.debug.get()) {
                    this.info("%s bred - parked for 5 minutes.", this.name(parents.get(i)));
                }
            }
        }
    }

    private void skip(Mob mob, int ticks) {
        AutoBreed.Tracked entry = this.entry(mob.getUUID());
        entry.readyAt = Math.max(entry.readyAt, this.tick + ticks);
    }

    private AutoBreed.Tracked entry(UUID id) {
        return this.tracked.computeIfAbsent(id, key -> new AutoBreed.Tracked());
    }

    private void prune() {
        this.tracked
            .values()
            .removeIf(entry -> entry.loveUntil <= this.tick && entry.readyAt <= this.tick && (entry.failures == 0 || entry.readyAt + 6000L <= this.tick));
        this.bambooChecks.values().removeIf(check -> check.expiresAt() <= this.tick);
    }

    private List<Entity> nearbyBabies() {
        return this.mc.level != null && this.mc.player != null
            ? this.mc
                .level
                .getEntities(this.mc.player, this.mc.player.getBoundingBox().inflate(16.0), e -> e instanceof Mob mob && mob.isBaby())
            : List.of();
    }

    private Set<Integer> currentBabies() {
        Set<Integer> babies = new HashSet<>();

        for (Entity entity : this.nearbyBabies()) {
            babies.add(entity.getId());
        }

        return babies;
    }

    private void warn(String key, String message) {
        if (this.notify.get()) {
            Long last = this.notified.get(key);
            if (last == null || this.tick - last >= 200L) {
                this.notified.put(key, this.tick);
                this.warning(message);
            }
        }
    }

    private String name(Mob mob) {
        return mob.getType().getDescription().getString();
    }

    @Override
    public String getInfoString() {
        int loving = 0;

        for (AutoBreed.Tracked entry : this.tracked.values()) {
            if (entry.loveUntil > this.tick) {
                loving++;
            }
        }

        return String.valueOf(loving);
    }

    private record BambooCheck(BlockPos pos, long expiresAt, boolean found) {
    }

    private enum State {
        IDLE,
        ENGAGE,
        CONFIRM;
    }

    private static final class Tracked {
        long loveUntil;
        long readyAt;
        int failures;
    }
}
