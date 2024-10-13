package dev.munky.instantiated.event;

import dev.munky.instantiated.dungeon.interfaces.Format;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

public abstract class DungeonFormatEvent extends Event implements Cancellable {
    private final Format dungeon;
    private boolean cancelled = false;
    public DungeonFormatEvent(Format dungeon, boolean async) {
        super(async);
        this.dungeon = dungeon;
    }
    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }
    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
    public Format getDungeon(){
        return this.dungeon;
    }
}
