package bep.hax.mixin;

import bep.hax.modules.WaypointFollower;
import java.util.ArrayList;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.world.Dimension;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.map.gui.GuiMap;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.RightClickOption;
import xaero.map.mods.SupportMods;

@Mixin(value = GuiMap.class, remap = false)
public abstract class MixinGuiMapWaypointFollower implements IRightClickableElement {
    @Shadow
    private int rightClickX;
    @Shadow
    private int rightClickY;
    @Shadow
    private int rightClickZ;
    @Shadow
    private double rightClickCoordinateScale;
    @Shadow
    private int mouseBlockPosX;
    @Shadow
    private int mouseBlockPosY;
    @Shadow
    private int mouseBlockPosZ;
    @Shadow
    private double mouseBlockCoordinateScale;

    @Inject(method = "getRightClickOptions", at = @At("RETURN"), remap = false)
    public void getRightClickOptionsInject(CallbackInfoReturnable<ArrayList<RightClickOption>> cir) {
        ArrayList<RightClickOption> options = cir.getReturnValue();
        final WaypointFollower waypointFollower = Modules.get().get(WaypointFollower.class);
        if (waypointFollower != null) {
            options.add(new RightClickOption("Add Hunt Waypoint", options.size(), this) {
                @Override
                public void onAction(Screen screen) {
                    MixinGuiMapWaypointFollower.this.createWaypointFromRightClick(waypointFollower);
                }
            });
        }
    }

    @Inject(method = "onInputPress", at = @At("HEAD"), cancellable = true, remap = false)
    public void onKeyPress(@Coerce Object type, int code, CallbackInfoReturnable<Boolean> cir) {
        if ("KEYSYM".equals(type.toString())) {
            Screen screen = (Screen)(Object)this;
            GuiEventListener focused = screen.getFocused();
            if (!(focused instanceof EditBox)) {
                if (focused == null || !focused.getClass().getName().contains("TextField")) {
                    WaypointFollower waypointFollower = Modules.get().get(WaypointFollower.class);
                    if (waypointFollower != null) {
                        if (code == waypointFollower.getAddWaypointKeyCode()) {
                            this.rightClickX = this.mouseBlockPosX;
                            this.rightClickY = this.mouseBlockPosY;
                            this.rightClickZ = this.mouseBlockPosZ;
                            this.rightClickCoordinateScale = this.mouseBlockCoordinateScale;
                            this.createWaypointFromRightClick(waypointFollower);
                            cir.setReturnValue(true);
                        }
                    }
                }
            }
        }
    }

    private void createWaypointFromRightClick(WaypointFollower waypointFollower) {
        try {
            this.createWaypoint(this.rightClickX, this.rightClickZ, this.rightClickCoordinateScale, waypointFollower);
        } catch (Exception e) {
            if (waypointFollower.shouldShowChatMessages()) {
                waypointFollower.error("Error creating waypoint: " + e.getMessage());
            }
        }
    }

    private void createWaypoint(int x, int z, double coordinateScale, WaypointFollower waypointFollower) {
        try {
            MinimapSession minimapSession = BuiltInHudModules.MINIMAP.getCurrentSession();
            if (minimapSession == null) {
                if (waypointFollower.shouldShowChatMessages()) {
                    waypointFollower.error("MinimapSession is null");
                }

                return;
            }

            MinimapWorld targetMinimapWorld = waypointFollower.getWaypointWorld();
            if (targetMinimapWorld == null) {
                targetMinimapWorld = SupportMods.xaeroMinimap.getWaypointWorld();
            }

            if (targetMinimapWorld == null) {
                targetMinimapWorld = minimapSession.getWorldManager().getCurrentWorld();
            }

            if (targetMinimapWorld == null) {
                if (waypointFollower.shouldShowChatMessages()) {
                    waypointFollower.error("MinimapWorld is null");
                }

                return;
            }

            double targetScale = minimapSession.getDimensionHelper().getDimCoordinateScale(targetMinimapWorld);
            double scaleFactor = targetScale <= 0.0 ? 1.0 : coordinateScale / targetScale;
            int finalX = (int)Math.floor(x * scaleFactor);
            int finalZ = (int)Math.floor(z * scaleFactor);
            int y = this.getNormalizedY(this.getDimensionOfWorld(targetMinimapWorld));
            String waypointName = waypointFollower.getWaypointPrefix() + waypointFollower.getNextWaypointNumber(targetMinimapWorld);
            Waypoint huntWaypoint = new Waypoint(finalX, y, finalZ, waypointName, "H", WaypointFollower.HUNT_COLOR, WaypointPurpose.NORMAL, false);
            WaypointSet currentSet = targetMinimapWorld.getCurrentWaypointSet();
            if (currentSet == null) {
                if (waypointFollower.shouldShowChatMessages()) {
                    waypointFollower.error("Current waypoint set is null");
                }

                return;
            }

            currentSet.add(huntWaypoint);

            try {
                minimapSession.getWorldManagerIO().saveWorld(targetMinimapWorld);
            } catch (Exception saveEx) {
                if (waypointFollower.shouldShowChatMessages()) {
                    waypointFollower.error("Failed to save waypoint: " + saveEx.getMessage());
                }
            }

            SupportMods.xaeroMinimap.requestWaypointsRefresh();
            if (waypointFollower.isActive()) {
                waypointFollower.addWaypointToTrack(finalX, y, finalZ);
            }

            if (waypointFollower.shouldShowChatMessages()) {
                waypointFollower.info("Created waypoint '" + waypointName + "' at " + finalX + ", " + finalZ);
            }
        } catch (Exception e) {
            if (waypointFollower.shouldShowChatMessages()) {
                waypointFollower.error("Error creating waypoint: " + e.getMessage());
            }
        }
    }

    private Dimension getDimensionOfWorld(MinimapWorld world) {
        try {
            String dimPath = world.getDimId().identifier().getPath();
            if ("the_nether".equals(dimPath)) {
                return Dimension.Nether;
            }

            if ("the_end".equals(dimPath)) {
                return Dimension.End;
            }
        } catch (Exception var3) {
        }

        return Dimension.Overworld;
    }

    private int getNormalizedY(Dimension dimension) {
        switch (dimension) {
            case Nether:
                return 120;
            case End:
                return 70;
            case Overworld:
            default:
                return 320;
        }
    }
}
