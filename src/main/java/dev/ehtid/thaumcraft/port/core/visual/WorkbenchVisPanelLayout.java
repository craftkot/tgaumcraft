package dev.ehtid.thaumcraft.port.core.visual;

/**
 * Pixel geometry for the BETA26 Arcane Workbench's Vis readout.
 *
 * The original 190x234 screen has six animated crystal sockets around the
 * green dial and an unused wooden strip on the right. Keep the readout in
 * that strip instead of painting over the three lower crystal requirements.
 */
public final class WorkbenchVisPanelLayout {
    public static final int GUI_WIDTH = 190;
    public static final int GUI_HEIGHT = 234;

    public static final int PANEL_X = 144;
    public static final int PANEL_Y = 42;
    public static final int PANEL_WIDTH = 46;
    public static final int PANEL_HEIGHT = 54;

    public static final int TEXT_X = 146;
    public static final int TEXT_WIDTH = 43;
    public static final int AVAILABLE_Y = 46;
    public static final int COST_Y = 70;
    public static final int MAX_LINES = 2;

    private static final int[] ASPECT_X = {72, 24, 24, 72, 120, 120};
    private static final int[] ASPECT_Y = {21, 43, 102, 124, 102, 43};
    private static final double ICON_CENTER_X = 7.5;
    private static final double ICON_CENTER_Y = 8.0;
    private static final double ICON_HALF_SIZE = 16.0;

    private WorkbenchVisPanelLayout() {}

    public static int aspectX(int index) {
        return ASPECT_X[index];
    }

    public static int aspectY(int index) {
        return ASPECT_Y[index];
    }

    /** True when the readout rectangle intersects any 32x32 rendered crystal icon. */
    public static boolean panelOverlapsCrystalIcons() {
        double panelLeft = PANEL_X;
        double panelTop = PANEL_Y;
        double panelRight = PANEL_X + PANEL_WIDTH;
        double panelBottom = PANEL_Y + PANEL_HEIGHT;
        for (int i = 0; i < ASPECT_X.length; i++) {
            double centerX = ASPECT_X[i] + ICON_CENTER_X;
            double centerY = ASPECT_Y[i] + ICON_CENTER_Y;
            double iconLeft = centerX - ICON_HALF_SIZE;
            double iconTop = centerY - ICON_HALF_SIZE;
            double iconRight = centerX + ICON_HALF_SIZE;
            double iconBottom = centerY + ICON_HALF_SIZE;
            if (panelLeft < iconRight && iconLeft < panelRight
                    && panelTop < iconBottom && iconTop < panelBottom) {
                return true;
            }
        }
        return false;
    }
}
