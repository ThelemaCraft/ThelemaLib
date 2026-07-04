package com.thelema.thelemalib.fluid;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thelema.thelemalib.ThelemaLib;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
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
import java.util.function.*;

public class FluidCreator {

    public interface EntityInsideHandler {
        void onEntityInside(BlockState state, Level level, BlockPos pos, Entity entity);
    }

    private final String modId;
    private final String name;

    // 纹理与视觉
    private ResourceLocation stillTexture = null;
    private ResourceLocation flowingTexture = null;
    private boolean useWaterTexture = false;
    private int tintColor = 0xFFFFFFFF;
    private int fogColor = 0xFF3399FF;
    private boolean hasFogColor = false;

    // 流体物理
    private int slopeFindDistance = 2;
    private int levelDecreasePerBlock = 2;

    // FluidType 属性
    private int lightLevel = 0;
    private int viscosity = 1000;
    private int density = 1000;
    private int temperature = 300;
    private boolean canConvertToSource = true;
    private SoundEvent bucketFillSound = SoundEvents.BUCKET_FILL;
    private SoundEvent bucketEmptySound = SoundEvents.BUCKET_EMPTY;
    private float dripstoneChance = 0f;
    private ParticleOptions dripstoneParticle = null;
    private Block dripstoneCauldron = null;
    private SoundEvent dripstoneFillSound = null;

    // 方块属性
    private Block.Properties blockProps = Block.Properties.ofFullCopy(Blocks.WATER).noLootTable();

    // 实体进入回调
    private EntityInsideHandler entityInsideHandler = null;

    // Tick 回调 (ServerLevel, BlockPos) -> void
    private BiConsumer<ServerLevel, BlockPos> tickHandler = null;

    private FluidCreator(String modId, String name) {
        this.modId = modId;
        this.name = name;
    }

    public static FluidCreator create(String modId, String name) {
        return new FluidCreator(modId, name);
    }

    // ---------- 纹理与视觉 ----------
    public FluidCreator textureStill(ResourceLocation still) {
        this.stillTexture = still;
        return this;
    }

    public FluidCreator textureFlowing(ResourceLocation flowing) {
        this.flowingTexture = flowing;
        return this;
    }

    public FluidCreator useWaterTexture() {
        this.useWaterTexture = true;
        return this;
    }

    public FluidCreator tintColor(int color) {
        this.tintColor = color;
        return this;
    }

    public FluidCreator fogColor(int color) {
        this.fogColor = color;
        this.hasFogColor = true;
        return this;
    }

    // ---------- 流体物理 ----------
    public FluidCreator slopeFindDistance(int distance) {
        this.slopeFindDistance = distance;
        return this;
    }

    public FluidCreator levelDecreasePerBlock(int decrease) {
        this.levelDecreasePerBlock = decrease;
        return this;
    }

    // ---------- FluidType 属性 ----------
    public FluidCreator lightLevel(int level) {
        this.lightLevel = level;
        return this;
    }

    public FluidCreator viscosity(int vis) {
        this.viscosity = vis;
        return this;
    }

    public FluidCreator density(int dens) {
        this.density = dens;
        return this;
    }

    public FluidCreator temperature(int temp) {
        this.temperature = temp;
        return this;
    }

    public FluidCreator canConvertToSource(boolean can) {
        this.canConvertToSource = can;
        return this;
    }

    public FluidCreator bucketFillSound(SoundEvent sound) {
        this.bucketFillSound = sound;
        return this;
    }

    public FluidCreator bucketEmptySound(SoundEvent sound) {
        this.bucketEmptySound = sound;
        return this;
    }

    public FluidCreator dripstoneDripping(float chance, ParticleOptions particle, Block cauldron, SoundEvent fillSound) {
        this.dripstoneChance = chance;
        this.dripstoneParticle = particle;
        this.dripstoneCauldron = cauldron;
        this.dripstoneFillSound = fillSound;
        return this;
    }

    // ---------- 方块属性 ----------
    public FluidCreator strength(float hardness) {
        blockProps = blockProps.strength(hardness);
        return this;
    }

    public FluidCreator strength(float hardness, float resistance) {
        blockProps = blockProps.strength(hardness, resistance);
        return this;
    }

    public FluidCreator sound(SoundType sound) {
        blockProps = blockProps.sound(sound);
        return this;
    }

    public FluidCreator noCollission() {
        blockProps = blockProps.noCollission();
        return this;
    }

    public FluidCreator noOcclusion() {
        blockProps = blockProps.noOcclusion();
        return this;
    }

    public FluidCreator requiresCorrectToolForDrops() {
        blockProps = blockProps.requiresCorrectToolForDrops();
        return this;
    }

    public FluidCreator blockProperties(Consumer<Block.Properties> consumer) {
        consumer.accept(blockProps);
        return this;
    }

    // ---------- 回调钩子 ----------
    public FluidCreator onEntityInside(EntityInsideHandler handler) {
        this.entityInsideHandler = handler;
        return this;
    }

