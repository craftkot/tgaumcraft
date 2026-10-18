package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.AspectRegistry;
import dev.ehtid.thaumcraft.port.core.BuiltInResearchCatalog;
import dev.ehtid.thaumcraft.port.core.BuiltInResearchCategories;
import dev.ehtid.thaumcraft.port.core.PlayerKnowledgeData;
import dev.ehtid.thaumcraft.port.core.ResearchBrowserModel;
import dev.ehtid.thaumcraft.port.core.ResearchBrowserPagination;
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
    private final Screen parent;
    private final List<Button> categoryButtons = new ArrayList<>();
    private Button previousPage, nextPage;
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
        updateButtons();
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
        if (previousPage != null) previousPage.active = viewport.hasPrevious();
        if (nextPage != null) nextPage.active = viewport.hasNext();
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
                graphics.drawString(font, font.plainSubstrByWidth(parents, 222),
                        x + 226, y + 291, 0x49321E, false);
            }
        }
        graphics.drawString(font, (page + 1) + "/"
                + viewport.pageCount(),
                x + 310, y + 314, 0x49321E, false);
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (mouseButton == 0) {
            int x = (width - BOOK_W) / 2, y = (height - BOOK_H) / 2;
            if (mouseX >= x + 221 && mouseX < x + 456 && mouseY >= y + 63
                    && mouseY < y + 63 + PAGE_SIZE * 23) {
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
