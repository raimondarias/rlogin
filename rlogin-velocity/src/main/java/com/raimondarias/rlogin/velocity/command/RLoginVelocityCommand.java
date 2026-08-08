package com.raimondarias.rlogin.velocity.command;

import com.raimondarias.rlogin.velocity.RLoginVelocityPlugin;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Proxy-side admin commands. Account management (register, login,
 * migration...) lives in {@code rlogin-paper}, which owns the database;
 * this only administers the proxy's own config (premium detection, Mojang
 * API failure policy, auth lobby routing...).
 */
public final class RLoginVelocityCommand implements SimpleCommand {

    private static final String PERMISSION = "rlogin.admin";

    private final RLoginVelocityPlugin plugin;

    public RLoginVelocityCommand(RLoginVelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reload();
            invocation.source().sendMessage(Component.text("rLogin (proxy): configuration reloaded.", NamedTextColor.GREEN));
            return;
        }
        invocation.source().sendMessage(Component.text(
                "/rlogin reload — reloads the proxy's configuration. "
                        + "Account management (register/login/migration) happens on each backend.",
                NamedTextColor.YELLOW));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(PERMISSION);
    }
}
