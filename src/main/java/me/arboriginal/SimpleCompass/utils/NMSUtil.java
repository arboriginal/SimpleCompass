package me.arboriginal.SimpleCompass.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class NMSUtil {
    private static final String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];

    // Public methods --------------------------------------------------------------------------------------------------

    public static boolean openBook(Player player, ItemStack book) {
        try { // 1.14 new method
            player.getClass().getMethod("openBook", ItemStack.class).invoke(player, book);
            return true;
        }
        catch (Exception e) {}
        // Fallback for 1.13...
        int       heldSlot = player.getInventory().getHeldItemSlot();
        ItemStack current  = player.getInventory().getItem(heldSlot);
        boolean   success  = false;

        player.getInventory().setItem(heldSlot, book);

        try {
            Object   key = getNMSClass("MinecraftKey").getConstructor(String.class).newInstance("minecraft:book_open");
            Class<?> bb  = getBufferClass("ByteBuf"), ic = int.class, pds = getNMSClass("PacketDataSerializer");
            // @formatter:off
            success = sendPacket(player, getNMSClass("PacketPlayOutCustomPayload").getConstructor(key.getClass(), pds)
                    .newInstance(key, pds.getConstructor(getBufferClass("ByteBuf"))
                        .newInstance(bb.getMethod("writerIndex", ic).invoke(bb.getMethod("setByte", ic, ic)
                            .invoke(getBufferClass("Unpooled").getMethod("buffer", ic).invoke(null, 256), 0, 0), 1))));
        } catch (Exception e) {}
        // @formatter:on
        player.getInventory().setItem(heldSlot, current);
        return success;
    }

    // Private methods -------------------------------------------------------------------------------------------------

    private static Class<?> getBufferClass(String name) {
        return getClass(name, "io.netty.buffer");
    }

    private static Class<?> getNMSClass(String name) {
        return getClass(name, "net.minecraft.server." + version);
    }

    private static Class<?> getClass(String name, String namespace) {
        try {
            return Class.forName(namespace + "." + name);
        }
        catch (Exception e) {
            return null;
        }
    }

    private static boolean sendPacket(Player player, Object packet) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object target = handle.getClass().getField("playerConnection").get(handle);

            target.getClass().getMethod("sendPacket", getNMSClass("Packet")).invoke(target, packet);

            return true;
        }
        catch (Exception e) {}

        return false;
    }
}
