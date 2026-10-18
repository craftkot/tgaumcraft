package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.AspectRegistry;
import dev.ehtid.thaumcraft.port.core.BuiltInResearchCatalog;
import dev.ehtid.thaumcraft.port.core.BuiltInResearchCategories;
import dev.ehtid.thaumcraft.port.core.PlayerKnowledgeData;
import dev.ehtid.thaumcraft.port.core.ResearchBrowserModel;
import dev.ehtid.thaumcraft.port.core.ResearchBrowserPagination;
import dev.ehtid.thaumcraft.port.core.LegacyResearchLayout;
import dev.ehtid.thaumcraft.port.core.LegacyResearchPages;
import dev.ehtid.thaumcraft.port.core.ResearchCatalog;
import dev.ehtid.thaumcraft.port.core.ResearchCategoryRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * First read-only Thaumonomicon category browser. All 148 entries and
 * seven original category unlock gates come from the BETA26 catalog.
 * It does not manufacture entries, unlock research, or impersonate the
 * not-yet-ported original spatial graph/chapter pages.
 */
public final class ResearchIndexScreen extends Screen {
    private static final ResourceLocation BOOK = ResourceLocation.fromNamespaceAndPath(
            "thaumcraft", "textures/gui/gui_researchbook.png");
    private static final ResearchCatalog CATALOG = BuiltInResearchCatalog.create();
    private static final ResearchCategoryRegistry CATEGORIES =
            BuiltInResearchCategories.create(new AspectRegistry());
    private static final int BOOK_W = 512, BOOK_H = 356;
    private static final int PAGE_SIZE = 10;
    // BETA26 research coordinates are whole grid units; 28 is display scale.
    private static final int GRID_PIXELS = 28;
    private static final int GRAPH_LEFT = 221, GRAPH_TOP = 61;
    private static final int GRAPH_RIGHT = 464, GRAPH_BOTTOM = 281;
    private final Screen parent;
    private final List<Button> categoryButtons = new ArrayList<>();
    private Button previousPage, nextPage, viewToggle, readPage;
    private final List<Button> panButtons = new ArrayList<>();
    private boolean graphView = true;
    private int graphPanX, graphPanY;
    private String selectedCategory = "BASICS";
    private String selectedEntry;
    private int page;

    public ResearchIndexScreen(Screen parent) {
        super(Component.translatable("item.thaumcraft.thaumonomicon"));
        this.parent = parent;
    }

    private PlayerKnowledgeData knowledge() {
        return minecraft == null || minecraft.player == null ? null
                : minecraft.player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
    }

    private List<ResearchBrowserModel.Entry> currentEntries() {
        PlayerKnowledgeData data = knowledge();
        return data == null ? List.of()
                : ResearchBrowserModel.entries(selectedCategory, data, CATALOG, CATEGORIES);
    }

