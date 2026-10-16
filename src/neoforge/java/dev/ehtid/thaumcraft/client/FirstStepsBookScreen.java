package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.network.FirstStepsProgressPayload;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.FirstStepsServerProgression;
import dev.ehtid.thaumcraft.port.core.KnowledgeType;
import dev.ehtid.thaumcraft.port.core.PlayerKnowledgeData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Playable FIRSTSTEPS chapter of the Thaumonomicon.
 *
 * Uses the actual original BETA26 research book PNG (512x512 atlas), with
 * readable text instead of original half-size glyphs. Future chapters need
 * the full ResearchBrowser implementation; this page intentionally cannot
 * approve progress for arbitrary research keys.
 */
public final class FirstStepsBookScreen extends Screen {
    private static final ResourceLocation ORIGINAL_BOOK =
            ResourceLocation.fromNamespaceAndPath("thaumcraft",
                    "textures/gui/gui_researchbook.png");
    private static final int WIDTH = 512;
    private static final int HEIGHT = 356;

    private Button advanceButton;

    public FirstStepsBookScreen() {
        super(Component.translatable("gui.thaumcraft.firststeps.title"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new FirstStepsBookScreen());
    }

    private static PlayerKnowledgeData knowledge() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? null
                : minecraft.player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
    }

    private static boolean mayAdvance(PlayerKnowledgeData data) {
        if (data == null) return false;
        if (!data.isResearchKnown("FIRSTSTEPS"))
            return data.isResearchKnown("!gotthaumonomicon");
        return new FirstStepsServerProgression().requisitesSatisfied(data);
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - WIDTH) / 2;
        int y = (height - HEIGHT) / 2;
        advanceButton = Button.builder(
                Component.translatable("gui.thaumcraft.firststeps.advance"),
                button -> {
                    PlayerKnowledgeData data = knowledge();
                    if (data == null || !mayAdvance(data)) return;
                    PacketDistributor.sendToServer(new FirstStepsProgressPayload(
                            data.isResearchKnown("FIRSTSTEPS"), true, false));
                    button.active = false; // wait for the authoritative sync
                }).bounds(x + 183, y + 311, 145, 20).build();
        addRenderableWidget(advanceButton);
    }

    @Override
    public void tick() {
        super.tick();
        if (advanceButton != null) {
            var data = knowledge();
            advanceButton.active = mayAdvance(data)
                    && data != null && data.getResearchStage("FIRSTSTEPS") <= 3;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        int x = (width - WIDTH) / 2;
        int y = (height - HEIGHT) / 2;
        graphics.blit(ORIGINAL_BOOK, x, y, 0, 0, 512, 356, 512, 512);

        graphics.drawString(font, title, x + 70, y + 23, 0x49321E, false);
        var data = knowledge();
        int stage = data == null ? -1 : data.getResearchStage("FIRSTSTEPS");
        String page = stage < 0 ? "start"
                : stage == 1 ? "workbench"
                : stage == 2 ? "thaumometer"
                : stage == 3 ? "finish"
                : "completed";
        Component text = Component.translatable("gui.thaumcraft.firststeps." + page);
        graphics.drawWordWrap(font, text, x + 46, y + 62, 170, 0x35271B);

        if (data != null && stage == 2) {
            int count = data.getKnowledge(KnowledgeType.OBSERVATION, "BASICS");
            graphics.drawString(font,
                    Component.translatable("gui.thaumcraft.firststeps.observation", count),
                    x + 46, y + 180, 0x35271B, false);
        }
        if (advanceButton != null) {
            advanceButton.visible = stage >= 0 && stage <= 3
                    || stage < 0 && data != null
                    && data.isResearchKnown("!gotthaumonomicon");
        }
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
