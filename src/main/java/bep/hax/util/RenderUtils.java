package bep.hax.util;

import meteordevelopment.meteorclient.renderer.ShapeMode;

public class RenderUtils {
    public static ShapeMode shapeMode(boolean fill, boolean outline) {
        if (fill && outline) {
            return ShapeMode.Both;
        } else if (fill) {
            return ShapeMode.Sides;
        } else {
            return outline ? ShapeMode.Lines : null;
        }
    }
}
