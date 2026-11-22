package com.yuushya.modelling.gui.showblock;

import com.yuushya.modelling.block.blockstate.YuushyaBlockStates;
import com.yuushya.modelling.blockentity.TransformData;
import com.yuushya.modelling.blockentity.TransformDataNetwork;
import com.yuushya.modelling.blockentity.TransformType;
import com.yuushya.modelling.blockentity.ITransformDataInventory;
import com.yuushya.modelling.blockentity.showblock.ShowBlockEntity;
import com.yuushya.modelling.gui.SliderButton;
import com.yuushya.modelling.gui.engrave.EngraveItemResultLoader;
import com.yuushya.modelling.gui.validate.DividedDoubleRange;
import com.yuushya.modelling.gui.validate.DoubleRange;
import com.yuushya.modelling.gui.validate.LazyDoubleRange;
import com.yuushya.modelling.item.YuushyaDebugStickItem;
import com.yuushya.modelling.utils.ShareApplyUtils;
import com.yuushya.modelling.utils.ShareUtils;
import dev.architectury.platform.Platform;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.IOException;
import java.util.*;

import static com.yuushya.modelling.blockentity.TransformType.*;
import static com.yuushya.modelling.item.showblocktool.PosTransItem.getMaxPos;
import static com.yuushya.modelling.item.showblocktool.PosTransItem.getStep;

public class ShowBlockScreen extends Screen {
    private final ShowBlockEntity blockEntity;
    private final BlockState newBlockState;
    //副手里是否为真实方块（而非复制状态的工具）whether the offhand holds a real block item (not the copy-state tool)
    private final boolean offhandConsumable;
    private int slot;
    //当前选中的物品栏槽位（-1 表示未选中，使用副手） the currently selected inventory hotbar slot (-1 = none, use offhand)
    private int selectedHotbarSlot = -1;
    public void setSlot(int slot){
        for(TransformType key:this.storage.keySet()){
            TransformDataNetwork.sendToServerSide(this.blockEntity.getBlockPos(),this.slot, key,this.storage.get(key));
        }
        this.storage.clear();
        this.slot = slot;
        this.blockEntity.setSlot(slot);
        for(TransformComponent component: this.panel.values()){
            component.setSliderInitial(this.blockEntity,this.slot);
        }
        shownStateButton.setValue(this.blockEntity.getTransformData(slot).isShown);
        updateStateButtonVisible(true);
    }
    private final Map<TransformType,Double> storage = new HashMap<>();

    private final Map<TransformType, TransformComponent> panel = new LinkedHashMap<>();
    private TransformComponent choose(TransformType type){
        return panel.computeIfAbsent(type, TransformComponent::new);
    }
    public static final class TransformComponent {
        TransformType type;
        double standardStep;
        double fine_tuneStep = 0.001;
        SliderButton<Double> sliderButton;
        Button minusButton;
        Button addButton;
        EditBox editBox;
        Button cancelButton;
        Button finishButton;
        TransformComponent(TransformType type){
            this.type = type;
        }

        double setStandardStep(double step){
            this.standardStep = step;
            return step;
        }
        void setSliderInitial(ShowBlockEntity blockEntity,int slot ){
            double step = sliderButton.getStep();
            sliderButton.setStep(fine_tuneStep);
            sliderButton.setInitialValidatedValue(type.extract(blockEntity,slot));
            sliderButton.setStep(step);
        }
        void setSliderStep(){ sliderButton.setStep(standardStep); }
        void setSliderFineTune(){ sliderButton.setStep(fine_tuneStep); }
        void step( boolean increase){
            if(increase) sliderButton.setValidatedValue(sliderButton.getValidatedValue()+sliderButton.getStep());
            else sliderButton.setValidatedValue(sliderButton.getValidatedValue()-sliderButton.getStep());
        }
        void setEditBoxInitial(){ editBox.setValue(String.valueOf(sliderButton.getValidatedValue()));}
        void saveEditBoxValue(){
            double number;
            try{
                number =  Double.parseDouble(editBox.getValue());
            } catch (NumberFormatException ignored){
                number = sliderButton.getValidatedValue();
            }
            sliderButton.setValidatedValue(number);
            setEditBoxInitial();
        }
        void triggerVisible(boolean sliderVisible){
            sliderButton.visible = sliderVisible;
            addButton.visible = sliderVisible;
            minusButton.visible = sliderVisible;
            if(!sliderVisible){ setEditBoxInitial(); }
            editBox.setVisible(!sliderVisible);
            finishButton.visible = !sliderVisible;
            cancelButton.visible = !sliderVisible;
        }

        Component editBoxComponent(){
            MutableComponent component = switch (type){
                case POS_X,ROT_X -> Component.translatable("block.yuushya.showblock.x","").withStyle(ChatFormatting.DARK_RED);
                case POS_Y,ROT_Y -> Component.translatable("block.yuushya.showblock.y","").withStyle(ChatFormatting.GREEN);
                case POS_Z,ROT_Z -> Component.translatable("block.yuushya.showblock.z","").withStyle(ChatFormatting.BLUE);
                case SCALE_X -> Component.translatable("gui.yuushya.showBlockScreen.scale_text","");
                default -> Component.empty();
            };
            return Component.empty().append(sliderButton.getCaption()).append(component);
        }

