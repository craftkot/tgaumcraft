package dev.ehtid.thaumcraft.port.core;

import java.util.List;

/** Loader-independent representation of thaumcraft.api.aspects.Aspect from BETA26. */
public final class AspectData {
    private String tag;
    private List<AspectData> components; // null for the six legacy primals
    private final int color;
    private String chatColor;
    private int blend;

    AspectData(String tag, int color, List<AspectData> components, String chatColor, int blend) {
        this.tag = tag;
        this.color = color;
        this.components = components == null ? null : List.copyOf(components);
        this.chatColor = chatColor;
        this.blend = blend;
    }

    public String tag() { return tag; }
    public int color() { return color; }
    public String chatColor() { return chatColor; }
    public int blend() { return blend; }
    public List<AspectData> components() { return components; }

    /** Preserved mutable API surface from TC6. Registry keys are intentionally not rewritten. */
    public void setTag(String tag) { this.tag = tag; }
    public void setComponents(List<AspectData> components) {
        this.components = components == null ? null : List.copyOf(components);
    }
    public void setChatColor(String chatColor) { this.chatColor = chatColor; }
    public void setBlend(int blend) { this.blend = blend; }

    /** BETA26 considers anything without exactly two components primal. */
    public boolean isPrimal() {
        return components == null || components.size() != 2;
    }

    @Override
    public String toString() { return tag; }
}
