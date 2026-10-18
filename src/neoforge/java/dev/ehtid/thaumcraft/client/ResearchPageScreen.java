package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.AspectRegistry;
import dev.ehtid.thaumcraft.port.core.BuiltInResearchCatalog;
import dev.ehtid.thaumcraft.port.core.BuiltInResearchCategories;
import dev.ehtid.thaumcraft.port.core.LegacyResearchLayout;
import dev.ehtid.thaumcraft.port.core.LegacyResearchPages;
import dev.ehtid.thaumcraft.port.core.PlayerKnowledgeData;
import dev.ehtid.thaumcraft.port.core.ResearchBrowserModel;
import dev.ehtid.thaumcraft.port.core.ResearchPageVisibility;
import dev.ehtid.thaumcraft.port.core.ResearchAddendumVisibility;
import dev.ehtid.thaumcraft.port.core.ResearchKnowledgeRequirementStatus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only BETA26 research stage metadata viewer. Text keys, item specs and
 * recipe IDs are original source data, NEVER invented modern recipes.
 *
 * This is not the complete original BookPage rendering: translated source
 * texts and actual recipe/item preview widgets are still required. Unknown
 * research and future stages are hidden by the synchronized player knowledge.
 */
public final class ResearchPageScreen extends Screen {
    private static final ResourceLocation BOOK = ResourceLocation.fromNamespaceAndPath(
            "thaumcraft", "textures/gui/gui_researchbook.png");
    private static final dev.ehtid.thaumcraft.port.core.ResearchCatalog CATALOG =
            BuiltInResearchCatalog.create();
    private static final dev.ehtid.thaumcraft.port.core.ResearchCategoryRegistry CATEGORIES =
            BuiltInResearchCategories.create(new AspectRegistry());
    private static final int BOOK_W = 512, BOOK_H = 356;
    private static final int LINES_ON_PAGE = 12;
    private static final int TEXT_WIDTH = 398;
    private final Screen parent;
    private final String researchKey;
    private final LegacyResearchPages.Page page;
    private int stageIndex;
    private int scroll;
    private Button earlier, later, scrollUp, scrollDown;

    public ResearchPageScreen(Screen parent, String researchKey) {
        super(Component.translatable("item.thaumcraft.thaumonomicon"));
        this.parent = parent;
        this.researchKey = researchKey;
        this.page = LegacyResearchPages.get(researchKey);
    }

    private PlayerKnowledgeData knowledge() {
        return minecraft == null || minecraft.player == null ? null
                : minecraft.player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
    }

    private int allowedStage() {
        var node = LegacyResearchLayout.get(researchKey);
        var data = knowledge();
        if (node == null || page == null || data == null) return -1;
        boolean visible = ResearchBrowserModel.entries(
                node.category(), data, CATALOG, CATEGORIES).stream()
                .anyMatch(e -> e.key().equals(researchKey));
        return visible ? ResearchPageVisibility.maximumStage(page, data, CATALOG) : -1;
    }

    private List<Component> detailLines() {
        int allowed = allowedStage();
        if (page == null || allowed < 0) return List.of(
                Component.literal("Research not discovered. Page contents are hidden."));
        // Synchronization may revoke stages between tick and render. Recheck
        // the current bound at the actual point of disclosure.
        int safeIndex = ResearchPageVisibility.clampRequestedStage(stageIndex, allowed);
        var stage = page.stages().get(safeIndex);
        ArrayList<Component> lines = new ArrayList<>();
        // The source research translations have not yet been shipped. Render
        // their actual translation keys until real BETA26 translations arrive.
        lines.add(Component.translatable(stage.textKey()));
        lines.add(Component.literal("Source text key: " + stage.textKey()));
        var data = knowledge();
        for (String key : stage.requiredKnowledge())
            lines.add(knowledgeRequirement("Knowledge", key, data));
        for (String key : stage.auramKnowledge())
            lines.add(knowledgeRequirement("Auram knowledge", key, data));
        for (String item : stage.requiredItemSpecs())
            lines.add(Component.literal("Item spec: " + item));
        for (String craft : stage.craftIds())
            lines.add(Component.literal("Required craft: " + craft));
        for (String required : stage.requiredResearch())
            lines.add(Component.literal("Required research: " + required));
        if (stage.warp() != 0)
            lines.add(Component.literal("Source warp: " + stage.warp()));
        for (String recipe : stage.recipeIds())
            lines.add(Component.literal("Recipe ID: " + recipe));

        // BETA26 addenda are part of completed research pages, not new
        // stages. Only the server's PAGE flag + EACH completed source
        // prerequisite can reveal a specific original addendum.
        if (data != null && safeIndex == page.stages().size() - 1) {
            for (var addon : ResearchAddendumVisibility.visible(page, data, CATALOG)) {
                lines.add(Component.literal("Addendum: " + addon.textKey()));
                for (String recipe : addon.recipeIds())
                    lines.add(Component.literal("Addendum recipe: " + recipe));
            }
        }
        return List.copyOf(lines);
    }

