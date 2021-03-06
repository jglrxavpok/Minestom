package net.minestom.server.listener;

import net.minestom.server.entity.Player;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryClickHandler;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.packet.client.play.ClientClickWindowPacket;
import net.minestom.server.network.packet.client.play.ClientCloseWindow;
import net.minestom.server.network.packet.server.play.ConfirmTransactionPacket;
import net.minestom.server.network.packet.server.play.SetSlotPacket;

public class WindowListener {

    public static void clickWindowListener(ClientClickWindowPacket packet, Player player) {
        InventoryClickHandler clickHandler = player.getOpenInventory();
        if (clickHandler == null) {
            clickHandler = player.getInventory();
        }

        byte windowId = packet.windowId;
        short slot = packet.slot;
        byte button = packet.button;
        short actionNumber = packet.actionNumber;
        int mode = packet.mode;

        // System.out.println("Window id: " + windowId + " | slot: " + slot + " | button: " + button + " | mode: " + mode);

        ConfirmTransactionPacket confirmTransactionPacket = new ConfirmTransactionPacket();
        confirmTransactionPacket.windowId = windowId;
        confirmTransactionPacket.actionNumber = actionNumber;
        confirmTransactionPacket.accepted = true; // Change depending on output

        switch (mode) {
            case 0:
                switch (button) {
                    case 0:
                        if (slot != -999) {
                            // Left click
                            clickHandler.leftClick(player, slot);
                        } else {
                            // DROP
                            clickHandler.drop(player, mode, slot, button);
                        }
                        break;
                    case 1:
                        if (slot != -999) {
                            // Right click
                            clickHandler.rightClick(player, slot);
                        } else {
                            // DROP
                            clickHandler.drop(player, mode, slot, button);
                        }
                        break;
                }
                break;
            case 1:
                clickHandler.shiftClick(player, slot); // Shift + left/right have identical behavior
                break;
            case 2:
                clickHandler.changeHeld(player, slot, button);
                break;
            case 3:
                // Middle click (only creative players in non-player inventories)
                break;
            case 4:
                // Dropping functions
                clickHandler.drop(player, mode, slot, button);
                break;
            case 5:
                // Dragging
                clickHandler.dragging(player, slot, button);
                break;
            case 6:
                clickHandler.doubleClick(player, slot);
                break;
        }

        ItemStack cursorItem = clickHandler instanceof Inventory ? ((Inventory) clickHandler).getCursorItem(player) : ((PlayerInventory) clickHandler).getCursorItem();
        SetSlotPacket setSlotPacket = new SetSlotPacket();
        setSlotPacket.windowId = -1;
        setSlotPacket.slot = -1;
        setSlotPacket.itemStack = cursorItem;

        player.getPlayerConnection().sendPacket(setSlotPacket);
        player.getPlayerConnection().sendPacket(confirmTransactionPacket);
    }

    public static void closeWindowListener(ClientCloseWindow packet, Player player) {
        // if windowId == 0 then it is player's inventory, meaning that they hadn't been any open inventory packet
        player.closeInventory();
    }

}
