package me.arboriginal.SimpleCompass.utils;

import java.io.File;
import java.util.HashMap;
import java.util.UUID;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import me.arboriginal.SimpleCompass.plugin.SimpleCompass;

public class CacheUtil {
  private SimpleCompass                        sc;
  private File                                 vcf;
  private FileConfiguration                    vcc;
  private HashMap<UUID, HashMap<String, Data>> datas;

  public static final int PERMANENT = -1;

  // ----------------------------------------------------------------------------------------------
  // Constructor methods
  // ----------------------------------------------------------------------------------------------

  public CacheUtil(SimpleCompass plugin) {
    sc  = plugin;
    vcf = new File(sc.getDataFolder(), "versionCache.yml");

    if (!vcf.exists())
      try {
        vcf.createNewFile();
      }
      catch (Exception e) {
        sc.getLogger().warning("Can't write to version cache file");
      }

    vcc = YamlConfiguration.loadConfiguration(vcf);
    reset();
  }

  // ----------------------------------------------------------------------------------------------
  // Static methods
  // ----------------------------------------------------------------------------------------------

  public static long now() {
    return System.currentTimeMillis();
  }

  // ----------------------------------------------------------------------------------------------
  // Public methods
  // ----------------------------------------------------------------------------------------------

  public void clear(UUID uid) {
    datas.remove(uid);
  }

  public void clear(UUID uid, String key) {
    datas.get(uid).remove(key);
  }

  public Object get(UUID uid, String key) {
    if (!datas.containsKey(uid)) return null;
    Data data = datas.get(uid).get(key);
    return (data != null && (data.expire == PERMANENT || data.expire > now())) ? data.value : null;
  }

  public void init(UUID uid) {
    datas.put(uid, new HashMap<String, Data>());
  }

  public void reset() {
    datas = new HashMap<UUID, HashMap<String, Data>>();

    for (Player player : sc.getServer().getOnlinePlayers()) init(player.getUniqueId());
  }

  public void set(UUID uid, String key, Object value, int duration) {
    datas.get(uid).put(key, new Data((duration == PERMANENT) ? PERMANENT : now() + duration, value));
  }

  // ----------------------------------------------------------------------------------------------
  // Public methods: Version update check cache
  // ----------------------------------------------------------------------------------------------

  public Object versionGet(String key) {
    long expire = vcc.getLong("version." + key + ".expire", 0);
    return (expire > now()) ? vcc.get("version." + key + ".value", null) : null;
  }

  public void versionSet(String key, Object value, int duration) {
    vcc.set("version." + key + ".expire", now() + duration * 60000);
    vcc.set("version." + key + ".value", value);

    try {
      vcc.save(vcf);
    }
    catch (Exception e) {
      sc.getLogger().warning("Can't write to version cache file");
    }
  }

  // ----------------------------------------------------------------------------------------------
  // Private classes
  // ----------------------------------------------------------------------------------------------

  private static class Data {
    public long   expire;
    public Object value;

    Data(long expiration, Object datas) {
      expire = expiration;
      value  = datas;
    }
  }
}