        void initWidget(Font font){
            minusButton = Button.builder(Component.literal("-"), (btn)-> step(false))
                    .bounds(sliderButton.getX()-SMALL_BUTTON_WIDTH,sliderButton.getY(),SMALL_BUTTON_WIDTH,PER_HEIGHT).build();
            addButton = Button.builder(Component.literal("+"), (btn)-> step(true))
                    .bounds(sliderButton.getX()+sliderButton.getWidth(),sliderButton.getY(),SMALL_BUTTON_WIDTH,PER_HEIGHT).build();
            editBox = new EditBox(font,sliderButton.getX() ,sliderButton.getY() , sliderButton.getWidth(), PER_HEIGHT, editBoxComponent());
            editBox.setMaxLength(15);
            setEditBoxInitial();

            cancelButton = Button.builder(Component.literal("×"), (btn)-> setEditBoxInitial())
                    .bounds(sliderButton.getX()-SMALL_BUTTON_WIDTH,sliderButton.getY(),SMALL_BUTTON_WIDTH,PER_HEIGHT).build();
            finishButton = Button.builder(Component.literal("√"), (btn)-> saveEditBoxValue())
                    .bounds(sliderButton.getX()+sliderButton.getWidth(),sliderButton.getY(),SMALL_BUTTON_WIDTH,PER_HEIGHT).build();
            triggerVisible(true);
        }
    }

    private CycleButton<Mode> modeButton;
    private Button addStateButton;
    private Button removeStateButton;
    private Button duplicateButton;
    private Button replaceButton;
    private Button copyButton;
    private Button parseButton;
    private Button saveButton;

    private CycleButton<Boolean> shownStateButton;
    private final Map<TransformType, EditBox> editBoxes = new HashMap<>();
    private BlockStateIconList blockStateList;
    private Button leftPropertyButton;
    private Property<?> property;
    private Button rightPropertyButton;
    private Button leftStateButton;
    private Button rightStateButton;

    public boolean updateStateButtonVisible(boolean force){
        Collection<Property<?>> collection = blockStateList.updateRenderProperties(getBlockState());
        boolean stateButtonVisible = !collection.isEmpty();
        if(stateButtonVisible && (property==null||force)) property = collection.iterator().next();
        leftStateButton.visible = stateButtonVisible;
        rightStateButton.visible = stateButtonVisible;
        leftPropertyButton.visible = stateButtonVisible;
        rightPropertyButton.visible = stateButtonVisible;
        return stateButtonVisible;
    }

    public BlockState getBlockState(){
        return blockEntity.getTransformData(slot).blockState;
    }

    public ShowBlockScreen(ShowBlockEntity blockEntity, BlockState newBlockState, boolean offhandConsumable) {
        super(GameNarrator.NO_TITLE);
        this.blockEntity = blockEntity;
        this.newBlockState = newBlockState;
        this.offhandConsumable = offhandConsumable;
        if(blockEntity.getSlot() < blockEntity.getTransformDatas().size()){
            this.slot = blockEntity.getSlot();
        }
    }

    //点击时实时读取副手，而非 GUI 打开时的快照（快照在消耗后会过期） read the offhand live at click time rather than the GUI-open snapshot (which goes stale after a consume)
    private BlockState liveOffhandBlockState(){
        if(this.minecraft == null || this.minecraft.player == null) return null;
        ItemStack off = this.minecraft.player.getOffhandItem();
        if(off.getItem() instanceof BlockItem item){
            return item.getBlock().defaultBlockState();
        }
        return null;
    }

    private int leftColumnX(){ return this.width/4*3 + 10; }
    private int leftColumnWidth(){ return this.width/4 - 20; }
    private static final int TOP = 10;
    private static final int PER_HEIGHT = 20;
    // i \in [1,...]
    private static int top(int i, int offset){ return TOP+PER_HEIGHT + 10 + PER_HEIGHT*i+offset; }

    private static final int RIGHT_COLUMN_X = 2;
    private static final int RIGHT_BAR_WIDTH = PER_HEIGHT;
    private static final int SMALL_BUTTON_WIDTH = 10;
    private static final int RIGHT_LIST_WIDTH = 40;
    private static final int RIGHT_LIST_PER_HEIGHT = 45;
    private static final int RIGHT_LIST_TOP = TOP+PER_HEIGHT+5;
    private static final int RIGHT_LIST_HEIGHT = 3* RIGHT_LIST_PER_HEIGHT+2;
    private static final int RIGHT_LIST_BOTTOM = RIGHT_LIST_TOP + RIGHT_LIST_HEIGHT;
    private static final int RIGHT_STATE_INFORM_X = RIGHT_COLUMN_X+RIGHT_LIST_WIDTH +3;
    private static final int RIGHT_STATE_PANEL_Y = RIGHT_LIST_BOTTOM + 5;

