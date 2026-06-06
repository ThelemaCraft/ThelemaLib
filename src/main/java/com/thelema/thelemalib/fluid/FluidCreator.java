package com.thelema.thelemalib.fluid;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 流体创建器（建造者模式）
 * 用法：FluidCreator.create(modId, "fluidName").register(bus)
 */
public class FluidCreator {

    private final String modId;
    private final String name;

    // 可配置属性（默认值）
    private int tintColor = 0xFFFFFFFF;
    private ResourceLocation stillTexture = null;
    private ResourceLocation flowingTexture = null;
    private int viscosity = 1000;
    private int density = 1000;
    private int temperature = 300;
    private boolean canConvertToSource = false;
    private int slopeFindDistance = 2;
    private int levelDecreasePerBlock = 2;
    private boolean useWaterTexture = false;

    private FluidCreator(String modId, String name) {
        this.modId = modId;
        this.name = name;
    }

    /** 静态入口 */
    public static FluidCreator create(String modId, String name) {
        return new FluidCreator(modId, name);
    }

    // ---------- 链式配置 ----------
    public FluidCreator color(int tintColor) {
        this.tintColor = tintColor;
        return this;
    }

    public FluidCreator stillTexture(ResourceLocation stillTexture) {
        this.stillTexture = stillTexture;
        return this;
    }

    public FluidCreator flowingTexture(ResourceLocation flowingTexture) {
        this.flowingTexture = flowingTexture;
        return this;
    }

    public FluidCreator viscosity(int viscosity) {
        this.viscosity = viscosity;
        return this;
    }

    public FluidCreator density(int density) {
        this.density = density;
        return this;
    }

    public FluidCreator temperature(int temperature) {
        this.temperature = temperature;
        return this;
    }

    public FluidCreator canConvertToSource(boolean canConvertToSource) {
        this.canConvertToSource = canConvertToSource;
        return this;
    }

    public FluidCreator slopeFindDistance(int slopeFindDistance) {
        this.slopeFindDistance = slopeFindDistance;
        return this;
    }

    public FluidCreator levelDecreasePerBlock(int levelDecreasePerBlock) {
        this.levelDecreasePerBlock = levelDecreasePerBlock;
        return this;
    }

    public FluidCreator useWaterTexture() {
        this.useWaterTexture = true;
        return this;
    }

    // ---------- 执行注册 ----------
    public RegisteredFluid register(IEventBus bus) {
        // 如果使用原版水纹理，强制覆盖纹理路径（忽略用户之前的设置）
        if (useWaterTexture) {
            stillTexture = ResourceLocation.fromNamespaceAndPath("minecraft", "block/water_still");
            flowingTexture = ResourceLocation.fromNamespaceAndPath("minecraft", "block/water_flow");
            // 推荐：若未主动设置颜色，保持白色以显示原版水颜色（不额外叠加）
            // 如果用户已经调用了 .color()，则保留用户设置，这样可以在原版水纹理上叠加色调。
        } else {
            // 原有的自动推断逻辑
            if (stillTexture == null)
                stillTexture = ResourceLocation.fromNamespaceAndPath(modId, "block/" + name + "_still");
            if (flowingTexture == null)
                flowingTexture = ResourceLocation.fromNamespaceAndPath(modId, "block/" + name + "_flow");
        }

        // 创建注册器
        DeferredRegister<FluidType> fluidTypeReg = DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, modId);
        DeferredRegister<Fluid> fluidReg = DeferredRegister.create(Registries.FLUID, modId);
        DeferredRegister<Block> blockReg = DeferredRegister.create(Registries.BLOCK, modId);
        DeferredRegister<Item> itemReg = DeferredRegister.create(Registries.ITEM, modId);

        // 1. 注册 FluidType（使用 initializeClient，添加抑制）
        Supplier<FluidType> fluidType = fluidTypeReg.register(name + "_type", () -> new FluidType(
                FluidType.Properties.create()
                        .viscosity(viscosity)
                        .density(density)
                        .temperature(temperature)
                        .canConvertToSource(canConvertToSource)
        ) {
            @Override
            @SuppressWarnings({"deprecation", "removal"})
            public void initializeClient(java.util.function.Consumer<IClientFluidTypeExtensions> consumer) {
                consumer.accept(new IClientFluidTypeExtensions() {
                    @Override
                    public ResourceLocation getStillTexture() {
                        return stillTexture;
                    }

                    @Override
                    public ResourceLocation getFlowingTexture() {
                        return flowingTexture;
                    }

                    @Override
                    public int getTintColor() {
                        return tintColor;
                    }
                });
            }
        });

        // 2. 准备占位属性（延迟设置）
        AtomicReference<BaseFlowingFluid.Properties> propsHolder = new AtomicReference<>();

        // 3. 注册静止流体和流动流体
        Supplier<FlowingFluid> stillFluid = fluidReg.register(name, () -> new BaseFlowingFluid.Source(propsHolder.get()));
        Supplier<FlowingFluid> flowingFluid = fluidReg.register(name + "_flowing", () -> new BaseFlowingFluid.Flowing(propsHolder.get()));

        // 4. 注册流体方块
        Supplier<LiquidBlock> block = blockReg.register(name + "_block", () -> new LiquidBlock(stillFluid.get(),
                Block.Properties.ofFullCopy(Blocks.WATER).noLootTable()));

        // 5. 注册桶
        Supplier<BucketItem> bucket = itemReg.register(name + "_bucket", () -> new BucketItem(stillFluid.get(),
                new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

        // 6. 构建真正的流体属性
        BaseFlowingFluid.Properties props = new BaseFlowingFluid.Properties(
                fluidType,
                stillFluid,
                flowingFluid)
                .block(block)
                .bucket(bucket)
                .slopeFindDistance(slopeFindDistance)
                .levelDecreasePerBlock(levelDecreasePerBlock);
        propsHolder.set(props);

        // 7. 将所有注册器注册到总线
        fluidTypeReg.register(bus);
        fluidReg.register(bus);
        blockReg.register(bus);
        itemReg.register(bus);

        return new RegisteredFluid(fluidType, stillFluid, flowingFluid, block, bucket);
    }
}