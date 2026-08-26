package bep.hax.modules;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;

public class StashMoverSelectionHandler {
    private static StashMoverSelectionHandler INSTANCE;
    private boolean wasSelecting = false;

    public static void init() {
        if (INSTANCE == null) {
            INSTANCE = new StashMoverSelectionHandler();
            MeteorClient.EVENT_BUS.subscribe(INSTANCE);
            System.out.println("[StashMover] Selection handler initialized and subscribed to events");
            if (MeteorClient.mc != null && MeteorClient.mc.player != null) {
                ChatUtils.info("[StashMover] Selection handler ready");
            }
        } else {
            System.out.println("[StashMover] Selection handler already initialized");
        }
    }

    @EventHandler(priority = 200)
    private void onInteractBlock(InteractBlockEvent event) {
        StashMover module = Modules.get().get(StashMover.class);
        if (module != null) {
            if (module.isSelecting()) {
                if (event.hand == InteractionHand.MAIN_HAND) {
                    event.cancel();
                    module.handleBlockSelectionPublic(event.result.getBlockPos());
                }
            }
        }
    }

    @EventHandler(priority = 200)
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        StashMover module = Modules.get().get(StashMover.class);
        if (module != null) {
            if (module.isSelecting()) {
                event.cancel();
                module.handleBlockSelectionPublic(event.blockPos);
            }
        }
    }

    @EventHandler
    private void onTick(Pre event) {
        if (MeteorClient.mc.player != null && MeteorClient.mc.level != null) {
            StashMover module = Modules.get().get(StashMover.class);
            if (module != null) {
                if (module.isSelecting()) {
                    if (!this.wasSelecting) {
                        this.wasSelecting = true;
                    }

                    if (MeteorClient.mc.options.keyAttack.isDown()) {
                        MeteorClient.mc.options.keyAttack.setDown(false);
                        if (MeteorClient.mc.hitResult != null && MeteorClient.mc.hitResult.getType() == Type.BLOCK) {
                            BlockHitResult hit = (BlockHitResult)MeteorClient.mc.hitResult;
                            BlockPos pos = hit.getBlockPos();
                            module.handleBlockSelectionPublic(pos);
                        }
                    }

                    if (MeteorClient.mc.options.keyInventory.consumeClick()) {
                        module.cancelSelection();
                        ChatUtils.info("§cSelection cancelled");
                        return;
                    }
                } else {
                    this.wasSelecting = false;
                }
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (MeteorClient.mc.player != null && MeteorClient.mc.level != null) {
            StashMover module = Modules.get().get(StashMover.class);
            if (module != null && !module.isActive() && module.isSelecting()) {
                if (module.getSelectionMode() != StashMover.SelectionMode.NONE) {
                    BlockPos selectionPos1 = module.getSelectionPos1();
                    if (selectionPos1 != null) {
                        BlockPos currentPos = MeteorClient.mc.hitResult != null && MeteorClient.mc.hitResult.getType() == Type.BLOCK
                            ? ((BlockHitResult)MeteorClient.mc.hitResult).getBlockPos()
                            : MeteorClient.mc.player.blockPosition();
                        AABB selectionBox = new AABB(
                            Math.min(selectionPos1.getX(), currentPos.getX()),
                            Math.min(selectionPos1.getY(), currentPos.getY()),
                            Math.min(selectionPos1.getZ(), currentPos.getZ()),
                            Math.max(selectionPos1.getX(), currentPos.getX()) + 1,
                            Math.max(selectionPos1.getY(), currentPos.getY()) + 1,
                            Math.max(selectionPos1.getZ(), currentPos.getZ()) + 1
                        );
                        boolean isInput = module.getSelectionMode() == StashMover.SelectionMode.INPUT_FIRST
                            || module.getSelectionMode() == StashMover.SelectionMode.INPUT_SECOND;
                        SettingColor color = isInput ? new SettingColor(0, 255, 0, 100) : new SettingColor(0, 100, 255, 100);
                        event.renderer.box(selectionBox, color, color, ShapeMode.Both, 0);
                        AABB corner1 = new AABB(
                            selectionPos1.getX(),
                            selectionPos1.getY(),
                            selectionPos1.getZ(),
                            selectionPos1.getX() + 1,
                            selectionPos1.getY() + 1,
                            selectionPos1.getZ() + 1
                        );
                        event.renderer.box(corner1, new SettingColor(255, 255, 0, 200), new SettingColor(255, 255, 0, 100), ShapeMode.Both, 0);
                    }
                }

                module.renderAreas(event);
            }
        }
    }
}
