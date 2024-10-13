package dev.munky.instantiated.event.room;

import dev.munky.instantiated.dungeon.interfaces.RoomInstance;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class DungeonRoomCompletionEvent extends DungeonRoomEvent {
    public static HandlerList handlers = new HandlerList();
    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }
    public static HandlerList getHandlerList(){
        return handlers;
    }
    public DungeonRoomCompletionEvent(RoomInstance room) {
        super(room);
    }
}
