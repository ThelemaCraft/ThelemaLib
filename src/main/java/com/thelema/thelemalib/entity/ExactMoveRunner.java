package com.thelema.thelemalib.entity;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Iterator;
import java.util.Map;

@EventBusSubscriber
public class ExactMoveRunner {

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof Mob mob)) return;

        Map<Mob, ExactMove.MoveTask> tasks = ExactMove.getTasks();
        if (tasks.isEmpty()) return;

        Iterator<Map.Entry<Mob, ExactMove.MoveTask>> it = tasks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Mob, ExactMove.MoveTask> entry = it.next();
            if (entry.getKey() != mob) continue;

            ExactMove.MoveTask task = entry.getValue();
            Vec3 t = task.target;
            Vec3 pos = mob.position();

            if (t.distanceTo(pos) < 2.25) {
                it.remove();                 // 安全移除，避免并发修改
                task.callback.run();         // 回调触发
            } else {
                // 立刻调整方向
                mob.getMoveControl().setWantedPosition(t.x, t.y, t.z, 1.0);
            }
            break; // 每个 mob 每 tick 只处理自己的任务
        }
    }
}