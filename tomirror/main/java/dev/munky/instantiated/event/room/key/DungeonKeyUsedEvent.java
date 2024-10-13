package dev.munky.instantiated.event.room.key;

import dev.munky.instantiated.dungeon.component.DoorComponent;
import dev.munky.instantiated.dungeon.interfaces.Instance;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class DungeonKeyUsedEvent extends DungeonKeyEvent {
    public static HandlerList handlers = new HandlerList();
    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }
    public static HandlerList getHandlerList(){
        return handlers;
    }
    private final DoorComponent openedDoor;

    /**
     * Called when a key is used.
     * @param dungeonInstance the instanced dungeon in which a key was used
     * @param openedDoor the door component that was opened with a key.
     */
    public DungeonKeyUsedEvent(Instance dungeonInstance, DoorComponent openedDoor) {
        super(dungeonInstance,dungeonInstance.getDoorKeys());
        this.openedDoor = openedDoor;
    }
    public DoorComponent getOpenedDoor(){
        return this.openedDoor;
    }
}
