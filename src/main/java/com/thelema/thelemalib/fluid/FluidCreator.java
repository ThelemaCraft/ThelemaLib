package com.thelema.thelemalib.fluid;

import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 流体创建器（建造者模式）- 底层属性完全透传版
 * <p>
 * 使用示例：
 * <pre>{@code
 * RegisteredFluid fluid = FluidCreator.create("yourmod", "lava_fluid")
 *     .useWaterTexture()                      // 可选：直接使用原版水纹理
 *     .textureStill(myStill)                  // 或自定义静止纹理
 *     .textureFlowing(myFlow)                 // 自定义流动纹理
 *     .tintColor(0xFFFF0000)                  // 叠加颜色（红色）
 *     .fogColor(0xFFFF0000)                   // 水下雾色（红色）
 *     .properties(props -> {
 *         props.lightLevel(15);               // 发光
 *         props.viscosity(3000);              // 高粘性
 *         props.density(3000);                // 高密度
 *         props.temperature(1300);            // 高温
 *         props.canConvertToSource(false);
 *         props.sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL);
 *         props.sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY);
 *         props.addDripstoneDripping(0.1f, ParticleTypes.DRIPPING_OBSIDIAN_TEAR, Blocks.CAULDRON, null);
 *     })
 *     .slopeFindDistance(2)
 *     .levelDecreasePerBlock(2)
 *     .register(eventBus);
 * }</pre>
 * </p>
 */
public class FluidCreator {

    private final String modId;
    private final String name;

    // 纹理（客户端）
    private ResourceLocation stillTexture = null;
    private ResourceLocation flowingTexture = null;
    private boolean useWaterTexture = false;
    private int tintColor = 0xFFFFFFFF;
    private int fogColor = 0xFF3399FF;
    private boolean hasFogColor = false;

    // 流体物理行为（BaseFlowingFluid.Properties）
    private int slopeFindDistance = 2;
    private int levelDecreasePerBlock = 2;

    // FluidType.Properties 实例，由用户通过 lambda 配置
    private final FluidType.Properties typeProps = FluidType.Properties.create();

    private FluidCreator(String modId, String name) {
        this.modId = modId;
        this.name = name;
    }

    /** 静态入口 */
    public static FluidCreator create(String modId, String name) {
        return new FluidCreator(modId, name);
    }

    // ---------- 纹理与视觉（客户端扩展）----------
    public FluidCreator textureStill(ResourceLocation still) {
        this.stillTexture = still;
        return this;
    }

    public FluidCreator textureFlowing(ResourceLocation flowing) {
        this.flowingTexture = flowing;
        return this;
    }

    /** 直接使用原版水纹理（无需提供图片文件） */
    public FluidCreator useWaterTexture() {
        this.useWaterTexture = true;
        return this;
    }

    /** 设置流体的叠加色调（ARGB） */
    public FluidCreator tintColor(int color) {
        this.tintColor = color;
        return this;
    }

    /** 设置浸没在流体中时的雾颜色（ARGB） */
    public FluidCreator fogColor(int color) {
        this.fogColor = color;
        this.hasFogColor = true;
        return this;
    }

    // ---------- 流体物理行为（BaseFlowingFluid）----------
    public FluidCreator slopeFindDistance(int distance) {
        this.slopeFindDistance = distance;
        return this;
    }

    public FluidCreator levelDecreasePerBlock(int decrease) {
        this.levelDecreasePerBlock = decrease;
        return this;
    }

    // ---------- 核心：透传 FluidType.Properties 的配置 ----------
    /**
     * 直接配置底层的 FluidType.Properties，所有原版支持的属性均可在此设置。
     * 例如：lightLevel, viscosity, density, temperature, canConvertToSource,
     * sound(SoundAction, SoundEvent), addDripstoneDripping(...) 等。
     */
    public FluidCreator properties(Consumer<FluidType.Properties> consumer) {
        consumer.accept(typeProps);
        return this;
    }

