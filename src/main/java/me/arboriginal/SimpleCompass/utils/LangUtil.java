package me.arboriginal.SimpleCompass.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import com.google.common.base.Charsets;
import me.arboriginal.SimpleCompass.plugin.SimpleCompass;

public class LangUtil {
    private SimpleCompass sc;

    // Constructor methods ---------------------------------------------------------------------------------------------

    public LangUtil(SimpleCompass plugin) {
        sc = plugin;
    }

    // Public methods --------------------------------------------------------------------------------------------------

    public FileConfiguration getLocale(String language) {
        FileConfiguration locale;
        boolean           newFile;

        String langRes  = "lang/" + language + ".yml";
        String langRdef = "lang/en.yml";
        File   langFile = new File(sc.getDataFolder(), langRes);

        if (newFile = !langFile.exists()) {
            String logMsg = null;

            sc.getLogger().warning("Lang file for « " + language + " » doesn't exist.");

            if (sc.getResource(langRes) != null) {
                logMsg = "Lang file for « " + language + " » copied from plugin.";
            }
            else {
                sc.getLogger().warning("Lang « " + language + " » doesn't exist in the plugin either.");

                try {
                    writeResourceToFile(sc.getResource(langRdef), langFile);

                    logMsg  = "A new lang file for « " + language + " » has been generated, based on english.";
                    langRes = null;
                }
                catch (Exception e) {
                    sc.getLogger().severe("Lang file for « " + language + " » cannot be generated.");

                    logMsg  = "Fallback to default lang file.";
                    langRes = langRdef;
                }
            }

            copyResourceToFile(langRes, langFile);
            sc.getLogger().info(logMsg);
        }

        locale = YamlConfiguration.loadConfiguration(langFile);

        if (!newFile) saveConfigToFile(locale, langFile, langRdef);

        return locale;
    }

    // Private methods -------------------------------------------------------------------------------------------------

    private void copyResourceToFile(String resource, File file) {
        if (resource != null) {
            sc.saveResource(resource, false);

            file = new File(sc.getDataFolder(), resource);
        }
    }

    private void saveConfigToFile(FileConfiguration config, File file, String defaultRes) {
        config.setDefaults(YamlConfiguration.loadConfiguration(
                new InputStreamReader(sc.getResource(defaultRes), Charsets.UTF_8)));
        // This ensure sentences added in next versions are stored in the file with their default values
        config.options().copyDefaults(true);

        try {
            config.save(file);
        }
        catch (Exception e) {
            sc.getLogger().warning("The language file cannot be updated in your plugin folder. "
                    + "You need to check by yourself if you didn't missed some sentences you want to translate. "
                    + "Default language will be used for them.");
        }
    }

    private void writeResourceToFile(InputStream in, File file) throws Exception {
        file.getParentFile().mkdirs();

        OutputStream out = new FileOutputStream(file);
        byte[]       buf = new byte[1024];
        int          len;

        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);

        out.close();
        in.close();
    }
}
