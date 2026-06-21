package com.thelema.thelemalib.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@EventBusSubscriber(value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public class HighlightRenderer {
    public static final Set<Integer> highlightedEntities = Collections.synchronizedSet(new HashSet<>());

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (highlightedEntities.isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.LINES);

        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(2.0f);

        for (int id : highlightedEntities) {
            Entity entity = mc.level.getEntity(id);
            if (entity == null) continue;

            AABB aabb = entity.getBoundingBox().inflate(0.1);
            double camX = mc.gameRenderer.getMainCamera().getPosition().x;
            double camY = mc.gameRenderer.getMainCamera().getPosition().y;
            double camZ = mc.gameRenderer.getMainCamera().getPosition().z;

            LevelRenderer.renderLineBox(
                    poseStack,
                    vertexConsumer,
                    aabb.move(-camX, -camY, -camZ),
                    1.0F, 1.0F, 1.0F, 0.8F
            );
        }

        bufferSource.endBatch(RenderType.LINES);
        RenderSystem.enableDepthTest();
        RenderSystem.lineWidth(1.0f);
    }
}