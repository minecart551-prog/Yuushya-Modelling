package com.yuushya.modelling.blockentity;

import com.mojang.logging.LogUtils;
import com.yuushya.modelling.Yuushya;
import com.yuushya.modelling.blockentity.showblock.ShowBlock;
import com.yuushya.modelling.blockentity.showblock.ShowBlockEntity;
import dev.architectury.networking.NetworkChannel;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.function.Supplier;

public class TransformDataNetwork {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceLocation TRANSFORM_DATA_PACKET_ID = new ResourceLocation(Yuushya.MOD_ID_USED, "transform_data_packet");
    public static final NetworkChannel INSTANCE = NetworkChannel.create(TRANSFORM_DATA_PACKET_ID);

    public record TransformDataOncePacket(
            BlockPos blockPos,
            TransformType transformType,
            int slot,
            double number
    ){
        //buf -> pack
        public static TransformDataOncePacket decoder(FriendlyByteBuf buf){
            return new TransformDataOncePacket(
                    buf.readBlockPos(),
                    TransformType.from(buf.readByte()),
                    buf.readByte(),
                    buf.readDouble()
            );
        }

        //pack -> buf
        public void encoder(FriendlyByteBuf buf){
            buf.writeBlockPos(blockPos);
            buf.writeByte(transformType.type);
            buf.writeByte(slot);
            buf.writeDouble(number);
        }

        //after receive
        public void handler(Supplier<NetworkManager.PacketContext> ctx){
            ctx.get().queue(() -> {
                Level level = ctx.get().getPlayer().level();
                if (level instanceof ServerLevel serverLevel && serverLevel.hasChunkAt(blockPos)) {
                    if(serverLevel.getBlockState(blockPos).getBlock() instanceof ShowBlock){
                        BlockEntity blockEntity = serverLevel.getBlockEntity(blockPos);
                        if (!(blockEntity instanceof ShowBlockEntity showBlockEntity)) {return;}
                        Player player = ctx.get().getPlayer();
                        //删除层时把该层的方块还给非创造模式玩家 return the layer's block to non-creative players
                        if (transformType == TransformType.REMOVE_SLOT && !player.isCreative()) {
                            BlockState removedState = showBlockEntity.getTransformData(slot).blockState;
                            if (!(removedState.getBlock() instanceof AirBlock)) {
                                ItemStack stack = new ItemStack(removedState.getBlock().asItem());
                                if (!stack.isEmpty()) {
                                    player.getInventory().placeItemBackInInventory(stack);
                                }
                            }
                        }
                        //从副手新增层时消耗副手里对应的方块 consume the matching offhand block when a layer is added from it
                        if (transformType == TransformType.ADD_CONSUME_OFFHAND && !player.isCreative()) {
                            ItemStack offhand = player.getOffhandItem();
                            if (offhand.getItem() instanceof BlockItem blockItem
                                    && blockItem.getBlock() == Block.stateById((int) Math.round(number)).getBlock()) {
                                offhand.shrink(1);
                            }
                        }
                        //用副手方块替换当前层：归还旧方块并消耗副手方块（新方块状态由 modify 原子应用）
                        //空手替换为空气时同样归还旧方块，但不消耗任何物品
                        //replace the current layer with the offhand block: hand the old block back and consume
                        //the offhand one (the new blockstate itself is applied atomically by modify below).
                        //an empty-hand replace (target = air) returns the old block without consuming anything
                        if (transformType == TransformType.REPLACE_EXCHANGE_OFFHAND && !player.isCreative()) {
                            BlockState newState = Block.stateById((int) Math.round(number));
                            boolean targetIsAir = newState.getBlock() instanceof AirBlock;
                            ItemStack offhand = player.getOffhandItem();
                            if (targetIsAir || (offhand.getItem() instanceof BlockItem blockItem
                                    && blockItem.getBlock() == newState.getBlock())) {
                                if (!targetIsAir) {
                                    offhand.shrink(1);
                                }
                                BlockState oldState = showBlockEntity.getTransformData(slot).blockState;
                                if (!(oldState.getBlock() instanceof AirBlock)) {
                                    ItemStack stack = new ItemStack(oldState.getBlock().asItem());
                                    if (!stack.isEmpty()) {
                                        player.getInventory().placeItemBackInInventory(stack);
                                    }
                                }
                            }
                        }
                        transformType.modify(showBlockEntity,slot,number);
                    }
                }
            });
        }
    }


    public static void sendToServerSide(BlockPos blockPos, int slot, TransformType type, double number) {
        INSTANCE.sendToServer(new TransformDataOncePacket(blockPos,type,slot,number));
    }

    public static void sendToServerSideSuccess(BlockPos blockPos){
        sendToServerSide(blockPos,0,TransformType.SUCCESS,0);
    }

    public static void registerChannel(){
        INSTANCE.register(TransformDataOncePacket.class,TransformDataOncePacket::encoder,TransformDataOncePacket::decoder,TransformDataOncePacket::handler);
    }

    //architectury提供的另一种风格的api
    public static void sendToServerSideLegacy(BlockPos blockPos, int slot, TransformType type, double number){
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new TransformDataOncePacket(blockPos,type,slot,number).encoder(buf);
        NetworkManager.sendToServer(TRANSFORM_DATA_PACKET_ID,buf);
    }

    public static void registerServerSideReceiverLegacy(){
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, TRANSFORM_DATA_PACKET_ID, (buf, context) -> {
            TransformDataOncePacket packet = TransformDataOncePacket.decoder(buf);
            packet.handler(()->context);
        });
    }




}
