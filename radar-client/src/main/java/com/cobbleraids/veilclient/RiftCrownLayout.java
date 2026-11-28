package com.cobbleraids.veilclient;

/** Pixel-aligned placement and caption space for the creature-shaped Rift Crown HUD. */
final class RiftCrownLayout {
    static final int WIDTH=360, HEIGHT=96;
    record Plan(int x,int y,int width,int height,int center,int titleY,int nameWidth,
                int railX,int railY,int railWidth,int railHeight,int infoY) {
        int rows(int numbersWidth,int statusWidth,boolean preparing) {
            return (preparing ? statusWidth<=railWidth : numbersWidth+statusWidth+8<=railWidth) ? 1 : 2;
        }
        int reserve(int rows) { return Math.max(y+height,infoY+rows*10)+3; }
    }
    static Plan at(int guiWidth) {
        if(guiWidth<144) throw new IllegalArgumentException("Viewport is too narrow");
        // Keep the wing silhouette clear of Cobblemon's native party panel.
        int width=Math.min(WIDTH,guiWidth-128),x=(guiWidth-width)/2,y=7;
        double scale=width/(double)WIDTH;
        return new Plan(x,y,width,(int)Math.round(HEIGHT*scale),x+width/2,
                y+(int)Math.round(50*scale),(int)Math.round(160*scale),
                x+(int)Math.round(85*scale),y+(int)Math.round(70*scale),
                Math.max(1,(int)Math.round(189*scale)),Math.max(1,(int)Math.round(6*scale)),
                y+(int)Math.round(82*scale));
    }
    private RiftCrownLayout() {}
}
