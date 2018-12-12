package me.arboriginal.SimpleCompass;

import java.util.HashMap;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import me.arboriginal.SimpleCompass.SimpleCompassUsersDatas.types;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class SimpleCompassManager {
  private Main plugin;

  public HashMap<types, HashMap<UUID, Object>> compasses;

  public SimpleCompassManager(Main main) {
    plugin    = main;
    compasses = new HashMap<types, HashMap<UUID, Object>>();

    for (types type : SimpleCompassUsersDatas.types.values()) {
      compasses.put(type, new HashMap<UUID, Object>());
    }
  }

  private void createCompass(Player player, types type) {
    Object compass;

    switch (type) {
      case ACTIONBAR:
        compass = true;
        break;

      case BOSSBAR:
        compass = Bukkit.createBossBar(buildDatas(player, type),
            BarColor.valueOf(Main.config.getString("compass.BOSSBAR.attributes.color")),
            BarStyle.valueOf(Main.config.getString("compass.BOSSBAR.attributes.style")));

        ((BossBar) compass).addPlayer(player);
        ((BossBar) compass).setVisible(true);
        break;

      default:
        return;
    }

    compasses.get(type).put(player.getUniqueId(), compass);
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

      if (needed) createCompass(player, type);
    }
  }

  public void updateDatas(Player player) {
    for (types type : SimpleCompassUsersDatas.types.values()) {
      Object compass = getCompass(player.getUniqueId(), type);

      if (compass == null) continue;

      String datas = buildDatas(player, type);

      switch (type) {
        case ACTIONBAR:
          player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(datas));
          break;

        case BOSSBAR:
          ((BossBar) compass).setTitle(datas);
          break;
      }
    }
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
    char   face    = player.getFacing().toString().charAt(0);
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
