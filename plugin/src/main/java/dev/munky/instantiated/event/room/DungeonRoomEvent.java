package dev.munky.instantiated.event.room;

import dev.munky.instantiated.dungeon.interfaces.RoomInstance;
import dev.munky.instantiated.event.DungeonEvent;
import org.bukkit.Bukkit;

public abstract class DungeonRoomEvent extends DungeonEvent {
    private final RoomInstance room;
    public DungeonRoomEvent(RoomInstance room) {
        super(room.getParent(), !Bukkit.isPrimaryThread());
        this.room = room;
    }
    public RoomInstance getRoom(){
        return this.room;
    }
}
