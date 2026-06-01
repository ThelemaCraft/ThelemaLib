package com.thelema.thelemalib.entity;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ExactMove {

    private static final Map<Mob, MoveTask> TASKS = new ConcurrentHashMap<>();

    public static void to(Mob mob, Vec3 pos) {
        to(mob, pos,null);
    }

    public static void to(Mob mob, Vec3 pos, Runnable callback) {
        TASKS.put(mob, new MoveTask(pos, callback));
    }

    public static void cancel(Mob mob) {
        TASKS.remove(mob);
    }

    static Map<Mob, MoveTask> getTasks() {
        return TASKS;
    }

    static class MoveTask {
        final Vec3 target;
        final Runnable callback;

        MoveTask(Vec3 target, Runnable callback) {
            this.target = target;
            this.callback = callback;
        }
    }
}