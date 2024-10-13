package dev.munky.instantiated.event

import dev.munky.instantiated.PluginState
import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class InstantiatedStateEvent internal constructor(val state: PluginState): Event(false){
    companion object{
        @JvmField
        var handlerList: HandlerList = HandlerList()
        @JvmStatic
        fun getHandlerList(): HandlerList = handlerList
    }
    override fun getHandlers(): HandlerList = handlerList
}

class ComponentReplacementEvent internal constructor(): Event(!Bukkit.isPrimaryThread()){
    companion object{
        @JvmField
        var handlerList: HandlerList = HandlerList()
        @JvmStatic
        fun getHandlerList(): HandlerList = handlerList
    }
    override fun getHandlers(): HandlerList = handlerList
}