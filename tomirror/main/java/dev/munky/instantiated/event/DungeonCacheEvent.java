package dev.munky.instantiated.event;


import dev.munky.instantiated.dungeon.interfaces.Instance;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public class DungeonCacheEvent extends DungeonEvent {
    public static HandlerList handlers = new HandlerList();
    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }
    public static HandlerList getHandlerList(){
        return handlers;
    }
    private final Location pasteLocation;
    public DungeonCacheEvent(Instance instance, Location pasteLocation) {
        super(instance, !Bukkit.isPrimaryThread());
        this.pasteLocation = pasteLocation;
    }
    public Location getSchematicPasteLocation(){
        return this.pasteLocation;
    }
}
