package dev.munky.instantiated.event.room;

import dev.munky.instantiated.dungeon.interfaces.RoomInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class DungeonRoomPlayerEnterEvent extends DungeonRoomEvent {
    public static HandlerList handlers = new HandlerList();
    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }
    public static HandlerList getHandlerList(){
        return handlers;
    }
    final Player player;
    public DungeonRoomPlayerEnterEvent(RoomInstance room, Player player) {
        super(room);
        this.player = player;
    }
    public Player getPlayer(){
        return this.player;
    }
}

