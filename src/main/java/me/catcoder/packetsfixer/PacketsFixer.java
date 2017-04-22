package me.catcoder.packetsfixer;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.google.common.collect.MapMaker;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

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
        saveDefaultConfig();
        protocolManager.addPacketListener(new PacketAdapter(this, PacketType.Play.Client.CUSTOM_PAYLOAD) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                String channel = event.getPacket().getStrings().read(0);
                if ("MC|BSign".equals(channel) || "MC|BEdit".equals(channel)) checkForFlood(event);
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
    private void checkForFlood(final PacketEvent event) {
        if (!elapsed(PACKET_USAGE.getOrDefault(event.getPlayer(), -1L), 20L)) {
            PACKET_USAGE.put(event.getPlayer(), System.currentTimeMillis());
        } else {
            getServer().getScheduler().runTask(this, () ->
                    event.getPlayer().kickPlayer(ChatColor.translateAlternateColorCodes(
                    '&',
                    getConfig().getString("kick-message")
            )));
            event.setCancelled(true);
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
