package me.catcoder.packetsfixer;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.utility.StreamSerializer;
import com.comphenix.protocol.wrappers.nbt.NbtCompound;
import com.comphenix.protocol.wrappers.nbt.NbtFactory;
import com.comphenix.protocol.wrappers.nbt.NbtList;
import com.google.common.collect.MapMaker;
import io.netty.buffer.ByteBuf;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by Ruslan on 20.04.2017.
 */
public class PacketsFixer extends JavaPlugin {

    private ProtocolManager protocolManager;

    public static final Map<Player, Long> PACKET_USAGE = new MapMaker().weakKeys().concurrencyLevel(4).makeMap();


    @Override
    public void onLoad() {
        protocolManager = ProtocolLibrary.getProtocolManager();
    }

    @Override
    public void onEnable() {
        protocolManager.addPacketListener(new PacketAdapter(this, PacketType.Play.Client.CUSTOM_PAYLOAD) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                checkForFlood(event);
            }
        });
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Iterator<Map.Entry<Player, Long>> iterator = PACKET_USAGE.entrySet().iterator(); iterator.hasNext(); ) {
                Player player = iterator.next().getKey();
                if (!player.isOnline() || !player.isValid()) iterator.remove();
            }

        }, 20L, 20L);
    }

    /**
     * @author JustBlender
     */
    protected void checkForFlood(final PacketEvent event) {
        if (!elapsed(PACKET_USAGE.getOrDefault(event.getPlayer(), -1L), 20L)) {
            PACKET_USAGE.put(event.getPlayer(), System.currentTimeMillis());
        } else {
            try {
                PacketContainer container = event.getPacket();
                ByteBuf buf = container.getSpecificModifier(ByteBuf.class).read(0);
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));

                ItemStack itemStack = StreamSerializer.getDefault().deserializeItemStack(input);
                if (itemStack == null)
                    throw new IOException("Unable to deserialize ItemStack");

                NbtCompound root = (NbtCompound) NbtFactory.fromItemTag(itemStack);
                if (root == null) {
                    throw new IOException("No NBT tag?!");
                } else if (!root.containsKey("pages")) {
                    throw new IOException("No 'pages' NBT compound was found");
                } else {
                    NbtList<String> pages = root.getList("pages");
                    if (pages.size() > 50)
                        throw new IOException("Too much pages");

                    for (String page : pages)
                        if (page.length() > 256)
                            throw new IOException("A very long page");
                }
            } catch (IOException ex) {
                getLogger().warning(event.getPlayer().getName() + " пытается флудить пакетами");
                event.setCancelled(true);
            }
        }
    }

    private boolean elapsed(long from, long required) {
        return from != -1L && System.currentTimeMillis() - from > required;
    }

    @Override
    public void onDisable() {
        protocolManager.removePacketListeners(this);
    }
}
