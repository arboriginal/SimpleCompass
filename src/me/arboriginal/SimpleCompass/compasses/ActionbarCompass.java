package me.arboriginal.SimpleCompass.compasses;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import me.arboriginal.SimpleCompass.plugin.SimpleCompass;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class ActionbarCompass extends AbstractCompass {
  // ----------------------------------------------------------------------------------------------
  // Constructor methods
  // ----------------------------------------------------------------------------------------------

  public ActionbarCompass(SimpleCompass main, Player player) {
    super(main, player, CompassTypes.ACTIONBAR);
  }

  // ----------------------------------------------------------------------------------------------
  // SimpleCompass methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public void display(String datas) {
    owner.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(datas));
  }

  @Override
  public void refresh() {
    super.refresh();

    if (plugin.config.getBoolean("compass.ACTIONBAR.maintain_when_not_moving") && task == null) {
      task = new BukkitRunnable() {
        @Override
        public void run() {
          if (isCancelled()) return;
          refresh();
        }
      };

      Long delay = plugin.config.getLong("compass.ACTIONBAR.maintain_delay");

      task.runTaskTimer(plugin, delay, delay);
    }
  }
}
