package bep.hax.modules.arealoader;

import bep.hax.modules.arealoader.modes.Rectangle;
import bep.hax.modules.arealoader.modes.Spiral;
import bep.hax.modules.arealoader.modes.SpiralCircle;
import bep.hax.modules.arealoader.modes.ZigZag;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class AreaRecoveryScreen extends WindowScreen {
    private final AreaLoader searchArea;
    private WDropdown<AreaLoaderModes> modeDropdown;
    private WTextBox originXEdit;
    private WTextBox originZEdit;
    private WIntEdit gapEdit;
    private WTextBox currentXEdit;
    private WTextBox currentZEdit;
    private WSection spiralSection;
    private WSection spiralCircleSection;
    private WSection rectangleSection;
    private WTextBox rectEndXEdit;
    private WTextBox rectEndZEdit;
    private WSection zigzagSection;
    private WIntEdit zigzagLegLengthEdit;
    private WIntEdit zigzagRowGapEdit;
    private WDropdown<AreaRecoveryScreen.CardinalDirection> zigzagDirectionDropdown;

    public AreaRecoveryScreen(GuiTheme theme, AreaLoader searchArea) {
        super(theme, "Area Recovery Configuration");
        this.searchArea = searchArea;
    }

    @Override
    public void initWidgets() {
        WVerticalList list = this.add(this.theme.verticalList()).expandX().widget();
        WHorizontalList modeRow = list.add(this.theme.horizontalList()).expandX().widget();
        modeRow.add(this.theme.label("Mode: "));
        this.modeDropdown = modeRow.add(this.theme.dropdown(this.searchArea.chunkLoadMode.get())).expandX().widget();
        this.modeDropdown.action = () -> this.rebuildModeSpecificSections();
        list.add(this.theme.horizontalSeparator()).expandX();
        WSection commonSection = list.add(this.theme.section("Position Configuration", true)).expandX().widget();
        commonSection.add(this.theme.label("Enter coordinates where the pattern started and where to resume")).expandX();
        commonSection.add(this.theme.horizontalSeparator()).expandX();
        WHorizontalList originRow = commonSection.add(this.theme.horizontalList()).expandX().widget();
        originRow.add(this.theme.label("Origin X: "));
        this.originXEdit = originRow.add(this.theme.textBox("0", this::filterNumeric)).expandX().widget();
        originRow.add(this.theme.label(" Z: "));
        this.originZEdit = originRow.add(this.theme.textBox("0", this::filterNumeric)).expandX().widget();
        WHorizontalList gapRow = commonSection.add(this.theme.horizontalList()).expandX().widget();
        gapRow.add(this.theme.label("Path Gap (chunks): "));
        this.gapEdit = gapRow.add(this.theme.intEdit(this.searchArea.rowGap.get(), 1, 100, false)).expandX().widget();
        WHorizontalList currentRow = commonSection.add(this.theme.horizontalList()).expandX().widget();
        currentRow.add(this.theme.label("Resume X: "));
        this.currentXEdit = currentRow.add(this.theme.textBox("0", this::filterNumeric)).expandX().widget();
        currentRow.add(this.theme.label(" Z: "));
        this.currentZEdit = currentRow.add(this.theme.textBox("0", this::filterNumeric)).expandX().widget();
        WButton useCurrentPosBtn = commonSection.add(this.theme.button("Use Current Player Position")).expandX().widget();
        useCurrentPosBtn.action = () -> {
            if (MeteorClient.mc.player != null) {
                this.currentXEdit.set(String.valueOf(MeteorClient.mc.player.getBlockX()));
                this.currentZEdit.set(String.valueOf(MeteorClient.mc.player.getBlockZ()));
                ChatUtils.info(
                    "Set resume position to player location: X=%d Z=%d", MeteorClient.mc.player.getBlockX(), MeteorClient.mc.player.getBlockZ()
                );
            }
        };
        list.add(this.theme.horizontalSeparator()).expandX();
        this.spiralSection = list.add(this.theme.section("Spiral Recovery Instructions", true)).expandX().widget();
        this.spiralSection.add(this.theme.label("Origin = where the spiral STARTED (first corner)")).expandX();
        this.spiralSection.add(this.theme.label("Resume = your current position or where you want to continue")).expandX();
        this.spiralSection.add(this.theme.label("Path Gap = chunks between each spiral arm (MUST match original)")).expandX();
        this.spiralSection.add(this.theme.horizontalSeparator()).expandX();
        this.spiralSection.add(this.theme.label("Quick Recovery:")).expandX();
        this.spiralSection.add(this.theme.label("  1. Fill in Origin and Resume coords, set correct Gap")).expandX();
        this.spiralSection.add(this.theme.label("  2. Click 'Snap to Next Corner' - updates Resume to nearest corner")).expandX();
        this.spiralSection.add(this.theme.label("  3. Click 'Apply from Corner' - saves state and closes")).expandX();
        this.spiralSection.add(this.theme.label("  4. Fly to the corner coords shown, then enable module")).expandX();
        this.spiralSection.add(this.theme.horizontalSeparator()).expandX();
        this.spiralSection.add(this.theme.label("Note: If spiral was modified since original run, position may")).expandX();
        this.spiralSection.add(this.theme.label("be slightly offset from your exact path - this is normal.")).expandX();
        this.spiralCircleSection = list.add(this.theme.section("Spiral (Circle) Recovery Instructions", true)).expandX().widget();
        this.spiralCircleSection.add(this.theme.label("Origin = the CENTER the circle spiral started from")).expandX();
        this.spiralCircleSection.add(this.theme.label("Resume = your current position or where you want to continue")).expandX();
        this.spiralCircleSection.add(this.theme.label("Path Gap = chunks gained per full loop (MUST match original)")).expandX();
        this.spiralCircleSection.add(this.theme.horizontalSeparator()).expandX();
        this.spiralCircleSection.add(this.theme.label("Quick Recovery:")).expandX();
        this.spiralCircleSection.add(this.theme.label("  1. Fill in Origin and Resume coords, set correct Gap")).expandX();
        this.spiralCircleSection.add(this.theme.label("  2. Click 'Snap to Path' - pulls Resume onto the arc")).expandX();
        this.spiralCircleSection.add(this.theme.label("  3. Click 'Apply & Save' - saves state and closes")).expandX();
        this.spiralCircleSection.add(this.theme.label("  4. Fly to the resume coords, then enable module")).expandX();
        this.spiralCircleSection.add(this.theme.horizontalSeparator()).expandX();
        this.spiralCircleSection.add(this.theme.label("The circle has no corners: 'Snap to Next Corner' and 'Apply from")).expandX();
        this.spiralCircleSection.add(this.theme.label("Corner' use the next quarter turn of the loop instead.")).expandX();
        this.spiralCircleSection.add(this.theme.label("Which loop you are on is read from your distance to the origin,")).expandX();
        this.spiralCircleSection.add(this.theme.label("so the Origin and Gap must be exactly right.")).expandX();
        this.rectangleSection = list.add(this.theme.section("Rectangle Recovery Instructions", true)).expandX().widget();
        this.rectangleSection.add(this.theme.label("Origin = starting corner of the rectangle")).expandX();
        this.rectangleSection.add(this.theme.label("End = opposite corner of the rectangle")).expandX();
        this.rectangleSection.add(this.theme.label("Resume = your current position within the rectangle")).expandX();
        this.rectangleSection.add(this.theme.horizontalSeparator()).expandX();
        WHorizontalList rectEndRow = this.rectangleSection.add(this.theme.horizontalList()).expandX().widget();
        rectEndRow.add(this.theme.label("End X: "));
        this.rectEndXEdit = rectEndRow.add(this.theme.textBox("0", this::filterNumeric)).expandX().widget();
        rectEndRow.add(this.theme.label(" Z: "));
        this.rectEndZEdit = rectEndRow.add(this.theme.textBox("0", this::filterNumeric)).expandX().widget();
        this.zigzagSection = list.add(this.theme.section("ZigZag Recovery Instructions", true)).expandX().widget();
        this.zigzagSection.add(this.theme.label("Origin = where zigzag started")).expandX();
        this.zigzagSection.add(this.theme.label("Resume = your current position")).expandX();
        this.zigzagSection.add(this.theme.horizontalSeparator()).expandX();
        WHorizontalList zigzagRow1 = this.zigzagSection.add(this.theme.horizontalList()).expandX().widget();
        zigzagRow1.add(this.theme.label("Leg Length: "));
        this.zigzagLegLengthEdit = zigzagRow1.add(this.theme.intEdit(this.searchArea.zigzagLegLength.get(), 100, 100000, false)).expandX().widget();
        zigzagRow1.add(this.theme.label(" Row Gap: "));
        this.zigzagRowGapEdit = zigzagRow1.add(this.theme.intEdit(this.searchArea.zigzagRowGap.get(), 16, 1000, false)).expandX().widget();
        WHorizontalList zigzagRow2 = this.zigzagSection.add(this.theme.horizontalList()).expandX().widget();
        zigzagRow2.add(this.theme.label("Main Direction: "));
        this.zigzagDirectionDropdown = zigzagRow2.add(this.theme.dropdown(AreaRecoveryScreen.CardinalDirection.SOUTH)).expandX().widget();
        list.add(this.theme.horizontalSeparator()).expandX();
        WSection actionSection = list.add(this.theme.section("Actions", true)).expandX().widget();
        actionSection.add(this.theme.label("Validate: Check if position is valid (shows info in chat)")).expandX();
        actionSection.add(this.theme.label("Snap to Path: Adjust Resume coords to nearest path position")).expandX();
        actionSection.add(this.theme.label("Snap to Next Corner: (Spiral) Find the next corner ahead")).expandX();
        actionSection.add(this.theme.label("Apply from Corner: (Spiral) Save state to continue from corner")).expandX();
        actionSection.add(this.theme.label("Apply & Save: Save recovery state for any mode")).expandX();
        actionSection.add(this.theme.horizontalSeparator()).expandX();
        WHorizontalList buttonRow1 = actionSection.add(this.theme.horizontalList()).expandX().widget();
        WButton validateBtn = buttonRow1.add(this.theme.button("Validate")).expandX().widget();
        validateBtn.action = this::onValidate;
        WButton snapBtn = buttonRow1.add(this.theme.button("Snap to Path")).expandX().widget();
        snapBtn.action = this::onSnapToNearest;
        WHorizontalList buttonRow2 = actionSection.add(this.theme.horizontalList()).expandX().widget();
        WButton snapCornerBtn = buttonRow2.add(this.theme.button("Snap to Next Corner")).expandX().widget();
        snapCornerBtn.action = this::onSnapToNextCorner;
        WButton applyCornerBtn = buttonRow2.add(this.theme.button("Apply from Corner")).expandX().widget();
        applyCornerBtn.action = this::onApplyFromCorner;
        WHorizontalList buttonRow3 = actionSection.add(this.theme.horizontalList()).expandX().widget();
        WButton applyBtn = buttonRow3.add(this.theme.button("Apply & Save")).expandX().widget();
        applyBtn.action = this::onApplyAndSave;
        WButton cancelBtn = buttonRow3.add(this.theme.button("Cancel")).expandX().widget();
        cancelBtn.action = this::onClose;
        this.rebuildModeSpecificSections();
    }

    private boolean filterNumeric(String text, char c) {
        return Character.isDigit(c) || c == '-' && text.isEmpty();
    }

    private int parseCoord(WTextBox textBox, int defaultValue) {
        try {
            String text = textBox.get().trim();
            return !text.isEmpty() && !text.equals("-") ? Integer.parseInt(text) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void rebuildModeSpecificSections() {
        AreaLoaderModes mode = this.modeDropdown.get();
        this.spiralSection.visible = mode == AreaLoaderModes.Spiral;
        this.spiralCircleSection.visible = mode == AreaLoaderModes.SpiralCircle;
        this.rectangleSection.visible = mode == AreaLoaderModes.Rectangle;
        this.zigzagSection.visible = mode == AreaLoaderModes.ZigZag;
    }

    private void onValidate() {
        AreaLoaderModes mode = this.modeDropdown.get();
        int originX = this.parseCoord(this.originXEdit, 0);
        int originZ = this.parseCoord(this.originZEdit, 0);
        int gap = this.gapEdit.get();
        int currentX = this.parseCoord(this.currentXEdit, 0);
        int currentZ = this.parseCoord(this.currentZEdit, 0);
        int blockGap = 16 * gap;
        switch (mode) {
            case Spiral:
                this.validateSpiral(originX, originZ, currentX, currentZ, blockGap);
                break;
            case SpiralCircle:
                new SpiralCircle().validateManualCoordinates(originX, originZ, currentX, currentZ, blockGap, false);
                break;
            case Rectangle:
                this.validateRectangle(originX, originZ, currentX, currentZ, blockGap);
                break;
            case ZigZag:
                this.validateZigZag(originX, originZ, currentX, currentZ);
        }
    }

    private void onSnapToNearest() {
        AreaLoaderModes mode = this.modeDropdown.get();
        int originX = this.parseCoord(this.originXEdit, 0);
        int originZ = this.parseCoord(this.originZEdit, 0);
        int gap = this.gapEdit.get();
        int currentX = this.parseCoord(this.currentXEdit, 0);
        int currentZ = this.parseCoord(this.currentZEdit, 0);
        int blockGap = 16 * gap;
        switch (mode) {
            case Spiral:
                this.snapSpiralToNearest(originX, originZ, currentX, currentZ, blockGap);
                break;
            case SpiralCircle:
                this.snapSpiralCircleToNearest(originX, originZ, currentX, currentZ, blockGap);
                break;
            case Rectangle:
                this.snapRectangleToNearest(originX, originZ, currentX, currentZ, blockGap);
                break;
            case ZigZag:
                this.snapZigZagToNearest(originX, originZ, currentX, currentZ);
        }
    }

    private void onSnapToNextCorner() {
        AreaLoaderModes mode = this.modeDropdown.get();
        if (mode != AreaLoaderModes.Spiral && mode != AreaLoaderModes.SpiralCircle) {
            ChatUtils.info("Snap to Next Corner is only available for the Spiral modes.");
        } else {
            int originX = this.parseCoord(this.originXEdit, 0);
            int originZ = this.parseCoord(this.originZEdit, 0);
            int gap = this.gapEdit.get();
            int currentX = this.parseCoord(this.currentXEdit, 0);
            int currentZ = this.parseCoord(this.currentZEdit, 0);
            int blockGap = 16 * gap;
            int[] corner = mode == AreaLoaderModes.SpiralCircle
                ? new SpiralCircle().snapToNextCorner(originX, originZ, currentX, currentZ, blockGap)
                : new Spiral().snapToNextCorner(originX, originZ, currentX, currentZ, blockGap);
            if (corner != null) {
                this.currentXEdit.set(String.valueOf(corner[0]));
                this.currentZEdit.set(String.valueOf(corner[1]));
                ChatUtils.info("Resume coordinates set to next corner: (%d, %d)", corner[0], corner[1]);
            }
        }
    }

    private void onApplyFromCorner() {
        AreaLoaderModes mode = this.modeDropdown.get();
        if (mode != AreaLoaderModes.Spiral && mode != AreaLoaderModes.SpiralCircle) {
            ChatUtils.info("Apply from Corner is only available for the Spiral modes.");
        } else {
            int originX = this.parseCoord(this.originXEdit, 0);
            int originZ = this.parseCoord(this.originZEdit, 0);
            int gap = this.gapEdit.get();
            int currentX = this.parseCoord(this.currentXEdit, 0);
            int currentZ = this.parseCoord(this.currentZEdit, 0);
            int blockGap = 16 * gap;
            boolean success = mode == AreaLoaderModes.SpiralCircle
                ? new SpiralCircle().applyFromNextCorner(originX, originZ, currentX, currentZ, blockGap)
                : new Spiral().applyFromNextCorner(originX, originZ, currentX, currentZ, blockGap);
            if (success) {
                this.searchArea.rowGap.set(gap);
                ChatUtils.info("Recovery state saved. Go to the corner and enable the module.");
                this.onClose();
            }
        }
    }

    private void onApplyAndSave() {
        AreaLoaderModes mode = this.modeDropdown.get();
        int originX = this.parseCoord(this.originXEdit, 0);
        int originZ = this.parseCoord(this.originZEdit, 0);
        int gap = this.gapEdit.get();
        int currentX = this.parseCoord(this.currentXEdit, 0);
        int currentZ = this.parseCoord(this.currentZEdit, 0);
        int blockGap = 16 * gap;

        boolean success = switch (mode) {
            case Spiral -> this.applySpiralRecovery(originX, originZ, currentX, currentZ, blockGap);
            case SpiralCircle -> new SpiralCircle().validateManualCoordinates(originX, originZ, currentX, currentZ, blockGap, true);
            case Rectangle -> this.applyRectangleRecovery(originX, originZ, currentX, currentZ, blockGap);
            case ZigZag -> this.applyZigZagRecovery(originX, originZ, currentX, currentZ);
        };
        if (success) {
            this.searchArea.rowGap.set(gap);
            ChatUtils.info("Recovery state saved successfully. Enable the module to resume.");
            this.onClose();
        }
    }

    private void validateSpiral(int originX, int originZ, int targetX, int targetZ, int blockGap) {
        AreaLoader sa = Modules.get().get(AreaLoader.class);
        if (sa != null) {
            Spiral spiral = new Spiral();
            spiral.validateManualCoordinates(originX, originZ, targetX, targetZ, blockGap, false);
        }
    }

    private void snapSpiralToNearest(int originX, int originZ, int targetX, int targetZ, int blockGap) {
        Spiral spiral = new Spiral();
        int[] snapped = spiral.snapToNearestCorner(originX, originZ, targetX, targetZ, blockGap);
        if (snapped != null) {
            this.currentXEdit.set(String.valueOf(snapped[0]));
            this.currentZEdit.set(String.valueOf(snapped[1]));
            ChatUtils.info("Resume coordinates updated to snapped position.");
        }
    }

    private void snapSpiralCircleToNearest(int originX, int originZ, int targetX, int targetZ, int blockGap) {
        int[] snapped = new SpiralCircle().snapToNearestCorner(originX, originZ, targetX, targetZ, blockGap);
        if (snapped != null) {
            this.currentXEdit.set(String.valueOf(snapped[0]));
            this.currentZEdit.set(String.valueOf(snapped[1]));
            ChatUtils.info("Resume coordinates updated to snapped position.");
        }
    }

    private boolean applySpiralRecovery(int originX, int originZ, int targetX, int targetZ, int blockGap) {
        Spiral spiral = new Spiral();
        return spiral.validateManualCoordinates(originX, originZ, targetX, targetZ, blockGap, true);
    }

    private void validateRectangle(int originX, int originZ, int currentX, int currentZ, int blockGap) {
        int endX = this.parseCoord(this.rectEndXEdit, 0);
        int endZ = this.parseCoord(this.rectEndZEdit, 0);
        int minX = Math.min(originX, endX);
        int maxX = Math.max(originX, endX);
        int minZ = Math.min(originZ, endZ);
        int maxZ = Math.max(originZ, endZ);
        boolean withinX = currentX >= minX - blockGap && currentX <= maxX + blockGap;
        boolean withinZ = currentZ >= minZ - blockGap && currentZ <= maxZ + blockGap;
        if (withinX && withinZ) {
            boolean goingPositiveZ = originZ < endZ;
            int zProgress = goingPositiveZ ? currentZ - originZ : originZ - currentZ;
            int rowIndex = zProgress / blockGap;
            int rowZ = originZ + (goingPositiveZ ? 1 : -1) * rowIndex * blockGap;
            boolean startEast = originX < endX;
            boolean onEvenRow = rowIndex % 2 == 0;
            boolean headingEast = startEast == onEvenRow;
            ChatUtils.info("VALID: Position X=%d Z=%d is within rectangle bounds.", currentX, currentZ);
            ChatUtils.info("Row %d (Z=%d), heading %s, Z-progress: %d blocks", rowIndex, rowZ, headingEast ? "East (-90)" : "West (90)", zProgress);
        } else {
            ChatUtils.error("Position X=%d Z=%d is OUTSIDE rectangle bounds.", currentX, currentZ);
            ChatUtils.info("Rectangle: X[%d to %d], Z[%d to %d]", minX, maxX, minZ, maxZ);
        }
    }

    private void snapRectangleToNearest(int originX, int originZ, int currentX, int currentZ, int blockGap) {
        int endX = this.parseCoord(this.rectEndXEdit, 0);
        int endZ = this.parseCoord(this.rectEndZEdit, 0);
        boolean goingPositiveZ = originZ < endZ;
        int zProgress = goingPositiveZ ? currentZ - originZ : originZ - currentZ;
        int rowIndex = Math.round((float)zProgress / blockGap);
        int snappedZ = originZ + (goingPositiveZ ? 1 : -1) * rowIndex * blockGap;
        int minX = Math.min(originX, endX);
        int maxX = Math.max(originX, endX);
        int snappedX = Math.max(minX, Math.min(maxX, currentX));
        this.currentXEdit.set(String.valueOf(snappedX));
        this.currentZEdit.set(String.valueOf(snappedZ));
        ChatUtils.info("Snapped to row %d: X=%d Z=%d", rowIndex, snappedX, snappedZ);
    }

    private boolean applyRectangleRecovery(int originX, int originZ, int currentX, int currentZ, int blockGap) {
        int endX = this.parseCoord(this.rectEndXEdit, 0);
        int endZ = this.parseCoord(this.rectEndZEdit, 0);
        boolean goingPositiveZ = originZ < endZ;
        int zProgress = goingPositiveZ ? currentZ - originZ : originZ - currentZ;
        int rowIndex = Math.round((float)zProgress / blockGap);
        int lastCompleteRowZ = originZ + (goingPositiveZ ? 1 : -1) * rowIndex * blockGap;
        boolean startEast = originX < endX;
        boolean onEvenRow = rowIndex % 2 == 0;
        boolean headingEast = startEast == onEvenRow;
        int currentRowEndX = headingEast ? Math.max(originX, endX) : Math.min(originX, endX);
        boolean atRowEnd = Math.abs(currentX - currentRowEndX) < blockGap / 2;
        boolean mainPath;
        float yaw;
        if (atRowEnd) {
            mainPath = false;
            yaw = goingPositiveZ ? 0.0F : 180.0F;
        } else {
            mainPath = true;
            yaw = headingEast ? -90.0F : 90.0F;
        }

        this.searchArea.startPos.set(new BlockPos(originX, 64, originZ));
        this.searchArea.targetPos.set(new BlockPos(endX, 64, endZ));
        Rectangle.PathingDataRectangle pd = new Rectangle.PathingDataRectangle(
            new BlockPos(originX, 64, originZ), new BlockPos(endX, 64, endZ), new BlockPos(currentX, 64, currentZ), yaw, mainPath, lastCompleteRowZ
        );
        ChatUtils.info("Rectangle recovery: row=%d, yaw=%.0f, mainPath=%b, lastRowZ=%d", rowIndex, yaw, mainPath, lastCompleteRowZ);
        return this.saveRectangleData(pd);
    }

    private boolean saveRectangleData(Rectangle.PathingDataRectangle pd) {
        try {
            File file = this.getRectangleJsonFile();
            if (file == null) {
                ChatUtils.error("Failed to get save file path.");
                return false;
            }

            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Writer writer = new FileWriter(file);
            gson.toJson(pd, writer);
            writer.flush();
            writer.close();
            ChatUtils.info("Saved Rectangle recovery to: " + file.getName());
            return true;
        } catch (Exception e) {
            ChatUtils.error("Failed to save: " + e.getMessage());
            return false;
        }
    }

    private File getRectangleJsonFile() {
        String saveName = this.searchArea.saveLocation.get();
        if (saveName == null || saveName.trim().isEmpty()) {
            saveName = "default";
        }

        File baseDir = new File(MeteorClient.FOLDER, "arealoader");
        File saveDir = new File(baseDir, saveName);
        String dimensionSuffix = this.getDimensionSuffix();
        return new File(saveDir, "Rectangle" + dimensionSuffix + ".json");
    }

    private void validateZigZag(int originX, int originZ, int currentX, int currentZ) {
        int legLength = this.zigzagLegLengthEdit.get();
        int rowGap = this.zigzagRowGapEdit.get();
        AreaRecoveryScreen.CardinalDirection dir = this.zigzagDirectionDropdown.get();
        float mainYaw = dir.yaw;
        float normYaw = this.normalizeYaw(mainYaw);
        boolean mainIsNS = normYaw >= 315.0F || normYaw < 45.0F || normYaw >= 135.0F && normYaw < 225.0F;
        int mainAxisProgress;
        int sideAxisProgress;
        if (mainIsNS) {
            mainAxisProgress = Math.abs(currentZ - originZ);
            sideAxisProgress = Math.abs(currentX - originX);
        } else {
            mainAxisProgress = Math.abs(currentX - originX);
            sideAxisProgress = Math.abs(currentZ - originZ);
        }

        int legsCompleted = sideAxisProgress / rowGap;
        int positionInCurrentLeg = mainAxisProgress % legLength;
        boolean likelyOnMainLeg = positionInCurrentLeg > rowGap / 2;
        ChatUtils.info("ZigZag validation:");
        ChatUtils.info("  Main direction: %s (yaw=%.0f)", dir.name(), mainYaw);
        ChatUtils.info("  Progress: main=%d, side=%d blocks from origin", mainAxisProgress, sideAxisProgress);
        ChatUtils.info("  ~%d legs completed, position in leg: %d/%d blocks", legsCompleted, positionInCurrentLeg, legLength);
        ChatUtils.info("  Likely on %s leg", likelyOnMainLeg ? "main" : "side");
    }

    private void snapZigZagToNearest(int originX, int originZ, int currentX, int currentZ) {
        int legLength = this.zigzagLegLengthEdit.get();
        int rowGap = this.zigzagRowGapEdit.get();
        AreaRecoveryScreen.CardinalDirection dir = this.zigzagDirectionDropdown.get();
        float normYaw = this.normalizeYaw(dir.yaw);
        boolean mainIsNS = normYaw >= 315.0F || normYaw < 45.0F || normYaw >= 135.0F && normYaw < 225.0F;
        int snappedX;
        int snappedZ;
        if (mainIsNS) {
            int xDiff = currentX - originX;
            int rowsCompleted = Math.round((float)xDiff / rowGap);
            snappedX = originX + rowsCompleted * rowGap;
            snappedZ = currentZ;
        } else {
            int zDiff = currentZ - originZ;
            int rowsCompleted = Math.round((float)zDiff / rowGap);
            snappedZ = originZ + rowsCompleted * rowGap;
            snappedX = currentX;
        }

        this.currentXEdit.set(String.valueOf(snappedX));
        this.currentZEdit.set(String.valueOf(snappedZ));
        ChatUtils.info("Snapped to: X=%d Z=%d", snappedX, snappedZ);
    }

    private boolean applyZigZagRecovery(int originX, int originZ, int currentX, int currentZ) {
        int legLength = this.zigzagLegLengthEdit.get();
        int rowGap = this.zigzagRowGapEdit.get();
        AreaRecoveryScreen.CardinalDirection dir = this.zigzagDirectionDropdown.get();
        float mainYaw = dir.yaw;
        float sideYaw = this.normalizeYaw(mainYaw + 90.0F);
        float normYaw = this.normalizeYaw(mainYaw);
        boolean mainIsNS = normYaw >= 315.0F || normYaw < 45.0F || normYaw >= 135.0F && normYaw < 225.0F;
        int sideAxisProgress;
        if (mainIsNS) {
            int mainAxisProgress = Math.abs(currentZ - originZ);
            sideAxisProgress = Math.abs(currentX - originX);
            int sideAxisDiff = currentX - originX;
        } else {
            int mainAxisProgress = Math.abs(currentX - originX);
            sideAxisProgress = Math.abs(currentZ - originZ);
            int sideAxisDiff = currentZ - originZ;
        }

        int legsCompleted = sideAxisProgress / rowGap;
        boolean goingForward = legsCompleted % 2 == 0;
        float currentYaw;
        if (goingForward) {
            currentYaw = mainYaw;
        } else {
            currentYaw = this.normalizeYaw(mainYaw + 180.0F);
        }

        boolean onMainLeg = true;
        this.searchArea.zigzagLegLength.set(legLength);
        this.searchArea.zigzagRowGap.set(rowGap);
        ZigZag.PathingDataZigZag pd = new ZigZag.PathingDataZigZag(
            new BlockPos(originX, 64, originZ), new BlockPos(currentX, 64, currentZ), currentYaw, mainYaw, sideYaw, goingForward, onMainLeg, legsCompleted
        );
        pd.legStartPos = new BlockPos(currentX, 64, currentZ);
        ChatUtils.info(
            "ZigZag recovery: legs=%d, goingForward=%b, yaw=%.0f, mainYaw=%.0f, sideYaw=%.0f", legsCompleted, goingForward, currentYaw, mainYaw, sideYaw
        );
        return this.saveZigZagData(pd);
    }

    private boolean saveZigZagData(ZigZag.PathingDataZigZag pd) {
        try {
            File file = this.getZigZagJsonFile();
            if (file == null) {
                ChatUtils.error("Failed to get save file path.");
                return false;
            }

            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Writer writer = new FileWriter(file);
            gson.toJson(pd, writer);
            writer.flush();
            writer.close();
            ChatUtils.info("Saved ZigZag recovery to: " + file.getName());
            return true;
        } catch (Exception e) {
            ChatUtils.error("Failed to save: " + e.getMessage());
            return false;
        }
    }

    private File getZigZagJsonFile() {
        String saveName = this.searchArea.saveLocation.get();
        if (saveName == null || saveName.trim().isEmpty()) {
            saveName = "default";
        }

        File baseDir = new File(MeteorClient.FOLDER, "arealoader");
        File saveDir = new File(baseDir, saveName);
        String dimensionSuffix = this.getDimensionSuffix();
        return new File(saveDir, "ZigZag" + dimensionSuffix + ".json");
    }

    private String getDimensionSuffix() {
        if (MeteorClient.mc.level == null) {
            return "";
        }

        try {
            if (MeteorClient.mc.level.dimension().equals(Level.NETHER)) {
                return "_nether";
            } else {
                return MeteorClient.mc.level.dimension().equals(Level.END) ? "_end" : "_overworld";
            }
        } catch (Exception e) {
            return "";
        }
    }

    private float normalizeYaw(float yaw) {
        yaw %= 360.0F;
        if (yaw < 0.0F) {
            yaw += 360.0F;
        }

        return yaw;
    }

    public enum CardinalDirection {
        NORTH(180.0F),
        SOUTH(0.0F),
        EAST(270.0F),
        WEST(90.0F);

        public final float yaw;

        CardinalDirection(float yaw) {
            this.yaw = yaw;
        }
    }
}
