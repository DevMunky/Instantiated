package dev.munky.instantiated.paperhack;

import dev.munky.instantiated.Instantiated;
import dev.munky.instantiated.common.network.NetworkCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

// maybe multi-version support with subprojects that supply an interface with implementation?
public final class PaperCodecSupport implements PluginMessageListener {
      private final @NotNull Map<CustomPacketPayload.Type<?>, PacketListener<?>> listeners = new HashMap<>();
      
      public <P extends CustomPacketPayload> void sendPluginMessage(
            @NotNull Player player,
            @NotNull P packet,
            @NotNull CustomPacketPayload.Type<P> type,
            @NotNull NetworkCodec<FriendlyByteBuf,P> codec
      ) {
            if (!(player instanceof CraftPlayer craftPlayer)) throw new IllegalArgumentException("Player is not a CraftPlayer");
            ServerGamePacketListenerImpl connection = craftPlayer.getHandle().connection;
            
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            codec.encode(buffer, packet);
            
            connection.send(new ClientboundCustomPayloadPacket(new DiscardedPayload(type.id(), buffer)));
      }
      
      public <P extends CustomPacketPayload> void registerPluginMessageListener(
            @NotNull CustomPacketPayload.Type<P> type,
            @NotNull NetworkCodec<ByteBuf, P> codec,
            @NotNull Class<P> packetClass,
            @NotNull BiConsumer<ServerPlayer, P> consumer
      ) {
            ResourceLocation identifier = type.id();
            Bukkit.getServer().getMessenger().registerIncomingPluginChannel(Instantiated.getInstantiated(), identifier.toString(), this);
            PacketListener<P> listener = new PacketListener<>(codec, consumer, packetClass);
            this.listeners.put(type, listener);
      }
      
      @Override
      public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
            this.genericPluginMessageReceived(channel, ((CraftPlayer) player).getHandle(), message);
      }
      
      @SuppressWarnings("unchecked") // we know this is correct because of the generics on registry
      private <T> void genericPluginMessageReceived(@NotNull String channel, @NotNull ServerPlayer player, @NotNull byte[] message) {
            ResourceLocation identifier = ResourceLocation.tryParse(channel);
            if (identifier == null) return;
            CustomPacketPayload.Type<?> type = new CustomPacketPayload.Type<>(identifier);
            if (!this.listeners.containsKey(type)) return;
            
            PacketListener<?> listener = this.listeners.get(type);
            NetworkCodec<ByteBuf, ?> codec = listener.codec();
            BiConsumer<ServerPlayer, T> consumer = (BiConsumer<ServerPlayer, T>) listener.consumer();
            Class<?> packetClass = listener.packetClass();
            
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.copiedBuffer(message));
            Object packet = codec.decode(buffer);
            if (packetClass.isInstance(packet)) consumer.accept(player, (T) packet);
            else throw new IllegalStateException("received packet is not of expected type");
      }
      
      public record PacketListener<T>(
            @NotNull NetworkCodec<ByteBuf, T> codec,
            @NotNull BiConsumer<ServerPlayer, T> consumer,
            @NotNull Class<T> packetClass
      ) {}
}