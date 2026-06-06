package com.thelema.thelemalib.fluid;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.function.Supplier;

/**
 * 记录一个完整流体注册后的所有对象
 */
public record RegisteredFluid(
        Supplier<FluidType> fluidType,
        Supplier<FlowingFluid> stillFluid,
        Supplier<FlowingFluid> flowingFluid,
        Supplier<LiquidBlock> block,
        Supplier<BucketItem> bucket
) {}