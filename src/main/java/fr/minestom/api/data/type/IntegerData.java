package fr.minestom.api.data.type;

import fr.minestom.api.data.DataType;

public class IntegerData extends DataType<Integer> {
    @Override
    public byte[] encode(Integer value) {
        byte[] result = new byte[4];

        result[0] = (byte) (value >> 24);
        result[1] = (byte) (value >> 16);
        result[2] = (byte) (value >> 8);
        result[3] = (byte) (value >> 0);

        return result;
    }

    @Override
    public Integer decode(Integer value) {
        return null;
    }
}
