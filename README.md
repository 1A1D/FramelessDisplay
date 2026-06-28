# FramelessDisplay (Unfinished / Abandoned)

**Status: ❌ 半成品，已废弃。保留供未来参考。**

## 背景

目标：用 Paper 1.21.11 的 ItemDisplay 实体替代原版展示框（ItemFrame），去除边框+去除碰撞箱，让物品直接显示在墙面上。

## 现状

### 已完成 ✅
- ✅ 右键方块放展示框 → 自动替换为 ItemDisplay 实体
- ✅ 右键取出/放入物品
- ✅ 左键破坏，掉落物品+展示框
- ✅ 8 个方向朝向（NORTH/SOUTH/EAST/WEST/UP/DOWN）
- ✅ Glow Item Frame 支持
- ✅ `/frameless migrate` 批量迁移现有展示框
- ✅ `/frameless stats` 统计
- ✅ ClearLag 排除保护（scoreboard tag + config）
- ✅ 方块破坏时连带移除附着展示框

### 未解决 ❌
- ❌ **根本性架构问题**：ItemDisplay 是真实实体，占用服务器实体计数、写入 chunk NBT、参与 tick 遍历
- ❌ ClearLag 清理威胁：虽然做了 tag+config 双重保护，但无法 100% 保证所有清理插件不误伤
- ❌ 需要持续维护实体保护逻辑，每加一个新插件都可能要加排除

### 更好的方案：虚拟实体（QuickShop 路线）
QuickShop-Hikari 用 ProtocolLib 伪造 `EntityType.ITEM` spawn/meta/destroy 数据包，物品**只存在于客户端**，服务端零实体开销：
- 不占 entity count
- 不写 chunk NBT
- 不参与 tick
- ClearLag 天然扫不到

## 文件结构

```
frameless/
├── build.gradle.kts          # Gradle 构建
├── settings.gradle.kts
├── src/main/java/io/papermc/frameless/
│   ├── FramelessDisplay.java     # 主类
│   ├── FramelessCommand.java     # /frameless 命令
│   └── FramePlaceListener.java   # 放置/交互/破坏逻辑
└── src/main/resources/
    ├── plugin.yml
    └── config.yml
```

## 构建

```bash
gradlew build
# 输出: build/libs/FramelessDisplay-1.0.0.jar
```

## 环境

- Paper 1.21.11 (api-version 1.21)
- Java 21
- Gradle 8.11

## 为什么废弃

经过完整学习 QuickShop-Hikari 虚拟实体架构后，确定**纯数据包方案**是正确方向。本项目的实体方案有根本性缺陷，不值得继续修补。

下一版将用 ProtocolLib 全重写。

---

⚠️ 此仓库仅作存档，不再维护。
