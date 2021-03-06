package net.minestom.server.listener;

import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.ItemUpdateStateEvent;
import net.minestom.server.event.PlayerStartDiggingEvent;
import net.minestom.server.event.PlayerSwapItemEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.CustomBlock;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.client.play.ClientPlayerDiggingPacket;
import net.minestom.server.network.packet.server.play.AcknowledgePlayerDiggingPacket;
import net.minestom.server.network.packet.server.play.EntityEffectPacket;
import net.minestom.server.network.packet.server.play.RemoveEntityEffectPacket;
import net.minestom.server.utils.BlockPosition;

public class PlayerDiggingListener {

    public static void playerDiggingListener(ClientPlayerDiggingPacket packet, Player player) {
        ClientPlayerDiggingPacket.Status status = packet.status;
        BlockPosition blockPosition = packet.blockPosition;

        PlayerInventory playerInventory = player.getInventory();
        ItemStack mainHand = playerInventory.getItemInMainHand();
        ItemStack offHand = playerInventory.getItemInOffHand();
        switch (status) {
            case STARTED_DIGGING:
                if (player.getGameMode() == GameMode.CREATIVE) {
                    Instance instance = player.getInstance();
                    if (instance != null) {
                        instance.breakBlock(player, blockPosition);
                    }
                } else if (player.getGameMode() == GameMode.SURVIVAL) {
                    Instance instance = player.getInstance();
                    if (instance != null) {
                        CustomBlock customBlock = instance.getCustomBlock(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ());
                        if (customBlock != null) {
                            PlayerStartDiggingEvent playerStartDiggingEvent = new PlayerStartDiggingEvent(blockPosition, customBlock);
                            player.callEvent(PlayerStartDiggingEvent.class, playerStartDiggingEvent);
                            if (!playerStartDiggingEvent.isCancelled()) {
                                player.refreshTargetBlock(customBlock, blockPosition);
                                sendAcknowledgePacket(player, blockPosition, customBlock.getBlockId(),
                                        ClientPlayerDiggingPacket.Status.STARTED_DIGGING, true);
                            } else {
                                sendAcknowledgePacket(player, blockPosition, customBlock.getBlockId(),
                                        ClientPlayerDiggingPacket.Status.STARTED_DIGGING, false);
                            }
                            addEffect(player);
                        } else {
                            player.resetTargetBlock();
                            removeEffect(player);
                        }
                    }
                }
                break;
            case CANCELLED_DIGGING:
                player.resetTargetBlock();
                removeEffect(player);
                break;
            case FINISHED_DIGGING:
                if (player.getCustomBlockTarget() != null) {
                    player.resetTargetBlock();
                    removeEffect(player);
                } else {
                    Instance instance = player.getInstance();
                    if (instance != null) {
                        instance.breakBlock(player, blockPosition);
                    }
                }
                break;
            case DROP_ITEM_STACK:
                ItemStack droppedItemStack = player.getInventory().getItemInMainHand().clone();
                dropItem(player, droppedItemStack, ItemStack.getAirItem());
                break;
            case DROP_ITEM:
                ItemStack droppedItemStack2 = player.getInventory().getItemInMainHand().clone();
                droppedItemStack2.setAmount((byte) 1);

                ItemStack handItem = player.getInventory().getItemInMainHand();
                handItem.setAmount((byte) (handItem.getAmount() - 1));
                handItem = handItem.getAmount() <= 0 ? ItemStack.getAirItem() : handItem;

                dropItem(player, droppedItemStack2, handItem);
                break;
            case UPDATE_ITEM_STATE:
                Material mainHandMat = Material.fromId(mainHand.getMaterialId());
                Material offHandMat = Material.fromId(offHand.getMaterialId());
                boolean isOffhand = offHandMat.hasState();

                ItemStack updatedItem = isOffhand ? offHand :
                        mainHandMat.hasState() ? mainHand : null;
                if (updatedItem == null) // No item with state, cancel
                    return;

                ItemUpdateStateEvent itemUpdateStateEvent = new ItemUpdateStateEvent(updatedItem);
                player.callEvent(ItemUpdateStateEvent.class, itemUpdateStateEvent);
                
                player.refreshActiveHand(itemUpdateStateEvent.hasHandAnimation(), isOffhand, false);
                player.sendPacketToViewers(player.getMetadataPacket());
                break;
            case SWAP_ITEM_HAND:
                PlayerSwapItemEvent swapItemEvent = new PlayerSwapItemEvent(offHand.clone(), mainHand.clone());
                player.callCancellableEvent(PlayerSwapItemEvent.class, swapItemEvent, () -> {
                    playerInventory.setItemInMainHand(swapItemEvent.getMainHandItem());
                    playerInventory.setItemInOffHand(swapItemEvent.getOffHandItem());
                });
                break;
        }
    }

    private static void dropItem(Player player, ItemStack droppedItem, ItemStack handItem) {
        if (player.dropItem(droppedItem)) {
            player.getInventory().setItemInMainHand(handItem);
        }
    }

    private static void addEffect(Player player) {
        EntityEffectPacket entityEffectPacket = new EntityEffectPacket();
        entityEffectPacket.entityId = player.getEntityId();
        entityEffectPacket.effectId = 4;
        entityEffectPacket.amplifier = -1;
        entityEffectPacket.duration = 0;
        entityEffectPacket.flags = 0;
        player.getPlayerConnection().sendPacket(entityEffectPacket);
    }

    private static void removeEffect(Player player) {
        RemoveEntityEffectPacket removeEntityEffectPacket = new RemoveEntityEffectPacket();
        removeEntityEffectPacket.entityId = player.getEntityId();
        removeEntityEffectPacket.effectId = 4;
        player.getPlayerConnection().sendPacket(removeEntityEffectPacket);
    }

    private static void sendAcknowledgePacket(Player player, BlockPosition blockPosition, int blockId,
                                              ClientPlayerDiggingPacket.Status status, boolean success) {
        AcknowledgePlayerDiggingPacket acknowledgePlayerDiggingPacket = new AcknowledgePlayerDiggingPacket();
        acknowledgePlayerDiggingPacket.blockPosition = blockPosition;
        acknowledgePlayerDiggingPacket.blockStateId = blockId;
        acknowledgePlayerDiggingPacket.status = status;
        acknowledgePlayerDiggingPacket.successful = success;

        player.getPlayerConnection().sendPacket(acknowledgePlayerDiggingPacket);
    }

}
