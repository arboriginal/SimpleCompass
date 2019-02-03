package me.arboriginal.SimpleCompass.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.MemorySection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import me.arboriginal.SimpleCompass.compasses.AbstractCompass;
import me.arboriginal.SimpleCompass.compasses.AbstractCompass.CompassTypes;
import me.arboriginal.SimpleCompass.compasses.ActionbarCompass;
import me.arboriginal.SimpleCompass.compasses.BossbarCompass;
import me.arboriginal.SimpleCompass.managers.TaskManager.TasksTypes;
import me.arboriginal.SimpleCompass.plugin.SimpleCompass;

public class CompassManager {
  public enum RequirementsSections {
    HOTBAR, INVENTORY, MAIN_HAND, OFF_HAND,
  }

  private SimpleCompass                                         sc;
  private HashMap<CompassTypes, HashMap<UUID, AbstractCompass>> compasses;

  // ----------------------------------------------------------------------------------------------
  // Constructor methods
  // ----------------------------------------------------------------------------------------------

  public CompassManager(SimpleCompass plugin) {
    sc        = plugin;
    compasses = new HashMap<CompassTypes, HashMap<UUID, AbstractCompass>>();

    for (CompassTypes type : CompassTypes.values()) compasses.put(type, new HashMap<UUID, AbstractCompass>());
  }

  // ----------------------------------------------------------------------------------------------
  // Misc methods
  // ----------------------------------------------------------------------------------------------

  public void commandTrigger(String command) {
    if (sc.config.getList("commands_trigger_refresh").contains(command.split(" ")[0]))
      for (Player player : sc.getServer().getOnlinePlayers()) sc.tasks.set(TasksTypes.REFRESH_STATUS, player);
  }

  public void unload() {
    removeCompass();
  }

  // ----------------------------------------------------------------------------------------------
  // Compass methods
  // ----------------------------------------------------------------------------------------------

  public void createCompass(CompassTypes type, Player player) {
    UUID            uid     = player.getUniqueId();
    AbstractCompass compass = getCompass(type, uid);

    if (compass != null) return;

    switch (type) {
      case ACTIONBAR:
        compass = new ActionbarCompass(sc, player);
        break;

      case BOSSBAR:
        compass = new BossbarCompass(sc, player);
        break;
    }

    if (compass != null) {
      BukkitRunnable task = sc.tasks.get(TasksTypes.REMOVEWARNING, uid);

      if (task != null) task.run();

      compasses.get(type).put(uid, compass);
    }
  }

  public AbstractCompass getCompass(CompassTypes type, UUID uid) {
    return compasses.get(type).get(uid);
  }

  public void refreshCompass(Player player, CompassTypes type) {
    refreshCompassState(player, type);
    refreshCompassDatas(player, type);
  }

  public void removeCompass() {
    for (CompassTypes type : CompassTypes.values()) removeCompass(type);
  }

  public void removeCompass(CompassTypes type) {
    for (UUID uid : compasses.get(type).keySet()) removeCompass(type, uid);
  }

  public void removeCompass(Player player) {
    for (CompassTypes type : CompassTypes.values()) removeCompass(type, player);
  }

  public void removeCompass(CompassTypes type, Player player) {
    removeCompass(type, player.getUniqueId());
  }

  public void removeCompass(CompassTypes type, UUID uid) {
    AbstractCompass compass = getCompass(type, uid);

    if (compass == null) return;

    compass.delete();
    compasses.get(type).remove(uid);
  }

  // ----------------------------------------------------------------------------------------------
  // Compass state methods
  // ----------------------------------------------------------------------------------------------

  public boolean getCompassState(Player player, CompassTypes type) {
    if (!player.hasPermission("scompass.use") || !player.hasPermission("scompass.use." + type)) return false;

    boolean active = false;

    switch (sc.datas.compassOptionGet(player, type)) {
      case ALWAYS:
        active = true;
        break;

      case VEHICLE:
        active = player.isInsideVehicle();
        break;

      case ELYTRA:
        active = player.isGliding();
        break;

      case ELYTRA_VEHICLE:
        active = (player.isInsideVehicle() || player.isGliding());
        break;

      default:
        active = false;
    }

    return active && hasRequiredItems(player, type, true);
  }

  public void refreshCompassState() {
    for (Player player : sc.getServer().getOnlinePlayers()) refreshCompassState(player);
  }

