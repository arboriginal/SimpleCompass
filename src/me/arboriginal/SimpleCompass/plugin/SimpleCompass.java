package me.arboriginal.SimpleCompass.plugin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import me.arboriginal.SimpleCompass.commands.InterfaceCommand;
import me.arboriginal.SimpleCompass.commands.OptionCommand;
import me.arboriginal.SimpleCompass.commands.TrackCommand;
import me.arboriginal.SimpleCompass.managers.CompassManager;
import me.arboriginal.SimpleCompass.managers.DataManager;
import me.arboriginal.SimpleCompass.managers.TaskManager;
import me.arboriginal.SimpleCompass.managers.TrackerManager;
import me.arboriginal.SimpleCompass.utils.CacheUtil;
import me.arboriginal.SimpleCompass.utils.ConfigUtil;
import me.arboriginal.SimpleCompass.utils.LangUtil;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

public class SimpleCompass extends JavaPlugin implements TabCompleter {
  public FileConfiguration config, locale;
  public CacheUtil         cache;
  public DataManager       datas;
  public TaskManager       tasks;
  public TrackerManager    trackers;
  public CompassManager    compasses;
  public boolean           isReady = false;

  // ----------------------------------------------------------------------------------------------
  // JavaPlugin methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public void onDisable() {
    isReady = false;
    compasses.unload();
    tasks.clear();
  }

  @Override
  public void onEnable() {
    try {
      getServer().spigot();
    }
    catch (Exception e) {
      getServer().getPluginManager().disablePlugin(this);
      getLogger().severe("This plugin only works on Spigot servers!");
      // No need to go on, it will not work
      return;
    }

    reloadConfig();

    getCommand("scompass-option").setExecutor(new OptionCommand(this));
    getCommand("scompass-track").setExecutor(new TrackCommand(this));
    getCommand("scompass").setExecutor(new InterfaceCommand(this));

    getServer().getPluginManager().registerEvents(new Listeners(this), this);
  }

  @Override
  public void onLoad() {
    super.onLoad();

    cache = new CacheUtil(this);
  }

  @Override
  public void reloadConfig() {
    super.reloadConfig();
    cache.reset();

    saveDefaultConfig();

    isReady = false;
    config  = getConfig();
    config.options().copyDefaults(true);

    loadLocale(config.getString("language"));

    for (ConfigUtil.ConfigError error : new ConfigUtil(this).validate(config))
      getLogger().warning(prepareMessage(error.errorKey, error.placeholders));

    saveConfig();

    if (compasses != null) compasses.unload();

    datas     = new DataManager(this);
    tasks     = new TaskManager(this);
    trackers  = new TrackerManager(this);
    compasses = new CompassManager(this);

    if (!trackers.loadTrackers()) compasses.refreshCompassState();

    isReady = true;
  }

  // ----------------------------------------------------------------------------------------------
  // JavaPlugin methods: Basic commands
  // ----------------------------------------------------------------------------------------------

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    String cmd = command.getName().toLowerCase();

    switch (cmd) {
      case "scompass-reload":
        reloadConfig();
        sendMessage(sender, "configuration_reloaded");

        return true;

      case "scompass-track-accept":
      case "scompass-track-deny":
        if (!(sender instanceof Player) || args.length != 1) return false;
        return trackers.respondPlayerTrackerRequest((Player) sender, args[0], (cmd.equals("scompass-track-accept")));
    }

    return super.onCommand(sender, command, label, args);
  }

  // ----------------------------------------------------------------------------------------------
  // TabCompleter methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
    switch (command.getName().toLowerCase()) {
      case "sctaccept":
      case "sctdeny":
        if (!(sender instanceof Player) || args.length != 1) return null;

        Set<UUID> candidates = trackers.getPlayerTrackerRequests((Player) sender);

        if (candidates == null) return null;

        List<String> list = new ArrayList<String>();

        for (UUID uid : candidates) {
          Player candidate = getServer().getPlayer(uid);

          if (candidate != null && candidate.getName().toLowerCase().startsWith(args[0].toLowerCase()))
            list.add(candidate.getName());
        }

        return list;

      default:
        return null;
    }
  }

  // ----------------------------------------------------------------------------------------------
  // Package methods: Helper methods for messages
  // ----------------------------------------------------------------------------------------------

  public String formatMessage(String message) {
    return ChatColor.translateAlternateColorCodes('&', message);
  }

  public String prepareMessage(String key) {
    return prepareMessage(key, null);
  }

  public String prepareMessage(String key, Map<String, String> placeholders) {
    String message = locale.getString(key);

    if (placeholders != null) {
      for (Iterator<String> i = placeholders.keySet().iterator(); i.hasNext();) {
        String placeholder = i.next();

        message = message.replace("{" + placeholder + "}", placeholders.get(placeholder));
      }
    }

    return formatMessage(message.replace("{prefix}", locale.getString("prefix")));
  }

  public void sendMessage(CommandSender sender, String key) {
    sendMessage(sender, key, null);
  }

  public void sendMessage(CommandSender sender, String key, Map<String, String> placeholders) {
    String message = prepareMessage(key, placeholders);

    if (!message.isEmpty()) sender.sendMessage(message);
  }

  // ----------------------------------------------------------------------------------------------
  // Package methods: Helper methods for advanced messages
  // ----------------------------------------------------------------------------------------------

  public TextComponent createClickableMessage(String text, Map<String, Map<String, String>> commands) {
    TextComponent textComponent = new TextComponent();

    for (String command : commands.keySet()) text = text.replace(command, "§k" + command + "§r");

    for (BaseComponent component : TextComponent.fromLegacyText(text)) {
      if (component instanceof TextComponent) {
        Map<String, String> command = commands.get(((TextComponent) component).getText().trim());

        if (command != null) {
          if (command.containsKey("text")) ((TextComponent) component).setText(command.get("text"));

          if (command.containsKey("click"))
            component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command.get("click")));

          if (command.containsKey("hover"))
            component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                TextComponent.fromLegacyText(command.get("hover"))));
        }
      }

      textComponent.addExtra(component);
    }

    return textComponent;
  }

  // ----------------------------------------------------------------------------------------------
  // Private methods
  // ----------------------------------------------------------------------------------------------

  private void loadLocale(String language) {
    locale = new LangUtil(this).get(language);
  }
}
