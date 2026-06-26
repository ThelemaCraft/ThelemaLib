package com.thelema.thelemalib.area;

import net.minecraft.world.phys.AABB;

public interface Area {

    String type();

    String name();

    AABB aabb();

}