    @Override
    protected void init() {
        super.init();
        // Screen.init is called again on resize: old button objects must
        // not leak into the new list or exceed the seven category slots.
        categoryButtons.clear();
        int x = (width - BOOK_W) / 2, y = (height - BOOK_H) / 2;
        var categories = ResearchBrowserModel.categories(
                knowledge() == null ? new PlayerKnowledgeData() : knowledge(), CATALOG, CATEGORIES);
        for (int i = 0; i < categories.size(); i++) {
            var category = categories.get(i);
            var button = Button.builder(Component.translatable(category.titleKey()), b -> {
                selectedCategory = category.key();
                selectedEntry = null;
                page = 0;
                graphPanX = graphPanY = 0;
                updateButtons();
            }).bounds(x + 39, y + 66 + i * 31, 162, 23).build();
            categoryButtons.add(addRenderableWidget(button));
        }
        previousPage = addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            page = Math.max(0, page - 1);
            selectedEntry = null;
            updateButtons();
        }).bounds(x + 229, y + 308, 35, 20).build());
        nextPage = addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            page++;
            selectedEntry = null;
            updateButtons();
        }).bounds(x + 398, y + 308, 35, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(x + 41, y + 307, 150, 20).build());
        readPage = addRenderableWidget(Button.builder(Component.literal("Read page"), b -> {
            if (selectedEntry != null && currentEntries().stream().anyMatch(
                    e -> e.key().equals(selectedEntry)))
                minecraft.setScreen(new ResearchPageScreen(this, selectedEntry));
        }).bounds(x + 41, y + 283, 150, 19).build());
        viewToggle = addRenderableWidget(Button.builder(
                Component.literal(graphView ? "List" : "Tree"), b -> {
                    graphView = !graphView;
                    updateButtons();
                }).bounds(x + 395, y + 284, 67, 20).build());
        panButtons.clear();
        addPanButton(x + 225, y + 307, "<", -3, 0);
        addPanButton(x + 265, y + 307, ">", 3, 0);
        addPanButton(x + 305, y + 307, "^", 0, -3);
        addPanButton(x + 345, y + 307, "v", 0, 3);
        updateButtons();
    }

    private void addPanButton(int x, int y, String text, int dx, int dy) {
        panButtons.add(addRenderableWidget(Button.builder(Component.literal(text), b -> {
            graphPanX = Math.max(-18, Math.min(18, graphPanX + dx));
            graphPanY = Math.max(-18, Math.min(18, graphPanY + dy));
        }).bounds(x, y, 33, 20).build()));
    }

    private void updateButtons() {
        PlayerKnowledgeData data = knowledge();
        var categories = ResearchBrowserModel.categories(
                data == null ? new PlayerKnowledgeData() : data, CATALOG, CATEGORIES);
        for (int i = 0; i < categoryButtons.size(); i++) {
            var category = categories.get(i);
            categoryButtons.get(i).active = category.unlocked();
        }
        var entries = currentEntries();
        var viewport = ResearchBrowserPagination.of(entries.size(), page, PAGE_SIZE);
        page = viewport.index();
        // A research could disappear after a fresh server sync (or switching
        // categories). Do not keep a stale selection of an invisible entry.
        if (selectedEntry != null && entries.stream().noneMatch(
                entry -> entry.key().equals(selectedEntry))) selectedEntry = null;
        if (previousPage != null) {
            previousPage.visible = !graphView;
            previousPage.active = !graphView && viewport.hasPrevious();
        }
        if (nextPage != null) {
            nextPage.visible = !graphView;
            nextPage.active = !graphView && viewport.hasNext();
        }
        if (viewToggle != null)
            viewToggle.setMessage(Component.literal(graphView ? "List" : "Tree"));
        for (Button pan : panButtons) pan.visible = graphView;
        if (readPage != null) readPage.active = selectedEntry != null
                && LegacyResearchPages.get(selectedEntry) != null
                && entries.stream().anyMatch(e -> e.key().equals(selectedEntry));
    }

    @Override
    public void tick() {
        super.tick();
        updateButtons(); // refreshed player knowledge arrives from the server
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        int x = (width - BOOK_W) / 2, y = (height - BOOK_H) / 2;
        graphics.blit(BOOK, x, y, 0, 0, BOOK_W, BOOK_H, 512, 512);
        graphics.drawString(font, title, x + 46, y + 28, 0x49321E, false);
        graphics.drawString(font, Component.translatable("tc.research_category." + selectedCategory),
                x + 226, y + 44, 0x49321E, false);

        var entries = currentEntries();
        if (graphView) {
            renderResearchGraph(graphics, entries, x, y);
        } else {
        var viewport = ResearchBrowserPagination.of(entries.size(), page, PAGE_SIZE);
        int offset = viewport.fromIndex();
        for (int i = 0; i < viewport.visibleCount(); i++) {
            var entry = entries.get(offset + i);
            int rowY = y + 65 + i * 23;
            boolean selected = entry.key().equals(selectedEntry);
            graphics.fill(x + 221, rowY - 2, x + 456, rowY + 16,
                    selected ? 0xAAC0AF81 : 0x44C0AF81);
            String name = font.plainSubstrByWidth(entry.key(), 164);
            graphics.drawString(font, name, x + 227, rowY + 2, 0x35271B, false);
            String status = entry.complete() ? "OK"
                    : entry.known() ? Math.max(0, entry.stage()) + "/"
                        + Math.max(0, entry.stageCount()) : "?";
            graphics.drawString(font, status, x + 403, rowY + 2, 0x35271B, false);
        }
        if (selectedEntry != null) {
            var selected = entries.stream().filter(e -> e.key().equals(selectedEntry))
                    .findFirst().orElse(null);
            if (selected != null && !selected.parents().isEmpty()) {
                String parents = String.join(", ", selected.parents());
                graphics.drawString(font, font.plainSubstrByWidth(parents, 160),
                        x + 226, y + 291, 0x49321E, false);
            }
        }
        graphics.drawString(font, (page + 1) + "/"
                + viewport.pageCount(),
                x + 310, y + 314, 0x49321E, false);
        }
        super.render(graphics, mouseX, mouseY, delta);
        if (graphView) renderNodeTooltip(graphics, entries, x, y, mouseX, mouseY);
    }

    /**
     * Coordinates are the ORIGINAL source JSON x/y, not arbitrary positions.
     * This graph uses temporary rectangles rather than non-ported original
     * research icons. Only visible nodes and valid visible parents are drawn.
     */
    private void renderResearchGraph(GuiGraphics graphics,
                                     List<ResearchBrowserModel.Entry> entries,
                                     int left, int top) {
        int centerX = left + (GRAPH_LEFT + GRAPH_RIGHT) / 2;
        int centerY = top + (GRAPH_TOP + GRAPH_BOTTOM) / 2;
        Map<String, ResearchBrowserModel.Entry> visible = new HashMap<>();
        for (var entry : entries) visible.put(entry.key(), entry);
        graphics.enableScissor(left + GRAPH_LEFT, top + GRAPH_TOP,
                left + GRAPH_RIGHT, top + GRAPH_BOTTOM);
        for (var child : entries) {
            var childNode = LegacyResearchLayout.get(child.key());
            if (childNode == null) continue; // fail closed on missing source
            int childX = centerX + (childNode.x() - graphPanX) * GRID_PIXELS;
            int childY = centerY + (childNode.y() - graphPanY) * GRID_PIXELS;
            for (String rawParent : child.parents()) {
                String key = rawParent.split("@", 2)[0];
                if (!visible.containsKey(key)) continue;
                var parentNode = LegacyResearchLayout.get(key);
                if (parentNode == null
                        || !parentNode.category().equals(childNode.category())) continue;
                int parentX = centerX + (parentNode.x() - graphPanX) * GRID_PIXELS;
                int parentY = centerY + (parentNode.y() - graphPanY) * GRID_PIXELS;
                graphics.hLine(Math.min(parentX, childX), Math.max(parentX, childX),
                        parentY, 0xAA8F7C62);
                graphics.vLine(childX, Math.min(parentY, childY),
                        Math.max(parentY, childY), 0xAA8F7C62);
            }
        }
        for (var entry : entries) {
            var node = LegacyResearchLayout.get(entry.key());
            if (node == null) continue;
            int cx = centerX + (node.x() - graphPanX) * GRID_PIXELS;
            int cy = centerY + (node.y() - graphPanY) * GRID_PIXELS;
            int color = entry.complete() ? 0xFF518363
                    : entry.known() ? 0xFFB9A05F : 0xFF69665F;
            graphics.fill(cx - 9, cy - 9, cx + 10, cy + 10, 0xFF342C23);
            graphics.fill(cx - 7, cy - 7, cx + 8, cy + 8, color);
            if (entry.key().equals(selectedEntry))
                graphics.renderOutline(cx - 11, cy - 11, 23, 23, 0xFFEEE1B9);
        }
        graphics.disableScissor();
        if (selectedEntry != null) {
            var selected = visible.get(selectedEntry);
            if (selected != null) {
                String status = selected.complete() ? "OK"
                        : selected.known() ? selected.stage() + "/"
                        + selected.stageCount() : "?";
                graphics.drawString(font, font.plainSubstrByWidth(
                                selected.key() + "  " + status, 160),
                        left + GRAPH_LEFT + 4, top + GRAPH_BOTTOM + 9, 0x49321E, false);
            }
        }
    }

    private void renderNodeTooltip(GuiGraphics graphics,
                                   List<ResearchBrowserModel.Entry> entries,
                                   int left, int top, int mouseX, int mouseY) {
        var hovered = nodeAt(entries, left, top, mouseX, mouseY);
        if (hovered == null) return;
        var node = LegacyResearchLayout.get(hovered.key());
        if (node != null)
            graphics.renderTooltip(font, Component.translatable(node.titleKey()),
                    mouseX, mouseY);
    }

    private ResearchBrowserModel.Entry nodeAt(
            List<ResearchBrowserModel.Entry> entries, int left, int top,
            double mouseX, double mouseY) {
        if (mouseX < left + GRAPH_LEFT || mouseX >= left + GRAPH_RIGHT
                || mouseY < top + GRAPH_TOP || mouseY >= top + GRAPH_BOTTOM)
            return null;
        int cx = left + (GRAPH_LEFT + GRAPH_RIGHT) / 2;
        int cy = top + (GRAPH_TOP + GRAPH_BOTTOM) / 2;
        for (var entry : entries) {
            var node = LegacyResearchLayout.get(entry.key());
            if (node == null) continue;
            int px = cx + (node.x() - graphPanX) * GRID_PIXELS;
            int py = cy + (node.y() - graphPanY) * GRID_PIXELS;
            if (mouseX >= px - 10 && mouseX < px + 11
                    && mouseY >= py - 10 && mouseY < py + 11)
                return entry;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (mouseButton == 0) {
            int x = (width - BOOK_W) / 2, y = (height - BOOK_H) / 2;
            if (graphView) {
                var node = nodeAt(currentEntries(), x, y, mouseX, mouseY);
                if (node != null) {
                    selectedEntry = node.key();
                    return true;
                }
            } else if (mouseX >= x + GRAPH_LEFT && mouseX < x + GRAPH_RIGHT
                    && mouseY >= y + 63 && mouseY < y + 63 + PAGE_SIZE * 23) {
                var entries = currentEntries();
                var viewport = ResearchBrowserPagination.of(entries.size(), page, PAGE_SIZE);
                int index = viewport.entryIndexAtRow((int) (mouseY - (y + 63)) / 23);
                if (index >= 0) {
                    selectedEntry = entries.get(index).key();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
