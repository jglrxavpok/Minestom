package net.minestom.server.entity;

import net.minestom.server.MinecraftServer;
import net.minestom.server.Viewable;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.collision.CollisionUtils;
import net.minestom.server.data.Data;
import net.minestom.server.data.DataContainer;
import net.minestom.server.event.Callback;
import net.minestom.server.event.CancellableEvent;
import net.minestom.server.event.Event;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.PacketWriter;
import net.minestom.server.network.packet.server.play.*;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.utils.*;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public abstract class Entity implements Viewable, DataContainer {

    private static Map<Integer, Entity> entityById = new ConcurrentHashMap<>();
    private static AtomicInteger lastEntityId = new AtomicInteger();

    // Metadata
    protected static final byte METADATA_BYTE = 0;
    protected static final byte METADATA_VARINT = 1;
    protected static final byte METADATA_FLOAT = 2;
    protected static final byte METADATA_STRING = 3;
    protected static final byte METADATA_CHAT = 4;
    protected static final byte METADATA_OPTCHAT = 5;
    protected static final byte METADATA_SLOT = 6;
    protected static final byte METADATA_BOOLEAN = 7;
    protected static final byte METADATA_POSE = 18;

    protected Instance instance;
    protected Position position;
    protected float lastX, lastY, lastZ;
    protected float lastYaw, lastPitch;
    private int id;

    private BoundingBox boundingBox;

    protected Entity vehicle;
    // Velocity
    protected Vector velocity = new Vector(); // Movement in block per second
    protected float gravityDragPerTick;
    private int gravityTickCounter;

    private Set<Player> viewers = new CopyOnWriteArraySet<>();
    private Data data;
    private Set<Entity> passengers = new CopyOnWriteArraySet<>();

    protected UUID uuid;
    private boolean isActive; // False if entity has only been instanced without being added somewhere
    private boolean shouldRemove;
    private long scheduledRemoveTime;
    private int entityType;
    private long lastUpdate;
    private Map<Class<? extends Event>, Callback> eventCallbacks = new ConcurrentHashMap<>();
    protected long velocityTime; // Reset velocity to 0 after countdown

    // Synchronization
    private long synchronizationDelay = 1500; // In ms
    private long lastSynchronizationTime;

    // Metadata
    protected boolean onFire;
    protected boolean crouched;
    protected boolean UNUSED_METADATA;
    protected boolean sprinting;
    protected boolean swimming;
    protected boolean invisible;
    protected boolean glowing;
    protected boolean usingElytra;
    protected int air = 300;
    protected String customName = "";
    protected boolean customNameVisible;
    protected boolean silent;
    protected boolean noGravity;
    protected Pose pose = Pose.STANDING;

    public Entity(int entityType, Position spawnPosition) {
        this.id = generateId();
        this.entityType = entityType;
        this.uuid = UUID.randomUUID();
        this.position = spawnPosition.clone();

        setBoundingBox(0, 0, 0);

        entityById.put(id, this);
    }

    public Entity(int entityType) {
        this(entityType, new Position());
    }


    public static Entity getEntity(int id) {
        return entityById.get(id);
    }

    private static int generateId() {
        return lastEntityId.incrementAndGet();
    }

    public abstract void update();

    // Called when a new instance is set
    public abstract void spawn();

    public boolean isOnGround() {
        return EntityUtils.isOnGround(this);
    }

    public void teleport(Position position, Runnable callback) {
        if (instance == null)
            throw new IllegalStateException("You need to use Entity#setInstance before teleporting an entity!");

        Runnable runnable = () -> {
            refreshPosition(position.getX(), position.getY(), position.getZ());
            refreshView(position.getYaw(), position.getPitch());
            EntityTeleportPacket entityTeleportPacket = new EntityTeleportPacket();
            entityTeleportPacket.entityId = getEntityId();
            entityTeleportPacket.position = position;
            entityTeleportPacket.onGround = isOnGround();
            sendPacketToViewers(entityTeleportPacket);
        };

        if (instance.hasEnabledAutoChunkLoad()) {
            instance.loadChunk(position, chunk -> {
                runnable.run();
                if (callback != null)
                    callback.run();
            });
        } else {
            if (ChunkUtils.isChunkUnloaded(instance, position.getX(), position.getZ()))
                return;
            runnable.run();
            if (callback != null)
                callback.run();
        }
    }

    public void teleport(Position position) {
        teleport(position, null);
    }

    @Override
    public void addViewer(Player player) {
        this.viewers.add(player);
        player.viewableEntities.add(this);
        PlayerConnection playerConnection = player.getPlayerConnection();
        playerConnection.sendPacket(getVelocityPacket());
        playerConnection.sendPacket(getPassengersPacket());
    }

    @Override
    public void removeViewer(Player player) {
        synchronized (viewers) {
            if (!viewers.contains(player))
                return;
            this.viewers.remove(player);
            DestroyEntitiesPacket destroyEntitiesPacket = new DestroyEntitiesPacket();
            destroyEntitiesPacket.entityIds = new int[]{getEntityId()};
            player.getPlayerConnection().sendPacket(destroyEntitiesPacket);
        }
        player.viewableEntities.remove(this);
    }

    @Override
    public Set<Player> getViewers() {
        return Collections.unmodifiableSet(viewers);
    }

    @Override
    public Data getData() {
        return data;
    }

    @Override
    public void setData(Data data) {
        this.data = data;
    }

    public void tick(long time) {
        if (instance == null)
            return;

        if (scheduledRemoveTime != 0) { // Any entity with scheduled remove does not update
            boolean finished = time >= scheduledRemoveTime;
            if (finished) {
                remove();
            }
            return;
        }

        if (shouldRemove()) {
            remove();
            return;
        } else if (shouldUpdate(time)) {
            this.lastUpdate = time;

            // Velocity
            if (velocityTime != 0) {
                if (this instanceof Player) {
                    sendPacketToViewersAndSelf(getVelocityPacket());
                } else {
                    final float tps = MinecraftServer.TICK_PER_SECOND;
                    float newX = position.getX() + velocity.getX() / tps;
                    float newY = position.getY() + velocity.getY() / tps;
                    float newZ = position.getZ() + velocity.getZ() / tps;

                    Position newPosition = new Position(newX, newY, newZ);

                    newPosition = CollisionUtils.entity(getInstance(), getBoundingBox(), getPosition(), newPosition);

                    refreshPosition(newPosition);
                    if (this instanceof ObjectEntity) {
                        // FIXME velocity/gravity
                        //sendPacketToViewers(getVelocityPacket());
                        teleport(getPosition());
                    } else if (this instanceof EntityCreature) {
                        teleport(getPosition());
                    }
                }
                if (time >= velocityTime) {
                    sendSynchronization(); // Send synchronization after velocity ended
                    resetVelocity();
                }
            }

            // Gravity
            if (!(this instanceof Player) && !noGravity && gravityDragPerTick != 0) { // Players do manage gravity client-side
                Position position = getPosition();
                //System.out.println("on ground: "+isOnGround());
                if (!isOnGround()) {
                    float strength = gravityDragPerTick * gravityTickCounter;

                    boolean foundBlock = false;
                    int firstBlock = 0;
                    for (int y = (int) position.getY(); y > 0; y--) {
                        BlockPosition blockPosition = new BlockPosition(position.getX(), y, position.getZ());
                        short blockId = instance.getBlockId(blockPosition);
                        //if (y == 70)
                        //   System.out.println("id: " + blockId);
                        if (blockId != 0) {
                            firstBlock = y;
                            foundBlock = true;
                            //System.out.println("first block: " + y);
                            break;
                        }
                    }

                    float newY = position.getY() - strength;
                    //System.out.println("REFRESH Y " + newY);
                    // allow entities to go below the world
                    if(foundBlock) {
                        newY = Math.max(newY, firstBlock);
                    }
                    refreshPosition(position.getX(), newY, position.getZ());
                    gravityTickCounter++;
                    if (isOnGround()) { // Round Y axis when gravity movement is done
                        //System.out.println("ROUND " + position.getY());
                        refreshPosition(position.getX(), Math.round(position.getY()), position.getZ());
                        gravityTickCounter = 0;
                        // System.out.println("recheck: " + isOnGround() + " : " + getPosition());
                    }

                    if (this instanceof EntityCreature) {
                        teleport(getPosition());
                    }
                }
            }

            handleVoid();

            // Call the abstract update method
            update();

            // Scheduled synchronization
            if (time - lastSynchronizationTime >= synchronizationDelay) {
                lastSynchronizationTime = time;
                sendSynchronization();
            }
        }

        if (shouldRemove()) {
            remove();
        }
    }

    /**
     * How does this entity handle being in the void?
     */
    protected void handleVoid() {
        // Kill if in void
        if(getInstance().isInVoid(this.position)) {
            remove();
        }
    }

    public <E extends Event> void setEventCallback(Class<E> eventClass, Callback<E> callback) {
        this.eventCallbacks.put(eventClass, callback);
    }

    public <E extends Event> Callback<E> getEventCallback(Class<E> eventClass) {
        return this.eventCallbacks.get(eventClass);
    }

    public <E extends Event> void callEvent(Class<E> eventClass, E event) {
        Callback<E> callback = getEventCallback(eventClass);
        if (callback != null)
            getEventCallback(eventClass).run(event);
    }

    public <E extends CancellableEvent> void callCancellableEvent(Class<E> eventClass, E event, Runnable runnable) {
        callEvent(eventClass, event);
        if (!event.isCancelled()) {
            runnable.run();
        }
    }

    public int getEntityId() {
        return id;
    }

    public int getEntityType() {
        return entityType;
    }

    public UUID getUuid() {
        return uuid;
    }

    public boolean isActive() {
        return isActive;
    }

    public BoundingBox getBoundingBox() {
        return boundingBox;
    }

    public void setBoundingBox(float x, float y, float z) {
        this.boundingBox = new BoundingBox(this, x, y, z);
    }

    public Instance getInstance() {
        return instance;
    }

    public void setInstance(Instance instance) {
        if (instance == null)
            throw new IllegalArgumentException("instance cannot be null!");

        if (this.instance != null) {
            this.instance.removeEntity(this);
        }

        this.isActive = true;
        this.instance = instance;
        instance.addEntity(this);
        spawn();
    }

    public Vector getVelocity() {
        return velocity;
    }

    public void setVelocity(Vector velocity, long velocityTime) {
        this.velocity = velocity;
        this.velocityTime = System.currentTimeMillis() + velocityTime;
    }

    public void resetVelocity() {
        this.velocity = new Vector();
        this.velocityTime = 0;
    }

    public void setGravity(float gravityDragPerTick) {
        this.gravityDragPerTick = gravityDragPerTick;
    }

    public float getDistance(Entity entity) {
        return getPosition().getDistance(entity.getPosition());
    }

    public Entity getVehicle() {
        return vehicle;
    }

    public void addPassenger(Entity entity) {
        if (instance == null)
            throw new IllegalStateException("You need to set an instance using Entity#setInstance");
        if (entity.getVehicle() != null) {
            entity.getVehicle().removePassenger(entity);
        }

        this.passengers.add(entity);
        entity.vehicle = this;

        sendPacketToViewersAndSelf(getPassengersPacket());
    }

    public void removePassenger(Entity entity) {
        if (instance == null)
            throw new IllegalStateException("You need to set an instance using Entity#setInstance");
        if (!passengers.contains(entity))
            return;
        this.passengers.remove(entity);
        entity.vehicle = null;
        sendPacketToViewersAndSelf(getPassengersPacket());
    }

    public boolean hasPassenger() {
        return !passengers.isEmpty();
    }

    public Set<Entity> getPassengers() {
        return Collections.unmodifiableSet(passengers);
    }

    protected SetPassengersPacket getPassengersPacket() {
        SetPassengersPacket passengersPacket = new SetPassengersPacket();
        passengersPacket.vehicleEntityId = getEntityId();

        int[] passengers = new int[this.passengers.size()];
        int counter = 0;
        for (Entity passenger : this.passengers) {
            passengers[counter++] = passenger.getEntityId();
        }

        passengersPacket.passengersId = passengers;
        return passengersPacket;
    }

    public void triggerStatus(byte status) {
        EntityStatusPacket statusPacket = new EntityStatusPacket();
        statusPacket.entityId = getEntityId();
        statusPacket.status = status;
        sendPacketToViewersAndSelf(statusPacket);
    }

    public void setOnFire(boolean fire) {
        this.onFire = fire;
        sendMetadataIndex(0);
    }

    public boolean isOnFire() {
        return onFire;
    }

    public void setGlowing(boolean glowing) {
        this.glowing = glowing;
        sendMetadataIndex(0);
    }

    public boolean isGlowing() {
        return glowing;
    }

    public void setNoGravity(boolean noGravity) {
        this.noGravity = noGravity;
        sendMetadataIndex(5);
    }

    public boolean hasNoGravity() {
        return noGravity;
    }

    public void refreshPosition(float x, float y, float z) {
        this.lastX = position.getX();
        this.lastY = position.getY();
        this.lastZ = position.getZ();
        position.setX(x);
        position.setY(y);
        position.setZ(z);

        for (Entity passenger : getPassengers()) {
            passenger.refreshPosition(x, y, z);
        }

        Instance instance = getInstance();
        if (instance != null) {
            Chunk lastChunk = instance.getChunkAt(lastX, lastZ);
            Chunk newChunk = instance.getChunkAt(x, z);
            if (lastChunk != null && newChunk != null && lastChunk != newChunk) {
                synchronized (instance) {
                    instance.removeEntityFromChunk(this, lastChunk);
                    instance.addEntityToChunk(this, newChunk);
                }
                updateView(this, lastChunk, newChunk);
            }
        }
    }

    public void refreshPosition(Position position) {
        refreshPosition(position.getX(), position.getY(), position.getZ());
    }

    private void updateView(Entity entity, Chunk lastChunk, Chunk newChunk) {
        if (entity instanceof Player)
            ((Player) entity).onChunkChange(lastChunk, newChunk); // Refresh loaded chunk

        // Refresh entity viewable list
        long[] lastVisibleChunksEntity = ChunkUtils.getChunksInRange(new Position(16 * lastChunk.getChunkX(), 0, 16 * lastChunk.getChunkZ()), MinecraftServer.ENTITY_VIEW_DISTANCE);
        long[] updatedVisibleChunksEntity = ChunkUtils.getChunksInRange(new Position(16 * newChunk.getChunkX(), 0, 16 * newChunk.getChunkZ()), MinecraftServer.ENTITY_VIEW_DISTANCE);

        boolean isPlayer = entity instanceof Player;

        int[] oldChunksEntity = ArrayUtils.getDifferencesBetweenArray(lastVisibleChunksEntity, updatedVisibleChunksEntity);
        for (int index : oldChunksEntity) {
            int[] chunkPos = ChunkUtils.getChunkCoord(lastVisibleChunksEntity[index]);
            Chunk chunk = instance.getChunk(chunkPos[0], chunkPos[1]);
            if (chunk == null)
                continue;
            instance.getChunkEntities(chunk).forEach(ent -> {
                if (ent instanceof Player) {
                    Player player = (Player) ent;
                    removeViewer(player);
                    if (isPlayer) {
                        player.removeViewer((Player) entity);
                    }
                } else if (isPlayer) {
                    ent.removeViewer((Player) entity);
                }
            });
        }

        int[] newChunksEntity = ArrayUtils.getDifferencesBetweenArray(updatedVisibleChunksEntity, lastVisibleChunksEntity);
        for (int index : newChunksEntity) {
            int[] chunkPos = ChunkUtils.getChunkCoord(updatedVisibleChunksEntity[index]);
            Chunk chunk = instance.getChunk(chunkPos[0], chunkPos[1]);
            if (chunk == null)
                continue;
            instance.getChunkEntities(chunk).forEach(ent -> {
                if (ent instanceof Player) {
                    Player player = (Player) ent;
                    addViewer(player);
                    if (entity instanceof Player) {
                        player.addViewer((Player) entity);
                    }
                } else if (isPlayer) {
                    ent.addViewer((Player) entity);
                }
            });
        }
    }

    public void refreshView(float yaw, float pitch) {
        this.lastYaw = position.getYaw();
        this.lastPitch = position.getPitch();
        position.setYaw(yaw);
        position.setPitch(pitch);
    }

    public void refreshSneaking(boolean sneaking) {
        this.crouched = sneaking;
        this.pose = sneaking ? Pose.SNEAKING : Pose.STANDING;
        sendMetadataIndex(0);
        sendMetadataIndex(6);
    }

    public void refreshSprinting(boolean sprinting) {
        this.sprinting = sprinting;
        sendMetadataIndex(0);
    }

    public Position getPosition() {
        return position;
    }

    public boolean sameChunk(Position position) {
        Position pos = getPosition();
        int chunkX1 = ChunkUtils.getChunkCoordinate((int) pos.getX());
        int chunkZ1 = ChunkUtils.getChunkCoordinate((int) pos.getZ());

        int chunkX2 = ChunkUtils.getChunkCoordinate((int) position.getX());
        int chunkZ2 = ChunkUtils.getChunkCoordinate((int) position.getZ());

        return chunkX1 == chunkX2 && chunkZ1 == chunkZ2;
    }

    public boolean sameChunk(Entity entity) {
        return sameChunk(entity.getPosition());
    }

    public void remove() {
        this.shouldRemove = true;
        entityById.remove(id);
        if (instance != null)
            instance.removeEntity(this);
    }

    public void scheduleRemove(long delay) {
        if (delay == 0) { // Cancel the scheduled remove
            this.scheduledRemoveTime = 0;
            return;
        }
        this.scheduledRemoveTime = System.currentTimeMillis() + delay;
    }

    public boolean isRemoveScheduled() {
        return scheduledRemoveTime != 0;
    }

    protected EntityVelocityPacket getVelocityPacket() {
        final float strength = 8000f / MinecraftServer.TICK_PER_SECOND;
        EntityVelocityPacket velocityPacket = new EntityVelocityPacket();
        velocityPacket.entityId = getEntityId();
        velocityPacket.velocityX = (short) (velocity.getX() * strength);
        velocityPacket.velocityY = (short) (velocity.getY() * strength);
        velocityPacket.velocityZ = (short) (velocity.getZ() * strength);
        return velocityPacket;
    }

    public EntityMetaDataPacket getMetadataPacket() {
        EntityMetaDataPacket metaDataPacket = new EntityMetaDataPacket();
        metaDataPacket.entityId = getEntityId();
        metaDataPacket.consumer = getMetadataConsumer();
        return metaDataPacket;
    }

    public Consumer<PacketWriter> getMetadataConsumer() {
        return packet -> {
            fillMetadataIndex(packet, 0);
            fillMetadataIndex(packet, 1);
            fillMetadataIndex(packet, 5);
            fillMetadataIndex(packet, 6);
        };
    }

    protected void sendMetadataIndex(int index) {
        EntityMetaDataPacket metaDataPacket = new EntityMetaDataPacket();
        metaDataPacket.entityId = getEntityId();
        metaDataPacket.consumer = packet -> {
            fillMetadataIndex(packet, index);
        };

        sendPacketToViewersAndSelf(metaDataPacket);
    }

    private void fillMetadataIndex(PacketWriter packet, int index) {
        switch (index) {
            case 0:
                fillStateMetadata(packet);
                break;
            case 1:
                fillAirTickMetaData(packet);
                break;
            case 2:
                fillCustomNameMetaData(packet);
                break;
            case 5:
                fillNoGravityMetaData(packet);
                break;
            case 6:
                fillPoseMetaData(packet);
                break;
        }
    }

    private void fillStateMetadata(PacketWriter packet) {
        packet.writeByte((byte) 0);
        packet.writeByte(METADATA_BYTE);
        byte index0 = 0;
        if (onFire)
            index0 += 1;
        if (crouched)
            index0 += 2;
        if (UNUSED_METADATA)
            index0 += 4;
        if (sprinting)
            index0 += 8;
        if (swimming)
            index0 += 16;
        if (invisible)
            index0 += 32;
        if (glowing)
            index0 += 64;
        if (usingElytra)
            index0 += 128;
        packet.writeByte(index0);
    }

    private void fillAirTickMetaData(PacketWriter packet) {
        packet.writeByte((byte) 1);
        packet.writeByte(METADATA_VARINT);
        packet.writeVarInt(air);
    }

    private void fillCustomNameMetaData(PacketWriter packet) {
        packet.writeByte((byte) 2);
        packet.writeByte(METADATA_CHAT);
        packet.writeSizedString(customName);
    }

    private void fillNoGravityMetaData(PacketWriter packet) {
        packet.writeByte((byte) 5);
        packet.writeByte(METADATA_BOOLEAN);
        packet.writeBoolean(noGravity);
    }

    private void fillPoseMetaData(PacketWriter packet) {
        packet.writeByte((byte) 6);
        packet.writeByte(METADATA_POSE);
        packet.writeVarInt(pose.ordinal());
    }

    protected void sendSynchronization() {
        EntityTeleportPacket entityTeleportPacket = new EntityTeleportPacket();
        entityTeleportPacket.entityId = getEntityId();
        entityTeleportPacket.position = getPosition();
        entityTeleportPacket.onGround = isOnGround();
        sendPacketToViewers(entityTeleportPacket);

        if (!passengers.isEmpty())
            sendPacketToViewers(getPassengersPacket());
    }

    private boolean shouldUpdate(long time) {
        return (float) (time - lastUpdate) >= MinecraftServer.TICK_MS * 0.9f; // Margin of error
    }

    public enum Pose {
        STANDING,
        FALL_FLYING,
        SLEEPING,
        SWIMMING,
        SPIN_ATTACK,
        SNEAKING,
        DYING
    }

    protected boolean shouldRemove() {
        return shouldRemove;
    }
}