    // ---------- 执行注册 ----------
    public RegisteredFluid register(IEventBus bus) {
        // 处理纹理（最终决定）
        if (useWaterTexture) {
            stillTexture = ResourceLocation.withDefaultNamespace("block/water_still");
            flowingTexture = ResourceLocation.withDefaultNamespace("block/water_flow");
        } else {
            if (stillTexture == null)
                stillTexture = ResourceLocation.fromNamespaceAndPath(modId, "block/" + name + "_still");
            if (flowingTexture == null)
                flowingTexture = ResourceLocation.fromNamespaceAndPath(modId, "block/" + name + "_flow");
        }

        // 创建各注册表
        DeferredRegister<FluidType> fluidTypeReg = DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, modId);
        DeferredRegister<Fluid> fluidReg = DeferredRegister.create(Registries.FLUID, modId);
        DeferredRegister<Block> blockReg = DeferredRegister.create(Registries.BLOCK, modId);
        DeferredRegister<Item> itemReg = DeferredRegister.create(Registries.ITEM, modId);

        // 注册 FluidType（使用最终配置好的 typeProps）
        final int finalTintColor = this.tintColor;
        final boolean finalHasFogColor = this.hasFogColor;
        final int finalFogColor = this.fogColor;
        final ResourceLocation finalStillTexture = this.stillTexture;
        final ResourceLocation finalFlowingTexture = this.flowingTexture;

        Supplier<FluidType> fluidType = fluidTypeReg.register(name + "_type", () -> new FluidType(typeProps) {
            @Override
            @SuppressWarnings("removal")
            public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
                consumer.accept(new IClientFluidTypeExtensions() {
                    @Override
                    public ResourceLocation getStillTexture() {
                        return finalStillTexture;
                    }

                    @Override
                    public ResourceLocation getFlowingTexture() {
                        return finalFlowingTexture;
                    }

                    @Override
                    public int getTintColor() {
                        return finalTintColor;
                    }

                    @Override
                    public void modifyFogRender(Camera camera, FogRenderer.FogMode mode, float renderDistance, float partialTick, float nearDistance, float farDistance, FogShape shape) {
                        if (mode == FogRenderer.FogMode.FOG_TERRAIN && finalHasFogColor) {
                            int r = (finalFogColor >> 16) & 0xFF;
                            int g = (finalFogColor >> 8) & 0xFF;
                            int b = finalFogColor & 0xFF;
                            float a = ((finalFogColor >> 24) & 0xFF) / 255.0F;
                            com.mojang.blaze3d.systems.RenderSystem.setShaderFogColor(r / 255.0F, g / 255.0F, b / 255.0F, a);
                        }
                    }
                });
            }
        });

        // 准备 BaseFlowingFluid.Properties（延迟设置）
        AtomicReference<BaseFlowingFluid.Properties> propsHolder = new AtomicReference<>();

        // 注册静止/流动流体
        Supplier<FlowingFluid> stillFluid = fluidReg.register(name, () -> new BaseFlowingFluid.Source(propsHolder.get()));
        Supplier<FlowingFluid> flowingFluid = fluidReg.register(name + "_flowing", () -> new BaseFlowingFluid.Flowing(propsHolder.get()));

        // 注册流体方块
        Supplier<LiquidBlock> block = blockReg.register(name + "_block", () -> new LiquidBlock(stillFluid.get(),
                Block.Properties.ofFullCopy(Blocks.WATER).noLootTable()) {
            @Override
            public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
                return fluidType.get().getLightLevel(); // 获取 FluidType 的 lightLevel
            }
        });

        // 注册桶
        Supplier<BucketItem> bucket = itemReg.register(name + "_bucket", () -> new BucketItem(stillFluid.get(),
                new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

        // 构建完整的 BaseFlowingFluid.Properties
        BaseFlowingFluid.Properties props = new BaseFlowingFluid.Properties(fluidType, stillFluid, flowingFluid)
                .block(block)
                .bucket(bucket)
                .slopeFindDistance(slopeFindDistance)
                .levelDecreasePerBlock(levelDecreasePerBlock);
        propsHolder.set(props);

        // 注册所有
        fluidTypeReg.register(bus);
        fluidReg.register(bus);
        blockReg.register(bus);
        itemReg.register(bus);

        return new RegisteredFluid(fluidType, stillFluid, flowingFluid, block, bucket);
    }
}