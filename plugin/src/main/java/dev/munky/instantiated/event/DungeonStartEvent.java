package dev.munky.instantiated.event;

import dev.munky.instantiated.dungeon.interfaces.Instance;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class DungeonStartEvent extends DungeonEvent {
    public static HandlerList handlers = new HandlerList();
    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }
    public static HandlerList getHandlerList(){
        return handlers;
    }
    private final Location location;
    private final Player[] players;
    public DungeonStartEvent(Instance instance, Location schematicOrigin, Player[] players) {
        super(instance, false);
        this.players = players;
        this.location = schematicOrigin;
    }
    public Location getSchematicOrigin(){
        return this.location;
    }
    public Player[] getPlayers(){
        return this.players;
    }
}
