package dev.munky.instantiated.event;

import dev.munky.instantiated.Instantiated;
import dev.munky.instantiated.common.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.jetbrains.annotations.NotNull;
import org.koin.core.Koin;
import org.koin.core.component.KoinComponent;
import org.koin.java.KoinJavaComponent;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * MADE BY DEVMUNKY!!!!
 */
public enum ListenerFactory {
    INSTANCE;
    public static <T extends Event> Listener registerEvent(Class<T> eventClass, Consumer<T> event){
        return registerEvent(eventClass,EventPriority.NORMAL,event);
    }
    public static <T extends Event> Listener registerEvent(Class<T> eventClass, BiConsumer<T,Listener> event) {
        return registerEvent(eventClass,EventPriority.NORMAL,event);
    }
    public static <T extends Event> Listener registerEvent(Class<T> eventClass,EventPriority priority, Consumer<T> event){
        return registerEvent(eventClass,priority,(e,l)->event.accept(e));
    }
    public static <T extends Event> Listener registerEvent(Class<T> eventClass, EventPriority priority, BiConsumer<T,Listener> eventAndListener) {
        Listener listener = new Listener() {};
        EventExecutor executor = (listenerInstance, ev) -> {
            try{
                if (eventClass.isInstance(ev)) eventAndListener.accept(eventClass.cast(ev), listener);
            }catch(Throwable t){
                Instantiated.getInstantiated().getLogger().severe("An event executor registered through Instantiated's ListenerFactory threw an exception: " + Util.formatException(t));
            }
        };
        Bukkit.getPluginManager().registerEvent(eventClass, listener, priority, executor, Instantiated.getInstantiated());
        return listener;
    }
}
