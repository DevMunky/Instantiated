package dev.munky.instantiated.event.room.mob;

import dev.munky.instantiated.dungeon.interfaces.RoomInstance;
import dev.munky.instantiated.dungeon.mob.DungeonMob;
import dev.munky.instantiated.event.room.DungeonRoomEvent;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.function.Consumer;

/**
 * Always called on server thread for your convenience :)
 * <p>
 * Literally everything else is off main
 */
public class DungeonMobSpawnEvent extends DungeonRoomEvent {
    public static HandlerList handlers = new HandlerList();
    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }
    public static HandlerList getHandlerList(){
        return handlers;
    }
    private final DungeonMob dungeonMob;
    private WeakReference<LivingEntity> entity;
    private Location spawnLocation;
    public DungeonMobSpawnEvent(RoomInstance roomInstance, DungeonMob dungeonMob, Location spawnLocation) {
        super(roomInstance);
        this.dungeonMob = dungeonMob;
        this.spawnLocation = spawnLocation;
        this.entity = new WeakReference<>(null);
    }
    public Location getSpawnLocation(){
        return this.spawnLocation;
    }
    public void setSpawnLocation(Location location){
        this.spawnLocation = location;
    }
    public DungeonMob getDungeonMob(){
        return this.dungeonMob;
    }
    public void setLivingEntity(LivingEntity entity){
        this.entity = new WeakReference<>(entity);
    }
    public <T extends Entity> void setLivingEntity(Class<T> entity, Consumer<T> function){
        this.entity = new WeakReference<>((LivingEntity) spawnLocation.getWorld().spawn(spawnLocation, entity, function));
    }
    
    /**
     * Nullable because entities are stored as a {@link WeakReference}
     * @return living entity if not yet reclaimed
     */
    public @Nullable LivingEntity getLivingEntity(){
        return entity.get();
    }
}
