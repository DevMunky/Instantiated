package dev.munky.instantiated.event;

import dev.munky.instantiated.dungeon.interfaces.Instance;

public abstract class DungeonEvent extends DungeonFormatEvent {
    private final Instance dungeonInstance;
    public DungeonEvent(Instance dungeonInstance, boolean async) {
        super(dungeonInstance.getFormat(), async);
        this.dungeonInstance = dungeonInstance;
    }
    public Instance getInstance(){
        return this.dungeonInstance;
    }
}
