package com.yuushya.modelling.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yuushya.modelling.Yuushya;
import com.yuushya.modelling.blockentity.showblock.ShowBlock;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses Minecraft's default block-selection outline (the "hit outline" drawn for the
 * block under the crosshair) on Yuushya show blocks, so that walking around a room full of
 * modified blocks no longer flashes outlines everywhere.
 *
 * <p>The outline is only drawn while the player holds one of the modelling tools:
 * the Gourd of Entitilization or the Modelling GUI tool. Every other case falls back to
 * vanilla behaviour (outline hidden), and non-show blocks are left completely untouched.
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Inject(method = "renderHitOutline", at = @At("HEAD"), cancellable = true)
    private void yuushya$outlineShowBlockOnlyWhileHoldingTool(PoseStack poseStack, VertexConsumer vertexConsumer,
            Entity entity, double camX, double camY, double camZ, BlockPos pos, BlockState state, CallbackInfo ci) {
        // Only ever touch show blocks; leave every other block's outline as vanilla renders it.
        if (!(state.getBlock() instanceof ShowBlock)) return;
        // The entity passed in is the camera entity (the player in normal play).
        if (!(entity instanceof Player player)) return;

        // Draw the outline only while holding the Gourd of Entitilization or the Modelling GUI tool.
        if (player.isHolding(item("get_showblock_item")) || player.isHolding(item("gui_item"))) return;

        ci.cancel();
    }

    private static Item item(String path) {
        return BuiltInRegistries.ITEM.get(new ResourceLocation(Yuushya.MOD_ID, path));
    }
}
