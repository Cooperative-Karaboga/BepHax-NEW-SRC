package bep.hax.modules.arealoader;

public enum AreaLoaderModes {
    Rectangle("Rectangle"),
    Spiral("Spiral (Squared)"),
    SpiralCircle("Spiral (Circle)"),
    ZigZag("ZigZag");

    private final String title;

    AreaLoaderModes(String title) {
        this.title = title;
    }

    public String fileName() {
        return this.name();
    }

    @Override
    public String toString() {
        return this.title;
    }
}
