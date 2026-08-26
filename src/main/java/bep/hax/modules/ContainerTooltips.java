package bep.hax.modules;

import bep.hax.Bep;
import bep.hax.modules.chesttracker.ChestTrackerDataManager;
import bep.hax.modules.chesttracker.ChestTrackerDataV2;
import bep.hax.modules.chesttracker.ChestTrackerModule;
import bep.hax.modules.chesttracker.TrackedContainer;
import bep.hax.util.ShulkerDataParser;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.InventoryEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DropperBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.TrappedChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;

public class ContainerTooltips extends Module {
    private static final Identifier SLOT_TEXTURE = Identifier.withDefaultNamespace("container/slot");
    private static final int Y_START = 10;
    private static final int MAX_ROW_LENGTH = 9;
    private static final int ITEM_SIZE_X = 18;
    private static final int ITEM_SIZE_Y = 18;
    private static final int TOOLTIP_BACKGROUND_PADDING = 2;
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgDisplay = this.settings.createGroup("Display");
    private final SettingGroup sgFilters = this.settings.createGroup("Filters");
    private final Setting<Boolean> showAutomatically = this.sgGeneral
        .add(new Builder().name("show-automatically").description("Show tooltip automatically when looking at containers.").defaultValue(true).build());
    private final Setting<Keybind> showKey = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.KeybindSetting.Builder()
                .name("show-key")
                .description("Key to hold to show tooltip (when auto-show is disabled).")
                .defaultValue(Keybind.fromKey(342))
                .visible(() -> !this.showAutomatically.get())
                .build()
        );
    private final Setting<Double> maxDistance = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("max-distance")
                .description("Maximum distance to show container tooltips.")
                .defaultValue(5.0)
                .min(1.0)
                .max(10.0)
                .sliderRange(1.0, 10.0)
                .build()
        );
    private final Setting<Boolean> hideInScreens = this.sgGeneral
        .add(new Builder().name("hide-in-screens").description("Hide tooltip when a screen is open.").defaultValue(true).build());
    private final Setting<ContainerTooltips.TooltipPosition> tooltipPosition = this.sgDisplay
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                            .name("position"))
                        .description("Position of the tooltip on screen."))
                    .defaultValue(ContainerTooltips.TooltipPosition.TOP_CENTER))
                .build()
        );
    private final Setting<Integer> offsetX = this.sgDisplay
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("offset-x")
                .description("Horizontal offset from position.")
                .defaultValue(0)
                .min(-500)
                .max(500)
                .sliderRange(-200, 200)
                .build()
        );
    private final Setting<Integer> offsetY = this.sgDisplay
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("offset-y")
                .description("Vertical offset from position.")
                .defaultValue(0)
                .min(-500)
                .max(500)
                .sliderRange(-200, 200)
                .build()
        );
    private final Setting<Boolean> showContainerName = this.sgDisplay
        .add(new Builder().name("show-name").description("Show the container name/type.").defaultValue(true).build());
    private final Setting<Boolean> showPosition = this.sgDisplay
        .add(new Builder().name("show-position").description("Show the container position coordinates.").defaultValue(false).build());
    private final Setting<Boolean> showLastUpdated = this.sgDisplay
        .add(new Builder().name("show-last-updated").description("Show when the container was last scanned.").defaultValue(false).build());
    private final Setting<Boolean> showTrackedContainers = this.sgFilters
        .add(new Builder().name("tracked-containers").description("Show tooltips for tracked containers from ChestTracker.").defaultValue(true).build());
    private final Setting<Boolean> showItemFrameShulkers = this.sgFilters
        .add(new Builder().name("item-frame-shulkers").description("Show tooltips for shulker boxes in item frames.").defaultValue(true).build());
    private final Setting<Boolean> showEmptyContainers = this.sgFilters
        .add(new Builder().name("show-empty").description("Show tooltip for empty containers.").defaultValue(true).build());
    private final Setting<Boolean> shulkerPreview = this.sgFilters
        .add(
            new Builder()
                .name("shulker-preview")
                .description("Show shulker content preview overlay on shulker boxes (uses ShulkerOverview rendering).")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> scanContainers = this.sgFilters
        .add(
            new Builder()
                .name("scan-containers")
                .description("Scan containers when opened (works even if ChestTracker is disabled).")
                .defaultValue(true)
                .build()
        );
    private ContainerTooltips.TooltipData currentTooltip = null;
    private long lastUpdateTime = 0L;
    private static final long UPDATE_INTERVAL_MS = 100L;
    private BlockPos lastInteractedBlock = null;
    private boolean wasInContainerScreen = false;
    private AbstractContainerMenu lastScreenHandler = null;
    private BlockPos[] containerPositionsForClose = new BlockPos[2];
    private static boolean blockInteractionCallbackRegistered = false;

    public ContainerTooltips() {
        super(Bep.CATEGORY, "container-tooltips", "Shows container contents when looking at tracked containers or shulkers in item frames.");
    }

    @Override
    public void onActivate() {
        ChestTrackerDataManager.onModuleActivate();
        this.lastInteractedBlock = null;
        this.wasInContainerScreen = false;
        this.lastScreenHandler = null;
        this.containerPositionsForClose = new BlockPos[2];
        if (!blockInteractionCallbackRegistered) {
            this.setupBlockInteractionTracking();
            blockInteractionCallbackRegistered = true;
        }
    }

    @Override
    public void onDeactivate() {
        this.currentTooltip = null;
        this.lastInteractedBlock = null;
        this.wasInContainerScreen = false;
        this.lastScreenHandler = null;
        this.containerPositionsForClose = new BlockPos[2];
        ChestTrackerDataManager.onModuleDeactivate();
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        ChestTrackerDataManager.onWorldJoin();
        this.currentTooltip = null;
    }

    private boolean shouldHandleTracking() {
        if (!this.scanContainers.get()) {
            return false;
        }

        ChestTrackerModule chestTracker = Modules.get().get(ChestTrackerModule.class);
        return chestTracker == null || !chestTracker.isActive();
    }

    private void setupBlockInteractionTracking() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!this.isActive()) {
                return InteractionResult.PASS;
            }

            if (!this.shouldHandleTracking()) {
                return InteractionResult.PASS;
            }

            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }

            if (this.mc.player != player) {
                return InteractionResult.PASS;
            }

            BlockPos pos = hitResult.getBlockPos();
            if (this.mc.level == null) {
                return InteractionResult.PASS;
            }

            Block block = this.mc.level.getBlockState(pos).getBlock();
            if (this.isTrackableContainer(block)) {
                this.lastInteractedBlock = pos.immutable();
            }

            return InteractionResult.PASS;
        });
    }

    private ChestTrackerDataV2 getData() {
        return ChestTrackerDataManager.getData();
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.level != null) {
            if (this.shouldHandleTracking()) {
                boolean currentlyInContainer = this.isInContainerScreen();
                if (this.wasInContainerScreen && !currentlyInContainer) {
                    this.trackContainerOnClose();
                }

                if (currentlyInContainer) {
                    AbstractContainerMenu handler = this.mc.player.containerMenu;
                    if (handler != null && handler != this.mc.player.inventoryMenu) {
                        this.lastScreenHandler = handler;
                        if (this.containerPositionsForClose[0] == null && this.lastInteractedBlock != null) {
                            this.containerPositionsForClose[0] = this.lastInteractedBlock;
                            BlockState state = this.mc.level.getBlockState(this.lastInteractedBlock);
                            this.containerPositionsForClose[1] = this.findDoubleChestOtherHalfForTracking(this.lastInteractedBlock, state);
                        }
                    }
                }

                this.wasInContainerScreen = currentlyInContainer;
            }
        }
    }

    @EventHandler
    private void onInventory(InventoryEvent event) {
        if (this.isActive()) {
            if (this.shouldHandleTracking()) {
                if (this.mc.player != null && this.mc.level != null) {
                    AbstractContainerMenu handler = this.mc.player.containerMenu;
                    if (handler != null && handler != this.mc.player.inventoryMenu) {
                        BlockPos trackPos = this.lastInteractedBlock;
                        if (trackPos != null) {
                            List<ItemStack> items = new ArrayList<>();
                            int containerSlots = handler.slots.size() - 36;

                            for (int i = 0; i < containerSlots && i < handler.slots.size(); i++) {
                                Slot slot = handler.slots.get(i);
                                ItemStack stack = slot.getItem();
                                if (!stack.isEmpty()) {
                                    items.add(stack.copy());
                                }
                            }

                            String currentDim = this.getCurrentDimension();
                            String containerType = this.getContainerType(trackPos);
                            this.getData().trackContainer(trackPos, currentDim, containerType, items);
                            this.containerPositionsForClose[0] = trackPos;
                            BlockState state = this.mc.level.getBlockState(trackPos);
                            this.containerPositionsForClose[1] = this.findDoubleChestOtherHalfForTracking(trackPos, state);
                            this.lastScreenHandler = handler;
                            this.lastInteractedBlock = null;
                        }
                    }
                }
            }
        }
    }

    private void trackContainerOnClose() {
        if (this.lastScreenHandler == null || this.containerPositionsForClose[0] == null) {
            this.lastScreenHandler = null;
            this.containerPositionsForClose = new BlockPos[2];
        } else if (this.mc.player != null && this.mc.level != null) {
            BlockPos trackPos = this.containerPositionsForClose[0];
            Block block = this.mc.level.getBlockState(trackPos).getBlock();
            if (!this.isTrackableContainer(block)) {
                this.lastScreenHandler = null;
                this.containerPositionsForClose = new BlockPos[2];
            } else {
                List<ItemStack> items = new ArrayList<>();
                int containerSlots = this.lastScreenHandler.slots.size() - 36;

                for (int i = 0; i < containerSlots && i < this.lastScreenHandler.slots.size(); i++) {
                    Slot slot = this.lastScreenHandler.slots.get(i);
                    ItemStack stack = slot.getItem();
                    if (!stack.isEmpty()) {
                        items.add(stack.copy());
                    }
                }

                String currentDim = this.getCurrentDimension();
                String containerType = this.getContainerType(trackPos);
                this.getData().trackContainer(trackPos, currentDim, containerType, items);
                this.lastInteractedBlock = null;
                this.lastScreenHandler = null;
                this.containerPositionsForClose = new BlockPos[2];
            }
        } else {
            this.lastScreenHandler = null;
            this.containerPositionsForClose = new BlockPos[2];
        }
    }

    private boolean isInContainerScreen() {
        if (this.mc.screen == null) {
            return false;
        } else {
            return this.mc.player == null ? false : this.mc.player.containerMenu != this.mc.player.inventoryMenu;
        }
    }

    private boolean isTrackableContainer(Block block) {
        return block instanceof ChestBlock
            || block instanceof TrappedChestBlock
            || block instanceof BarrelBlock
            || block instanceof ShulkerBoxBlock
            || block instanceof EnderChestBlock
            || block instanceof HopperBlock
            || block instanceof DispenserBlock
            || block instanceof DropperBlock;
    }

    private String getContainerType(BlockPos pos) {
        if (this.mc.level == null) {
            return "container";
        } else {
            Block block = this.mc.level.getBlockState(pos).getBlock();
            if (block instanceof ChestBlock || block instanceof TrappedChestBlock) {
                return "chest";
            } else if (block instanceof BarrelBlock) {
                return "barrel";
            } else if (block instanceof ShulkerBoxBlock) {
                return "shulker_box";
            } else if (block instanceof EnderChestBlock) {
                return "ender_chest";
            } else if (block instanceof HopperBlock) {
                return "hopper";
            } else if (block instanceof DispenserBlock) {
                return "dispenser";
            } else {
                return block instanceof DropperBlock ? "dropper" : "container";
            }
        }
    }

    private BlockPos findDoubleChestOtherHalfForTracking(BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof ChestBlock)) {
            return null;
        }

        try {
            if (state.hasProperty(ChestBlock.TYPE)) {
                ChestType chestType = state.getValue(ChestBlock.TYPE);
                if (chestType == ChestType.SINGLE) {
                    return null;
                }

                if (state.hasProperty(ChestBlock.FACING)) {
                    Direction facing = state.getValue(ChestBlock.FACING);
                    return chestType == ChestType.LEFT ? pos.relative(facing.getClockWise()) : pos.relative(facing.getCounterClockWise());
                }
            }
        } catch (Exception var5) {
        }

        return null;
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (this.mc.player != null && this.mc.level != null) {
            if (this.hideInScreens.get() && this.mc.screen != null) {
                this.currentTooltip = null;
            } else if (!this.showAutomatically.get() && !this.showKey.get().isPressed()) {
                this.currentTooltip = null;
            } else {
                this.updateTooltipData();
                if (this.currentTooltip != null) {
                    this.renderTooltip(event.drawContext, this.currentTooltip);
                }
            }
        }
    }

    private void updateTooltipData() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - this.lastUpdateTime >= 100L || this.currentTooltip == null) {
            this.lastUpdateTime = currentTime;
            HitResult hitResult = this.mc.hitResult;
            if (hitResult == null) {
                this.currentTooltip = null;
            } else {
                double distance = hitResult.getLocation().distanceTo(this.mc.player.getEyePosition());
                if (distance > this.maxDistance.get()) {
                    this.currentTooltip = null;
                } else {
                    if (hitResult.getType() == Type.BLOCK && hitResult instanceof BlockHitResult blockHit) {
                        BlockPos pos = blockHit.getBlockPos();
                        if (this.showTrackedContainers.get()) {
                            ContainerTooltips.TooltipData data = this.getTrackedContainerTooltip(pos);
                            if (data != null) {
                                this.currentTooltip = data;
                                return;
                            }
                        }
                    }

                    if (hitResult.getType() == Type.ENTITY
                        && hitResult instanceof EntityHitResult entityHit
                        && this.showItemFrameShulkers.get()
                        && entityHit.getEntity() instanceof ItemFrame itemFrame) {
                        ContainerTooltips.TooltipData data = this.getItemFrameShulkerTooltip(itemFrame);
                        if (data != null) {
                            this.currentTooltip = data;
                            return;
                        }
                    }

                    this.currentTooltip = null;
                }
            }
        }
    }

    private ContainerTooltips.TooltipData getTrackedContainerTooltip(BlockPos pos) {
        ChestTrackerDataV2 data = this.getData();
        if (data == null) {
            return null;
        }

        String dimension = this.getCurrentDimension();
        BlockState state = this.mc.level.getBlockState(pos);
        Block block = state.getBlock();
        TrackedContainer container = data.getContainer(pos, dimension);
        if (container == null && block instanceof ChestBlock) {
            BlockPos otherHalf = this.findDoubleChestOtherHalf(pos, state);
            if (otherHalf != null) {
                container = data.getContainer(otherHalf, dimension);
            }
        }

        if (container == null && block instanceof EnderChestBlock) {
            container = this.findAnyEnderChest(data, dimension);
        }

        if (container == null) {
            return null;
        }

        List<ItemStack> items = container.getItemStacks();
        if (items.isEmpty() && !this.showEmptyContainers.get()) {
            return null;
        }

        String name = this.formatContainerName(container);
        String positionStr = this.showPosition.get() ? this.formatPosition(pos) : null;
        String lastUpdatedStr = this.showLastUpdated.get() ? this.formatLastUpdated(container.getLastUpdated()) : null;
        return new ContainerTooltips.TooltipData(name, items, positionStr, lastUpdatedStr);
    }

    private ContainerTooltips.TooltipData getItemFrameShulkerTooltip(ItemFrame itemFrame) {
        ItemStack heldItem = itemFrame.getItem();
        if (heldItem.isEmpty()) {
            return null;
        }

        if (heldItem.getItem() instanceof BlockItem blockItem) {
            if (!(blockItem.getBlock() instanceof ShulkerBoxBlock)) {
                return null;
            }

            List<ItemStack> items = ShulkerDataParser.parseShulkerContentsAsList(heldItem);
            if (items.isEmpty() && !this.showEmptyContainers.get()) {
                return null;
            }

            String name = heldItem.getHoverName().getString();
            String positionStr = this.showPosition.get() ? this.formatPosition(itemFrame.blockPosition()) : null;
            return new ContainerTooltips.TooltipData(name, items, positionStr, null);
        } else {
            return null;
        }
    }

    private void renderTooltip(GuiGraphics drawContext, ContainerTooltips.TooltipData data) {
        Font textRenderer = this.mc.font;
        int screenWidth = this.mc.getWindow().getGuiScaledWidth();
        int screenHeight = this.mc.getWindow().getGuiScaledHeight();
        int itemsOnRow = this.getItemsOnOneRow(data.items);
        int rows = this.getRowCount(data.items);
        int contentWidth = itemsOnRow * 18 + 2;
        int contentHeight = rows * 18 + 2;
        if (data.items.isEmpty()) {
            contentWidth = Math.max(contentWidth, textRenderer.width("Empty") + 4);
        }

        int headerHeight = 0;
        if (this.showContainerName.get() && data.name != null) {
            headerHeight += 9 + 2;
            contentWidth = Math.max(contentWidth, textRenderer.width(data.name) + 4);
        }

        if (data.position != null) {
            headerHeight += 9 + 1;
            contentWidth = Math.max(contentWidth, textRenderer.width(data.position) + 4);
        }

        if (data.lastUpdated != null) {
            headerHeight += 9 + 1;
            contentWidth = Math.max(contentWidth, textRenderer.width(data.lastUpdated) + 4);
        }

        int totalHeight = contentHeight + headerHeight;
        int x;
        int y;
        switch ((ContainerTooltips.TooltipPosition)this.tooltipPosition.get()) {
            case TOP_LEFT:
                x = 10;
                y = 10;
                break;
            case TOP_CENTER:
            default:
                x = (screenWidth - contentWidth) / 2;
                y = 10;
                break;
            case TOP_RIGHT:
                x = screenWidth - contentWidth - 10;
                y = 10;
                break;
            case CENTER:
                x = (screenWidth - contentWidth) / 2;
                y = (screenHeight - totalHeight) / 2;
                break;
            case BOTTOM_LEFT:
                x = 10;
                y = screenHeight - totalHeight - 10;
                break;
            case BOTTOM_RIGHT:
                x = screenWidth - contentWidth - 10;
                y = screenHeight - totalHeight - 10;
        }

        x = Math.max(12, Math.min(x, screenWidth - contentWidth - 12)) + this.offsetX.get();
        y = Math.max(12, Math.min(y, screenHeight - totalHeight - 12)) + this.offsetY.get();
        TooltipRenderUtil.renderTooltipBackground(drawContext, x, y, contentWidth, totalHeight, null);
        int currentY = y;
        if (this.showContainerName.get() && data.name != null) {
            drawContext.drawString(textRenderer, data.name, x, currentY, -1, true);
            currentY += 9 + 2;
        }

        if (data.position != null) {
            drawContext.drawString(textRenderer, data.position, x, currentY, -8355712, true);
            currentY += 9 + 1;
        }

        if (data.lastUpdated != null) {
            drawContext.drawString(textRenderer, data.lastUpdated, x, currentY, -8355712, true);
            currentY += 9 + 1;
        }

        if (data.items.isEmpty()) {
            drawContext.drawString(textRenderer, "Empty", x, currentY + 2, -8355712, true);
        } else {
            int backgroundY = currentY;
            drawContext.fill(x, backgroundY, x + itemsOnRow * 18 + 2, backgroundY + rows * 18 + 2, -7631989);
            ShulkerOverviewModule shulkerOverview = this.shulkerPreview.get() ? Modules.get().get(ShulkerOverviewModule.class) : null;
            int index = 0;

            for (int row = 0; row < rows && index < data.items.size(); row++) {
                for (int col = 0; col < itemsOnRow && index < data.items.size(); col++) {
                    ItemStack stack = data.items.get(index++);
                    int itemX = x + col * 18 + 2;
                    int itemY = backgroundY + row * 18 + 2;
                    drawContext.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_TEXTURE, itemX, itemY, 18, 18);
                    drawContext.renderItem(stack, itemX + 1, itemY + 1);
                    drawContext.renderItemDecorations(textRenderer, stack, itemX + 1, itemY + 1);
                    if (shulkerOverview != null && this.isShulkerBox(stack)) {
                        shulkerOverview.renderShulkerOverlay(drawContext, itemX + 1, itemY + 1, stack);
                    }
                }
            }
        }
    }

    private boolean isShulkerBox(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        } else {
            return stack.getItem() instanceof BlockItem blockItem ? blockItem.getBlock() instanceof ShulkerBoxBlock : false;
        }
    }

    private int getItemsOnOneRow(List<ItemStack> items) {
        return items.size() < 9 ? Math.max(items.size(), 1) : 9;
    }

    private int getRowCount(List<ItemStack> items) {
        return items.isEmpty() ? 1 : (int)Math.ceil(items.size() / 9.0);
    }

    private BlockPos findDoubleChestOtherHalf(BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof ChestBlock)) {
            return null;
        }

        try {
            if (state.hasProperty(ChestBlock.TYPE)) {
                ChestType chestType = state.getValue(ChestBlock.TYPE);
                if (chestType == ChestType.SINGLE) {
                    return null;
                }

                if (state.hasProperty(ChestBlock.FACING)) {
                    Direction facing = state.getValue(ChestBlock.FACING);
                    return chestType == ChestType.LEFT ? pos.relative(facing.getClockWise()) : pos.relative(facing.getCounterClockWise());
                }
            }
        } catch (Exception var5) {
        }

        return null;
    }

    private String getCurrentDimension() {
        return this.mc.level == null ? "unknown" : this.mc.level.dimension().identifier().toString();
    }

    private TrackedContainer findAnyEnderChest(ChestTrackerDataV2 data, String dimension) {
        for (TrackedContainer container : data.getAllContainers(dimension)) {
            if ("ender_chest".equals(container.getContainerType())) {
                return container;
            }
        }

        for (TrackedContainer container : data.getAllContainers()) {
            if ("ender_chest".equals(container.getContainerType())) {
                return container;
            }
        }

        return null;
    }

    private String formatContainerName(TrackedContainer container) {
        String customName = container.getCustomName();
        return customName != null && !customName.isEmpty() ? customName : this.formatContainerType(container.getContainerType());
    }

    private String formatContainerType(String type) {
        if (type == null) {
            return "Container";
        }

        return switch (type) {
            case "chest" -> "Chest";
            case "trapped_chest" -> "Trapped Chest";
            case "barrel" -> "Barrel";
            case "shulker_box" -> "Shulker Box";
            case "ender_chest" -> "Ender Chest";
            case "hopper" -> "Hopper";
            case "dispenser" -> "Dispenser";
            case "dropper" -> "Dropper";
            case "copper_chest" -> "Copper Chest";
            default -> "Container";
        };
    }

    private String formatPosition(BlockPos pos) {
        return String.format("§7[%d, %d, %d]", pos.getX(), pos.getY(), pos.getZ());
    }

    private String formatLastUpdated(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long seconds = diff / 1000L;
        long minutes = seconds / 60L;
        long hours = minutes / 60L;
        long days = hours / 24L;
        if (days > 0L) {
            return "§7Scanned " + days + "d ago";
        } else if (hours > 0L) {
            return "§7Scanned " + hours + "h ago";
        } else {
            return minutes > 0L ? "§7Scanned " + minutes + "m ago" : "§7Scanned just now";
        }
    }

    private static class TooltipData {
        final String name;
        final List<ItemStack> items;
        final String position;
        final String lastUpdated;

        TooltipData(String name, List<ItemStack> items, String position, String lastUpdated) {
            this.name = name;
            this.items = items != null ? items : new ArrayList<>();
            this.position = position;
            this.lastUpdated = lastUpdated;
        }
    }

    public enum TooltipPosition {
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT,
        CENTER,
        BOTTOM_LEFT,
        BOTTOM_RIGHT;
    }
}