    private Component knowledgeRequirement(String label, String key, PlayerKnowledgeData data) {
        if (data == null) return Component.literal(label + ": " + key);
        var current = ResearchKnowledgeRequirementStatus.inspect(key, data, CATEGORIES);
        if (current == null) return Component.literal(label + ": " + key);
        return Component.literal(label + ": " + current.type() + " "
                + current.category() + " " + current.currentLevels() + "/"
                + current.requiredLevels()
                + (current.satisfied() ? " [OK]" : " [NEEDED]"));
    }

    /** Wrap actual lore once translations are installed; do not truncate source
     * item SNBT/knowledge/recipe requirements into a single unreadable row.
     * Font.split preserves formatted Minecraft text and works for long tokens.
     */
    private List<FormattedCharSequence> wrappedLines() {
        ArrayList<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component component : detailLines())
            wrapped.addAll(font.split(component, TEXT_WIDTH));
        return List.copyOf(wrapped);
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - BOOK_W) / 2, y = (height - BOOK_H) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),
                b -> onClose()).bounds(x + 44, y + 317, 120, 20).build());
        earlier = addRenderableWidget(Button.builder(Component.literal("Prev stage"), b -> {
            stageIndex = Math.max(0, stageIndex - 1);
            scroll = 0;
            refresh();
        }).bounds(x + 175, y + 317, 75, 20).build());
        later = addRenderableWidget(Button.builder(Component.literal("Next stage"), b -> {
            stageIndex++;
            scroll = 0;
            refresh();
        }).bounds(x + 258, y + 317, 75, 20).build());
        scrollUp = addRenderableWidget(Button.builder(Component.literal("Up"), b -> {
            scroll = Math.max(0, scroll - 4);
            refresh();
        }).bounds(x + 346, y + 317, 50, 20).build());
        scrollDown = addRenderableWidget(Button.builder(Component.literal("Down"), b -> {
            scroll += 4;
            refresh();
        }).bounds(x + 403, y + 317, 57, 20).build());
        refresh();
    }

    private void refresh() {
        int allowed = allowedStage();
        stageIndex = ResearchPageVisibility.clampRequestedStage(stageIndex, allowed);
        if (earlier != null) earlier.active = allowed >= 0 && stageIndex > 0;
        if (later != null) later.active = allowed >= 0 && stageIndex < allowed;
        int longest = Math.max(0, wrappedLines().size() - LINES_ON_PAGE);
        scroll = Math.max(0, Math.min(scroll, longest));
        if (scrollUp != null) scrollUp.active = scroll > 0;
        if (scrollDown != null) scrollDown.active = scroll < longest;
    }

    @Override
    public void tick() {
        super.tick();
        refresh();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        int x = (width - BOOK_W) / 2, y = (height - BOOK_H) / 2;
        graphics.blit(BOOK, x, y, 0, 0, BOOK_W, BOOK_H, 512, 512);
        var node = LegacyResearchLayout.get(researchKey);
        int allowed = allowedStage();
        // Do not disclose a now-locked title after a multiplayer resync.
        graphics.drawString(font, node == null || allowed < 0 ? title
                : Component.translatable(node.titleKey()),
                x + 47, y + 29, 0x49321E, false);
        if (allowed >= 0)
            graphics.drawString(font, "Stage " + (ResearchPageVisibility.clampRequestedStage(stageIndex, allowed) + 1) + "/"
                    + page.stages().size(), x + 348, y + 29, 0x49321E, false);
        else
            graphics.drawString(font, "Undiscovered", x + 335, y + 29, 0x49321E, false);
        var lines = wrappedLines();
        for (int i = 0; i < LINES_ON_PAGE && scroll + i < lines.size(); i++) {
            graphics.drawString(font, lines.get(scroll + i),
                    x + 56, y + 81 + i * 18, 0x35271B, false);
        }
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override public boolean isPauseScreen() { return false; }
}
