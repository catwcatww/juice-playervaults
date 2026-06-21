package net.miron.playervaults;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.context.ContextManager;
import net.luckperms.api.query.QueryOptions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.RegistryAccess;

public class PermissionUtils {

    public static boolean hasVaultPermission(ServerPlayer player, int number) {
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getUserManager().getUser(player.getUUID());
            if (user == null) return false;

            ContextManager contextManager = luckPerms.getContextManager();
            QueryOptions queryOptions = contextManager.getQueryOptions(user)
                    .orElse(contextManager.getStaticQueryOptions());

            return user.getCachedData().getPermissionData(queryOptions)
                    .checkPermission("playervaults.pv." + number).asBoolean()
                    || user.getCachedData().getPermissionData(queryOptions)
                    .checkPermission("playervaults.pv.*").asBoolean()
                    || player.hasPermissions(2); // fallback for OPs

        } catch (Exception e) {
            return player.hasPermissions(2);
        }
    }

    public static boolean canExportVault(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            try {
                LuckPerms luckPerms = LuckPermsProvider.get();
                User user = luckPerms.getUserManager().getUser(player.getUUID());
                if (user == null) return false;

                QueryOptions queryOptions = luckPerms.getContextManager()
                        .getQueryOptions(user).orElse(luckPerms.getContextManager().getStaticQueryOptions());

                return user.getCachedData().getPermissionData(queryOptions)
                        .checkPermission("playervaults.export").asBoolean()
                        || player.hasPermissions(2);
            } catch (Exception e) {
                return player.hasPermissions(2);
            }
        }

        return source.hasPermission(2); // Console fallback
    }

    public static boolean canViewOthers(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            try {
                LuckPerms luckPerms = LuckPermsProvider.get();
                User user = luckPerms.getUserManager().getUser(player.getUUID());
                if (user == null) return false;

                QueryOptions queryOptions = luckPerms.getContextManager()
                        .getQueryOptions(user).orElse(luckPerms.getContextManager().getStaticQueryOptions());

                return user.getCachedData().getPermissionData(queryOptions)
                        .checkPermission("playervaults.viewothers").asBoolean()
                        || player.hasPermissions(2);
            } catch (Exception e) {
                return player.hasPermissions(2);
            }
        }

        return source.hasPermission(2);
    }

    public static boolean canEditOthers(ServerPlayer viewer, ServerPlayer target) {
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getUserManager().getUser(viewer.getUUID());
            if (user == null) return false;

            QueryOptions queryOptions = luckPerms.getContextManager()
                    .getQueryOptions(user).orElse(luckPerms.getContextManager().getStaticQueryOptions());

            return user.getCachedData().getPermissionData(queryOptions)
                    .checkPermission("playervaults.editothers").asBoolean()
                    || viewer.hasPermissions(2);
        } catch (Exception e) {
            return viewer.hasPermissions(2);
        }
    }
    public static boolean hasVaultPermissionOrItems(ServerPlayer player, int number, RegistryAccess registryAccess) {
        if (hasVaultPermission(player, number)) return true;
        int size = ConfigManager.config.vaultSize;
        return VaultStorage.hasAnyItems(player.getUUID(), number, size, registryAccess);
    }
}