    /**
     * 设置后液体方块将自动启用计划刻。
     */
    public FluidCreator onTick(BiConsumer<ServerLevel, BlockPos> handler) {
        this.tickHandler = handler;
        return this;
    }

    // ---------- 注册 ----------
    public RegisteredFluid register(IEventBus bus) {
        // 纹理默认值处理（不变）
        if (stillTexture == null && flowingTexture == null && !useWaterTexture) useWaterTexture = true;
        if (useWaterTexture) {
            stillTexture = ResourceLocation.withDefaultNamespace("block/water_still");
            flowingTexture = ResourceLocation.withDefaultNamespace("block/water_flow");
        } else {
            if (stillTexture == null)
                stillTexture = ResourceLocation.fromNamespaceAndPath(modId, "block/" + name + "_still");
            if (flowingTexture == null)
                flowingTexture = ResourceLocation.fromNamespaceAndPath(modId, "block/" + name + "_flow");
        }

        DeferredRegister<FluidType> fluidTypeReg = DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, modId);
        DeferredRegister<Fluid> fluidReg = DeferredRegister.create(Registries.FLUID, modId);
        DeferredRegister<Block> blockReg = DeferredRegister.create(Registries.BLOCK, modId);
        DeferredRegister<Item> itemReg = DeferredRegister.create(Registries.ITEM, modId);

        // FluidType.Properties
        FluidType.Properties typeProps = FluidType.Properties.create();
        typeProps.lightLevel(lightLevel).viscosity(viscosity).density(density).temperature(temperature)
                .canConvertToSource(canConvertToSource)
                .sound(SoundActions.BUCKET_FILL, bucketFillSound)
                .sound(SoundActions.BUCKET_EMPTY, bucketEmptySound);
        if (dripstoneChance > 0 && dripstoneParticle != null && dripstoneCauldron != null)
            typeProps.addDripstoneDripping(dripstoneChance, dripstoneParticle, dripstoneCauldron, dripstoneFillSound);

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
                    public void modifyFogRender(Camera camera, FogRenderer.FogMode mode,
                                                float renderDistance, float partialTick,
                                                float nearDistance, float farDistance, FogShape shape) {
                        if (mode == FogRenderer.FogMode.FOG_TERRAIN && finalHasFogColor) {
                            int r = (finalFogColor >> 16) & 0xFF;
                            int g = (finalFogColor >> 8) & 0xFF;
                            int b = finalFogColor & 0xFF;
                            float a = ((finalFogColor >> 24) & 0xFF) / 255.0F;
                            RenderSystem.setShaderFogColor(r / 255.0F, g / 255.0F, b / 255.0F, a);
                        }
                    }
                });
            }
        });

        AtomicReference<BaseFlowingFluid.Properties> propsHolder = new AtomicReference<>();
        Supplier<FlowingFluid> stillFluid = fluidReg.register(name, () -> new BaseFlowingFluid.Source(propsHolder.get()));
        Supplier<FlowingFluid> flowingFluid = fluidReg.register(name + "_flowing", () -> new BaseFlowingFluid.Flowing(propsHolder.get()));

        // 方块
        final EntityInsideHandler finalEntityHandler = this.entityInsideHandler;
        final BiConsumer<ServerLevel, BlockPos> finalTickHandler = this.tickHandler;
        final int finalLightLevel = this.lightLevel;

        Supplier<? extends LiquidBlock> block = blockReg.register(name + "_block", () -> new LiquidBlock(stillFluid.get(), blockProps) {
            @Override
            public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
                return finalLightLevel;
            }

            @Override
            public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
                super.entityInside(state, level, pos, entity);
                if (finalEntityHandler != null && !level.isClientSide) {
                    finalEntityHandler.onEntityInside(state, level, pos, entity);
                }
            }

            @Override
            public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
                super.onPlace(state, level, pos, oldState, isMoving);
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.scheduleTick(pos, this, 5);
                }
            }

            @Override
            public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
                // 1. 自定义检测（放在 super.tick 前或后均可）
                if (finalTickHandler != null) {
                    finalTickHandler.accept(level, pos);
                }
                // 2. 执行流体扩散（核心！）
                super.tick(state, level, pos, random);
                // 3. 若方块仍为自己，继续调度下次检测
                if (level.getBlockState(pos).is(this)) {
                    level.scheduleTick(pos, this, 5);
                }
            }

            @Override
            protected boolean isRandomlyTicking(BlockState state) {
                return false;
            }
        });

        // 桶
        Supplier<BucketItem> bucket = itemReg.register(name + "_bucket", () -> new BucketItem(stillFluid.get(),
                new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

        BaseFlowingFluid.Properties props = new BaseFlowingFluid.Properties(fluidType, stillFluid, flowingFluid)
                .block(block).bucket(bucket)
                .slopeFindDistance(slopeFindDistance).levelDecreasePerBlock(levelDecreasePerBlock);
        propsHolder.set(props);

        fluidTypeReg.register(bus);
        fluidReg.register(bus);
        blockReg.register(bus);
        itemReg.register(bus);

        return new RegisteredFluid(fluidType, stillFluid, flowingFluid, block, bucket);
    }
}