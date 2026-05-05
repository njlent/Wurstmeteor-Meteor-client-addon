package de.njlent.wurstmeteor.modules.misc;

import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public class CommandScannerModule extends Module {
    private static final int RESPONSE_TIMEOUT_TICKS = 20;
    private static final int REQUEST_COOLDOWN_TICKS = 2;
    private static final int EXECUTE_TIMEOUT_TICKS = 20;
    private static final int EXECUTE_COOLDOWN_TICKS = 4;
    private static final char[] LETTERS = "abcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final Set<String> VANILLA_COMMANDS = new HashSet<>(Arrays.asList(
        "advancement", "attribute", "ban", "ban-ip", "banlist", "bossbar", "clear", "clone", "damage", "data",
        "datapack", "debug", "defaultgamemode", "deop", "difficulty", "effect", "enchant", "execute", "experience",
        "fill", "fillbiome", "forceload", "function", "gamemode", "gamerule", "give", "help", "item", "jfr",
        "kick", "kill", "list", "locate", "loot", "me", "msg", "op", "pardon", "pardon-ip", "particle", "perf",
        "place", "playsound", "publish", "random", "recipe", "reload", "return", "ride", "save-all", "save-off",
        "save-on", "say", "schedule", "scoreboard", "seed", "setblock", "setidletimeout", "setworldspawn",
        "spawnpoint", "spectate", "spreadplayers", "stop", "stopsound", "summon", "tag", "team", "teammsg",
        "teleport", "tell", "tellraw", "tick", "time", "title", "tm", "tp", "transfer", "trigger", "w",
        "weather", "whitelist", "worldborder", "xp"
    ));

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<ScanMode> scanMode = sgGeneral.add(new EnumSetting.Builder<ScanMode>()
        .name("scan-mode")
        .description("How to collect command roots.")
        .defaultValue(ScanMode.PacketProbing)
        .build()
    );

    private final Setting<Boolean> runFoundCommands = sgGeneral.add(new BoolSetting.Builder()
        .name("run-found-commands")
        .description("Runs discovered non-vanilla commands and prints the first response.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> debugProbe = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-probe")
        .description("Prints packet probe progress.")
        .defaultValue(false)
        .build()
    );

    private final Setting<java.util.List<String>> dontSendFilter = sgGeneral.add(new StringListSetting.Builder()
        .name("dont-send-filter")
        .description("Command substrings that should not be executed.")
        .defaultValue()
        .build()
    );

    private final TreeSet<String> scannedCommands = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private final ArrayList<String> commandsToExecute = new ArrayList<>();
    private Phase phase = Phase.Scanning;
    private boolean awaitingResponse;
    private int waitTicks;
    private int cooldownTicks;
    private int letterIndex;
    private int requestId;

    public CommandScannerModule() {
        super(WurstMeteorAddon.CATEGORY, "command-scanner", "Scans server command roots through autocomplete packets.");
    }

    @Override
    public void onActivate() {
        if (mc.getConnection() == null || mc.player == null) {
            error("Not connected to a server.");
            toggle();
            return;
        }

        scannedCommands.clear();
        commandsToExecute.clear();
        phase = Phase.Scanning;
        awaitingResponse = false;
        waitTicks = 0;
        cooldownTicks = 0;
        letterIndex = 0;
        requestId = 1;

        info("CommandScanner started (%s).", scanMode.get());

        if (scanMode.get() == ScanMode.ClientSideEnumeration) {
            collectClientCommands(scannedCommands);
            finishScan();
        } else {
            sendNextRequest();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (phase == Phase.Executing) {
            runExecutionStep();
            return;
        }

        if (awaitingResponse) {
            waitTicks++;
            if (waitTicks >= RESPONSE_TIMEOUT_TICKS) {
                if (debugProbe.get()) info("Probe timeout: /%s.", LETTERS[letterIndex]);
                awaitingResponse = false;
                letterIndex++;
                cooldownTicks = REQUEST_COOLDOWN_TICKS;
            }
            return;
        }

        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        sendNextRequest();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!awaitingResponse || !(event.packet instanceof ClientboundCommandSuggestionsPacket packet)) return;
        if (packet.id() != requestId) return;

        Suggestions suggestions = packet.toSuggestions();
        if (debugProbe.get()) info("Probe response: /%s (%s suggestions).", LETTERS[letterIndex], suggestions.getList().size());
        readSuggestions(suggestions);
        awaitingResponse = false;
        letterIndex++;
        cooldownTicks = REQUEST_COOLDOWN_TICKS;
    }

    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        if (phase != Phase.Executing || !awaitingResponse) return;

        String msg = event.getMessage().getString();
        if (msg == null || msg.isBlank() || msg.toLowerCase(Locale.ROOT).contains("commandscanner")) return;

        String command = getCurrentExecutionCommand();
        awaitingResponse = false;
        letterIndex++;
        cooldownTicks = EXECUTE_COOLDOWN_TICKS;
        info("/%s -> %s", command, msg.trim());
    }

    private void sendNextRequest() {
        if (mc.getConnection() == null || letterIndex >= LETTERS.length) {
            finishScan();
            return;
        }

        char letter = LETTERS[letterIndex];
        requestId++;
        waitTicks = 0;
        awaitingResponse = true;
        if (debugProbe.get()) info("Probe sent: /%s.", letter);
        mc.getConnection().send(new ServerboundCommandSuggestionPacket(requestId, "/" + letter));
    }

    private void readSuggestions(Suggestions suggestions) {
        if (suggestions == null) return;

        for (Suggestion suggestion : suggestions.getList()) {
            String command = extractRootCommand(suggestion.getText());
            if (command != null) scannedCommands.add(command);
        }
    }

    private void finishScan() {
        TreeSet<String> nonVanilla = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        TreeSet<String> clientSide = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        if (scanMode.get() == ScanMode.UnknownOnly) collectClientCommands(clientSide);

        for (String command : scannedCommands) {
            if (isVanilla(command)) continue;
            if (scanMode.get() == ScanMode.UnknownOnly && clientSide.contains(command)) continue;
            nonVanilla.add(command);
        }

        if (nonVanilla.isEmpty()) {
            warning("No non-vanilla root commands found.");
            toggle();
            return;
        }

        info("Found %s non-vanilla commands:", nonVanilla.size());
        printCommandList(nonVanilla);

        if (!runFoundCommands.get()) {
            toggle();
            return;
        }

        commandsToExecute.clear();
        commandsToExecute.addAll(filterCommands(nonVanilla));
        if (commandsToExecute.isEmpty()) {
            warning("All commands were filtered.");
            toggle();
            return;
        }

        phase = Phase.Executing;
        awaitingResponse = false;
        waitTicks = 0;
        cooldownTicks = 0;
        letterIndex = 0;
        info("Running %s discovered commands.", commandsToExecute.size());
    }

    private void runExecutionStep() {
        if (awaitingResponse) {
            waitTicks++;
            if (waitTicks < EXECUTE_TIMEOUT_TICKS) return;

            info("/%s -> [no response]", getCurrentExecutionCommand());
            awaitingResponse = false;
            letterIndex++;
            cooldownTicks = EXECUTE_COOLDOWN_TICKS;
            return;
        }

        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        if (letterIndex >= commandsToExecute.size()) {
            info("CommandScanner finished.");
            toggle();
            return;
        }

        String command = getCurrentExecutionCommand();
        waitTicks = 0;
        awaitingResponse = true;
        mc.getConnection().sendCommand(command);
    }

    private void collectClientCommands(Set<String> target) {
        if (mc.getConnection() == null || mc.getConnection().getCommands() == null) return;
        for (var node : mc.getConnection().getCommands().getRoot().getChildren()) {
            String name = node.getName();
            if (name != null && !name.isBlank()) target.add(name.toLowerCase(Locale.ROOT));
        }
    }

    private String extractRootCommand(String text) {
        if (text == null) return null;
        String command = text.trim().toLowerCase(Locale.ROOT);
        if (command.startsWith("/")) command = command.substring(1).trim();
        int space = command.indexOf(' ');
        if (space > 0) command = command.substring(0, space);
        return command.isEmpty() ? null : command;
    }

    private boolean isVanilla(String command) {
        return command == null || command.startsWith("minecraft:") || VANILLA_COMMANDS.contains(command);
    }

    private java.util.List<String> filterCommands(Set<String> commands) {
        ArrayList<String> filtered = new ArrayList<>();
        for (String command : commands) {
            boolean blocked = false;
            for (String term : dontSendFilter.get()) {
                if (term != null && !term.isBlank() && command.contains(term.toLowerCase(Locale.ROOT).trim())) {
                    blocked = true;
                    break;
                }
            }
            if (!blocked) filtered.add(command);
        }
        return filtered;
    }

    private String getCurrentExecutionCommand() {
        return letterIndex >= 0 && letterIndex < commandsToExecute.size() ? commandsToExecute.get(letterIndex) : "";
    }

    private void printCommandList(TreeSet<String> commands) {
        ArrayList<String> line = new ArrayList<>();
        for (String command : commands) {
            line.add("/" + command);
            if (line.size() >= 8) {
                info(String.join(", ", line));
                line.clear();
            }
        }
        if (!line.isEmpty()) info(String.join(", ", line));
    }

    public enum ScanMode {
        PacketProbing,
        ClientSideEnumeration,
        UnknownOnly
    }

    private enum Phase {
        Scanning,
        Executing
    }
}
