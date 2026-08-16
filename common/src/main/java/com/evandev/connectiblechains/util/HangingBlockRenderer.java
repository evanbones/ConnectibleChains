package com.evandev.connectiblechains.util;

import com.evandev.connectiblechains.CommonClass;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class HangingBlockRenderer {

    private static final int BLOCK_ENTITY_CACHE_SIZE = 256;

    private final Map<ResourceLocation, BlockState> states = new HashMap<>();
    private final Set<ResourceLocation> berDenyList = new HashSet<>();

    private final Map<Long, BlockEntity> blockEntities =
            new LinkedHashMap<>(BLOCK_ENTITY_CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, BlockEntity> eldest) {
                    return size() > BLOCK_ENTITY_CACHE_SIZE;
                }
            };

    private static int lightAt(Level level, BlockPos pos, BlockState state) {
        int blockLight = Math.max(level.getBrightness(LightLayer.BLOCK, pos), state.getLightEmission());
        return LightTexture.pack(blockLight, level.getBrightness(LightLayer.SKY, pos));
    }

    public void clear() {
        states.clear();
        blockEntities.clear();
        berDenyList.clear();
    }

    public void render(Minecraft mc, ResourceLocation blockId, BlockPos pos, PoseStack matrices,
                       MultiBufferSource buffers, float x, float y) {
        Level level = mc.level;
        if (level == null) return;

        BlockState state = stateFor(blockId);
        if (state == null) return;

        int light = lightAt(level, pos, state);

        matrices.pushPose();
        if (state.getRenderShape() == RenderShape.MODEL) {
            matrices.translate(x - 0.5f, y - 1.0f, -0.5f);
            renderModel(mc, state, level, pos, matrices, buffers, light);
            renderBlockEntity(mc, blockId, state, level, pos, matrices, buffers, light);
        } else if (hasBlockEntityRenderer(mc, blockId, state, level, pos)) {
            matrices.translate(x - 0.5f, y - 1.0f, -0.5f);
            renderBlockEntity(mc, blockId, state, level, pos, matrices, buffers, light);
        } else {
            Item item = BuiltInRegistries.ITEM.get(blockId);
            if (item == Items.AIR) {
                matrices.popPose();
                return;
            }
            matrices.translate(x, y - 0.5f, 0f);
            matrices.scale(0.5f, 0.5f, 0.5f);
            mc.getItemRenderer().renderStatic(new ItemStack(item), ItemDisplayContext.FIXED,
                    light, OverlayTexture.NO_OVERLAY, matrices, buffers, level, 0);
        }
        matrices.popPose();
    }

    private void renderModel(Minecraft mc, BlockState state, Level level, BlockPos pos, PoseStack matrices,
                             MultiBufferSource buffers, int light) {
        BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();
        int tint = mc.getBlockColors().getColor(state, level, pos, 0);
        float r = (tint >> 16 & 0xFF) / 255.0F;
        float g = (tint >> 8 & 0xFF) / 255.0F;
        float b = (tint & 0xFF) / 255.0F;

        blockRenderer.getModelRenderer().renderModel(
                matrices.last(),
                buffers.getBuffer(ItemBlockRenderTypes.getRenderType(state, false)),
                state,
                blockRenderer.getBlockModel(state),
                r, g, b,
                light,
                OverlayTexture.NO_OVERLAY);
    }

    private boolean hasBlockEntityRenderer(Minecraft mc, ResourceLocation blockId, BlockState state, Level level, BlockPos pos) {
        BlockEntity blockEntity = blockEntityFor(blockId, state, level, pos);
        return blockEntity != null && mc.getBlockEntityRenderDispatcher().getRenderer(blockEntity) != null;
    }

    private void renderBlockEntity(Minecraft mc, ResourceLocation blockId, BlockState state, Level level, BlockPos pos,
                                   PoseStack matrices, MultiBufferSource buffers, int light) {
        BlockEntity blockEntity = blockEntityFor(blockId, state, level, pos);
        if (blockEntity == null) return;

        BlockEntityRenderDispatcher dispatcher = mc.getBlockEntityRenderDispatcher();
        if (dispatcher.getRenderer(blockEntity) == null) return;

        try {
            dispatcher.renderItem(blockEntity, matrices, buffers, light, OverlayTexture.NO_OVERLAY);
        } catch (Throwable t) {
            berDenyList.add(blockId);
            blockEntities.values().removeIf(be -> be == blockEntity);
            CommonClass.LOGGER.warn("Block entity renderer for {} failed on a chain decoration, disabling it", blockId, t);
        }
    }

    @Nullable
    private BlockEntity blockEntityFor(ResourceLocation blockId, BlockState state, Level level, BlockPos pos) {
        if (berDenyList.contains(blockId)) return null;
        if (!(state.getBlock() instanceof EntityBlock entityBlock)) return null;

        long key = pos.asLong() * 31L + blockId.hashCode();
        BlockEntity cached = blockEntities.get(key);
        if (cached != null) return cached;

        BlockEntity created;
        try {
            created = entityBlock.newBlockEntity(pos, state);
        } catch (Throwable t) {
            berDenyList.add(blockId);
            return null;
        }
        if (created == null) {
            berDenyList.add(blockId);
            return null;
        }

        created.setLevel(level);
        blockEntities.put(key, created);
        return created;
    }

    @Nullable
    private BlockState stateFor(ResourceLocation blockId) {
        BlockState cached = states.get(blockId);
        if (cached != null) return cached;

        Block block = BuiltInRegistries.BLOCK.get(blockId);
        if (block == Blocks.AIR) return null;

        BlockState state = block.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.HANGING)) {
            state = state.setValue(BlockStateProperties.HANGING, true);
        }
        states.put(blockId, state);
        return state;
    }
}