    @Override
    protected void init() {
        addStateButton = Button.builder(Component.literal("+"),
                        (btn)->{
                            if(selectedHotbarSlot >= 0){
                                //从选中的物品栏槽位新增层 add a layer from the selected inventory slot
                                BlockState add = liveSlotBlockState(selectedHotbarSlot);
                                if(add == null){
                                    return; //槽位为空/非方块：不新增 do nothing if the slot is empty or holds a non-block
                                }
                                if(!blockStateList.addSlot()){
                                    return;
                                }
                                updateTransformData(BLOCK_STATE,(double) Block.getId(add));
                                updateTransformData(SHOWN,1.0);
                                TransformDataNetwork.sendToServerSide(blockEntity.getBlockPos(), slot, ADD_CONSUME_OFFHAND, (double) Block.getId(add), selectedHotbarSlot);
                                updateStateButtonVisible(true);
                            }
                            else if(this.newBlockState!=null){
                                //副手来源须在点击时仍持有该方块，否则不新增（快照过期会凭空新增） an offhand-sourced block must still be held at click time, else don't add (a stale snapshot would add for free)
                                BlockState add = offhandConsumable ? liveOffhandBlockState() : this.newBlockState;
                                if(add == null){
                                    return; //副手已空：不凭旧快照新增 the offhand is empty: don't add from the stale snapshot
                                }
                                if(!blockStateList.addSlot()){
                                    return;
                                }
                                updateTransformData(BLOCK_STATE,(double) Block.getId(add));
                                updateTransformData(SHOWN,1.0);
                                if(offhandConsumable){
                                    TransformDataNetwork.sendToServerSide(blockEntity.getBlockPos(), slot, ADD_CONSUME_OFFHAND, (double) Block.getId(add));
                                }
                                updateStateButtonVisible(true);
                            }
                        })
                .tooltip(Tooltip.create(Component.translatable("gui.showBlockScreen.display.add")))
                .bounds(RIGHT_COLUMN_X,TOP,RIGHT_BAR_WIDTH,PER_HEIGHT).build();
        removeStateButton = Button.builder(Component.literal("×"),
                        (btn)->{
                            int removeSlot = this.slot;
                            //先补发该层尚未同步的修改（如刚新增层的 BLOCK_STATE），服务端列表和方块状态才能与客户端对齐
                            //flush the layer's pending edits (e.g. a just-added layer's BLOCK_STATE) first so the
                            //server's list and blockstate match the client before the removal is processed
                            for(TransformType key:this.storage.keySet()){
                                TransformDataNetwork.sendToServerSide(this.blockEntity.getBlockPos(),removeSlot, key,this.storage.get(key));
                            }
                            this.storage.clear();
                            //真正地从列表中删除该层 completely remove the layer from the list
                            blockEntity.removeSlotInList(removeSlot);
                            //列表下标会移动，所以立即发送给服务端 indices shift, so sync immediately
                            TransformDataNetwork.sendToServerSide(blockEntity.getBlockPos(), removeSlot, REMOVE_SLOT, 0.0, selectedHotbarSlot);
                            TransformDataNetwork.sendToServerSideSuccess(blockEntity.getBlockPos());
                            //刷新左侧列表并选中一个合法的新层 refresh the list and select a valid slot
                            blockStateList.updateRenderList();
                            int nextSlot = Math.min(removeSlot, blockEntity.getTransformDatas().size()-1);
                            blockStateList.setSelectedSlot(nextSlot);
                            updateStateButtonVisible(true);
                        }
                )
                .tooltip(Tooltip.create(Component.translatable("gui.showBlockScreen.display.remove")))
                .bounds(RIGHT_COLUMN_X+RIGHT_BAR_WIDTH,TOP,RIGHT_BAR_WIDTH,PER_HEIGHT).build();
        duplicateButton = Button.builder(Component.literal("⧉"),
                        (btn)->{
                            int source = this.slot;
                            TransformData srcData = new TransformData();
                            srcData.set(blockEntity.getTransformData(source));
                            if(srcData.blockState.getBlock() instanceof net.minecraft.world.level.block.AirBlock){
                                return; //不复制空气层 don't duplicate an air layer
                            }
                            boolean creative = this.minecraft.player != null && this.minecraft.player.isCreative();
                            if(!creative){
                                net.minecraft.world.item.Item required = srcData.blockState.getBlock().asItem();
                                if(required != net.minecraft.world.item.Items.AIR){
                                    boolean has = false;
                                    if(this.minecraft.player != null){
                                        for(ItemStack st : this.minecraft.player.getInventory().items){
                                            if(!st.isEmpty() && st.getItem() == required){ has = true; break; }
                                        }
                                    }
                                    if(!has){
                                        if(this.minecraft.player != null){
                                            this.minecraft.player.displayClientMessage(
                                                    Component.literal("Need ").append(required.getDefaultInstance().getHoverName())
                                                            .append(Component.literal(" to duplicate")), false);
                                        }
                                        return;
                                    }
                                }
                            }
                            if(!blockStateList.addSlot()){
                                return;
                            }
                            int newIdx = blockEntity.getTransformDatas().size() - 1;
                            blockEntity.getTransformDatas().set(newIdx, srcData);
                            blockStateList.setSelectedSlot(newIdx);
                            TransformDataNetwork.sendToServerSide(blockEntity.getBlockPos(), source, TransformType.DUPLICATE_SLOT, 0.0, selectedHotbarSlot);
                            updateStateButtonVisible(true);
                        })
                .tooltip(Tooltip.create(Component.translatable("gui.showBlockScreen.display.duplicate")))
                .bounds(RIGHT_COLUMN_X+RIGHT_BAR_WIDTH*2,TOP,RIGHT_BAR_WIDTH,PER_HEIGHT).build();
        replaceButton = Button.builder(Component.literal("⇄"),
                        (btn)->{
                            if(selectedHotbarSlot >= 0){
                                //从选中的物品栏槽位替换 exchange with the selected inventory slot
                                BlockState live = liveSlotBlockState(selectedHotbarSlot);
                                if(live == null){
                                    //槽位为空/非方块：视为空手替换，换成空气并收回原方块 selected slot empty/non-block: treat as an empty-hand replace, swap to air and return the old block
                                    TransformDataNetwork.sendToServerSide(blockEntity.getBlockPos(), slot, REPLACE_EXCHANGE_OFFHAND, 0.0, selectedHotbarSlot);
                                    updateTransformData(BLOCK_STATE,0.0);
                                }
                                else{
                                    TransformDataNetwork.sendToServerSide(blockEntity.getBlockPos(), slot, REPLACE_EXCHANGE_OFFHAND, (double) Block.getId(live), selectedHotbarSlot);
                                    updateTransformData(BLOCK_STATE,(double) Block.getId(live));
                                }
                                updateStateButtonVisible(true);
                            }
                            else if(offhandConsumable){
                                //点击时实时读取副手，快照可能已过期（方块已被消耗） read the offhand live at click time; the snapshot may be stale (block already consumed)
                                BlockState live = liveOffhandBlockState();
                                if(live == null){
                                    //副手已空：视为空手替换，换成空气并收回原方块 offhand now empty: treat as an empty-hand replace, swap to air and return the old block
                                    TransformDataNetwork.sendToServerSide(blockEntity.getBlockPos(), slot, REPLACE_EXCHANGE_OFFHAND, 0.0);
                                    updateTransformData(BLOCK_STATE,0.0);
                                }
                                else{
                                    TransformDataNetwork.sendToServerSide(blockEntity.getBlockPos(), slot, REPLACE_EXCHANGE_OFFHAND, (double) Block.getId(live));
                                    updateTransformData(BLOCK_STATE,(double) Block.getId(live));
                                }
                                updateStateButtonVisible(true);
                            }
                            else if(this.newBlockState==null){
                                //空手打开 GUI：替换为空气并收回原方块 GUI opened with an empty hand: replace to air and return the old block
                                TransformDataNetwork.sendToServerSide(blockEntity.getBlockPos(), slot, REPLACE_EXCHANGE_OFFHAND, 0.0);
                                updateTransformData(BLOCK_STATE,0.0);
                                updateStateButtonVisible(true);
                            }
                            else{
                                //复制状态的工具：直接替换，不交换（工具不被消耗） copy-state tool: replace directly with no exchange (the tool isn't consumed)
                                updateTransformData(BLOCK_STATE,(double) Block.getId(this.newBlockState));
                                updateStateButtonVisible(true);
                            }
                        }
                )
                .tooltip(Tooltip.create(Component.translatable("gui.showBlockScreen.display.replace")))
                .bounds(RIGHT_COLUMN_X+RIGHT_BAR_WIDTH*3,TOP,RIGHT_BAR_WIDTH,PER_HEIGHT).build();

        shownStateButton = CycleButton.<Boolean>booleanBuilder(
                        Component.literal("\uD83D\uDD76"),//Component.translatable("gui.showBlockScreen.display.on"),
                        Component.literal("\uD83D\uDC40"))//Component.translatable("gui.showBlockScreen.display.off"))
                .displayOnlyValue()
                .withInitialValue(true)
                .withTooltip((on)-> Tooltip.create(on? Component.translatable("gui.showBlockScreen.display.on"):Component.translatable("gui.showBlockScreen.display.off")))
                .create(RIGHT_COLUMN_X+RIGHT_BAR_WIDTH*4,TOP,RIGHT_BAR_WIDTH,PER_HEIGHT,Component.empty(),
                        (btn,bl)->{
                            updateTransformData(SHOWN,bl?1.0:0.0);
                        }
                );


        copyButton = Button.builder(Component.literal("\uD83D\uDCE4").withStyle(ChatFormatting.BOLD),//Component.translatable("gui.showBlockScreen.workshop.copy"),
                        (btn)->{
                            String res = ShareUtils.transfer(blockEntity.getTransformDatas());
                            setClipboard(res);
                            this.minecraft.getToasts().addToast(
                                    SystemToast.multiline(this.minecraft, SystemToast.SystemToastIds.TUTORIAL_HINT,Component.translatable("gui.showBlockScreen.workshop.copy_pass"), Component.translatable("gui.showBlockScreen.workshop.share_hint"))
                            );
                        }
                )
                .tooltip(Tooltip.create(Component.translatable("gui.showBlockScreen.workshop.copy")))
                .bounds(RIGHT_COLUMN_X+RIGHT_BAR_WIDTH*4+40,TOP,RIGHT_BAR_WIDTH,PER_HEIGHT).build();

        parseButton = Button.builder(Component.literal("\uD83D\uDCE5").withStyle(ChatFormatting.BOLD),//Component.translatable("gui.showBlockScreen.workshop.paste"),
                        (btn)->{
                            String string = getClipboard();
                            try {
                                ShareUtils.ShareInformation shareInformation = ShareUtils.from(string);
                                checkModLack(shareInformation);
                                //客户端先本地试算并重建层以即时刷新界面 optimistic local compute + rebuild for instant feedback
                                boolean creative = this.minecraft.player == null || this.minecraft.player.isCreative();
                                ShareApplyUtils.ApplyResult result = ShareApplyUtils.computeApply(
                                        shareInformation, blockEntity.getTransformDatas(), this.minecraft.player, creative);
                                if(!creative && this.minecraft.player != null){
                                    for(var e : result.leftover().entrySet()){
                                        if(e.getValue() > 0) TransformDataNetwork.giveBackToOffhandOrInventory(this.minecraft.player, new ItemStack(e.getKey(), e.getValue()));
                                    }
                                }
                                List<TransformData> dataList = blockEntity.getTransformDatas();
                                BlockPos pos = blockEntity.getBlockPos();
                                dataList.clear();
                                dataList.addAll(result.built());
                                this.storage.clear();
                                this.slot = 0;
                                blockEntity.setSlot(0);
                                this.blockEntity.getLevel().sendBlockUpdated(pos, blockEntity.getBlockState(), blockEntity.getBlockState(), Block.UPDATE_ALL_IMMEDIATE);
                                this.blockStateList.updateRenderList();
                                //服务端权威结算：扣背包并用聊天报告缺失 authoritative settlement on the server: consume inventory and report missing blocks in chat
                                TransformDataNetwork.sendApplyShare(pos, string);
                                updateStateButtonVisible(true);
                                this.minecraft.getToasts().addToast(
                                        new SystemToast(SystemToast.SystemToastIds.TUTORIAL_HINT,Component.translatable("gui.showBlockScreen.workshop.paste_pass"),null)
                                );
                            } catch (Exception e) {
                                this.minecraft.getToasts().addToast(
                                        SystemToast.multiline(this.minecraft, SystemToast.SystemToastIds.PACK_LOAD_FAILURE,Component.translatable("gui.showBlockScreen.workshop.error"), Component.literal(e.getMessage()))
                                );
                            }
                        }
                )
                .tooltip(Tooltip.create(Component.translatable("gui.showBlockScreen.workshop.paste")))
                .bounds(RIGHT_COLUMN_X+RIGHT_BAR_WIDTH*4+60,TOP,RIGHT_BAR_WIDTH,PER_HEIGHT).build();

        saveButton = Button.builder(Component.literal("\uD83D\uDCBE").withStyle(ChatFormatting.BOLD),
                        (btn)->{
                            this.minecraft.setScreen(new EditScreen(this,
                                    Component.translatable("gui.showBlockScreen.workshop.save"),
                                    Component.translatable("gui.showBlockScreen.workshop.save.tip"),
                                    (string)->{
                                        if(string!=null){
                                            String res = ShareUtils.transfer(blockEntity.getTransformDatas());
                                            try {
                                                EngraveItemResultLoader.save(res,string);
                                                this.minecraft.getToasts().addToast(
                                                        SystemToast.multiline(this.minecraft, SystemToast.SystemToastIds.NARRATOR_TOGGLE,Component.translatable("gui.showBlockScreen.workshop.save_pass"), Component.translatable("gui.showBlockScreen.workshop.share_hint"))
                                                );
                                            } catch (IOException e) {
                                                this.minecraft.getToasts().addToast(
                                                        SystemToast.multiline(this.minecraft, SystemToast.SystemToastIds.PACK_LOAD_FAILURE,Component.translatable("gui.showBlockScreen.workshop.save_error"), Component.literal(e.getMessage()))
                                                );
                                            }
                                            this.minecraft.setScreen(this);
                                        }
                                        else{
                                            this.minecraft.setScreen(this);
                                        }
                                    },
                                    (string)->true
                                    ));
                        }
                )
                .tooltip(Tooltip.create(Component.translatable("gui.showBlockScreen.workshop.save")))
                .bounds(RIGHT_COLUMN_X+RIGHT_BAR_WIDTH*5+60,TOP,RIGHT_BAR_WIDTH,PER_HEIGHT).build();

        blockStateList =  new BlockStateIconList(this.minecraft,RIGHT_LIST_WIDTH ,RIGHT_LIST_HEIGHT ,RIGHT_COLUMN_X,RIGHT_LIST_TOP,RIGHT_LIST_BOTTOM , RIGHT_LIST_WIDTH,RIGHT_LIST_PER_HEIGHT,this.blockEntity.getTransformDatas(),this);

        leftPropertyButton = Button.builder(Component.literal("<"),
                        (btn)->{
                            property = YuushyaBlockStates.getRelative(blockStateList.updateRenderProperties(getBlockState()), property, true);
                        })
                .bounds(RIGHT_COLUMN_X,RIGHT_STATE_PANEL_Y,SMALL_BUTTON_WIDTH,PER_HEIGHT)
                .build();
        rightPropertyButton = Button.builder(Component.literal(">"),
                        (btn)->{
                            property = YuushyaBlockStates.getRelative(blockStateList.updateRenderProperties(getBlockState()), property, false);
                        })
                .bounds(RIGHT_COLUMN_X+RIGHT_LIST_WIDTH/2*3,RIGHT_STATE_PANEL_Y,SMALL_BUTTON_WIDTH,PER_HEIGHT)
                .build();
        leftStateButton = Button.builder(Component.literal("<"),
                        (btn)->{
                            BlockState nextBlockState = YuushyaBlockStates.cycleState(getBlockState(), property, true);
                            updateTransformData(BLOCK_STATE,(double)Block.getId(nextBlockState));
                        })
                .bounds(RIGHT_COLUMN_X,RIGHT_STATE_PANEL_Y+PER_HEIGHT,SMALL_BUTTON_WIDTH,PER_HEIGHT)
                .build();
        rightStateButton = Button.builder(Component.literal(">"),
                        (btn)->{
                            BlockState nextBlockState = YuushyaBlockStates.cycleState(getBlockState(), property, true);
                            updateTransformData(BLOCK_STATE,(double)Block.getId(nextBlockState));
                        })
                .bounds(RIGHT_COLUMN_X+RIGHT_LIST_WIDTH/2*3,RIGHT_STATE_PANEL_Y+PER_HEIGHT,SMALL_BUTTON_WIDTH,PER_HEIGHT)
                .build();

        modeButton = CycleButton.builder(Mode::getSymbol)
                        .displayOnlyValue()
                        .withValues(Mode.values())
                        .withInitialValue(Mode.SLIDER)
                        .withTooltip((mode)-> Tooltip.create(
                                    switch (mode){
                                        case SLIDER -> Component.translatable("gui.showBlockScreen.mode.slider.tooltip");
                                        case FINE_TUNE -> Component.translatable("gui.showBlockScreen.mode.fine_tune.tooltip");
                                        case EDIT -> Component.translatable("gui.showBlockScreen.mode.edit.tooltip");
                                    }
                        ))
                        .create(leftColumnX(),TOP,leftColumnWidth(),PER_HEIGHT,Component.literal("MODE"),
                                (btn,mode)->{
                                    switch (mode){
                                        case SLIDER -> panel.values().forEach(TransformComponent::setSliderStep);
                                        case EDIT,FINE_TUNE -> panel.values().forEach(TransformComponent::setSliderFineTune);
                                    }
                                    switch (mode){
                                        case SLIDER,FINE_TUNE -> panel.values().forEach((it)->it.triggerVisible(true));
                                        case EDIT -> panel.values().forEach((it)->it.triggerVisible(false));
                                    }
                                }
                        );

        choose(SCALE_X); // 首先放置scala_x, 因为pos_x依赖于它
        choose(POS_X).sliderButton =
                LazyDoubleRange.buttonBuilder(Component.translatable("gui.yuushya.showBlockScreen.pos_text"),
                                ()-> -getMaxPos(blockEntity.getTransformData(slot).scales.x),
                                ()-> getMaxPos(blockEntity.getTransformData(slot).scales.x),
                                ()-> getStep(getMaxPos(blockEntity.getTransformData(slot).scales.x)),
                                (number)->{updateTransformData(POS_X,number);})
                        .text((caption,number)->Component.empty().append(caption).append(Component.translatable("block.yuushya.showblock.x",String.format("%05.1f",number)).withStyle(ChatFormatting.DARK_RED)))
                        .step(choose(POS_X).setStandardStep(0.0))
                        .onMouseOver((btn)->{blockEntity.setShowAxis(Direction.Axis.X);blockEntity.setShowPosAixs();})
                        .initial(POS_X.extract(blockEntity,slot))
                        .bounds(leftColumnX(),top(0,0) , leftColumnWidth(),PER_HEIGHT).build();

        choose(POS_Y).sliderButton =
                LazyDoubleRange.buttonBuilder(Component.translatable("gui.yuushya.showBlockScreen.pos_text"),
                                ()-> -getMaxPos(blockEntity.getTransformData(slot).scales.y),
                                ()-> getMaxPos(blockEntity.getTransformData(slot).scales.y),
                                ()-> getStep(getMaxPos(blockEntity.getTransformData(slot).scales.y)),
                                (number)->{updateTransformData(POS_Y,number);})
                        .text((caption,number)->Component.empty().append(caption).append(Component.translatable("block.yuushya.showblock.y",String.format("%05.1f",number)).withStyle(ChatFormatting.GREEN)))
                        .step(choose(POS_Y).setStandardStep(0.0))
                        .onMouseOver((btn)->{blockEntity.setShowAxis(Direction.Axis.Y);blockEntity.setShowPosAixs();})
                        .initial(POS_Y.extract(blockEntity,slot))
                        .bounds(leftColumnX(),top(1,0) , leftColumnWidth(),PER_HEIGHT).build();

        choose(POS_Z).sliderButton =
                LazyDoubleRange.buttonBuilder(Component.translatable("gui.yuushya.showBlockScreen.pos_text"),
                                ()-> -getMaxPos(blockEntity.getTransformData(slot).scales.z),
                                ()-> getMaxPos(blockEntity.getTransformData(slot).scales.z),
                                ()-> getStep(getMaxPos(blockEntity.getTransformData(slot).scales.z)),
                                (number)->{updateTransformData(POS_Z,number);})
                        .text((caption,number)->Component.empty().append(caption).append(Component.translatable("block.yuushya.showblock.z",String.format("%05.1f",number)).withStyle(ChatFormatting.BLUE)))
                        .step(choose(POS_Z).setStandardStep(0.0))
                        .onMouseOver((btn)->{blockEntity.setShowAxis(Direction.Axis.Z);blockEntity.setShowPosAixs();})
                        .initial(POS_Z.extract(blockEntity,slot))
                        .bounds(leftColumnX(),top(2,0) , leftColumnWidth(),PER_HEIGHT).build();

        choose(ROT_X).sliderButton =
                DoubleRange.buttonBuilder(Component.translatable("gui.yuushya.showBlockScreen.rot_text"),0.0,360.0,
                            (number)->{updateTransformData(ROT_X,number);})
                        .text((caption,number)->Component.empty().append(caption).append(Component.translatable("block.yuushya.showblock.x",String.format("%05.1f",number)).withStyle(ChatFormatting.DARK_RED)))
                        .step(choose(ROT_X).setStandardStep(22.5))
                        .onMouseOver((btn)->{blockEntity.setShowAxis(Direction.Axis.X);blockEntity.setShowRotAixs();})
                        .initial(ROT_X.extract(blockEntity,slot))
                        .bounds(leftColumnX(),top(3,10) , leftColumnWidth(),PER_HEIGHT).build();

        choose(ROT_Y).sliderButton =
                DoubleRange.buttonBuilder(Component.translatable("gui.yuushya.showBlockScreen.rot_text"),0.0,360.0,
                                (number)->{updateTransformData(ROT_Y,number);})
                        .text((caption,number)->Component.empty().append(caption).append(Component.translatable("block.yuushya.showblock.y",String.format("%05.1f",number)).withStyle(ChatFormatting.GREEN)))
                        .step(choose(ROT_Y).setStandardStep(22.5))
                        .onMouseOver((btn)->{blockEntity.setShowAxis(Direction.Axis.Y);blockEntity.setShowRotAixs();})
                        .initial(ROT_Y.extract(blockEntity,slot))
                        .bounds(leftColumnX(),top(4,10) , leftColumnWidth(),PER_HEIGHT).build();

        choose(ROT_Z).sliderButton =
                DoubleRange.buttonBuilder(Component.translatable("gui.yuushya.showBlockScreen.rot_text"),0.0,360.0,
                                (number)->{updateTransformData(ROT_Z,number);})
                        .text((caption,number)->Component.empty().append(caption).append(Component.translatable("block.yuushya.showblock.z",String.format("%05.1f",number)).withStyle(ChatFormatting.BLUE)))
                        .step(choose(ROT_Z).setStandardStep(22.5))
                        .onMouseOver((btn)->{blockEntity.setShowAxis(Direction.Axis.Z);blockEntity.setShowRotAixs();})
                        .initial(ROT_Z.extract(blockEntity,slot))
                        .bounds(leftColumnX(),top(5,10) , leftColumnWidth(),PER_HEIGHT).build();

        choose(SCALE_X).sliderButton =
                DividedDoubleRange.buttonBuilder(Component.empty(),0.0,1.0,10.0,
                                (number)->{
                                    updateTransformData(SCALE_X,number);
                                    updateTransformData(SCALE_Y,number);
                                    updateTransformData(SCALE_Z,number);
                                    choose(POS_X).sliderButton.setValidatedValue(choose(POS_X).sliderButton.getValidatedValue());
                                    choose(POS_Y).sliderButton.setValidatedValue(choose(POS_Y).sliderButton.getValidatedValue());
                                    choose(POS_Z).sliderButton.setValidatedValue(choose(POS_Z).sliderButton.getValidatedValue());
                                })
                        .text((caption,number)->Component.translatable("gui.yuushya.showBlockScreen.scale_text",String.format("%05.1f",number)))
                        .step(choose(SCALE_X).setStandardStep(0.1))
                        .initial(SCALE_X.extract(blockEntity,slot))
                        .bounds(leftColumnX(),top(6,20) , leftColumnWidth(),PER_HEIGHT).build();

        choose(LIT).sliderButton =
                DoubleRange.buttonBuilder(Component.translatable("gui.yuushya.showBlockScreen.brightness_text"),0.0,15.0,
                        (number)->{
                            updateTransformData(LIT,number);
                        })
                        .text(LazyDoubleRange::captionToString)
                        .step(choose(LIT).setStandardStep(1))
                        .initial(LIT.extract(blockEntity,slot))
                        .bounds(leftColumnX(),top(7,30),leftColumnWidth(),PER_HEIGHT).build();

        for(TransformComponent component:this.panel.values()){
            component.initWidget(this.font);
            this.addRenderableWidget(component.sliderButton);
            this.addRenderableWidget(component.minusButton);
            this.addRenderableWidget(component.addButton);
            this.addRenderableWidget(component.editBox);
            this.addRenderableWidget(component.cancelButton);
            this.addRenderableWidget(component.finishButton);
        }
        this.addRenderableWidget(modeButton);
        this.addWidget(this.blockStateList);
        this.addRenderableWidget(addStateButton);
        this.addRenderableWidget(removeStateButton);
        this.addRenderableWidget(duplicateButton);
        this.addRenderableWidget(replaceButton);
        this.addRenderableWidget(shownStateButton);
        this.addRenderableWidget(leftPropertyButton);
        this.addRenderableWidget(rightPropertyButton);
        this.addRenderableWidget(leftStateButton);
        this.addRenderableWidget(rightStateButton);
        this.addRenderableWidget(copyButton);
        this.addRenderableWidget(parseButton);
        this.addRenderableWidget(saveButton);

        blockStateList.setSelectedSlot(slot);//updateStateButtonVisible();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.blockStateList.render(guiGraphics,mouseX,mouseY,partialTick);
        BlockState blockState = getBlockState();
        guiGraphics.drawString(this.font,this.blockStateList.updateRenderDisplayName(blockState), RIGHT_STATE_INFORM_X, TOP +6 + PER_HEIGHT, 0xFFFFFFFF, false);
        List<String> properties = this.blockStateList.updateRenderBlockStateProperties(blockState);
        for(int i=0;i<properties.size();i++){
            MutableComponent displayBlockState = Component.literal(properties.get(i));
            guiGraphics.drawString(this.font, displayBlockState, RIGHT_STATE_INFORM_X, TOP + 6 + PER_HEIGHT + this.font.lineHeight*(i+1)+1, 0xFFEBC6, false);
        }
        if(updateStateButtonVisible(false)){
            guiGraphics.drawString(this.font, property.getName(), RIGHT_COLUMN_X+RIGHT_LIST_WIDTH/2,RIGHT_STATE_PANEL_Y+5, 0xFFFFFFFF, false);
            guiGraphics.drawString(this.font, YuushyaDebugStickItem.getNameHelper(blockState,property), RIGHT_COLUMN_X+RIGHT_LIST_WIDTH/2,RIGHT_STATE_PANEL_Y+5+PER_HEIGHT, 0xFFFFFFFF, false);
        }
        if(modeButton.getValue() == Mode.EDIT){
            for(TransformComponent component:this.panel.values()){
                guiGraphics.drawString(this.font,component.editBox.getMessage(),component.editBox.getX()+component.editBox.getWidth()/2,component.editBox.getY()+component.editBox.getHeight()/3,0x707070);
                component.editBox.render(guiGraphics,mouseX,mouseY,partialTick);
            }
        }
        //渲染与原版一致的物品栏热键栏（用于 +/×/⇄ 的来源/去向） render the hotbar matching vanilla's geometry (source/target for +/×/⇄)
        int hx = (this.width - 182) / 2;
        int hy = this.height - 22;
        net.minecraft.resources.ResourceLocation widgets = new net.minecraft.resources.ResourceLocation("textures/gui/widgets.png");
        //原版热键栏底图 vanilla hotbar background
        guiGraphics.blit(widgets, hx, hy, 0, 0, 182, 22);
        //选中槽高亮 vanilla selection highlight
        if (selectedHotbarSlot >= 0) {
            guiGraphics.blit(widgets, hx - 1 + selectedHotbarSlot * 20, hy - 1, 0, 22, 24, 22);
        }
        //悬停高亮 hover highlight
        int hovered = slotAt(mouseX, mouseY);
        if (hovered >= 0 && hovered != selectedHotbarSlot) {
            guiGraphics.fill(hx + hovered * 20 + 1, hy + 1, hx + hovered * 20 + 19, hy + 21, 0x40FFFFFF);
        }
        //物品图标 item icons
        for (int i = 0; i < 9; i++) {
            ItemStack stack = (this.minecraft != null && this.minecraft.player != null)
                    ? this.minecraft.player.getInventory().getItem(i) : ItemStack.EMPTY;
            if (!stack.isEmpty()) {
                int ix = hx + 3 + i * 20 + 1; // vanilla item offset
                int iy = hy + 3;
                guiGraphics.renderItem(stack, ix, iy);
                guiGraphics.renderItemDecorations(this.font, stack, ix, iy);
            }
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }


    @Override
    public void tick() {
        for(TransformComponent component:this.panel.values()){
            component.editBox.tick();
        }
        //承载方块被摧毁（如删除最后一层）后自动关闭界面 close the screen once the backing block is destroyed
        if (blockEntity.isRemoved()) {
            this.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }



    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int s = slotAt((int) mouseX, (int) mouseY);
            if (s >= 0) {
                selectedHotbarSlot = (selectedHotbarSlot == s) ? -1 : s;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    //物品栏热键栏渲染与选中 hotbar rendering and selection (vanilla geometry)
    private int hotbarX() { return (this.width - 182) / 2; }
    private int hotbarY() { return this.height - 22; }
    private int slotAt(int mx, int my) {
        int hx = hotbarX(), hy = hotbarY();
        if (my < hy || my >= hy + 22) return -1;
        int rel = mx - hx;
        if (rel < 0 || rel >= 182) return -1;
        return rel / 20;
    }
    private BlockState liveSlotBlockState(int idx) {
        if (this.minecraft == null || this.minecraft.player == null) return null;
        ItemStack st = this.minecraft.player.getInventory().getItem(idx);
        if (st.getItem() instanceof BlockItem bi) return bi.getBlock().defaultBlockState();
        return null;
    }

    @Override
    public void removed() {
        for(TransformType key:storage.keySet()){
            TransformDataNetwork.sendToServerSide(blockEntity.getBlockPos(),slot, key,storage.get(key));
        }
        this.storage.clear();
        TransformDataNetwork.sendToServerSideSuccess(blockEntity.getBlockPos());
    }

    public void checkModLack(ShareUtils.ShareInformation shareInformation){
        List<String> unLoaded = shareInformation.mods().stream().filter(id->!Platform.getModIds().contains(id)).toList();
        Minecraft.getInstance().getToasts().addToast(
                SystemToast.multiline(Minecraft.getInstance(), SystemToast.SystemToastIds.PACK_LOAD_FAILURE, Component.literal("Mod Lack"), Component.literal(String.join(", ",unLoaded)))
        );
    }

    private void updateTransformDataServerImmediate(TransformData data,int slot){
        BlockPos pos = blockEntity.getBlockPos();
        TransformDataNetwork.sendToServerSide(pos,slot, POS_X,data.pos.x);
        TransformDataNetwork.sendToServerSide(pos,slot, POS_Y,data.pos.y);
        TransformDataNetwork.sendToServerSide(pos,slot, POS_Z,data.pos.z);

        TransformDataNetwork.sendToServerSide(pos,slot, ROT_X,data.rot.x);
        TransformDataNetwork.sendToServerSide(pos,slot, ROT_Y,data.rot.y);
        TransformDataNetwork.sendToServerSide(pos,slot, ROT_Z,data.rot.z);

        TransformDataNetwork.sendToServerSide(pos,slot, SCALE_X,data.scales.x);
        TransformDataNetwork.sendToServerSide(pos,slot, SCALE_Y,data.scales.y);
        TransformDataNetwork.sendToServerSide(pos,slot, SCALE_Z,data.scales.z);
        TransformDataNetwork.sendToServerSide(pos,slot, BLOCK_STATE,Block.getId(data.blockState) );
        TransformDataNetwork.sendToServerSide(pos,slot, SHOWN,data.isShown?1:0 );
    }

    private void updateTransformData(TransformType type, Double number){
        //渲染时方块中心 = 0.5 + scale*pos/16，改缩放会拖动带偏移的方块；先记下旧缩放，
        //改完后补偿 pos（pos' = pos*oldScale/newScale）并一并同步给服务端，让方块原地缩放。
        //the rendered center is 0.5 + scale*pos/16, so scaling would drag an offset block along;
        //record the old scale, compensate pos afterwards and sync it alongside the new scale so
        //the block resizes in place. the server applies both values as plain setters (no
        //compensation there), so packet order can never double-compensate.
        boolean scaling = type == SCALE_X || type == SCALE_Y || type == SCALE_Z;
        double oldScale = 1.0;
        if (scaling) {
            var scales = blockEntity.getTransformData(slot).scales;
            oldScale = switch (type) {
                case SCALE_X -> scales.x();
                case SCALE_Y -> scales.y();
                default -> scales.z();
            };
        }
        this.storage.put(type,number);
        type.modify(blockEntity,slot,number);
        if (scaling && oldScale != 0 && number != 0) {
            TransformType posType = switch (type) {
                case SCALE_X -> POS_X;
                case SCALE_Y -> POS_Y;
                default -> POS_Z;
            };
            double newPos = posType.extract(blockEntity, slot) * oldScale / number;
            this.storage.put(posType, newPos);
            posType.modify(blockEntity, slot, newPos);
        }
        this.blockEntity.getLevel().sendBlockUpdated(blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity.getBlockState(), Block.UPDATE_ALL_IMMEDIATE);
    }

    public enum Mode implements StringRepresentable {
        SLIDER("slider"),FINE_TUNE("fine_tune"),EDIT("edit");

        private final String name;
        private final Component symbol;
        Mode(String name){
            this.name = name;
            this.symbol = Component.translatable("gui.showBlockScreen.mode."+name);
        }
        @Override
        public String getSerializedName() { return name; }
        public Component getSymbol(){ return symbol; }
    }

    private void setClipboard(String clipboardValue) {
        if (this.minecraft != null) {
            TextFieldHelper.setClipboardContents(this.minecraft, clipboardValue);
        }
    }

    private String getClipboard() {
        return this.minecraft != null ? TextFieldHelper.getClipboardContents(this.minecraft) : "";
    }
}
