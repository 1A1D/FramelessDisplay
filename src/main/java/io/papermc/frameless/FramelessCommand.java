package io.papermc.frameless;

import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * /frameless migrate [radius] — 迁移附近展示框
 * /frameless stats — 查看统计
 * /frameless reload — 重载配置
 */
public class FramelessCommand implements CommandExecutor {

    private final FramelessDisplay plugin;

    public FramelessCommand(FramelessDisplay plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "migrate" -> doMigrate(sender, args);
            case "stats" -> doStats(sender);
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage("§a[FramelessDisplay] 配置已重载");
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== FramelessDisplay ===");
        sender.sendMessage("§e/frameless migrate [radius] §7- 迁移附近展示框（默认32格）");
        sender.sendMessage("§e/frameless stats §7- 查看统计");
        sender.sendMessage("§e/frameless reload §7- 重载配置");
    }

    private void doMigrate(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§c此命令只能由玩家执行");
            return;
        }

        int radius = 32;
        if (args.length >= 2) {
            try { radius = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
        }
        radius = Math.min(radius, 256);

        final int finalRadius = radius;
        final int[] count = {0};

        new BukkitRunnable() {
            @Override
            public void run() {
                int processed = 0;
                for (Entity entity : player.getWorld().getNearbyEntities(
                        player.getLocation(), finalRadius, finalRadius, finalRadius)) {
                    if (processed >= 50) break;

                    if (entity.getType() == EntityType.ITEM_FRAME
                            || entity.getType() == EntityType.GLOW_ITEM_FRAME) {
                        ItemFrame frame = (ItemFrame) entity;
                        boolean isGlow = entity.getType() == EntityType.GLOW_ITEM_FRAME;

                        // 生成 ItemDisplay
                        ItemDisplay display = (ItemDisplay) player.getWorld().spawnEntity(
                                entity.getLocation(), EntityType.ITEM_DISPLAY);

                        display.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
                        display.setGlowing(isGlow);
                        display.setPersistent(true);

                        // 移交物品
                        if (frame.getItem() != null && frame.getItem().getType() != org.bukkit.Material.AIR) {
                            display.setItemStack(frame.getItem().clone());
                        }

                        // 朝向 + 旋转
                        org.bukkit.block.BlockFace face = frame.getAttachedFace();
                        int frameRotation = frame.getRotation().ordinal();
                        display.setTransformation(FramePlaceListener.buildTransform(face, frameRotation));

                        // PDC 标记
                        NamespacedKey keyMark = new NamespacedKey(plugin, FramelessDisplay.PDC_KEY);
                        NamespacedKey keyFacing = new NamespacedKey(plugin, FramelessDisplay.PDC_FACING);
                        NamespacedKey keyRot = new NamespacedKey(plugin, FramelessDisplay.PDC_ROTATION);
                        display.getPersistentDataContainer().set(keyMark, PersistentDataType.STRING, "1");
                        display.getPersistentDataContainer().set(keyFacing, PersistentDataType.STRING, face.name());
                        display.getPersistentDataContainer().set(keyRot, PersistentDataType.STRING, String.valueOf(frameRotation));
                        if (isGlow) {
                            NamespacedKey keyGlow = new NamespacedKey(plugin, FramelessDisplay.PDC_GLOW);
                            display.getPersistentDataContainer().set(keyGlow, PersistentDataType.STRING, "1");
                        }

                        frame.remove();
                        count[0]++;
                        processed++;
                    }
                }

                if (processed == 0) {
                    cancel();
                    player.sendMessage("§a[FramelessDisplay] 迁移完成！共转换 " + count[0] + " 个展示框");
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void doStats(CommandSender sender) {
        int displayCount = 0;
        int frameCount = 0;

        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getType() == EntityType.ITEM_DISPLAY) {
                    NamespacedKey keyMark = new NamespacedKey(plugin, FramelessDisplay.PDC_KEY);
                    if (entity.getPersistentDataContainer().has(keyMark, PersistentDataType.STRING)) {
                        displayCount++;
                    }
                } else if (entity.getType() == EntityType.ITEM_FRAME
                        || entity.getType() == EntityType.GLOW_ITEM_FRAME) {
                    frameCount++;
                }
            }
        }

        sender.sendMessage("§6=== FramelessDisplay 统计 ===");
        sender.sendMessage("§aItemDisplay (已转换): " + displayCount);
        sender.sendMessage("§e剩余展示框: " + frameCount);
    }
}
