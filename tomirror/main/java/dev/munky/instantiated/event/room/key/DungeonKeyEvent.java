package dev.munky.instantiated.event.room.key;

import dev.munky.instantiated.dungeon.interfaces.Instance;
import dev.munky.instantiated.event.DungeonEvent;

public abstract class DungeonKeyEvent extends DungeonEvent {
    private final int current;
    public DungeonKeyEvent(Instance dungeonInstance, int currentKeys) {
        super(dungeonInstance, false);
        this.current = currentKeys;
    }
    public int getCurrentKeys(){
        return this.current;
    }
}
