package bep.hax.modules;

import bep.hax.Bep;
import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import java.util.Set;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class Stripper extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Integer> axeSlot = this.sgGeneral
        .add(new Builder().name("axe-slot").description("Hotbar slot for axe (1-9)").defaultValue(1).range(1, 9).sliderRange(1, 9).build());
    private final Setting<Integer> stripDelay = this.sgGeneral
        .add(new Builder().name("strip-delay").description("Ticks to wait before stripping").defaultValue(1).range(0, 40).sliderRange(0, 40).build());
    private final Setting<Integer> breakDelay = this.sgGeneral
        .add(new Builder().name("break-delay").description("Ticks to wait before breaking").defaultValue(1).range(0, 40).sliderRange(0, 40).build());
    private final Setting<Integer> placeDelay = this.sgGeneral
        .add(new Builder().name("place-delay").description("Ticks to wait before placing next log").defaultValue(1).range(0, 40).sliderRange(0, 40).build());
    private final Setting<Integer> rotationTime = this.sgGeneral
        .add(new Builder().name("rotation-time").description("Ticks to hold rotation before action").defaultValue(1).range(0, 20).sliderRange(0, 20).build());
    private final Setting<Boolean> autoMine = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("auto-mine")
                .description("Automatically mine the stripped log")
                .defaultValue(true)
                .build()
        );
    private Stripper.State state = Stripper.State.WAITING_FOR_FIRST_LOG;
    private BlockPos targetPos = null;
    private BlockPos workingPos = null;
    private int tickTimer = 0;
    private int rotationTimer = 0;
    private boolean firstLogDetected = false;
    private static final Set<Block> LOGS = Set.of(
        Blocks.OAK_LOG,
        Blocks.SPRUCE_LOG,
        Blocks.BIRCH_LOG,
        Blocks.JUNGLE_LOG,
        Blocks.ACACIA_LOG,
        Blocks.DARK_OAK_LOG,
        Blocks.MANGROVE_LOG,
        Blocks.CHERRY_LOG,
        Blocks.PALE_OAK_LOG,
        Blocks.BAMBOO_BLOCK,
        Blocks.CRIMSON_STEM,
        Blocks.WARPED_STEM,
        Blocks.OAK_WOOD,
        Blocks.SPRUCE_WOOD,
        Blocks.BIRCH_WOOD,
        Blocks.JUNGLE_WOOD,
        Blocks.ACACIA_WOOD,
        Blocks.DARK_OAK_WOOD,
        Blocks.MANGROVE_WOOD,
        Blocks.CHERRY_WOOD,
        Blocks.PALE_OAK_WOOD,
        Blocks.CRIMSON_HYPHAE,
        Blocks.WARPED_HYPHAE
    );
    private static final Set<Block> STRIPPED_LOGS = Set.of(
        Blocks.STRIPPED_OAK_LOG,
        Blocks.STRIPPED_SPRUCE_LOG,
        Blocks.STRIPPED_BIRCH_LOG,
        Blocks.STRIPPED_JUNGLE_LOG,
        Blocks.STRIPPED_ACACIA_LOG,
        Blocks.STRIPPED_DARK_OAK_LOG,
        Blocks.STRIPPED_MANGROVE_LOG,
        Blocks.STRIPPED_CHERRY_LOG,
        Blocks.STRIPPED_PALE_OAK_LOG,
        Blocks.STRIPPED_BAMBOO_BLOCK,
        Blocks.STRIPPED_CRIMSON_STEM,
        Blocks.STRIPPED_WARPED_STEM,
        Blocks.STRIPPED_OAK_WOOD,
        Blocks.STRIPPED_SPRUCE_WOOD,
        Blocks.STRIPPED_BIRCH_WOOD,
        Blocks.STRIPPED_JUNGLE_WOOD,
        Blocks.STRIPPED_ACACIA_WOOD,
        Blocks.STRIPPED_DARK_OAK_WOOD,
        Blocks.STRIPPED_MANGROVE_WOOD,
        Blocks.STRIPPED_CHERRY_WOOD,
        Blocks.STRIPPED_PALE_OAK_WOOD,
        Blocks.STRIPPED_CRIMSON_HYPHAE,
        Blocks.STRIPPED_WARPED_HYPHAE
    );

    public Stripper() {
        super(Bep.CATEGORY, "stripper", "Strips and breaks logs after you place the first one");
    }

    @Override
    public void onActivate() {
        this.state = Stripper.State.WAITING_FOR_FIRST_LOG;
        this.targetPos = null;
        this.workingPos = null;
        this.tickTimer = 0;
        this.rotationTimer = 0;
        this.firstLogDetected = false;
        this.info("Place a log to set the working position");
    }

    @Override
    public void onDeactivate() {
        this.targetPos = null;
        this.workingPos = null;
        this.state = Stripper.State.WAITING_FOR_FIRST_LOG;
        this.firstLogDetected = false;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.level != null) {
            if (this.tickTimer > 0) {
                this.tickTimer--;
            } else {
                switch (this.state) {
                    case WAITING_FOR_FIRST_LOG:
                    {
                        BlockPos playerPos = this.mc.player.blockPosition();

                        for (int x = -3; x <= 3; x++) {
                            for (int y = -1; y <= 2; y++) {
                                for (int z = -3; z <= 3; z++) {
                                    BlockPos checkPos = playerPos.offset(x, y, z);
                                    Block block = this.mc.level.getBlockState(checkPos).getBlock();
                                    if (LOGS.contains(block) && !this.firstLogDetected) {
                                        this.workingPos = checkPos;
                                        this.targetPos = checkPos;
                                        this.firstLogDetected = true;
                                        this.info("Working position set");
                                        this.state = Stripper.State.ROTATING_TO_STRIP;
                                        this.rotationTimer = this.rotationTime.get();
                                        return;
                                    }
                                }
                            }
                        }
                        break;
                    }
                    case ROTATING_TO_PLACE:
                    {
                        if (this.workingPos == null) {
                            this.state = Stripper.State.WAITING_FOR_FIRST_LOG;
                            return;
                        }

                        Vec3 target = this.workingPos.getCenter();
                        Rotations.rotate(this.getYaw(target), this.getPitch(target));
                        this.rotationTimer--;
                        if (this.rotationTimer <= 0) {
                            this.state = Stripper.State.PLACING_LOG;
                        }
                        break;
                    }
                    case PLACING_LOG:
                    {
                        if (this.workingPos == null) {
                            this.state = Stripper.State.WAITING_FOR_FIRST_LOG;
                            return;
                        }

                        int logSlot = this.findLogInInventory();
                        if (logSlot == -1) {
                            this.error("No logs in inventory");
                            this.toggle();
                            return;
                        }

                        ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot(logSlot);
                        BlockPos placeAgainst = this.workingPos.below();
                        Vec3 target = placeAgainst.getCenter().add(0.0, 0.5, 0.0);
                        Rotations.rotate(this.getYaw(target), this.getPitch(target));
                        BlockHitResult hitResult = new BlockHitResult(target, Direction.UP, placeAgainst, false);
                        this.mc.gameMode.useItemOn(this.mc.player, InteractionHand.MAIN_HAND, hitResult);
                        this.mc.player.swing(InteractionHand.MAIN_HAND);
                        this.state = Stripper.State.WAIT_AFTER_PLACE;
                        this.tickTimer = this.placeDelay.get();
                        this.targetPos = this.workingPos;
                        break;
                    }
                    case WAIT_AFTER_PLACE:
                    {
                        if (this.workingPos != null && LOGS.contains(this.mc.level.getBlockState(this.workingPos).getBlock())) {
                            this.state = Stripper.State.ROTATING_TO_STRIP;
                            this.rotationTimer = this.rotationTime.get();
                        } else {
                            this.state = Stripper.State.ROTATING_TO_PLACE;
                            this.rotationTimer = this.rotationTime.get();
                        }
                        break;
                    }
                    case ROTATING_TO_STRIP:
                    {
                        if (this.targetPos == null || this.workingPos == null) {
                            this.state = Stripper.State.WAITING_FOR_FIRST_LOG;
                            return;
                        }

                        Vec3 target = this.targetPos.getCenter();
                        Rotations.rotate(this.getYaw(target), this.getPitch(target));
                        this.rotationTimer--;
                        if (this.rotationTimer <= 0) {
                            this.state = Stripper.State.STRIPPING;
                        }
                        break;
                    }
                    case STRIPPING:
                    {
                        if (this.targetPos == null || this.workingPos == null) {
                            this.state = Stripper.State.WAITING_FOR_FIRST_LOG;
                            return;
                        }

                        int slot = this.axeSlot.get() - 1;
                        ItemStack stack = this.mc.player.getInventory().getItem(slot);
                        if (stack.isEmpty() || !(stack.getItem() instanceof AxeItem)) {
                            this.error("No axe in slot " + this.axeSlot.get());
                            this.toggle();
                            return;
                        }

                        ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot(slot);
                        Vec3 target = this.targetPos.getCenter();
                        Rotations.rotate(this.getYaw(target), this.getPitch(target));
                        BlockHitResult hitResult = new BlockHitResult(target, Direction.UP, this.targetPos, false);
                        this.mc.gameMode.useItemOn(this.mc.player, InteractionHand.MAIN_HAND, hitResult);
                        this.mc.player.swing(InteractionHand.MAIN_HAND);
                        this.state = Stripper.State.WAIT_AFTER_STRIP;
                        this.tickTimer = this.stripDelay.get();
                        break;
                    }
                    case WAIT_AFTER_STRIP:
                    {
                        if (this.targetPos != null && STRIPPED_LOGS.contains(this.mc.level.getBlockState(this.targetPos).getBlock())) {
                            if (this.autoMine.get()) {
                                this.state = Stripper.State.ROTATING_TO_BREAK;
                                this.rotationTimer = this.rotationTime.get();
                            } else {
                                this.state = Stripper.State.WAIT_BEFORE_NEXT;
                                this.tickTimer = this.breakDelay.get();
                            }
                        } else if (this.targetPos != null && LOGS.contains(this.mc.level.getBlockState(this.targetPos).getBlock())) {
                            this.state = Stripper.State.ROTATING_TO_STRIP;
                            this.rotationTimer = this.rotationTime.get();
                        } else {
                            this.state = Stripper.State.WAIT_BEFORE_NEXT;
                            this.tickTimer = this.breakDelay.get();
                        }
                        break;
                    }
                    case ROTATING_TO_BREAK:
                    {
                        if (this.targetPos == null || this.workingPos == null) {
                            this.state = Stripper.State.WAITING_FOR_FIRST_LOG;
                            return;
                        }

                        Vec3 target = this.targetPos.getCenter();
                        Rotations.rotate(this.getYaw(target), this.getPitch(target));
                        this.rotationTimer--;
                        if (this.rotationTimer <= 0) {
                            this.state = Stripper.State.BREAKING;
                        }
                        break;
                    }
                    case BREAKING:
                    {
                        if (this.targetPos == null || this.workingPos == null) {
                            this.state = Stripper.State.WAITING_FOR_FIRST_LOG;
                            return;
                        }

                        int slot = this.axeSlot.get() - 1;
                        ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot(slot);
                        Vec3 target = this.targetPos.getCenter();
                        Rotations.rotate(this.getYaw(target), this.getPitch(target));
                        this.mc.gameMode.continueDestroyBlock(this.targetPos, Direction.UP);
                        this.mc.player.swing(InteractionHand.MAIN_HAND);
                        this.state = Stripper.State.WAIT_AFTER_BREAK;
                        this.tickTimer = 2;
                        break;
                    }
                    case WAIT_AFTER_BREAK:
                    {
                        if (this.targetPos != null && !this.mc.level.getBlockState(this.targetPos).isAir()) {
                            this.state = Stripper.State.BREAKING;
                            this.tickTimer = 1;
                        } else {
                            this.state = Stripper.State.WAIT_BEFORE_NEXT;
                            this.tickTimer = this.breakDelay.get();
                        }
                        break;
                    }
                    case WAIT_BEFORE_NEXT:
                    {
                        if (this.findLogInInventory() != -1) {
                            this.state = Stripper.State.ROTATING_TO_PLACE;
                            this.rotationTimer = this.rotationTime.get();
                        } else {
                            this.info("No more logs in inventory");
                            this.toggle();
                        }
                    }
                }
            }
        }
    }

    private float getYaw(Vec3 target) {
        Vec3 playerPos = this.mc.player.getEyePosition();
        double deltaX = target.x - playerPos.x;
        double deltaZ = target.z - playerPos.z;
        return (float)Math.toDegrees(Math.atan2(-deltaX, deltaZ));
    }

    private float getPitch(Vec3 target) {
        Vec3 playerPos = this.mc.player.getEyePosition();
        double deltaX = target.x - playerPos.x;
        double deltaY = target.y - playerPos.y;
        double deltaZ = target.z - playerPos.z;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        return (float)(-Math.toDegrees(Math.atan2(deltaY, horizontalDistance)));
    }

    private int findLogInInventory() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = this.mc.player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                Block block = Block.byItem(stack.getItem());
                if (LOGS.contains(block)) {
                    return i;
                }
            }
        }

        return -1;
    }

    @Override
    public String getInfoString() {
        return this.state.toString().replace("_", " ");
    }

    private enum State {
        WAITING_FOR_FIRST_LOG,
        ROTATING_TO_PLACE,
        PLACING_LOG,
        WAIT_AFTER_PLACE,
        ROTATING_TO_STRIP,
        STRIPPING,
        WAIT_AFTER_STRIP,
        ROTATING_TO_BREAK,
        BREAKING,
        WAIT_AFTER_BREAK,
        WAIT_BEFORE_NEXT;
    }
}
