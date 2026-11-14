package dev.ehtid.thaumcraft.port.core.golem;

import java.util.List;

/** ConfigRecipes GolemPress and Matrix.Rotate90DegRight, relative to the 2x2 lower corner. */
public final class GolemPressBlueprint {
    public static final String RESEARCH = "MINDCLOCKWORK";
    public static final int DELAY = 50;
    public enum Part { CAULDRON, ANVIL, CONTROLLER, TABLE, BARS }
    public record Cell(Part part, int x, int y, int z) {}
    private static final List<Cell> BASE = List.of(new Cell(Part.CAULDRON, 0, 0, 0),
            new Cell(Part.ANVIL, 0, 0, 1), new Cell(Part.CONTROLLER, 1, 0, 0),
            new Cell(Part.TABLE, 1, 0, 1), new Cell(Part.BARS, 1, 1, 0));
    private GolemPressBlueprint() {}
    public static List<Cell> cells(int rotation) {
        if (rotation < 0 || rotation > 3) throw new IllegalArgumentException("Blueprint rotation");
        return BASE.stream().map(cell -> {
            int x = cell.x, z = cell.z;
            for (int turn = 0; turn < rotation; turn++) { int previousX = x; x = z; z = 1 - previousX; }
            return new Cell(cell.part, x, cell.y, z);
        }).toList();
    }
}