  public void refreshCompassState(CompassTypes type) {
    for (Player player : sc.getServer().getOnlinePlayers()) refreshCompassState(player, type);
  }

  public void refreshCompassState(Player player) {
    for (CompassTypes type : CompassTypes.values()) refreshCompassState(player, type);
  }

  public void refreshCompassState(Player player, CompassTypes type) {
    if (getCompassState(player, type))
      createCompass(type, player);
    else
      removeCompass(type, player);
  }

  // ----------------------------------------------------------------------------------------------
  // Compass item requirements methods
  // ----------------------------------------------------------------------------------------------

  ItemStack consumeItem(Player player, CompassTypes type, ItemStack stack) {
    stack.setAmount(stack.getAmount() - 1);
    sc.datas.cooldownConsumeSet(player, type);

    return stack;
  }

  public boolean hasRequiredItems(Player player, CompassTypes type, boolean consume) {
    if (((MemorySection) sc.config.get("compass." + type + ".require.items")).getKeys(false).isEmpty()) return true;

    List<?>         lores = sc.config.getList("ignored_lores");
    PlayerInventory inv   = player.getInventory();
    ItemStack       stack;

    consume &= shouldConsume(player, type);

    for (RequirementsSections section : RequirementsSections.values()) {
      List<?> items = sc.config.getList("compass." + type + ".require.items." + section, new ArrayList<String>());

      if (items.isEmpty()) continue;

      switch (section) {
        case OFF_HAND:
          stack = inv.getItemInOffHand();

          if (stack != null && isValidItem(stack, items, lores)) {
            if (consume) inv.setItemInOffHand(consumeItem(player, type, stack));
            return true;
          }
          break;

        case MAIN_HAND:
          stack = inv.getItemInMainHand();

          if (stack != null && isValidItem(stack, items, lores)) {
            if (consume) inv.setItemInMainHand(consumeItem(player, type, stack));
            return true;
          }
          break;

        case HOTBAR:
        case INVENTORY:
          for (int i = (section.equals(RequirementsSections.HOTBAR) ? 0 : 9); i <= (section
              .equals(RequirementsSections.HOTBAR) ? 8 : 35); i++) {
            stack = inv.getItem(i);

            if (stack == null) continue;

            if (isValidItem(stack, items, lores)) {
              if (consume) inv.setItem(i, consumeItem(player, type, stack));
              return true;
            }
          }
          break;
      }
    }

    return false;
  }

  public boolean isValidItem(ItemStack stack, List<?> requiredItems, List<?> ignoredLores) {
    if (!requiredItems.contains(stack.getType().toString())) return false;
    if (ignoredLores.isEmpty()) return true;

    List<?> itemLores = stack.getItemMeta().getLore();

    if (itemLores == null) return true;

    for (Object lore : stack.getItemMeta().getLore())
      if (ignoredLores.contains(lore)) return false;

    return true;
  }

  public boolean shouldConsume(Player player, CompassTypes type) {
    return sc.config.getBoolean("compass." + type + ".require.consume")
        && !player.hasPermission("scompass.use.free")
        && sc.datas.cooldownConsumeGet(player, type) < 1;
  }

  // ----------------------------------------------------------------------------------------------
  // Compass data methods
  // ----------------------------------------------------------------------------------------------

  public void refreshCompassDatas() {
    for (CompassTypes type : CompassTypes.values()) refreshCompassDatas(type);
  }

  public void refreshCompassDatas(CompassTypes type) {
    for (UUID uid : compasses.get(type).keySet()) refreshCompassDatas(type, uid);
  }

  public void refreshCompassDatas(CompassTypes type, UUID uid) {
    AbstractCompass compass = getCompass(type, uid);

    if (compass == null) return;

    if (!((MemorySection) sc.config.get("compass." + type + ".require.items")).getKeys(false).isEmpty()
        && shouldConsume(compass.owner, type) && !hasRequiredItems(compass.owner, type, true))
      removeCompass(type, compass.owner);
    else
      compass.refresh();
  }

  public void refreshCompassDatas(Player player) {
    refreshCompassDatas(player.getUniqueId());
  }

  public void refreshCompassDatas(Player player, CompassTypes type) {
    refreshCompassDatas(type, player.getUniqueId());
  }

  public void refreshCompassDatas(UUID uid) {
    for (CompassTypes type : CompassTypes.values()) refreshCompassDatas(type, uid);
  }
}
