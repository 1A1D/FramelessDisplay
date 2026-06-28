package io.papermc.frameless;

import org.bukkit.plugin.java.JavaPlugin;

public class FramelessDisplay extends JavaPlugin {

    private static FramelessDisplay instance;
    public static final String PDC_KEY = "frameless_display";
    public static final String PDC_ROTATION = "frameless_rotation";
    public static final String PDC_GLOW = "frameless_glow";
    public static final String PDC_FACING = "frameless_facing";

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(new FramePlaceListener(this), this);
        getCommand("frameless").setExecutor(new FramelessCommand(this));
        getLogger().info("FramelessDisplay 已启用");
    }

    @Override
    public void onDisable() {
        getLogger().info("FramelessDisplay 已禁用");
    }

    public static FramelessDisplay getInstance() {
        return instance;
    }
}
