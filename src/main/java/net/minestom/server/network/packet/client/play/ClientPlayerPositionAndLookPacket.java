package net.minestom.server.network.packet.client.play;

import net.minestom.server.network.packet.PacketReader;
import net.minestom.server.network.packet.client.ClientPlayPacket;

public class ClientPlayerPositionAndLookPacket extends ClientPlayPacket {

    public double x, y, z;
    public float yaw, pitch;
    public boolean onGround;

    @Override
    public void read(PacketReader reader) {
        this.x = reader.readDouble();
        this.y = reader.readDouble();
        this.z = reader.readDouble();

        this.yaw = reader.readFloat();
        this.pitch = reader.readFloat();

        this.onGround = reader.readBoolean();
    }
}
