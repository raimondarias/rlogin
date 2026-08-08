package com.raimondarias.rlogin.velocity.command;

import com.raimondarias.rlogin.velocity.RLoginVelocityPlugin;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Comandos de administración del lado proxy. La gestión de cuentas
 * (registro, login, migración...) vive en {@code rlogin-paper}, que es
 * quien tiene la base de datos; aquí solo se administra la config del
 * propio proxy (detección premium, política de fallo de la API de Mojang...).
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
            invocation.source().sendMessage(Component.text("rLogin (proxy): configuración recargada.", NamedTextColor.GREEN));
            return;
        }
        invocation.source().sendMessage(Component.text(
                "/rlogin reload — recarga la configuración del proxy. "
                        + "La gestión de cuentas (registro/login/migración) se hace en cada backend.",
                NamedTextColor.YELLOW));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(PERMISSION);
    }
}
