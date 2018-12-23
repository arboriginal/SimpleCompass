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

  public BossbarCompass(SimpleCompass main, Player player) {
    super(main, player, CompassTypes.BOSSBAR);
  }

  // ----------------------------------------------------------------------------------------------
  // SimpleCompass methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public void delete() {
    super.delete();
    plugin.tasks.set(TasksTypes.REMOVEWARNING, owner, this);
  }

  @Override
  public void display(String datas) {
    bossbar.setTitle(datas);
    bossbar.setProgress(getProgress());
  }

  @Override
  public void refresh() {
    super.refresh();

    if (plugin.config.getBoolean("compass.BOSSBAR.disappear_when_not_moving")) {
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

      task.runTaskLaterAsynchronously(plugin, plugin.config.getInt("compass.BOSSBAR.disappear_delay"));
    }
  }

  @Override
  public void init() {
    super.init();

    bossbar = Bukkit.createBossBar("",
        BarColor.valueOf(plugin.config.getString("compass.BOSSBAR.attributes.color")),
        BarStyle.valueOf(plugin.config.getString("compass.BOSSBAR.attributes.style")));

    bossbar.addPlayer(owner);
    bossbar.setProgress(getProgress());
    bossbar.setVisible(true);
  }

  // ----------------------------------------------------------------------------------------------
  // Specific methods
  // ----------------------------------------------------------------------------------------------

  private void alterColor(double durability) {
    Object levels = plugin.config.get("compass.BOSSBAR.attributes.elytra_durability.levels");

    if (levels == null || !(levels instanceof Map)) return;

    durability *= 100;

    for (Object value : ((Map<?, ?>) levels).keySet()) {
      if (durability < (int) value) {
        bossbar.setColor(BarColor.valueOf((String) ((Map<?, ?>) levels).get(value)));

        return;
      }
    }

    bossbar.setColor(BarColor.valueOf(plugin.config.getString("compass.BOSSBAR.attributes.color")));
  }

  private double getProgress() {
    String cacheKey = "compass." + type;
    Double progress = (Double) plugin.cache.get(owner.getUniqueId(), cacheKey);

    if (progress != null) return progress;

    if (/**/plugin.config.getBoolean("compass.BOSSBAR.attributes.elytra_durability.wearing")
        || (plugin.config.getBoolean("compass.BOSSBAR.attributes.elytra_durability.gliding") && owner.isGliding())) {
      ItemStack chestPlate = owner.getInventory().getChestplate();

      if (chestPlate != null && chestPlate.getType().equals(Material.ELYTRA)
          && !chestPlate.getItemMeta().isUnbreakable()) {
        Map<String, Object> metas = chestPlate.getItemMeta().serialize();

        if (metas.containsKey("Damage") && metas.get("Damage") instanceof Integer) {
          progress = 1 - ((int) metas.get("Damage")) / ((double) chestPlate.getType().getMaxDurability());

          alterColor(progress);
        }
      }
    }

    if (progress == null) progress = plugin.config.getDouble("compass.BOSSBAR.attributes.progress");

    plugin.cache.set(owner.getUniqueId(), cacheKey, progress, plugin.config.getInt("delays.elytra_durability") * 1000);

    return progress;
  }
}
