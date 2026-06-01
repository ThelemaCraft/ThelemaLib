package com.thelema.thelemalib.container;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;

public class ContainerHelper {

    private static final MenuType<?>[] MENU_TYPES = {
            MenuType.GENERIC_9x1, MenuType.GENERIC_9x2, MenuType.GENERIC_9x3,
            MenuType.GENERIC_9x4, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6
    };

    /**
     * 打开可编辑的物品栏界面
     */
    public static void open(ServerPlayer player, IItemHandler handler, Component title) {
        int slots = handler.getSlots();
        if (slots % 9 != 0 || slots < 9 || slots > 54)
            throw new IllegalArgumentException("无效的槽位数：" + slots);

        int rows = slots / 9;
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new ChestMenu(MENU_TYPES[rows - 1], id, inv, new ItemHandlerContainer(handler), rows),
                title
        ));
    }

    /**
     * 打开仅查看（不可编辑）的物品栏界面
     */
    public static void view(ServerPlayer player, IItemHandler handler, Component title) {
        int slots = handler.getSlots();
        if (slots % 9 != 0 || slots < 9 || slots > 54)
            throw new IllegalArgumentException("无效的槽位数：" + slots);

        int rows = slots / 9;
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> buildReadOnlyMenu(rows, new ReadOnlyItemHandlerContainer(handler), inv, id),
                title
        ));
    }

    private static AbstractContainerMenu buildReadOnlyMenu(int rows, Container container, Container playerInv, int containerId) {
        return new AbstractContainerMenu(MENU_TYPES[rows - 1], containerId) {
            {
                // 容器槽位（不可拿取）
                for (int row = 0; row < rows; row++) {
                    for (int col = 0; col < 9; col++) {
                        int idx = row * 9 + col;
                        addSlot(new Slot(container, idx, 8 + col * 18, 18 + row * 18) {
                            @Override public boolean mayPlace(@NotNull ItemStack stack) { return false; }
                            @Override public boolean mayPickup(@NotNull Player player) { return false; }
                        });
                    }
                }

                // 玩家物品栏
                for (int row = 0; row < 3; row++)
                    for (int col = 0; col < 9; col++)
                        addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 32 + rows * 18 + row * 18));

                // 玩家快捷栏
                for (int col = 0; col < 9; col++)
                    addSlot(new Slot(playerInv, col, 8 + col * 18, 90 + rows * 18));
            }

            @Override
            public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(@NotNull Player player) {
                return true;
            }

            @Override
            public void clicked(int slotId, int button, @NotNull ClickType clickType, @NotNull Player player) {
                if (slotId >= 0 && slotId < rows * 9) return;
                super.clicked(slotId, button, clickType, player);
            }
        };
    }

    /** 普通可编辑容器*/
    private record ItemHandlerContainer(IItemHandler handler) implements Container {

        @Override
        public int getContainerSize() {
            return handler.getSlots();
        }

        @Override
        public boolean isEmpty() {
                for (int i = 0; i < handler.getSlots(); i++)
                    if (!handler.getStackInSlot(i).isEmpty()) return false;
                return true;
            }

        @Override
        public @NotNull ItemStack getItem(int slot) {
            return handler.getStackInSlot(slot);
        }

        @Override
        public @NotNull ItemStack removeItem(int slot, int amount) {
                return (handler instanceof IItemHandlerModifiable m)
                        ? m.extractItem(slot, amount, false)
                        : ItemStack.EMPTY;
            }

        @Override
        public @NotNull ItemStack removeItemNoUpdate(int slot) {
                if (handler instanceof IItemHandlerModifiable m) {
                    ItemStack stack = m.getStackInSlot(slot).copy();
                    m.setStackInSlot(slot, ItemStack.EMPTY);
                    return stack;
                }
                return ItemStack.EMPTY;
            }

        @Override
        public void setItem(int slot, @NotNull ItemStack stack) {
                if (handler instanceof IItemHandlerModifiable m)
                    m.setStackInSlot(slot, stack);
            }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(@NotNull Player player) {
            return true;
        }

        @Override
        public void clearContent() {
                if (handler instanceof IItemHandlerModifiable m)
                    for (int i = 0; i < handler.getSlots(); i++)
                        m.setStackInSlot(i, ItemStack.EMPTY);
            }
        }

    /**
         * 只读容器（无法修改）
         */
    private record ReadOnlyItemHandlerContainer(IItemHandler handler) implements Container {

        @Override
        public int getContainerSize() {
            return handler.getSlots();
        }

        @Override
        public boolean isEmpty() {
                for (int i = 0; i < handler.getSlots(); i++)
                    if (!handler.getStackInSlot(i).isEmpty()) return false;
                return true;
            }

        @Override
        public @NotNull ItemStack getItem(int slot) {
            return handler.getStackInSlot(slot);
        }

        @Override
        public @NotNull ItemStack removeItem(int slot, int amount) {
            return ItemStack.EMPTY;
        }

        @Override
        public @NotNull ItemStack removeItemNoUpdate(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, @NotNull ItemStack stack) {
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(@NotNull Player player) {
            return true;
        }

        @Override
        public void clearContent() {
        }
        }
}