package net.snobblemon.hubclient.ui;

import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.world.item.ItemStack;

/**
 * One cosmetic in the grid: a beveled tile (grey-blue when owned, red when locked), the item model, a padlock for
 * locked items, a rarity bar along the top, a gold outline when selected, and a soft highlight under the mouse.
 */
public final class CosmeticTile extends BaseComponent {
    public static final int SIZE = 26;

    private final ItemStack stack;
    private final boolean owned;
    private final boolean worn;
    private final int rarityColor;
    private boolean selected;

    public CosmeticTile(ItemStack stack, boolean owned, boolean worn, int rarityColor, boolean selected) {
        this.stack = stack;
        this.owned = owned;
        this.worn = worn;
        this.rarityColor = rarityColor;
        this.selected = selected;
        this.sizing(Sizing.fixed(SIZE), Sizing.fixed(SIZE));
    }

    @Override
    protected int determineHorizontalContentSize(Sizing sizing) {
        return SIZE;
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        return SIZE;
    }

    @Override
    public void draw(OwoUIDrawContext ctx, int mouseX, int mouseY, float partialTicks, float delta) {
        Theme.blit(ctx, Theme.sprite(owned ? "tile" : "tile_locked"), x, y, SIZE, SIZE);
        if (!stack.isEmpty()) ctx.renderFakeItem(stack, x + 5, y + 4);
        if (!owned) ctx.fill(x + 3, y + 3, x + SIZE - 3, y + SIZE - 3, 0x99301418);                  // locked: the item is dimmed
        ctx.fill(x + 4, y + SIZE - 5, x + SIZE - 4, y + SIZE - 4, rarityColor);                       // rarity bar
        if (!owned) {
            ctx.fill(x + SIZE - 12, y + 2, x + SIZE - 2, y + 12, 0xFF7A2A32);                       // padlock badge, top right
            Theme.tex(ctx, Theme.icon("lock"), x + SIZE - 11, y + 3, 8, 8, 0f, 0f, 16, 16, 16, 16);
        } else {
            ctx.fill(x + SIZE - 12, y + 2, x + SIZE - 2, y + 12, worn ? 0xFF2F9E5C : 0xFF4E6468);    // owned badge: green tick when worn
            Theme.tex(ctx, Theme.icon("check"), x + SIZE - 11, y + 3, 8, 8, 0f, 0f, 16, 16, 16, 16);
        }
        if (isInBoundingBox(mouseX, mouseY)) Theme.blit(ctx, Theme.sprite("tile_hover"), x, y, SIZE, SIZE);
        if (selected) Theme.blit(ctx, Theme.sprite("tile_selected"), x, y, SIZE, SIZE);
    }
}
