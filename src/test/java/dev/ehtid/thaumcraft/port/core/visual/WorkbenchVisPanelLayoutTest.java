package dev.ehtid.thaumcraft.port.core.visual;

public final class WorkbenchVisPanelLayoutTest {
    private static int assertions;

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(WorkbenchVisPanelLayout.PANEL_X >= 0, "panel left outside GUI");
        check(WorkbenchVisPanelLayout.PANEL_Y >= 0, "panel top outside GUI");
        check(WorkbenchVisPanelLayout.PANEL_X + WorkbenchVisPanelLayout.PANEL_WIDTH
                        <= WorkbenchVisPanelLayout.GUI_WIDTH,
                "panel right outside GUI");
        check(WorkbenchVisPanelLayout.PANEL_Y + WorkbenchVisPanelLayout.PANEL_HEIGHT
                        <= WorkbenchVisPanelLayout.GUI_HEIGHT,
                "panel bottom outside GUI");

        check(WorkbenchVisPanelLayout.TEXT_X >= WorkbenchVisPanelLayout.PANEL_X + 1,
                "text overlaps left border");
        check(WorkbenchVisPanelLayout.TEXT_X + WorkbenchVisPanelLayout.TEXT_WIDTH
                        <= WorkbenchVisPanelLayout.PANEL_X
                        + WorkbenchVisPanelLayout.PANEL_WIDTH - 1,
                "shadow-inclusive text budget crosses right border");
        check(WorkbenchVisPanelLayout.AVAILABLE_Y >= WorkbenchVisPanelLayout.PANEL_Y + 1,
                "available text overlaps top border");
        check(WorkbenchVisPanelLayout.COST_Y
                        + (WorkbenchVisPanelLayout.MAX_LINES - 1) * 10 + 10
                        <= WorkbenchVisPanelLayout.PANEL_Y
                        + WorkbenchVisPanelLayout.PANEL_HEIGHT - 1,
                "two wrapped fields exceed panel height");

        for (int i = 0; i < 6; i++) {
            int x = WorkbenchVisPanelLayout.aspectX(i);
            int y = WorkbenchVisPanelLayout.aspectY(i);
            check(x >= 0 && x < WorkbenchVisPanelLayout.GUI_WIDTH,
                    "aspect x outside GUI: " + i);
            check(y >= 0 && y < WorkbenchVisPanelLayout.GUI_HEIGHT,
                    "aspect y outside GUI: " + i);
        }
        check(!WorkbenchVisPanelLayout.panelOverlapsCrystalIcons(),
                "Vis readout hides one or more BETA26 crystal requirement icons");
        System.out.println("Workbench Vis panel geometry: OK (" + assertions + " assertions)");
    }
}
