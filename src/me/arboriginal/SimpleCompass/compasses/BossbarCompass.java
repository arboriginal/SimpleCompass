package me.arboriginal.SimpleCompass.compasses;

import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import me.arboriginal.SimpleCompass.managers.TaskManager.TasksTypes;
import me.arboriginal.SimpleCompass.plugin.SimpleCompass;

public class BossbarCompass extends AbstractCompass {
  public BossBar bossbar;

  // ----------------------------------------------------------------------------------------------
  // Constructor methods
  // ----------------------------------------------------------------------------------------------

  public BossbarCompass(SimpleCompass plugin, Player player) {
    super(plugin, player, CompassTypes.BOSSBAR);
  }

  // ----------------------------------------------------------------------------------------------
  // SimpleCompass methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public void delete() {
    super.delete();
    sc.tasks.set(TasksTypes.REMOVEWARNING, owner, this);
  }

  @Override
  public void display(String datas) {
    bossbar.setTitle(datas);
    bossbar.setProgress(getProgress());
  }

  @Override
  public void refresh() {
    super.refresh();

    if (sc.config.getBoolean("compass.BOSSBAR.disappear_when_not_moving")) {
      if (task != null) task.cancel();

      bossbar.setVisible(true);

      task = new BukkitRunnable() {
        @Override
        public void run() {
          if (isCancelled()) return;
          bossbar.setVisible(false);
          this.cancel();
        }
      };

      task.runTaskLaterAsynchronously(sc, sc.config.getInt("compass.BOSSBAR.disappear_delay"));
    }
  }

  @Override
  public void init() {
    super.init();

    bossbar = Bukkit.createBossBar("",
        BarColor.valueOf(sc.config.getString("compass.BOSSBAR.attributes.color")),
        BarStyle.valueOf(sc.config.getString("compass.BOSSBAR.attributes.style")));

    bossbar.addPlayer(owner);
    bossbar.setProgress(getProgress());
    bossbar.setVisible(true);
  }

  // ----------------------------------------------------------------------------------------------
  // Specific methods
  // ----------------------------------------------------------------------------------------------

  private void alterColor(double durability) {
    Object levels = sc.config.get("compass.BOSSBAR.attributes.elytra_durability.levels");

    if (levels == null || !(levels instanceof Map)) return;

    durability *= 100;

    for (Object value : ((Map<?, ?>) levels).keySet()) {
      if (durability < (int) value) {
        bossbar.setColor(BarColor.valueOf((String) ((Map<?, ?>) levels).get(value)));
        return;
      }
    }

    bossbar.setColor(BarColor.valueOf(sc.config.getString("compass.BOSSBAR.attributes.color")));
  }

  @SuppressWarnings("deprecation")
  private int elytraDurabilityOldMethod(ItemStack chestplate) {
    try {
      if (chestplate.getClass().getMethod("getDurability", (Class<?>[]) null) != null)
        return (int) chestplate.getDurability();
    }
    catch (Exception e) {}

    return -1;
  }

  private double getProgress() {
    String cacheKey = "compass." + type;
    Double progress = (Double) sc.cache.get(owner.getUniqueId(), cacheKey);

    if (progress != null) return progress;

    if (/**/sc.config.getBoolean("compass.BOSSBAR.attributes.elytra_durability.wearing")
        || (sc.config.getBoolean("compass.BOSSBAR.attributes.elytra_durability.gliding") && owner.isGliding())) {
      ItemStack chestplate = owner.getInventory().getChestplate();

      if (chestplate != null && chestplate.getType().equals(Material.ELYTRA)
          && !chestplate.getItemMeta().isUnbreakable()) {
        Map<String, Object> metas = chestplate.getItemMeta().serialize();

        int damages = (metas.containsKey("Damage") && metas.get("Damage") instanceof Integer)
            ? (int) metas.get("Damage")
            : elytraDurabilityOldMethod(chestplate);

        if (damages > -1) {
          progress = Math.max(0, 1 - damages / ((double) chestplate.getType().getMaxDurability()));

          alterColor(progress);
        }
      }
    }

    if (progress == null) progress = sc.config.getDouble("compass.BOSSBAR.attributes.progress");

    sc.cache.set(owner.getUniqueId(), cacheKey, progress, sc.config.getInt("delays.elytra_durability") * 1000);

    return progress;
  }
}
