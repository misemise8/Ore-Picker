package net.misemise.ore_picker.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 簡易設定マネージャ（minableList を追加）
 */
public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "orepicker.json";

    public static Config INSTANCE = new Config(); // loaded at startup (default until read)

    public static class Config {
        @SerializedName("maxBlocks")
        public int maxBlocks = 128;

        @SerializedName("mode")
        public String mode = "hold"; // "hold" or "toggle"

        @SerializedName("requireSneak")
        public boolean requireSneak = false;

        @SerializedName("requireTool")
        public boolean requireTool = false;

        @SerializedName("minableTag")
        public String minableTag = "minecraft:ores";

        @SerializedName("minableList")
        public List<String> minableList = defaultMinableList();
    }

    private static List<String> defaultMinableList() {
        List<String> l = new ArrayList<>();
        l.add("minecraft:coal_ore");
        l.add("minecraft:iron_ore");
        l.add("minecraft:gold_ore");
        l.add("minecraft:diamond_ore");
        l.add("minecraft:lapis_ore");
        l.add("minecraft:nether_quartz_ore");
        return l;
    }

    public static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static void load() {
        Path p = getConfigPath();
        try {
            if (Files.notExists(p)) {
                INSTANCE = new Config();
                save();
                System.out.println("[OrePicker] Created default config at: " + p.toAbsolutePath());
                return;
            }
            String json = Files.readString(p);
            Config loaded = GSON.fromJson(json, Config.class);
            if (loaded != null) {
                INSTANCE = loaded;
                System.out.println("[OrePicker] Loaded config: maxBlocks=" + INSTANCE.maxBlocks + ", mode=" + INSTANCE.mode + ", minableList=" + INSTANCE.minableList);
            } else {
                System.err.println("[OrePicker] Config file parsed to null, using defaults");
                INSTANCE = new Config();
            }
        } catch (Throwable t) {
            t.printStackTrace();
            System.err.println("[OrePicker] Failed to load config, using defaults");
            INSTANCE = new Config();
        }
    }

    public static void save() {
        Path p = getConfigPath();
        try {
            String json = GSON.toJson(INSTANCE);
            Files.createDirectories(p.getParent());
            Files.writeString(p, json);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("[OrePicker] Failed to save config: " + e.getMessage());
        }
    }
}
