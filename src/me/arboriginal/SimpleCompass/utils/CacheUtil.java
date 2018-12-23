package me.arboriginal.SimpleCompass.utils;

import java.util.HashMap;
import java.util.UUID;
import org.bukkit.entity.Player;
import me.arboriginal.SimpleCompass.plugin.SimpleCompass;

public class CacheUtil {
  private SimpleCompass                        plugin;
  private HashMap<UUID, HashMap<String, Data>> datas;

  public static final int PERMANENT = -1;

  // ----------------------------------------------------------------------------------------------
  // Constructor methods
  // ----------------------------------------------------------------------------------------------

  public CacheUtil(SimpleCompass main) {
    plugin = main;
    reset();
  }

  //-----------------------------------------------------------------------------------------------
  // Static methods
  // ----------------------------------------------------------------------------------------------

  public static long now() {
    return System.currentTimeMillis();
  }

  //-----------------------------------------------------------------------------------------------
  // Public methods
  // ----------------------------------------------------------------------------------------------

  public void clear(UUID uid) {
    datas.remove(uid);
  }

  public void clear(UUID uid, String key) {
    datas.get(uid).remove(key);
  }

  public Object get(UUID uid, String key) {
    Data data = datas.get(uid).get(key);

    return (data != null && (data.expire == PERMANENT || data.expire > now())) ? data.value : null;
  }

  public void init(UUID uid) {
    datas.put(uid, new HashMap<String, Data>());
  }

  public void reset() {
    datas = new HashMap<UUID, HashMap<String, Data>>();

    for (Player player : plugin.getServer().getOnlinePlayers()) init(player.getUniqueId());
  }

  public void set(UUID uid, String key, Object value, int duration) {
    datas.get(uid).put(key, new Data((duration == PERMANENT) ? PERMANENT : now() + duration, value));
  }

  //-----------------------------------------------------------------------------------------------
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
