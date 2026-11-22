package com.yuushya.modelling.item.showblocktool;

import org.joml.Vector3f;
import com.yuushya.modelling.blockentity.TransformData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

public class ScaleTransItem extends PosTransItem{
    public ScaleTransItem(Properties properties, Integer tipLines) {
        super(properties, tipLines);
        MAX_FORMS=1;
    }
    @Override
    public InteractionResult inMainHandRightClickOnBlock(Player player, BlockState blockState, Level level, BlockPos blockPos, ItemStack handItemStack){
        //右手右键 向后位移
        return translateData(player,blockState,level,blockPos,handItemStack,(transformData)->{
            Vector3f scales = transformData.scales;
            float oldX = scales.x(), oldY = scales.y(), oldZ = scales.z();
            switch (getForm()){
                case 0-> scales.add(0.1f,0.1f,0.1f);
            }
            compensatePosToScaleInPlace(transformData, oldX, oldY, oldZ);
            player.displayClientMessage(Component.translatable(this.getDescriptionId()+".switch",scales.x(),scales.y(),scales.z()),true);
        });
    }
    @Override
    public InteractionResult inMainHandLeftClickOnBlock(Player player, BlockState blockState, Level level, BlockPos blockPos, ItemStack handItemStack){
        //右手左键 向前位移
        return translateData(player,blockState,level,blockPos,handItemStack,(transformData)->{
            Vector3f scales = transformData.scales;
            float oldX = scales.x(), oldY = scales.y(), oldZ = scales.z();
            switch (getForm()){
                case 0-> scales.add(-0.1f,-0.1f,-0.1f);
            }
            compensatePosToScaleInPlace(transformData, oldX, oldY, oldZ);
            player.displayClientMessage(Component.translatable(this.getDescriptionId()+".switch",scales.x()),true);
        });
    }

    //原地缩放：渲染中心 = 0.5 + scale*pos/16，缩放后按 pos' = pos*oldScale/newScale 补偿 pos，
    //使方块只变大小而不移动。resize in place: the rendered center is 0.5 + scale*pos/16, so after
    //scaling we compensate pos (pos' = pos*oldScale/newScale) and the block only changes size.
    protected static void compensatePosToScaleInPlace(TransformData transformData, float oldX, float oldY, float oldZ){
        Vector3f scales = transformData.scales;
        if (scales.x() != oldX && oldX != 0 && scales.x() != 0) transformData.pos.x = transformData.pos.x * oldX / scales.x();
        if (scales.y() != oldY && oldY != 0 && scales.y() != 0) transformData.pos.y = transformData.pos.y * oldY / scales.y();
        if (scales.z() != oldZ && oldZ != 0 && scales.z() != 0) transformData.pos.z = transformData.pos.z * oldZ / scales.z();
    }

}
