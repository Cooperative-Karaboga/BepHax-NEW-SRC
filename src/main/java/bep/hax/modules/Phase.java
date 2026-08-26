package bep.hax.modules;

import bep.hax.Bep;
import bep.hax.managers.SwapManager;
import bep.hax.mixin.accessor.EntityVelocityUpdateS2CPacketAccessor;
import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import bep.hax.util.PlacementUtils;
import bep.hax.util.PushOutOfBlocksEvent;
import bep.hax.util.RotationUtils;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.CollisionShapeEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

public class Phase extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgPearl = this.settings.createGroup("Pearl");
    private final SettingGroup sgClipping = this.settings.createGroup("Clipping");
    private final Setting<Phase.PhaseMode> mode = this.sgGeneral
        .add(
            ((Builder)((Builder)((Builder)new Builder().name("mode")).description("The phase mode for clipping into blocks."))
                    .defaultValue(Phase.PhaseMode.Pearl))
                .build()
        );
    private final Setting<Integer> pitch = this.sgPearl
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("pitch")
                .description("The pitch angle to throw pearls.")
                .defaultValue(83)
                .range(70, 90)
                .visible(() -> this.mode.get() == Phase.PhaseMode.Pearl)
                .build()
        );
    private final Setting<Boolean> swapAlternative = this.sgPearl
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("swap-alternative")
                .description("Uses inventory swap for swapping to pearls.")
                .defaultValue(true)
                .visible(() -> this.mode.get() == Phase.PhaseMode.Pearl)
                .build()
        );
    private final Setting<Boolean> attack = this.sgPearl
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("attack")
                .description("Attacks entities in the way of the pearl phase.")
                .defaultValue(false)
                .visible(() -> this.mode.get() == Phase.PhaseMode.Pearl)
                .build()
        );
    private final Setting<Boolean> swing = this.sgPearl
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("swing")
                .description("Swings the hand when throwing pearls.")
                .defaultValue(true)
                .visible(() -> this.mode.get() == Phase.PhaseMode.Pearl)
                .build()
        );
    private final Setting<Boolean> instantRotation = this.sgPearl
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("instant-rotation")
                .description(
                    "Throws on the activation tick with a single flick instead of turning into the throw over a few ticks. Faster, but the flick is one unquantized rotation - smooth is the safer one against Grim's aim checks."
                )
                .defaultValue(false)
                .visible(() -> this.mode.get() == Phase.PhaseMode.Pearl)
                .build()
        );
    private final Setting<Boolean> selfFill = this.sgPearl
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("self-fill")
                .description("Automatically fills blocks you are phasing on.")
                .defaultValue(false)
                .visible(() -> this.mode.get() == Phase.PhaseMode.Pearl)
                .build()
        );
    private final Setting<Double> blocks = this.sgClipping
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("blocks")
                .description("The block distance to phase clip.")
                .defaultValue(0.003)
                .range(0.001, 10.0)
                .sliderMax(1.0)
                .visible(() -> this.mode.get() != Phase.PhaseMode.Pearl && this.mode.get() != Phase.PhaseMode.Clip)
                .build()
        );
    private final Setting<Double> distance = this.sgClipping
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("distance")
                .description("The distance to phase.")
                .defaultValue(0.2)
                .range(0.0, 10.0)
                .sliderMax(1.0)
                .visible(() -> this.mode.get() != Phase.PhaseMode.Pearl && this.mode.get() != Phase.PhaseMode.Clip)
                .build()
        );
    private final Setting<Boolean> autoClip = this.sgClipping
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("auto-clip")
                .description("Automatically clips into the block.")
                .defaultValue(true)
                .visible(() -> this.mode.get() != Phase.PhaseMode.Pearl && this.mode.get() != Phase.PhaseMode.Clip)
                .build()
        );
    private static final int PEARL_ALIGN_TIMEOUT = 8;
    private static final int PEARL_RELEASE_TICKS = 4;
    private static final double PEARL_TURN_SPEED = 360.0;
    private static final double PEARL_ALIGN_EPS = 2.0;
    private int pearlTicks = -1;
    private int pearlSlot = -1;
    private float pearlYaw;

    public Phase() {
        super(Bep.CATEGORY, "phase", "Allows player to phase through solid blocks.");
    }

    @Override
    public void onActivate() {
        if (this.mc.player != null && this.mc.level != null) {
            if (this.mode.get() == Phase.PhaseMode.Pearl) {
                if (!this.beginPearlPhase()) {
                    this.toggle();
                }
            } else if (this.mode.get() == Phase.PhaseMode.Clip) {
                this.performClipPhase();
                this.toggle();
            } else {
                if (this.autoClip.get() && this.mode.get() == Phase.PhaseMode.Normal) {
                    this.performAutoClip();
                }
            }
        } else {
            this.toggle();
        }
    }

    @Override
    public void onDeactivate() {
        if (this.pearlTicks >= 0) {
            this.endPearlPhase();
        }

        if (this.mc.player != null) {
            this.mc.player.noPhysics = false;
        }
    }

    @EventHandler(priority = 100)
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.level != null) {
            if (this.pearlTicks >= 0) {
                this.tickPearlPhase();
            } else {
                if (this.mode.get() == Phase.PhaseMode.Clip && this.mc.player.onGround() && !this.mc.player.isPassenger()) {
                    this.performClipTick();
                    this.toggle();
                }
            }
        }
    }

    @EventHandler
    private void onPlayerMove(PlayerMoveEvent event) {
        if (this.mc.player != null && this.mc.level != null) {
            switch ((Phase.PhaseMode)this.mode.get()) {
                case Normal:
                    this.handleNormalMovement(event);
                    break;
                case Sand:
                    this.handleSandMovement(event);
                    break;
                case Climb:
                    this.handleClimbMovement(event);
            }
        }
    }

    @EventHandler
    private void onPacketReceive(Receive event) {
        if (event.packet instanceof ClientboundSetEntityMotionPacket packet) {
            EntityVelocityUpdateS2CPacketAccessor accessor = (EntityVelocityUpdateS2CPacketAccessor)packet;
            if (accessor.getEntityId() == this.mc.player.getId() && this.isActive()) {
                Vec3 velocity = accessor.getVelocity();
                if (velocity.lengthSqr() < 0.1) {
                    event.cancel();
                }
            }
        }
    }

    @EventHandler
    private void onCollisionShape(CollisionShapeEvent event) {
        if (this.mc.player != null && this.mc.level != null) {
            switch ((Phase.PhaseMode)this.mode.get()) {
                case Normal:
                    if (event.shape != Shapes.empty()
                        && event.shape.bounds().maxY > this.mc.player.getBoundingBox().minY
                        && this.mc.player.isShiftKeyDown()) {
                        event.cancel();
                        event.shape = Shapes.empty();
                    }
                    break;
                case Sand:
                    event.cancel();
                    event.shape = Shapes.empty();
                    this.mc.player.noPhysics = true;
                    break;
                case Climb:
                    if (this.mc.player.horizontalCollision) {
                        event.cancel();
                        event.shape = Shapes.empty();
                    }

                    if (this.mc.options.keyShift.isDown()
                        || this.mc.options.keyJump.isDown() && event.pos.getY() > this.mc.player.getY()) {
                        event.cancel();
                    }
            }
        }
    }

    @EventHandler
    private void onPushOutOfBlocks(PushOutOfBlocksEvent event) {
        if (this.isActive()) {
            event.cancel();
        }
    }

    private boolean beginPearlPhase() {
        int slot = PlacementUtils.getEnderPearlSlot();
        if (slot == -1 || this.mc.player.getCooldowns().isOnCooldown(Items.ENDER_PEARL.getDefaultInstance())) {
            return false;
        }

        if (!this.swapAlternative.get() && slot >= 9) {
            return false;
        }

        if (this.swapAlternative.get() && this.mc.player.containerMenu != this.mc.player.inventoryMenu) {
            this.error("Close the open container before phasing.");
            return false;
        }

        Vec3 pearlTargetVec = new Vec3(Math.floor(this.mc.player.getX()) + 0.5, 0.0, Math.floor(this.mc.player.getZ()) + 0.5);
        float[] rotations = RotationUtils.getRotationsTo(this.mc.player.getEyePosition(), pearlTargetVec);
        this.pearlYaw = rotations[0] + 180.0F;
        this.pearlSlot = slot;
        if (this.attack.get()) {
            this.handlePearlAttacks(this.pearlYaw);
        }

        if (this.selfFill.get()) {
            this.handleSelfFill(this.pearlYaw);
        }

        this.pearlTicks = 0;
        if (this.assertPearlAim() && this.instantRotation.get() && this.throwPearl()) {
            this.endPearlPhase();
            return false;
        } else {
            return true;
        }
    }

    private boolean assertPearlAim() {
        RotationUtils rot = RotationUtils.getInstance();
        return this.instantRotation.get()
            ? rot.setRotationSilentInstant(this, 40, this.pearlYaw, this.pitch.get().intValue())
            : rot.setRotationSilent(this, 40, this.pearlYaw, this.pitch.get().intValue(), 360.0);
    }

    private void tickPearlPhase() {
        RotationUtils rot = RotationUtils.getInstance();
        this.assertPearlAim();
        if (rot.isRotating() && rot.isAlignedFor(this, 2.0) && this.throwPearl()) {
            this.endPearlPhase();
            this.toggle();
        } else if (++this.pearlTicks > 8) {
            this.error("Could not line up the pearl throw - gliding, swimming or riding leaves no consistent rotation.");
            this.endPearlPhase();
            this.toggle();
        }
    }

    private boolean throwPearl() {
        int selected = ((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot();
        int holdSlot = this.swapAlternative.get() ? selected : this.pearlSlot;
        if (!SwapManager.getInstance().hold(this, holdSlot, 40, 4)) {
            return false;
        }

        if (this.swapAlternative.get()) {
            this.performInventorySwapPVP(this.pearlSlot);
        }

        RotationUtils rot = RotationUtils.getInstance();
        float yaw = rot.getSentYaw();
        float throwPitch = rot.getSentPitch();

        try (BlockStatePredictionHandler prediction = this.mc.level.getBlockStatePredictionHandler().startPredicting()) {
            this.mc.getConnection().send(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, prediction.currentSequence(), yaw, throwPitch));
        }

        if (this.swing.get()) {
            this.mc.player.swing(InteractionHand.MAIN_HAND);
        } else {
            this.mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        }

        if (this.swapAlternative.get()) {
            this.performInventorySwapPVP(this.pearlSlot);
        }

        return true;
    }

    private void endPearlPhase() {
        this.pearlTicks = -1;
        this.pearlSlot = -1;
        RotationUtils.getInstance().release(this);
    }

    private void handlePearlAttacks(float yaw) {
        BlockHitResult hitResult = (BlockHitResult)this.mc.player.pick(3.0, 0.0F, false);
        AABB searchBox = AABB.unitCubeFromLowerCorner(Vec3.atCenterOf(hitResult.getBlockPos())).inflate(0.2);

        for (Entity entity : this.mc.level.getEntities(null, searchBox)) {
            if (entity instanceof ItemFrame itemFrame) {
                this.mc.getConnection().send(ServerboundInteractPacket.createAttackPacket(entity, this.mc.player.isShiftKeyDown()));
                this.mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
            }
        }

        BlockState state = this.mc.level.getBlockState(this.mc.player.blockPosition());
        if (state.getBlock() instanceof ScaffoldingBlock) {
            BlockPos pos = this.mc.player.blockPosition();
            this.mc.getConnection().send(new ServerboundPlayerActionPacket(Action.START_DESTROY_BLOCK, pos, Direction.UP));
            this.mc.getConnection().send(new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, pos, Direction.UP));
        }
    }

    private void handleSelfFill(float yaw) {
        float yaw1 = yaw % 360.0F;
        if (yaw1 < 0.0F) {
            yaw1 += 360.0F;
        }

        BlockPos blockPos = this.mc.player.blockPosition();
        if (yaw1 >= 22.5 && yaw1 < 67.5) {
            blockPos = blockPos.south().west();
        } else if (yaw1 >= 67.5 && yaw1 < 112.5) {
            blockPos = blockPos.west();
        } else if (yaw1 >= 112.5 && yaw1 < 157.5) {
            blockPos = blockPos.north().west();
        } else if (yaw1 >= 157.5 && yaw1 < 202.5) {
            blockPos = blockPos.north();
        } else if (yaw1 >= 202.5 && yaw1 < 247.5) {
            blockPos = blockPos.north().east();
        } else if (yaw1 >= 247.5 && yaw1 < 292.5) {
            blockPos = blockPos.east();
        } else if (yaw1 >= 292.5 && yaw1 < 337.5) {
            blockPos = blockPos.south().east();
        } else {
            blockPos = blockPos.south();
        }

        FindItemResult resistantBlock = PlacementUtils.findResistantBlock();
        if (resistantBlock.found() && blockPos != null && !this.mc.level.getBlockState(blockPos.below()).canBeReplaced()) {
            RotationUtils rotationManager = RotationUtils.getInstance();
            PlacementUtils.placeBlock(blockPos, true, true, true);
        }
    }

    private void performInventorySwapPVP(int pearlSlot) {
        this.mc.gameMode.handleInventoryMouseClick(0, pearlSlot < 9 ? pearlSlot + 36 : pearlSlot, 0, ClickType.PICKUP, this.mc.player);
        this.mc
            .gameMode
            .handleInventoryMouseClick(0, ((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot() + 36, 0, ClickType.PICKUP, this.mc.player);
        this.mc.gameMode.handleInventoryMouseClick(0, pearlSlot < 9 ? pearlSlot + 36 : pearlSlot, 0, ClickType.PICKUP, this.mc.player);
    }

    private void performAutoClip() {
        if (this.mc.player != null && this.mc.level != null) {
            double cos = Math.cos(Math.toRadians(this.mc.player.getYRot() + 90.0F));
            double sin = Math.sin(Math.toRadians(this.mc.player.getYRot() + 90.0F));
            double newX = this.mc.player.getX() + this.blocks.get() * cos;
            double newZ = this.mc.player.getZ() + this.blocks.get() * sin;
            this.mc.player.setPos(newX, this.mc.player.getY(), newZ);
        }
    }

    private void performClipTick() {
        Vec3 center = this.mc.player.blockPosition().getCenter();
        boolean flagX = center.x - this.mc.player.getX() > 0.0;
        boolean flagZ = center.z - this.mc.player.getZ() > 0.0;
        double x = center.x + 0.2 * (flagX ? -1 : 1);
        double z = center.z + 0.2 * (flagZ ? -1 : 1);
        this.mc.player.setPos(x, this.mc.player.getY(), z);
    }

    private void performClipPhase() {
        this.performClipTick();
    }

    private void handleNormalMovement(PlayerMoveEvent event) {
        if (this.mc.player.isShiftKeyDown() && PlacementUtils.isPhasing()) {
            float yaw = this.mc.player.getYRot();
            double offsetX = this.distance.get() * Math.cos(Math.toRadians(yaw + 90.0F));
            double offsetZ = this.distance.get() * Math.sin(Math.toRadians(yaw + 90.0F));
            AABB newBB = this.mc.player.getBoundingBox().move(offsetX, 0.0, offsetZ);
            this.mc.player.setBoundingBox(newBB);
        }
    }

    private void handleSandMovement(PlayerMoveEvent event) {
        this.mc.player.noPhysics = true;
        double yMotion = 0.0;
        if (this.mc.options.keyJump.isDown()) {
            yMotion = 0.3;
        } else if (this.mc.options.keyShift.isDown()) {
            yMotion = -0.3;
        }

        event.movement = new Vec3(event.movement.x, yMotion, event.movement.z);
    }

    private void handleClimbMovement(PlayerMoveEvent event) {
        if (this.mc.player.horizontalCollision) {
            double yMotion = event.movement.y;
            if (this.mc.options.keyJump.isDown()) {
                yMotion = 0.3;
            } else if (this.mc.options.keyShift.isDown()) {
                yMotion = -0.3;
            }

            event.movement = new Vec3(event.movement.x, yMotion, event.movement.z);
        }
    }

    @Override
    public String getInfoString() {
        return this.mode.get().toString();
    }

    public enum PhaseMode {
        Normal("Normal"),
        Sand("Sand"),
        Climb("Climb"),
        Pearl("Pearl"),
        Clip("Clip");

        private final String title;

        PhaseMode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return this.title;
        }
    }
}
