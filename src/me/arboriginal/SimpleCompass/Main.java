package me.arboriginal.SimpleCompass;

import java.util.HashMap;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class Main extends JavaPlugin implements Listener {
  public static FileConfiguration config;
  public HashMap<UUID, BossBar>   bossbars;

  @Override
  public void onEnable() {
    bossbars = new HashMap<UUID, BossBar>() {
      private static final long serialVersionUID = 1L;
    };

    reloadConfig();

    getServer().getPluginManager().registerEvents(this, this);
  }

  @Override
  public void onDisable() {
    super.onDisable();

    HandlerList.unregisterAll((JavaPlugin) this);

    removerAllBars();
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (command.getName().equalsIgnoreCase("scompass-reload")) {
      reloadConfig();
      sender.sendMessage(config.getString("messages.configuration_reloaded"));

      return true;
    }

    return super.onCommand(sender, command, label, args);
  }

  @Override
  public void reloadConfig() {
    super.reloadConfig();

    saveDefaultConfig();
    config = getConfig();
    config.options().copyDefaults(true);
    validateBossbarAttributes();
    saveConfig();
    removerAllBars();

    for (Player p : getServer().getOnlinePlayers()) {
      createBar(p);
    }
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    createBar(event.getPlayer());
  }

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    removerBar(event.getPlayer());
  }

  @EventHandler
  public void onPlayerMove(PlayerMoveEvent event) {
    Player player = event.getPlayer();

    if (config.getBoolean("compass.actionbar.enable"))
      ((Player) player).spigot().sendMessage(ChatMessageType.ACTION_BAR,
          new TextComponent(getPlayerCompass(player, "actionbar")));

    if (config.getBoolean("compass.bossbar.enable"))
      updateBar(player, getPlayerCompass(player, "bossbar"));
  }
  
  @EventHandler
  public void onEntityToggleGlide(EntityToggleGlideEvent event) {
    
  }

  private void validateBossbarAttributes() {
    // Pas de bras, pas de chocolat !
    if (!config.getBoolean("compass.bossbar.enable")) return;

    try {
      BarColor.valueOf(config.getString("compass.bossbar.attributes.color"));
    }
    catch (Exception e) {
      config.set("compass.bossbar.attributes.color", "YELLOW");
      getLogger().warning(config.getString("messages.invalid_bossbar_color"));
    }

    try {
      BarStyle.valueOf(config.getString("compass.bossbar.attributes.style"));
    }
    catch (Exception e) {
      config.set("compass.bossbar.attributes.style", "SOLID");
      getLogger().warning(
          config.getString("messages.invalid_bossbar_style") + config.getString("compass.bossbar.attributes.style"));
    }
  }

  private String getPlayerCompass(Player player, String type) {
    double rotation = (player.getEyeLocation().getYaw() - 180) % 360;

    if (rotation < 0) rotation += 360;

    String sep     = config.getString("compass." + type + ".separator_value");
    String cSep    = config.getString("compass." + type + ".separator_color");
    String cOn     = config.getString("compass." + type + ".active_color");
    String cOff    = config.getString("compass." + type + ".inactive_color");
    String nOn     = config.getString("compass." + type + ".active_north_color");
    String nOff    = config.getString("compass." + type + ".north_color");
    String west    = config.getString("compass." + type + ".cardinals.west");
    String north   = config.getString("compass." + type + ".cardinals.north");
    String east    = config.getString("compass." + type + ".cardinals.east");
    String south   = config.getString("compass." + type + ".cardinals.south");
    String compass = sep + "♤" + sep + "♡" + sep + "♢" + sep + "♧";
    char   face    = player.getFacing().toString().charAt(0);
    int    start   = (int) Math.round(rotation *
        (compass.length() - 4 + west.length() + north.length() + east.length() + south.length()) / 360);

    compass = (cSep + compass.substring(start) + compass.substring(0, start))
        .replace("♤", ((face == 'W') ? cOn : cOff) + west + cSep)
        .replace("♡", ((face == 'N') ? nOn : nOff) + north + cSep)
        .replace("♢", ((face == 'E') ? cOn : cOff) + east + cSep)
        .replace("♧", ((face == 'S') ? cOn : cOff) + south + cSep);
    
    return config.getString("compass." + type + ".before") + compass + config.getString("compass." + type + ".after");
  }

  private BossBar getBar(Player player) {
    return getBar(player.getUniqueId());
  }

  private BossBar getBar(UUID id) {
    if (bossbars.containsKey(id)) return bossbars.get(id);

    return null;
  }

  private BossBar createBar(Player player) {
    BossBar bar = Bukkit.createBossBar(getPlayerCompass(player, "bossbar"),
        BarColor.valueOf(config.getString("compass.bossbar.attributes.color")),
        BarStyle.valueOf(config.getString("compass.bossbar.attributes.style")));

    bar.addPlayer(player);
    bar.setVisible(true);

    bossbars.put(player.getUniqueId(), bar);

    return bar;
  }

  private void updateBar(Player player, String text) {
    BossBar bar = getBar(player);

    if (bar != null) {
      bar.setTitle(text);
    }
  }

  private void removerAllBars() {
    for (UUID id : bossbars.keySet()) {
      removerBar(id);
    }
  }

  private void removerBar(Player player) {
    removerBar(player.getUniqueId());
  }

  private void removerBar(UUID id) {
    BossBar bar = getBar(id);

    if (bar != null) {
      bar.removeAll();
      bossbars.remove(id);
    }
  }
}
