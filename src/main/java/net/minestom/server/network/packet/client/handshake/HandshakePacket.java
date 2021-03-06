package net.minestom.server.network.packet.client.handshake;

import net.minestom.server.network.ConnectionManager;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.packet.PacketReader;
import net.minestom.server.network.packet.client.ClientPreplayPacket;
import net.minestom.server.network.player.PlayerConnection;

public class HandshakePacket implements ClientPreplayPacket {

    private int protocolVersion;
    private String serverAddress;
    private int serverPort;
    private int nextState;

    @Override
    public void read(PacketReader reader) {
        this.protocolVersion = reader.readVarInt();
        this.serverAddress = reader.readSizedString();
        this.serverPort = reader.readUnsignedShort();
        this.nextState = reader.readVarInt();
    }

    @Override
    public void process(PlayerConnection connection, ConnectionManager connectionManager) {
        switch (nextState) {
            case 1:
                connection.setConnectionState(ConnectionState.STATUS);
                break;
            case 2:
                connection.setConnectionState(ConnectionState.LOGIN);
                break;
            default:
                // Unexpected error
                break;
        }
    }
}
