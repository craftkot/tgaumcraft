package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.menu.SmelterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Original BETA26 176x166 smelter GUI texture with synced 5 progress bars. */
public final class SmelterScreen extends AbstractContainerScreen<SmelterMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("thaumcraft","textures/gui/gui_smelter.png");

    public SmelterScreen(SmelterMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title);
        imageWidth=176;
        imageHeight=166;
        titleLabelX=8;
        titleLabelY=5;
        inventoryLabelX=8;
        inventoryLabelY=72;
    }

    @Override protected void renderBg(GuiGraphics graphics,float partial,
                                      int mouseX,int mouseY) {
        graphics.blit(TEXTURE,leftPos,topPos,0,0,imageWidth,imageHeight,256,256);
        int fuel=menu.burnTotal()>0?Math.clamp(menu.burnRemaining()*20/menu.burnTotal(),0,20):0;
        if(fuel>0)
            graphics.blit(TEXTURE,leftPos+80,topPos+26+20-fuel,
                    176,20-fuel,16,fuel,256,256);
        int cook=menu.smeltDuration()>0
                ?Math.clamp(menu.cookProgress()*46/menu.smeltDuration(),0,46):0;
        if(cook>0)
            graphics.blit(TEXTURE,leftPos+106,topPos+13+46-cook,
                    216,46-cook,9,cook,256,256);
        int stored=Math.clamp(menu.storedVis()*48/256,0,48);
        if(stored>0)
            graphics.blit(TEXTURE,leftPos+61,topPos+12+48-stored,
                    200,48-stored,8,stored,256,256);
        graphics.blit(TEXTURE,leftPos+60,topPos+8,232,0,10,55,256,256);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my) {
        // BETA26 leaves foreground empty; improve only legibility of the
        // inventory header without obscuring the original artwork.
        g.drawString(font,playerInventoryTitle,8,72,0x404040,false);
    }
    @Override public void render(GuiGraphics graphics,int mx,int my,float partial){
        renderBackground(graphics,mx,my,partial);
        super.render(graphics,mx,my,partial);
        renderTooltip(graphics,mx,my);
    }
}
