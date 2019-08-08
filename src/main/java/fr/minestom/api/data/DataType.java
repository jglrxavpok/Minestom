package fr.minestom.api.data;

import fr.minestom.api.data.type.IntegerData;

public abstract class DataType<T> {

    public static final DataType INTEGER = new IntegerData();

    public abstract byte[] encode(T value);

    public abstract T decode(T value);

    // TODO get object type class ?

}
