package dev.munky.instantiated.event.room.mob;

import dev.munky.instantiated.dungeon.interfaces.RoomInstance;
import dev.munky.instantiated.dungeon.mob.DungeonMob;
import dev.munky.instantiated.event.room.DungeonRoomEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class DungeonMobKillEvent extends DungeonRoomEvent {
    public static HandlerList handlers = new HandlerList();
    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }
    public static HandlerList getHandlerList(){
        return handlers;
    }
    public final LivingEntity victim;
    public final DungeonMob mob;
    public DungeonMobKillEvent(RoomInstance dungeon, LivingEntity victim, DungeonMob mob) {
        super(dungeon);
        this.victim = victim;
        this.mob = mob;
    }
}
