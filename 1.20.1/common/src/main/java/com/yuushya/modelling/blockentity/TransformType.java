package com.yuushya.modelling.blockentity;

import com.yuushya.modelling.block.blockstate.YuushyaBlockStates;
import com.yuushya.modelling.blockentity.showblock.ShowBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public enum TransformType {
    POS_X(0), POS_Y(1), POS_Z(2),
    ROT_X(3), ROT_Y(4), ROT_Z(5),
    SCALE_X(6), SCALE_Y(7), SCALE_Z(8),
    BLOCK_STATE(9),
    SHOWN(10),
    LIT(11),
    REMOVE(12),
    SUCCESS(13), FAIL(14),
    REMOVE_SLOT(15),
    ADD_CONSUME_OFFHAND(16),
    REPLACE_EXCHANGE_OFFHAND(17);

    public final int type;

    TransformType(int i) {
        type = i;
    }

    public static TransformType from(int i) {
        return switch (i) {
            case 0 -> POS_X;
            case 1 -> POS_Y;
            case 2 -> POS_Z;
            case 3 -> ROT_X;
            case 4 -> ROT_Y;
            case 5 -> ROT_Z;
            case 6 -> SCALE_X;
            case 7 -> SCALE_Y;
            case 8 -> SCALE_Z;
            case 9 -> BLOCK_STATE;
            case 10 -> SHOWN;
            case 11 -> LIT;
            case 12 -> REMOVE;
            case 13 -> SUCCESS;
            case 15 -> REMOVE_SLOT;
            case 16 -> ADD_CONSUME_OFFHAND;
            case 17 -> REPLACE_EXCHANGE_OFFHAND;
            default -> FAIL;
        };
    }


    public double extract(TransformData transformData){
        return switch (this){
            case POS_X -> transformData.pos.x();
            case POS_Y -> transformData.pos.y();
            case POS_Z -> transformData.pos.z();
            case ROT_X -> transformData.rot.x();
            case ROT_Y -> transformData.rot.y();
            case ROT_Z -> transformData.rot.z();
            case SCALE_X -> transformData.scales.x();
            case SCALE_Y -> transformData.scales.y();
            case SCALE_Z -> transformData.scales.z();
            case BLOCK_STATE -> Block.getId(transformData.blockState);
            case SHOWN -> transformData.isShown ? 1 : 0;
            case LIT, REMOVE, SUCCESS, FAIL, REMOVE_SLOT, ADD_CONSUME_OFFHAND, REPLACE_EXCHANGE_OFFHAND -> 0;
        };
    }

    public void modify(TransformData transformData, Double number){
        switch (this){
            case POS_X -> transformData.pos.set(number,transformData.pos.y(),transformData.pos.z());
            case POS_Y -> transformData.pos.set(transformData.pos.x(),number,transformData.pos.z());
            case POS_Z -> transformData.pos.set(transformData.pos.x(),transformData.pos.y(),number);
            case ROT_X -> transformData.rot.set(number,transformData.rot.y(),transformData.rot.z());
            case ROT_Y -> transformData.rot.set(transformData.rot.x(),number,transformData.rot.z());
            case ROT_Z -> transformData.rot.set(transformData.rot.x(),transformData.rot.y(),number);
            case SCALE_X -> transformData.scales.set(number,transformData.scales.y(),transformData.scales.z());
            case SCALE_Y -> transformData.scales.set(transformData.scales.x(),number,transformData.scales.z());
            case SCALE_Z -> transformData.scales.set(transformData.scales.x(),transformData.scales.y(),number);
            case BLOCK_STATE -> transformData.blockState = Block.stateById((int) Math.round(number));
            case SHOWN -> transformData.isShown = number != 0;
            //case SUCCESS -> showBlockEntity.saveChanged();
            case FAIL -> {
            }
        }
    }


    public double extract(ShowBlockEntity showBlockEntity, int slot){
        if(this == LIT){
            return showBlockEntity.getBlockState().getValue(YuushyaBlockStates.LIT);
        }
        return extract(showBlockEntity.getTransformData(slot));
    }

    public void modify(ShowBlockEntity showBlockEntity, int slot, double number){
        if(this == SUCCESS){
            showBlockEntity.saveChanged();
            return;
        }
        if(this == LIT){
            Level level = showBlockEntity.getLevel();
            level.setBlock(showBlockEntity.getBlockPos(), showBlockEntity.getBlockState().setValue(YuushyaBlockStates.LIT,(int) Math.round(number)), 18);
            return;
        }
        if(this == REMOVE){
            showBlockEntity.removeTransformData(slot);
            return;
        }
        if(this == REMOVE_SLOT){
            showBlockEntity.removeSlotInList(slot);
            //列表变短后当前层下标可能越界 clamp the current slot after the list shrinks
            if (showBlockEntity.getSlot() >= showBlockEntity.size()) {
                showBlockEntity.setSlot(showBlockEntity.size()-1);
            }
            return;
        }
        //仅消耗副手方块，方块状态由客户端另行写入 only consume the offhand block; the blockstate itself is written by the client separately
        if(this == ADD_CONSUME_OFFHAND){
            return;
        }
        //用副手方块替换当前层的方块状态，随后由数据包处理器归还旧方块 replace this layer's blockstate with the offhand block; the packet handler returns the old block afterwards
        if(this == REPLACE_EXCHANGE_OFFHAND){
            showBlockEntity.setSlot(slot);
            showBlockEntity.getTransformData(slot).blockState = Block.stateById((int) Math.round(number));
            return;
        }
        showBlockEntity.setSlot(slot);
        modify(showBlockEntity.getTransformData(slot),number);
    }
}
