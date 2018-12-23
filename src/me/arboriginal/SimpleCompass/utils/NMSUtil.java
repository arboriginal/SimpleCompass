package me.arboriginal.SimpleCompass.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public final class NMSUtil {
  // ----------------------------------------------------------------------------------------------
  // Public methods
  // ----------------------------------------------------------------------------------------------

  public static boolean openBook(Player player, ItemStack book) {
    int       heldSlot = player.getInventory().getHeldItemSlot();
    ItemStack current  = player.getInventory().getItem(heldSlot);
    boolean   success  = false;

    player.getInventory().setItem(heldSlot, book);

    try {
      Object key = getClass("MinecraftKey").getConstructor(String.class).newInstance("minecraft:book_open");

      success = sendPacket(player, getClass("PacketPlayOutCustomPayload")
          .getConstructor(key.getClass(), getClass("PacketDataSerializer"))
          .newInstance(key, getClass("PacketDataSerializer").getConstructor(ByteBuf.class)
              .newInstance(Unpooled.buffer(256).setByte(0, (byte) 0).writerIndex(1))));
    }
    catch (Exception e) {}

    player.getInventory().setItem(heldSlot, current);

    return success;
  }

  // ----------------------------------------------------------------------------------------------
  // Private methods
  // ----------------------------------------------------------------------------------------------

  private static Class<?> getClass(String name) {
    try {
      return Class.forName("net.minecraft.server." +
          Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3] + "." + name);
    }
    catch (Exception e) {}

    return null;
  }

  private static boolean sendPacket(Player player, Object packet) {
    try {
      Object handle = player.getClass().getMethod("getHandle").invoke(player);
      Object target = handle.getClass().getField("playerConnection").get(handle);

      target.getClass().getMethod("sendPacket", getClass("Packet")).invoke(target, packet);

      return true;
    }
    catch (Exception e) {}

    return false;
  }
}
