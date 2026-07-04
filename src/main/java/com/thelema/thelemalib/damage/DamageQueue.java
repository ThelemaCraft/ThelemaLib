package com.thelema.thelemalib.damage;
import com.thelema.thelemalib.ThelemaLib;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@EventBusSubscriber
public class DamageQueue extends SavedData {
    private static final String DATA_NAME = "thelema_damage_queue";
    private static final String DEFAULT_IDENTIFIER = "undefined";

    // 主队列：每个实体待处理的伤害列表（先进先出）
    private final Map<UUID, LinkedList<Damage>> conveyor = new HashMap<>();
    // 缓冲区：暂存未触发的伤害，键为实体UUID -> 标识符 -> Damage
    private final Map<UUID, Map<String, Damage>> bufferZone = new HashMap<>();
    // 上次成功处理tick记录（用于动态间隔）
    private final Map<UUID, Long> lastProcessTick = new HashMap<>();
    // 连续加速次数：键为实体UUID，值为剩余需要连续加速的伤害数量
    private final Map<UUID, Integer> accelerateCount = new HashMap<>();
    // 延迟伤害任务列表：键为实体UUID -> 标识符 -> DelayedDamage
    private final Map<UUID, Map<String, DelayedDamage>> delayed = new HashMap<>();

    // ============ 静态访问 ============
    public static DamageQueue get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(DamageQueue::new, DamageQueue::load),
                DATA_NAME
        );
    }

    private static DamageQueue getStorage(LivingEntity entity) {
        return get((ServerLevel) entity.level());
    }

    // ============ 公开接口 ============


    public static void hurt(LivingEntity target, float damage, ResourceLocation damageType) {
        hurt(target, damage, damageType, false, null);
    }
    /**
     * 添加伤害到队列尾部
     */

    public static void hurt(LivingEntity target, float damage, ResourceLocation damageType, boolean head) {
        hurt(target, damage, damageType, head, null);
    }

    public static void hurt(LivingEntity target, float damage, ResourceLocation damageType, @Nullable Entity source) {
        hurt(target, damage, damageType, false, source);
    }
    /**
     * 添加伤害到队列
     * @param head true：插入头部（优先处理），false：插入尾部
     */
    public static void hurt(LivingEntity target, float damage, ResourceLocation damageType, boolean head, @Nullable Entity source) {
        if (target.level().isClientSide) return;
        DamageQueue storage = getStorage(target);
        UUID sourceUUID = source != null ? source.getUUID() : null;
        if (head) {
            storage.addToHead(target.getUUID(), Damage.of(damage, damageType, sourceUUID));
        } else {
            storage.addToTail(target.getUUID(), Damage.of(damage, damageType, sourceUUID));
        }
    }

    /**
     * 将伤害暂存到缓冲区，等待后续触发
     */
    public static void waiter(LivingEntity target, String identifier, float damage, ResourceLocation damageType, @Nullable Entity source) {
        if (target.level().isClientSide) return;
        getStorage(target).addToBuffer(target.getUUID(), identifier, Damage.of(damage, damageType, identifier, source != null ? source.getUUID() : null));
    }

    public static void waiter(LivingEntity target, String identifier, float damage, ResourceLocation damageType) {
        waiter(target, identifier, damage, damageType, null);
    }
    /**
     * 触发缓冲区中的指定伤害，将其加入队列尾部
     */
    public static boolean trigger(LivingEntity target, String identifier) {
        return trigger(target, identifier, true);
    }

    /**
     * 触发缓冲区中的指定伤害，将其加入队列
     * @param head true：插入头部（优先处理），false：插入尾部
     */
    public static boolean trigger(LivingEntity target, String identifier, boolean head) {
        if (target.level().isClientSide) return false;
        DamageQueue storage = getStorage(target);
        if (head) {
            // 需要将缓冲区中的伤害转移到队首
            Damage damage = storage.removeFromBuffer(target.getUUID(), identifier);
            if (damage != null) {
                storage.addToHead(target.getUUID(), damage);
                return true;
            }
            return false;
        } else {
            return storage.transferToConveyor(target.getUUID(), identifier); // 原有逻辑，转到队尾
        }
    }

    /**
     * 连续加速指定数量的伤害
     * @param target 目标实体
     * @param ticks  提前的tick数（越大越早处理）
     * @param count  连续加速的伤害数量
     */
    public static void accelerate(LivingEntity target, int ticks, int count) {
        if (target.level().isClientSide) return;
        if (count <= 0) return; // 避免无效计数
        DamageQueue storage = getStorage(target);
        UUID uuid = target.getUUID();
        long currentTick = target.level().getGameTime();

        // 先执行原有加速逻辑
        long newLastTick = Math.max(0, storage.lastProcessTick.getOrDefault(uuid, currentTick) - ticks);
        storage.lastProcessTick.put(uuid, newLastTick);

        // 叠加连续加速次数（多次调用可累加）
        int currentCount = storage.accelerateCount.getOrDefault(uuid, 0);
        storage.accelerateCount.put(uuid, currentCount + count);

        storage.setDirty();
    }

    /**
     * 连续加速指定数量的伤害（使用默认最大加速幅度）
     * @param target 目标实体
     * @param count  连续加速的伤害数量
     */
    public static void accelerate(LivingEntity target, int count) {
        accelerate(target, 100, count);
    }

    /**
     * 延迟伤害：在 delayTicks 个 tick 后自动触发该伤害（加入队列头部）
     * @param delayTicks 延迟的 tick数
     */
    public static void delay(LivingEntity target, String identifier, float damage, ResourceLocation damageType, int delayTicks, @Nullable Entity source) {
        delay(target, identifier, damage, damageType, delayTicks, true, source);
    }

    /**
     * 延迟伤害：在 delayTicks 个 tick 后自动触发该伤害
     * @param delayTicks 延迟的 tick数
     * @param head true：插入头部（优先处理），false：插入尾部
     */
    public static void delay(LivingEntity target, String identifier, float damage, ResourceLocation damageType, int delayTicks, boolean head, @Nullable Entity source) {
        if (target.level().isClientSide) return;
        DamageQueue storage = getStorage(target);
        long triggerTick = target.level().getGameTime() + delayTicks;
        UUID sourceUUID = source != null ? source.getUUID() : null;
        DelayedDamage delayedDamage = new DelayedDamage(damage, damageType, identifier, triggerTick, head, sourceUUID);
        storage.delayed.computeIfAbsent(target.getUUID(), k -> new HashMap<>()).put(identifier, delayedDamage);
        storage.setDirty();
    }

    public static void delay(LivingEntity target, String identifier, float damage, ResourceLocation damageType, int delayTicks) {
        delay(target, identifier, damage, damageType, delayTicks, true, null);
    }

    public static void delay(LivingEntity target, String identifier, float damage, ResourceLocation damageType, int delayTicks, boolean head) {
        delay(target, identifier, damage, damageType, delayTicks, head, null);
    }
    // ============ 队列操作（内部） ============
    private void addToHead(UUID entityId, Damage damage) {
        conveyor.computeIfAbsent(entityId, k -> new LinkedList<>()).addFirst(damage);
        setDirty();
    }

    private void addToTail(UUID entityId, Damage damage) {
        conveyor.computeIfAbsent(entityId, k -> new LinkedList<>()).addLast(damage);
        setDirty();
    }

    private void addToBuffer(UUID entityId, String identifier, Damage damage) {
        bufferZone.computeIfAbsent(entityId, k -> new HashMap<>()).put(identifier, damage);
        setDirty();
    }

    private boolean transferToConveyor(UUID entityId, String identifier) {
        Damage damage = removeFromBuffer(entityId, identifier);
        if (damage != null) {
            addToTail(entityId, damage);
            return true;
        }
        return false;
    }

    @Nullable
    private Damage removeFromBuffer(UUID entityId, String identifier) {
        Map<String, Damage> map = bufferZone.get(entityId);
        if (map == null) return null;
        Damage removed = map.remove(identifier);
        if (map.isEmpty()) bufferZone.remove(entityId);
        if (removed != null) setDirty();
        return removed;
    }

    // ============ 动态调度 ============
    private static int getInterval(int queueSize) {
        return Math.max(1, 20 / queueSize);
    }

    private void processEntity(UUID uuid, LivingEntity living, long currentTick) {
        // 忽略创造模式玩家
        if (living instanceof ServerPlayer player && player.isCreative()) {
            return;
        }

        // 处理延迟伤害（原有逻辑不变）
        Map<String, DelayedDamage> delays = delayed.get(uuid);
        if (delays != null && !delays.isEmpty()) {
            Iterator<Map.Entry<String, DelayedDamage>> it = delays.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, DelayedDamage> entry = it.next();
                if (entry.getValue().triggerTick <= currentTick) {
                    DelayedDamage d = entry.getValue();
                    if (d.head) {
                        addToHead(uuid, Damage.of(d.damage, d.damageType, d.identifier, d.sourceUUID));
                    } else {
                        addToTail(uuid, Damage.of(d.damage, d.damageType, d.identifier, d.sourceUUID));
                    }
                    it.remove();
                }
            }
            if (delays.isEmpty()) delayed.remove(uuid);
            setDirty();
        }

        // 处理主队列
        LinkedList<Damage> queue = conveyor.get(uuid);
        if (queue == null || queue.isEmpty()) return;

        int size = queue.size();
        int interval = getInterval(size);
        long lastTick = lastProcessTick.getOrDefault(uuid, currentTick - interval);

        // 核心修改：判断是否有连续加速次数，有则强制忽略间隔
        boolean isAccelerating = accelerateCount.getOrDefault(uuid, 0) > 0;
        if (isAccelerating || currentTick - lastTick >= interval) {
            Damage damage = queue.peek();
            if (damage != null && damage.damage > 0) {
                Entity sourceEntity = null;
                if (damage.sourceUUID != null) {
                    ServerLevel level = (ServerLevel) living.level();
                    sourceEntity = level.getEntity(damage.sourceUUID);
                }

                DamageSource ds = sourceOf(living, damage.damageType, sourceEntity);

                int originalInvul = living.invulnerableTime;
                if (originalInvul > 10) {
                    living.invulnerableTime = 10;
                }
                boolean success = living.hurt(ds, damage.damage);
                if (success) {
                    queue.poll();
                    lastProcessTick.put(uuid, currentTick);
                    setDirty();

                    // 核心修改：消耗一次连续加速次数
                    if (isAccelerating) {
                        int remainCount = accelerateCount.get(uuid) - 1;
                        if (remainCount <= 0) {
                            accelerateCount.remove(uuid);
                        } else {
                            accelerateCount.put(uuid, remainCount);
                        }
                    }
                } else if (originalInvul > 10) {
                    living.invulnerableTime = originalInvul;
                }
            } else {
                // 无效伤害直接丢弃
                if (damage != null && damage.damage <= 0) {
                    queue.poll();
                    if (queue.isEmpty()) {
                        conveyor.remove(uuid);
                        lastProcessTick.remove(uuid);
                    }
                    setDirty();
                }
            }
        }
    }

    // ============ 事件监听 ============
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long tick = event.getServer().getTickCount();
        event.getServer().getAllLevels().forEach(level -> {
            if (level instanceof ServerLevel serverLevel) {
                DamageQueue queue = get(serverLevel);
                queue.cleanup(serverLevel);

                serverLevel.getAllEntities().forEach(entity -> {
                    if (entity instanceof LivingEntity living && living.isAlive()) {
                        queue.processEntity(living.getUUID(), living, tick);
                    }
                });
            }
        });
    }

    // ============ 清理 ============
    private void cleanup(ServerLevel level) {
        boolean changed = false;
        changed |= conveyor.keySet().removeIf(id -> level.getEntity(id) == null);
        changed |= bufferZone.keySet().removeIf(id -> level.getEntity(id) == null);
        changed |= lastProcessTick.keySet().removeIf(id -> level.getEntity(id) == null);
        changed |= delayed.keySet().removeIf(id -> level.getEntity(id) == null);
        changed |= accelerateCount.keySet().removeIf(id -> level.getEntity(id) == null);

        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.isCreative()) {
                UUID uuid = player.getUUID();
                // 如果该玩家有任何伤害数据，则清除
                if (conveyor.containsKey(uuid) || bufferZone.containsKey(uuid) ||
                        lastProcessTick.containsKey(uuid) || delayed.containsKey(uuid) || accelerateCount.containsKey(uuid)) {
                    clearAllDamageFor(uuid);
                    changed = true;
                }
            }
        }
        if (changed) setDirty();
    }

    private void clearAllDamageFor(UUID uuid) {
        boolean changed = conveyor.remove(uuid) != null;
        if (bufferZone.remove(uuid) != null) changed = true;
        if (lastProcessTick.remove(uuid) != null) changed = true;
        if (delayed.remove(uuid) != null) changed = true;
        if (accelerateCount.remove(uuid) != null) changed = true;
        if (changed) setDirty();
    }

    // ============ 序列化 ============
    private static DamageQueue load(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        DamageQueue data = new DamageQueue();

        // 读取 conveyor
        if (tag.contains("Conveyor")) {
            CompoundTag conveyorTag = tag.getCompound("Conveyor");
            for (String uuidStr : conveyorTag.getAllKeys()) {
                UUID uuid = UUID.fromString(uuidStr);
                ListTag listTag = conveyorTag.getList(uuidStr, CompoundTag.TAG_COMPOUND);
                LinkedList<Damage> list = new LinkedList<>();
                for (int i = 0; i < listTag.size(); i++) {
                    list.add(Damage.fromNBT(listTag.getCompound(i)));
                }
                if (!list.isEmpty()) data.conveyor.put(uuid, list);
            }
        }

        // 读取 bufferZone
        if (tag.contains("BufferZone")) {
            CompoundTag bufferTag = tag.getCompound("BufferZone");
            for (String uuidStr : bufferTag.getAllKeys()) {
                UUID uuid = UUID.fromString(uuidStr);
                ListTag listTag = bufferTag.getList(uuidStr, CompoundTag.TAG_COMPOUND);
                Map<String, Damage> inner = new HashMap<>();
                for (int i = 0; i < listTag.size(); i++) {
                    CompoundTag dTag = listTag.getCompound(i);
                    String identifier = dTag.getString("identifier");
                    inner.put(identifier, Damage.fromNBT(dTag));
                }
                if (!inner.isEmpty()) data.bufferZone.put(uuid, inner);
            }
        }

        // 读取 lastProcessTick
        if (tag.contains("LastProcessTick")) {
            CompoundTag tickTag = tag.getCompound("LastProcessTick");
            for (String uuidStr : tickTag.getAllKeys()) {
                UUID uuid = UUID.fromString(uuidStr);
                data.lastProcessTick.put(uuid, tickTag.getLong(uuidStr));
            }
        }

        // 读取 delayed
        if (tag.contains("Delayed")) {
            CompoundTag delayedTag = tag.getCompound("Delayed");
            for (String uuidStr : delayedTag.getAllKeys()) {
                UUID uuid = UUID.fromString(uuidStr);
                ListTag listTag = delayedTag.getList(uuidStr, CompoundTag.TAG_COMPOUND);
                Map<String, DelayedDamage> inner = new HashMap<>();
                for (int i = 0; i < listTag.size(); i++) {
                    CompoundTag dTag = listTag.getCompound(i);
                    String identifier = dTag.getString("identifier");
                    inner.put(identifier, DelayedDamage.fromNBT(dTag));
                }
                if (!inner.isEmpty()) data.delayed.put(uuid, inner);
            }
        }

        // 读取 accelerateCount
        if (tag.contains("AccelerateCount")) {
            CompoundTag accTag = tag.getCompound("AccelerateCount");
            for (String uuidStr : accTag.getAllKeys()) {
                UUID uuid = UUID.fromString(uuidStr);
                data.accelerateCount.put(uuid, accTag.getInt(uuidStr));
            }
        }

        return data;
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag tag, HolderLookup.@NotNull Provider lookupProvider) {
        CompoundTag conveyorTag = new CompoundTag();
        conveyor.forEach((uuid, list) -> {
            ListTag listTag = new ListTag();
            for (Damage d : list) listTag.add(d.toNBT());
            conveyorTag.put(uuid.toString(), listTag);
        });
        tag.put("Conveyor", conveyorTag);

        CompoundTag bufferTag = new CompoundTag();
        bufferZone.forEach((uuid, map) -> {
            ListTag listTag = new ListTag();
            for (Damage d : map.values()) listTag.add(d.toNBT());
            bufferTag.put(uuid.toString(), listTag);
        });
        tag.put("BufferZone", bufferTag);

        CompoundTag tickTag = new CompoundTag();
        lastProcessTick.forEach((uuid, tick) -> tickTag.putLong(uuid.toString(), tick));
        tag.put("LastProcessTick", tickTag);

        CompoundTag delayedTag = new CompoundTag();
        delayed.forEach((uuid, map) -> {
            ListTag listTag = new ListTag();
            for (DelayedDamage d : map.values()) listTag.add(d.toNBT());
            delayedTag.put(uuid.toString(), listTag);
        });
        tag.put("Delayed", delayedTag);

        // 保存 accelerateCount
        CompoundTag accTag = new CompoundTag();
        accelerateCount.forEach((uuid, count) -> accTag.putInt(uuid.toString(), count));
        tag.put("AccelerateCount", accTag);

        return tag;
    }

    // ============ 工具方法 ============
    private static DamageSource sourceOf(LivingEntity target, ResourceLocation damageTypeId, @Nullable Entity sourceEntity) {
        var registry = target.level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
        ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, damageTypeId);
        var holder = registry.getOrThrow(key);
        if (sourceEntity != null) {
            return new DamageSource(holder, sourceEntity);
        } else {
            return new DamageSource(holder);  // 无来源
        }
    }

    // ============ 内部类 ============
    private static class Damage {
        public final float damage;
        public final ResourceLocation damageType;
        public final String identifier;
        @Nullable
        public final UUID sourceUUID;  // 新增：伤害来源实体UUID，可为null

        public Damage(float damage, ResourceLocation damageType, String identifier, @Nullable UUID sourceUUID) {
            this.damage = damage;
            this.damageType = damageType;
            this.identifier = identifier != null ? identifier : DEFAULT_IDENTIFIER;
            this.sourceUUID = sourceUUID;
        }

        public static Damage of(float damage, ResourceLocation damageType, UUID sourceUUID) {
            return new Damage(damage, damageType, DEFAULT_IDENTIFIER, sourceUUID);
        }

        public static Damage of(float damage, ResourceLocation damageType, String identifier, UUID sourceUUID) {
            return new Damage(damage, damageType, identifier, sourceUUID);
        }

        public CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putFloat("damage", damage);
            tag.putString("type", damageType.toString());
            tag.putString("identifier", identifier);
            if (sourceUUID != null) {
                tag.putUUID("sourceUUID", sourceUUID);
            }
            return tag;
        }

        public static Damage fromNBT(CompoundTag tag) {
            UUID sourceUUID = tag.hasUUID("sourceUUID") ? tag.getUUID("sourceUUID") : null;
            return new Damage(
                    tag.getFloat("damage"),
                    ResourceLocation.parse(tag.getString("type")),
                    tag.getString("identifier"),
                    sourceUUID
            );
        }
    }

    private static class DelayedDamage {
        public final float damage;
        public final ResourceLocation damageType;
        public final String identifier;
        public final long triggerTick;
        public final boolean head;
        public final UUID sourceUUID;  // 新增

        public DelayedDamage(float damage, ResourceLocation damageType, String identifier, long triggerTick, boolean head, UUID sourceUUID) {
            this.damage = damage;
            this.damageType = damageType;
            this.identifier = identifier;
            this.triggerTick = triggerTick;
            this.head = head;
            this.sourceUUID = sourceUUID;
        }

        public CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putFloat("damage", damage);
            tag.putString("type", damageType.toString());
            tag.putString("identifier", identifier);
            tag.putLong("triggerTick", triggerTick);
            tag.putBoolean("head", head);
            if (sourceUUID != null) {
                tag.putUUID("sourceUUID", sourceUUID);
            }
            return tag;
        }

        public static DelayedDamage fromNBT(CompoundTag tag) {
            UUID sourceUUID = tag.hasUUID("sourceUUID") ? tag.getUUID("sourceUUID") : null;
            return new DelayedDamage(
                    tag.getFloat("damage"),
                    ResourceLocation.parse(tag.getString("type")),
                    tag.getString("identifier"),
                    tag.getLong("triggerTick"),
                    tag.getBoolean("head"),
                    sourceUUID
            );
        }
    }
}