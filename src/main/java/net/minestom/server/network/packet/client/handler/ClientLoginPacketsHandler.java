package net.minestom.server.network.packet.client.handler;

import net.minestom.server.network.packet.client.login.LoginStartPacket;

public class ClientLoginPacketsHandler extends ClientPacketsHandler {

    public ClientLoginPacketsHandler() {
        register(0, LoginStartPacket.class);
    }

}
