package me.arboriginal.SimpleCompass.plugin;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import com.google.common.collect.ImmutableMap;
import me.arboriginal.SimpleCompass.commands.InterfaceCommand;
import me.arboriginal.SimpleCompass.commands.OptionCommand;
import me.arboriginal.SimpleCompass.commands.TrackCommand;
import me.arboriginal.SimpleCompass.managers.CompassManager;
import me.arboriginal.SimpleCompass.managers.DataManager;
import me.arboriginal.SimpleCompass.managers.TargetManager;
import me.arboriginal.SimpleCompass.managers.TaskManager;
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
  public TargetManager     targets;
  public CompassManager    compasses;
  public Listeners         listeners;
  public boolean           isReady = false;

  public HashMap<String, AbstractTracker> trackers;

  // ----------------------------------------------------------------------------------------------
  // JavaPlugin methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public void onDisable() {
    super.onDisable();

    isReady = false;
    compasses.unload();
    tasks.clear();
  }

  @Override
  public void onEnable() {
    super.onEnable();

    try {
      getServer().spigot();
    }
    catch (Exception e) {
      getServer().getPluginManager().disablePlugin(this);
      getLogger().severe("This plugin only works on Spigot servers!");
      // No need to go on, it will not work
      return;
    }

    loadTrackers();
    reloadConfig();

    if (!trackers.isEmpty()) getCommand("scompass-track").setExecutor(new TrackCommand(this));

    getCommand("scompass-option").setExecutor(new OptionCommand(this));
    getCommand("scompass").setExecutor(new InterfaceCommand(this));

    listeners = new Listeners(this);
    getServer().getPluginManager().registerEvents(listeners, this);
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
    locale = new LangUtil(this).getLocale(config.getString("language"));

    for (ConfigUtil.ConfigError error : new ConfigUtil(this).validate(config))
      getLogger().warning(prepareMessage(error.errorKey, error.placeholders));

    saveConfig();

    if (compasses != null) compasses.unload();

    datas     = new DataManager(this);
    tasks     = new TaskManager(this);
    targets   = new TargetManager(this);
    compasses = new CompassManager(this);

    targets.loadTargets();
    compasses.refreshCompassState();

    isReady = true;
  }

  // ----------------------------------------------------------------------------------------------
  // JavaPlugin methods: Basic commands
  // ----------------------------------------------------------------------------------------------

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (command.getName().toLowerCase().equals("scompass-reload")) {
      reloadConfig();
      
      for (String tracker : trackers.keySet()) {
        if (!trackers.get(tracker).init()) {
          trackers.remove(tracker);
          sendMessage(sender, "tracker_disabled", ImmutableMap.of("tracker", tracker));
        }
      }
      
      sendMessage(sender, "configuration_reloaded");
      return true;
    }

    return super.onCommand(sender, command, label, args);
  }

  // ----------------------------------------------------------------------------------------------
  // Package methods: Helper methods for messages
  // ----------------------------------------------------------------------------------------------

  public String formatMessage(String message) {
    return ChatColor.translateAlternateColorCodes('&', message);
  }

  public String formatTime(long time) {
    String[] parts  = DurationFormatUtils.formatDuration(time, "HH:mm:ss").split(":");
    String   hour   = locale.getString("time_display.hour");
    String   minute = locale.getString("time_display.minute");
    String   second = locale.getString("time_display.second");
    String   human  = "";

    if (time < 60000) {
      human = Math.max(Integer.parseInt(parts[2]), 1) + second;
    }
    else {
      if (time >= 3600000) {
        human = parts[0] + hour + " ";
      }

      human += (time > config.getInt("min_time_to_display_seconds") * 1000)
          ? parts[1] + minute + " " + parts[2] + second
          : (Integer.parseInt(parts[1]) + 1) + minute;
    }

    return human.replaceFirst("^0+(?!$)", "");
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
    if (key.isEmpty()) return;

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
          if (command.containsKey("text")) ((TextComponent) component).setText("§r" + command.get("text"));

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

  private void loadTrackers() {
    File dir = new File(getDataFolder(), "trackers");
    if (!dir.exists()) dir.mkdirs();

    if (!dir.exists() || !dir.isDirectory()) {
      getLogger().severe("Unable to create trackers folder...");
      return;
    }

    trackers = new HashMap<String, AbstractTracker>();
    for (final File file : dir.listFiles()) {
      if (file.isDirectory() || !file.getName().endsWith(".jar")) continue;

      try {
        String         path   = file.getAbsolutePath();
        JarFile        jar    = new JarFile(path);
        URLClassLoader loader = new URLClassLoader(
            new URL[] { new URL("jar:file://" + path + "!/") },
            getClassLoader());

        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
          JarEntry entry = entries.nextElement();
          if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;

          String className = entry.getName().substring(0, entry.getName().length() - 6).replace('/', '.');
          Object tracker   = null;

          try {
            tracker = Class.forName(className, true, loader).getConstructor(this.getClass()).newInstance(this);
          }
          catch (NoClassDefFoundError e) {}

          if (tracker == null || !(tracker instanceof AbstractTracker)) continue;
          String trackerID = ((AbstractTracker) tracker).trackerID();

          if (trackers.containsKey(trackerID)) {
            getLogger().warning(
                "Tracker {tracker} is using the ID {id} which is already used by {other}, can't load it..."
                    .replace("{tracker}", file.getName()).replace("id", trackerID)
                    .replace("{other}", trackers.get(trackerID).getClass().getSimpleName()));
            continue;
          }

          if (((AbstractTracker) tracker).init()) {
            trackers.put(trackerID, (AbstractTracker) tracker);
            getLogger().info("Tracker §6{tracker}§r successfully loaded".replace("{tracker}", file.getName()));
          }
          else
            throw new Exception();
        }

        jar.close();
      }
      catch (Exception e) {
        getLogger().severe("Error loading tracker " + file.getName() + "...");
      }
    }
  }
}
