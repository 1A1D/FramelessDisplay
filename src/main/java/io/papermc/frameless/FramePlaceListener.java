package io.papermc.frameless;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class FramePlaceListener implements Listener {

    private final FramelessDisplay plugin;
    private final Set<UUID> destroying = new HashSet<>();

    public FramePlaceListener(FramelessDisplay plugin) {
        this.plugin = plugin;
    }

    private NamespacedKey key(String name) {
        return new NamespacedKey(plugin, name);
    }

    // ═══════════════ 右键 ═══════════════

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onRightClickBlock(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        var clicked = event.getClickedBlock();
        if (clicked == null) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        ItemDisplay existing = findOnFace(clicked, event.getBlockFace());
        if (existing != null) {
            event.setCancelled(true);
            interact(player, existing);
            return;
        }

        if (hand.getType() != Material.ITEM_FRAME && hand.getType() != Material.GLOW_ITEM_FRAME) return;
        place(event, player, hand, clicked, event.getBlockFace());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof ItemDisplay display)) return;
        if (!isOurs(display)) return;
        event.setCancelled(true);
        interact(event.getPlayer(), display);
    }

    // ═══════════════ 左键破坏 ═══════════════

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof ItemDisplay display)) return;
        if (!isOurs(display)) return;
        if (!player.hasPermission("frameless.break")) return;

        event.setCancelled(true);
        destroy(display, player);
    }

    // ═══════════════ 方块破坏 ═══════════════

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        var block = event.getBlock();
        Location center = block.getLocation().toCenterLocation();
        Set<ItemDisplay> toDestroy = new HashSet<>();

        for (Entity e : block.getWorld().getNearbyEntities(center, 1.0, 1.0, 1.0)) {
            if (!(e instanceof ItemDisplay display)) continue;
            if (!isOurs(display)) continue;

            String faceName = display.getPersistentDataContainer()
                    .get(key(FramelessDisplay.PDC_FACING), PersistentDataType.STRING);
            if (faceName == null) continue;

            org.bukkit.block.BlockFace face = org.bukkit.block.BlockFace.valueOf(faceName);
            org.bukkit.block.Block attached = display.getLocation().getBlock().getRelative(face.getOppositeFace());

            if (attached.getX() == block.getX()
                    && attached.getY() == block.getY()
                    && attached.getZ() == block.getZ()) {
                toDestroy.add(display);
            }
        }

        for (ItemDisplay d : toDestroy) {
            destroy(d, null);
        }
    }

    // ═══════════════ 核心 ═══════════════

    private boolean isOurs(ItemDisplay display) {
        return display.getPersistentDataContainer()
                .has(key(FramelessDisplay.PDC_KEY), PersistentDataType.STRING);
    }

    private ItemDisplay findOnFace(org.bukkit.block.Block clicked, org.bukkit.block.BlockFace face) {
        Location center = clicked.getLocation().toCenterLocation()
                .add(face.getModX() * 0.51, face.getModY() * 0.51, face.getModZ() * 0.51);
        for (Entity e : clicked.getWorld().getNearbyEntities(center, 0.35, 0.35, 0.35)) {
            if (!(e instanceof ItemDisplay d)) continue;
            if (!isOurs(d)) continue;
            String savedFace = d.getPersistentDataContainer()
                    .get(key(FramelessDisplay.PDC_FACING), PersistentDataType.STRING);
            if (savedFace != null && savedFace.equals(face.name())) {
                return d;
            }
        }
        return null;
    }

    private void place(PlayerInteractEvent event, Player player, ItemStack hand,
                       org.bukkit.block.Block clicked, org.bukkit.block.BlockFace face) {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("enabled", true)) return;
        if (config.getStringList("disabled-worlds").contains(player.getWorld().getName())) return;
        if (!player.hasPermission("frameless.use")) return;

        event.setCancelled(true);
        boolean isGlow = hand.getType() == Material.GLOW_ITEM_FRAME;

        // ★ 放置前清理同一面上所有已有的旧 display（防堆叠）
        Location searchCenter = clicked.getLocation().toCenterLocation()
                .add(face.getModX() * 0.51, face.getModY() * 0.51, face.getModZ() * 0.51);
        for (Entity e : clicked.getWorld().getNearbyEntities(searchCenter, 0.5, 0.5, 0.5)) {
            if (e instanceof ItemDisplay d && isOurs(d)) {
                String saved = d.getPersistentDataContainer()
                        .get(key(FramelessDisplay.PDC_FACING), PersistentDataType.STRING);
                if (saved != null && saved.equals(face.name())) {
                    d.remove();
                }
            }
        }

        Location loc = clicked.getLocation()
                .add(0.5 + face.getModX() * 0.51,
                     0.5 + face.getModY() * 0.51,
                     0.5 + face.getModZ() * 0.51);

        ItemDisplay display = (ItemDisplay) clicked.getWorld().spawnEntity(loc, EntityType.ITEM_DISPLAY);
        display.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        display.setGlowing(isGlow);
        display.setPersistent(true);
        display.setTransformation(buildTransform(face, 0));
        display.setItemStack(new ItemStack(isGlow ? Material.GLOW_ITEM_FRAME : Material.ITEM_FRAME));

        display.getPersistentDataContainer().set(key(FramelessDisplay.PDC_KEY), PersistentDataType.STRING, "1");
        display.getPersistentDataContainer().set(key(FramelessDisplay.PDC_FACING), PersistentDataType.STRING, face.name());
        display.getPersistentDataContainer().set(key(FramelessDisplay.PDC_ROTATION), PersistentDataType.STRING, "0");
        if (isGlow) {
            display.getPersistentDataContainer().set(key(FramelessDisplay.PDC_GLOW), PersistentDataType.STRING, "1");
        }
        // ClearLag 保护：tagged=true 会保护带此标签的实体不被清理
        display.addScoreboardTag("clearlag_exclude");

        if (player.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
        }
    }

    private void interact(Player player, ItemDisplay display) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        ItemStack shown = display.getItemStack();
        boolean showsGlow = display.getPersistentDataContainer()
                .has(key(FramelessDisplay.PDC_GLOW), PersistentDataType.STRING);
        boolean isShowingFrame = shown != null &&
                (shown.getType() == Material.ITEM_FRAME || shown.getType() == Material.GLOW_ITEM_FRAME);

        if (hand.getType().isAir()) {
            if (!isShowingFrame && shown != null && !shown.getType().isAir()) {
                giveOrDrop(player, shown.clone());
                display.setItemStack(new ItemStack(showsGlow ? Material.GLOW_ITEM_FRAME : Material.ITEM_FRAME));
                player.playSound(display.getLocation(), Sound.ENTITY_ITEM_FRAME_REMOVE_ITEM, 0.5f, 1.0f);
            }
        } else {
            if (!isShowingFrame && shown != null && !shown.getType().isAir()) {
                giveOrDrop(player, shown.clone());
            }
            ItemStack toShow = hand.clone();
            toShow.setAmount(1);
            display.setItemStack(toShow);
            player.playSound(display.getLocation(), Sound.ENTITY_ITEM_FRAME_ADD_ITEM, 0.5f, 1.0f);
            if (player.getGameMode() != GameMode.CREATIVE) {
                hand.setAmount(hand.getAmount() - 1);
            }
        }
    }

    private void destroy(ItemDisplay display, Player player) {
        // ★ UUID 去重：防多个事件同时触发重复掉落
        if (!destroying.add(display.getUniqueId())) return;
        try {
            ItemStack shown = display.getItemStack();
            // 空框图标不算展示物，跳过避免掉落双倍
            if (shown != null && !shown.getType().isAir()
                    && shown.getType() != Material.ITEM_FRAME
                    && shown.getType() != Material.GLOW_ITEM_FRAME) {
                display.getWorld().dropItemNaturally(display.getLocation(), shown.clone());
            }
            boolean isGlow = display.getPersistentDataContainer()
                    .has(key(FramelessDisplay.PDC_GLOW), PersistentDataType.STRING);
            display.getWorld().dropItemNaturally(display.getLocation(),
                    new ItemStack(isGlow ? Material.GLOW_ITEM_FRAME : Material.ITEM_FRAME));
            if (player != null) {
                player.playSound(display.getLocation(), Sound.ENTITY_ITEM_FRAME_BREAK, 0.5f, 1.0f);
            }
            display.remove();
        } finally {
            destroying.remove(display.getUniqueId());
        }
    }

    private void giveOrDrop(Player player, ItemStack item) {
        for (ItemStack leftover : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItem(player.getLocation(), leftover);
        }
    }

    // ═══════════════ 变换 ═══════════════

    public static Transformation buildTransform(org.bukkit.block.BlockFace face, int rotation) {
        float baseYaw = switch (face) {
            case SOUTH -> 0f;
            case WEST  -> 90f;
            case NORTH -> 180f;
            case EAST  -> -90f;
            default   -> 0f;
        };
        float yawRad = (float) Math.toRadians(baseYaw + rotation * 45f);

        Quaternionf left = new Quaternionf();
        if (face == org.bukkit.block.BlockFace.UP) {
            left.rotateX((float) Math.toRadians(-90));
        } else if (face == org.bukkit.block.BlockFace.DOWN) {
            left.rotateX((float) Math.toRadians(90));
        }
        left.mul(new Quaternionf().rotateY(yawRad));

        return new Transformation(
                new Vector3f(0, 0, 0),
                left,
                new Vector3f(0.5f, 0.5f, 0.5f),
                new Quaternionf());
    }
}
