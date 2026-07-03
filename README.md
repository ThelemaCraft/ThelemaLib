# ThelemaLib

一个为 Minecraft NeoForge 1.21.1 设计的轻量级模组开发库，提供数据持久化、区域管理、配方系统等常用工具。

## 快速开始

### 添加依赖
```gradle
dependencies {
    implementation files("libs/thelemalib-1.0.0.jar")
}
```

---

## 核心功能

### 1. 数据持久化（LevelMap）

基于 NBT 的键值对存储，自动同步服务端与客户端。

```java
// 获取一个持久化 Map
LevelMap<String, BlockPos> map = LevelMap.common(level, "my_data");

// 读写数据（自动保存）
map.put("home", new BlockPos(0, 64, 0));
BlockPos pos = map.get("home");

// 手动触发全量同步
map.sync();

// 增量同步（性能更好）
map.syncPut(new BlockPos(10, 64, 10), "player", "lastPos");
map.syncRemove("player", "lastPos");
```

**客户端读取**：
```java
Map<String, Object> data = LevelMapClient.common("minecraft:overworld", "my_data");
```

### 2. 区域管理（Area）

创建带有事件回调的世界区域。

```java
// 注册区域类型
AreaRegistry.register("my_zone", (name, aabb) -> new Area() {
    @Override public String type() { return "my_zone"; }
    @Override public String name() { return name; }
    @Override public AABB aabb() { return aabb; }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (aabb().contains(event.getPos().getCenter())) {
            // 阻止放置逻辑
        }
    }
});

// 创建区域
AreaManager.add(level, "my_zone", "spawn_area", 
    new BlockPos(-10, 0, -10), new BlockPos(10, 10, 10));
```

### 3. 自定义配方

支持带操作链的有序/无序合成及烧炼配方。

**JSON 示例（无序合成）**：
```json
{
  "type": "thelemalib:t_shapeless",
  "ingredients": [{"item": "minecraft:diamond"}],
  "result": "minecraft:diamond_block",
  "handle": [
    {"type": "set_result"},
    {"type": "rename", "text": "压缩钻石"},
    {"type": "modify_count", "op": "muti", "wrapper": 2}
  ]
}
```

**可用的操作（handle）**：
- `set_result` / `add_result`：设置/追加输出
- `modify_count` / `modify_damage`：修改数量/耐久
- `modify_armor` / `modify_attack_damage`：修改属性
- `modify_custom_data` / `modify_block_entity_data`：修改NBT
- `rename`：重命名物品
- `branch`：条件分支（支持 item_id、damage、tag、custom_data 等）

### 4. 工具提示（ToolTip）

构建格式化的物品提示。

```java
new ToolTip(tip)
    .trans("item.mymod.info").color(ChatFormatting.GOLD).enter()
    .is(player.isCreative())
    .text("[仅创造模式]").color(ChatFormatting.RED)
    .or().text("[生存模式]").color(ChatFormatting.GREEN)
    .enter()
    .build();
```

**语言文件注入**（`lang/zh_cn.json`）：
```json
{
  "tooltip.inject.mymod:diamond_sword": "§6强力武器"
}
```

---

## 其他工具

- **容器GUI**：`ContainerHelper.open(player, handler, title)` 快速打开物品栏
- **耐久操作**：`Durability.get(stack)` / `Durability.damage(stack, 1)`
- **物品NBT**：`ItemCustomData.update(stack, tag -> tag.putString("key", "value"))`
- **伤害队列**：`DamageQueue.hurt(entity, 10f, damageType)` 带延迟/加速/缓冲的伤害系统

---

## 依赖

- Minecraft 1.21.1
- NeoForge 21.1.233+

---

## 开源协议

MIT License