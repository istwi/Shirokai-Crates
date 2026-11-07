package dev.shirokaicrates.integration;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.query.QueryOptions;
import net.minecraft.server.network.ServerPlayerEntity;

public final class Permissions {
    private Permissions() {}
    public static boolean has(ServerPlayerEntity player,String node) {
        try {
            var user=LuckPermsProvider.get().getUserManager().getUser(player.getUuid());
            if(user!=null) {
                var data=user.getCachedData().getPermissionData(LuckPermsProvider.get().getContextManager()
                        .getQueryOptions(user).orElse(QueryOptions.defaultContextualOptions()));
                var value=data.checkPermission(node);
                if(value != net.luckperms.api.util.Tristate.UNDEFINED) return value.asBoolean();
            }
            // Explicit false always wins; only undefined administrative nodes fall back to OP.
            return node.startsWith("shirokaicrates.") && switch(node) {
                case "shirokaicrates.use","shirokaicrates.preview","shirokaicrates.fragments","shirokaicrates.pending" -> true;
                default -> player.hasPermissionLevel(2);
            };
        } catch(RuntimeException | LinkageError ex) { return false; }
    }
}
