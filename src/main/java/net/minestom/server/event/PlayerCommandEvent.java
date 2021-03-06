package net.minestom.server.event;

import net.minestom.server.entity.Player;

public class PlayerCommandEvent extends CancellableEvent {

    private Player player;
    private String command;

    public PlayerCommandEvent(Player player, String command) {
        this.player = player;
        this.command = command;
    }

    public Player getPlayer() {
        return player;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }
}
