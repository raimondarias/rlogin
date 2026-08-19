package com.raimondarias.rlogin.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveCommandsTest {

    private final SensitiveCommands commands = SensitiveCommands.withDefaults();

    @Test
    void catchesEveryCommandThatTakesAPassword() {
        assertTrue(commands.revealsSecret("/login hunter2"));
        assertTrue(commands.revealsSecret("/register hunter2 hunter2"));
        assertTrue(commands.revealsSecret("/changepassword old new"));
        assertTrue(commands.revealsSecret("/recover AB12-CD34 newPassword"));
        assertTrue(commands.revealsSecret("/2fa confirm 123456"));
    }

    @Test
    void catchesTheAliasesToo() {
        assertTrue(commands.revealsSecret("/l hunter2"));
        assertTrue(commands.revealsSecret("/reg hunter2 hunter2"));
        assertTrue(commands.revealsSecret("/changepass old new"));
        assertTrue(commands.revealsSecret("/rlogin recover AB12-CD34 newPassword"));
    }

    @Test
    void catchesThemBehindTheUmbrellaCommand() {
        assertTrue(commands.revealsSecret("/rlogin login hunter2"));
        assertTrue(commands.revealsSecret("/rl register hunter2 hunter2"));
    }

    @Test
    void catchesThemBehindAPluginNamespace() {
        // Any player can type this form, and it runs the exact same command.
        assertTrue(commands.revealsSecret("/rlogin:login hunter2"));
        assertTrue(commands.revealsSecret("/minecraft:login hunter2"));
    }

    @Test
    void isCaseInsensitive() {
        assertTrue(commands.revealsSecret("/LOGIN hunter2"));
        assertTrue(commands.revealsSecret("/RLogin Register a b"));
    }

    @Test
    void leavesHarmlessCommandsAlone() {
        // No arguments -> nothing to hide, and the attempt itself is worth seeing.
        assertFalse(commands.revealsSecret("/login"));
        assertFalse(commands.revealsSecret("/register"));

        assertFalse(commands.revealsSecret("/rlogin reload"));
        assertFalse(commands.revealsSecret("/rlogin info Steve"));
        assertFalse(commands.revealsSecret("/logout"));
        assertFalse(commands.revealsSecret("/premium"));
        assertFalse(commands.revealsSecret("/say hello"));
        assertFalse(commands.revealsSecret("/give Steve stone 64"));
    }

    @Test
    void doesNotConfuseALoginArgumentWithTheLoginCommand() {
        assertFalse(commands.revealsSecret("/msg login hello"));
        assertFalse(commands.revealsSecret("/rlogin spawn set login"));
    }

    @Test
    void masksArgumentsButKeepsTheCommandReadable() {
        assertEquals("/login ***", commands.mask("/login hunter2"));
        assertEquals("/register ***", commands.mask("/register hunter2 hunter2"));
        assertEquals("/recover ***", commands.mask("/recover AB12-CD34 newPassword"));
        assertEquals("/rlogin login ***", commands.mask("/rlogin login hunter2"));
        assertEquals("/login", commands.mask("/login"));
    }

    @Test
    void readsTheCommandOutOfARealServerLogLine() {
        String line = "Raimondtx issued server command: /register hunter2 hunter2";

        String command = SensitiveCommands.commandInLogMessage(line);

        assertEquals("/register hunter2 hunter2", command);
        assertTrue(commands.revealsSecret(command));
    }

    @Test
    void ignoresLinesThatArentCommands() {
        assertNull(SensitiveCommands.commandInLogMessage("Raimondtx joined the game"));
        assertNull(SensitiveCommands.commandInLogMessage(null));
    }

    @Test
    void honoursTheLabelsTheServerActuallyRegistered() {
        // A server that renamed the command keeps it protected; one that didn't
        // register an alias doesn't start hiding an unrelated command by that name.
        SensitiveCommands renamed = SensitiveCommands.of(java.util.List.of("entrar"), java.util.List.of("rlogin"));

        assertTrue(renamed.revealsSecret("/entrar hunter2"));
        assertFalse(renamed.revealsSecret("/login hunter2"));
    }
}
