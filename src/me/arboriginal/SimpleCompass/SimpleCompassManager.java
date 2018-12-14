package me.arboriginal.SimpleCompass;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import me.arboriginal.SimpleCompass.SimpleCompassUsersDatas.types;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class SimpleCompassManager {
  private Main plugin;

  public enum sections {
    OFF_HAND,
    MAIN_HAND,
    HOTBAR,
    INVENTORY,
  };

  public HashMap<types, HashMap<UUID, Object>> compasses;
  public HashMap<UUID, BossBar>                noFuelWarns;

  public SimpleCompassManager(Main main) {
    plugin      = main;
    compasses   = new HashMap<types, HashMap<UUID, Object>>();
    noFuelWarns = new HashMap<UUID, BossBar>();

    for (types type : SimpleCompassUsersDatas.types.values()) {
      compasses.put(type, new HashMap<UUID, Object>());
    }
  }

  public Object createCompass(Player player, types type, boolean managed) {
    Object compass;

    switch (type) {
      case ACTIONBAR:
        compass = true;
        break;

      case BOSSBAR:
        removePlayerWarnNoFuel(player);

        compass = Bukkit.createBossBar(buildDatas(player, type),
            BarColor.valueOf(Main.config.getString("compass.BOSSBAR.attributes.color")),
            BarStyle.valueOf(Main.config.getString("compass.BOSSBAR.attributes.style")));

        ((BossBar) compass).addPlayer(player);
        ((BossBar) compass).setVisible(true);
        break;

      default:
        return null;
    }

    if (managed) compasses.get(type).put(player.getUniqueId(), compass);

    return compass;
  }

  public Object getCompass(UUID uid, types type) {
    if (compasses.get(type).containsKey(uid)) return compasses.get(type).get(uid);

    return null;
  }

  public void removeCompass(UUID uid, types type) {
    Object compass = getCompass(uid, type);

    if (compass != null) {
      compasses.get(type).remove(uid);

      if (compass instanceof BossBar) ((BossBar) compass).removeAll();
    }
  }

  public void updateState(Player player) {
    updateState(player, true);
  }

  public void updateState(Player player, boolean consume) {
    for (types type : SimpleCompassUsersDatas.types.values()) {
      removeCompass(player.getUniqueId(), type);

      if (!player.isOnline()) continue;

      boolean needed = false;

      switch (plugin.udm.getPlayerOption(player, type)) {
        case DISABLED:
          break;

        case ALWAYS:
          needed = true;
          break;

        case VEHICLE:
          needed = player.isInsideVehicle();
          break;

        case ELYTRA:
          needed = player.isGliding();
          break;

        case ELYTRA_VEHICLE:
          needed = (player.isInsideVehicle() || player.isGliding());
          break;
      }

      if (needed && hasRequirements(player, type, consume && shouldConsume(player, type)))
        createCompass(player, type, true);
    }
  }

  public void updateDatas(Player player) {
    for (types type : SimpleCompassUsersDatas.types.values()) {
      Object compass = getCompass(player.getUniqueId(), type);

      if (compass == null) continue;

      if (!hasRequirements(player, type, true) && shouldRemove(player, type)) {
        removeCompass(player.getUniqueId(), type);
        warnPlayerNoFuel(player, type);
        continue;
      }

      sendCompassDatas(player, type, compass, buildDatas(player, type));
    }
  }

  public void sendCompassDatas(Player player, types type, Object compass, String datas) {
    switch (type) {
      case ACTIONBAR:
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(datas));
        break;

      case BOSSBAR:
        if (compass instanceof BossBar) ((BossBar) compass).setTitle(datas);
        break;
    }
  }

  public boolean hasRequirements(Player player, types type, boolean consume) {
    if (!player.hasPermission("scompass.use." + type)) return false;
    if (player.hasPermission("scompass.use.free")) return true;

    boolean requirements = false;

    for (sections section : sections.values()) {
      List<?> list = Main.config.getList("compass." + type + ".require." + section);

      if (list.size() == 0) continue;

      if (hasRequiredItem(player, type, section, consume, list)) return true;

      requirements = true;
    }

    return !requirements;
  }

  public boolean hasRequiredItem(Player player, types type, sections section, boolean consume) {
    return hasRequiredItem(player, type, section, consume,
        Main.config.getList("compass." + type + ".require." + section));
  }

  public boolean hasRequiredItem(Player player, types type, sections section, boolean consume, List<?> required) {
    boolean         found = false;
    PlayerInventory inv   = player.getInventory();
    ItemStack       stack;

    switch (section) {
      case OFF_HAND:
        stack = inv.getItemInOffHand();

        if (stack != null && (found = required.contains(stack.getType().toString()))
            && consume && shouldConsume(player, type))
          inv.setItemInOffHand(consumeItem(player, type, stack));
        break;

      case MAIN_HAND:
        stack = inv.getItemInMainHand();

        if (stack != null && (found = required.contains(stack.getType().toString()))
            && consume && shouldConsume(player, type))
          inv.setItemInMainHand(consumeItem(player, type, stack));
        break;

      case HOTBAR:
      case INVENTORY:
        for (int i = (section.equals(sections.HOTBAR) ? 0 : 9); i <= (section.equals(sections.HOTBAR) ? 8 : 35); i++) {
          stack = inv.getItem(i);

          if (stack == null) continue;

          if (found = required.contains(stack.getType().toString())) {
            if (consume && shouldConsume(player, type))
              inv.setItem(i, consumeItem(player, type, stack));
            break;
          }
        }
        break;
    }

    return found;
  }

  public void warnPlayerNoFuel(Player player, types type) {
    String message = Main.config.getString("compass." + type + ".require_settings.warnPlayerNoFuel");

    if (message.isEmpty()) return;

    Object compass = createCompass(player, type, false);

    if (compass instanceof BossBar) {
      noFuelWarns.put(player.getUniqueId(), (BossBar) compass);

      new BukkitRunnable() {
        @Override
        public void run() {
          if (isCancelled()) return;

          removePlayerWarnNoFuel(player);
        }
      }.runTaskLaterAsynchronously(plugin, Main.config.getInt("compass." + type + ".require_settings.warnDuration",
          Main.config.getDefaults().getInt("compass." + type + ".require_settings.warnDuration")) * 20);
    }

    sendCompassDatas(player, type, compass, plugin.formatMessage(message));
  }

  public void removePlayerWarnNoFuel(Player player) {
    UUID uid = player.getUniqueId();

    if (noFuelWarns.containsKey(uid)) {
      BossBar warning = noFuelWarns.get(uid);

      warning.removeAll();
      noFuelWarns.remove(uid);
    }
  }

  public boolean shouldConsume(Player player, types type) {
    return Main.config.getBoolean("compass." + type + ".require_settings.consume", false)
        && plugin.udm.getConsumeCooldown(player, type) == 0;
  }

  public boolean shouldRemove(Player player, types type) {
    return !Main.config.getBoolean("compass." + type + ".require_settings.consume", false)
        || plugin.udm.getConsumeCooldown(player, type) == 0;
  }

  public ItemStack consumeItem(Player player, types type, ItemStack stack) {
    stack.setAmount(stack.getAmount() - 1);
    plugin.udm.setConsumeCooldown(player, type);

    return stack;
  }

  private String buildDatas(Player player, types type) {
    double rotation = (player.getEyeLocation().getYaw() - 180) % 360;

    if (rotation < 0) rotation += 360;

    String sep     = Main.config.getString("compass." + type + ".separator_value");
    String cSep    = Main.config.getString("compass." + type + ".separator_color");
    String cOn     = Main.config.getString("compass." + type + ".active_color");
    String cOff    = Main.config.getString("compass." + type + ".inactive_color");
    String nOn     = Main.config.getString("compass." + type + ".active_north_color");
    String nOff    = Main.config.getString("compass." + type + ".north_color");
    String west    = Main.config.getString("compass." + type + ".cardinals.west");
    String north   = Main.config.getString("compass." + type + ".cardinals.north");
    String east    = Main.config.getString("compass." + type + ".cardinals.east");
    String south   = Main.config.getString("compass." + type + ".cardinals.south");
    String compass = sep + "♤" + sep + "♡" + sep + "♢" + sep + "♧";
    char   face    = Arrays.asList('S', 'W', 'N', 'E').get(Math.round(player.getLocation().getYaw() / 90f) & 0x3);
    int    start   = (int) Math.round(rotation *
        (compass.length() - 4 + west.length() + north.length() + east.length() + south.length()) / 360);

    compass = (cSep + compass.substring(start) + compass.substring(0, start))
        .replace("♤", ((face == 'W') ? cOn : cOff) + west + cSep)
        .replace("♡", ((face == 'N') ? nOn : nOff) + north + cSep)
        .replace("♢", ((face == 'E') ? cOn : cOff) + east + cSep)
        .replace("♧", ((face == 'S') ? cOn : cOff) + south + cSep);

    return Main.config.getString("compass." + type + ".before") + compass
        + Main.config.getString("compass." + type + ".after");
  }
}
