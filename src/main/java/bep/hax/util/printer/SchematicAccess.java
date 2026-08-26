package bep.hax.util.printer;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialCache;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement.RequiredEnabled;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import fi.dy.masa.malilib.util.LayerRange;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public final class SchematicAccess {
    private SchematicAccess() {
    }

    public static WorldSchematic getWorld() {
        return SchematicWorldHandler.getSchematicWorld();
    }

    public static SchematicPlacement getSelectedPlacement() {
        SchematicPlacementManager mgr = DataManager.getSchematicPlacementManager();
        return mgr == null ? null : mgr.getSelectedSchematicPlacement();
    }

    public static boolean hasActivePlacement() {
        SchematicPlacement p = getSelectedPlacement();
        return p != null && p.isEnabled();
    }

    public static boolean isWithinRenderLayers(BlockPos pos) {
        LayerRange range = DataManager.getRenderLayerRange();
        return range == null || range.isPositionWithinRange(pos);
    }

    public static List<PrinterRegion> getSelectedRegions() {
        SchematicPlacement placement = getSelectedPlacement();
        if (placement == null) {
            return List.of();
        }

        List<PrinterRegion> out = new ArrayList<>();

        for (Box box : placement.getSubRegionBoxes(RequiredEnabled.PLACEMENT_ENABLED).values()) {
            BlockPos p1 = box.getPos1();
            BlockPos p2 = box.getPos2();
            if (p1 != null && p2 != null) {
                out.add(
                    new PrinterRegion(
                        Math.min(p1.getX(), p2.getX()),
                        Math.min(p1.getY(), p2.getY()),
                        Math.min(p1.getZ(), p2.getZ()),
                        Math.max(p1.getX(), p2.getX()),
                        Math.max(p1.getY(), p2.getY()),
                        Math.max(p1.getZ(), p2.getZ())
                    )
                );
            }
        }

        return out;
    }

    public static BlockState getTargetState(BlockPos pos) {
        WorldSchematic world = getWorld();
        return world == null ? null : world.getBlockState(pos);
    }

    public static ItemStack getRequiredItem(BlockState state, BlockPos pos) {
        return MaterialCache.getInstance().getRequiredBuildItemForState(state, getWorld(), pos);
    }

    public static MaterialListBase getMaterialList() {
        return DataManager.getMaterialList();
    }

    public static List<PrinterMaterial> getMissingMaterials() {
        MaterialListBase list = DataManager.getMaterialList();
        if (list == null) {
            return List.of();
        }

        List<PrinterMaterial> out = new ArrayList<>();

        for (MaterialListEntry entry : list.getMaterialsMissingOnly(true)) {
            out.add(new PrinterMaterial(entry.getStack(), entry.getCountMissing()));
        }

        return out;
    }
}
