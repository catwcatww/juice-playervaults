package net.miron.playervaults;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.NonNullList;
import net.minecraft.world.MenuProvider;

import java.util.HashMap;
import java.util.UUID;

public class PVCommand {

    private static final HashMap<UUID, HashMap<Integer, SimpleContainer>> vaults = new HashMap<>();

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return net.minecraft.commands.Commands.literal("pv")
                .then(net.minecraft.commands.Commands.argument("number", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            int number = IntegerArgumentType.getInteger(context, "number");
                            return openVault(player, player, number, context);
                        })
                        .then(net.minecraft.commands.Commands.argument("target", EntityArgument.player())
                                .requires(PermissionUtils::canViewOthers)
                                .executes(context -> {
                                    ServerPlayer viewer = context.getSource().getPlayerOrException();
                                    ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                    int number = IntegerArgumentType.getInteger(context, "number");
                                    return openVault(viewer, target, number, context);
                                })
                        )
                );
    }

    private static int openVault(ServerPlayer viewer, ServerPlayer target, int number, CommandContext<CommandSourceStack> context) {
        boolean isSelf = viewer.getUUID().equals(target.getUUID());
        UUID uuid = target.getUUID();
        int vaultSize = ConfigManager.config.vaultSize;
        RegistryAccess registryAccess = target.server.registryAccess();

        boolean hasPermission = PermissionUtils.hasVaultPermission(viewer, number) || viewer.hasPermissions(2);
        boolean hasItems = isSelf && VaultStorage.hasAnyItems(uuid, number, vaultSize, registryAccess);

        // Block completely if no permission AND no items
        if (!hasPermission && !hasItems) {
            viewer.sendSystemMessage(Component.literal("§cYou don't have permission to open PV #" + number));
            return 0;
        }

        // Drain-only mode: had permission before but lost it, vault still has items
        boolean isDrainOnly = isSelf && !hasPermission && hasItems;

        var loaded = VaultStorage.loadVault(uuid, number, vaultSize, registryAccess);
        SimpleContainer vaultContainer = new SimpleContainer(loaded.toArray(new ItemStack[0]));

        boolean isEditable = !isDrainOnly && (isSelf || PermissionUtils.canEditOthers(viewer, target));

        // Only add save listener if they can actually edit

        if (isEditable || isDrainOnly) {
            vaultContainer.addListener(sender -> {
                NonNullList<ItemStack> toSave = NonNullList.withSize(vaultContainer.getContainerSize(), ItemStack.EMPTY);
                for (int i = 0; i < vaultContainer.getContainerSize(); i++) {
                    toSave.set(i, vaultContainer.getItem(i));
                }
                VaultStorage.saveVault(uuid, number, toSave, registryAccess);
            });
        }

        // Save on close for drain-only too (so taken items are removed from the vault file)
        final boolean finalDrainOnly = isDrainOnly;

        MenuProvider menuProvider = new MenuProvider() {
            @Override
            public Component getDisplayName() {
                if (finalDrainOnly)
                    return Component.literal("Vault #" + number + " (Drain Only)");
                return Component.literal((isSelf ? "Your" : target.getName().getString() + "'s")
                        + " Vault #" + number + (isEditable ? "" : " (Read-Only)"));
            }

            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
                if (finalDrainOnly)
                    return new DrainOnlyVaultMenu(syncId, playerInventory, vaultContainer);
                if (!isEditable)
                    return new ReadOnlyVaultScreenHandler(syncId, playerInventory, vaultContainer);
                return new ChestMenu(MenuType.GENERIC_9x6, syncId, playerInventory, vaultContainer, 6);
            }
        };

        viewer.openMenu(menuProvider);

        if (isDrainOnly) {
            viewer.sendSystemMessage(Component.literal("§eYou no longer have permission for PV #" + number + "."));
            viewer.sendSystemMessage(Component.literal("§6You may only remove your existing items."));
        } else if (!isEditable) {
            viewer.sendSystemMessage(Component.literal("§eNote: You are viewing this vault in read-only mode."));
        }

        return 1;
    }
}