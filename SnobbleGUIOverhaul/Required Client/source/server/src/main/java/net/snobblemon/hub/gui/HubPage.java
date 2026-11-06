package net.snobblemon.hub.gui;

import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Inventory tab. The four armor slots and the offhand are the player's REAL equipment slots, mapped into the menu,
 * so armor can be swapped here; the main inventory and hotbar below are the real inventory too.
 */
final class HubPage {
    void draw(HubGui g) {
        g.layer("hub");
        g.layer("hubreg_" + g.accent());
        Container inv = g.p.getInventory();
        // Inventory slots: 36 feet, 37 legs, 38 chest, 39 head, 40 offhand
        g.setSlotRedirect(HubGui.slot(0, 1), new EquipSlot(g.p, inv, 39, EquipmentSlot.HEAD));
        g.setSlotRedirect(HubGui.slot(0, 2), new EquipSlot(g.p, inv, 38, EquipmentSlot.CHEST));
        g.setSlotRedirect(HubGui.slot(0, 3), new EquipSlot(g.p, inv, 37, EquipmentSlot.LEGS));
        g.setSlotRedirect(HubGui.slot(0, 4), new EquipSlot(g.p, inv, 36, EquipmentSlot.FEET));
        g.setSlotRedirect(HubGui.slot(1, 4), new EquipSlot(g.p, inv, 40, null));
    }

    /** A real equipment slot that only accepts what the player could wear there. */
    static final class EquipSlot extends Slot {
        private final EquipmentSlot type;
        private final LivingEntity owner;

        EquipSlot(LivingEntity owner, Container c, int index, EquipmentSlot type) {
            super(c, index, 0, 0);
            this.owner = owner;
            this.type = type;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return type == null || owner.getEquipmentSlotForItem(stack) == type;
        }

        @Override
        public int getMaxStackSize() {
            return type == null ? super.getMaxStackSize() : 1;
        }
    }
}
