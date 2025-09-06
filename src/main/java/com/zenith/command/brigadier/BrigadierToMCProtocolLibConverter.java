package com.zenith.command.brigadier;

import com.google.common.collect.Queues;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import com.zenith.command.api.CommandContext;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.experimental.UtilityClass;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandNode;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandParser;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandType;
import org.geysermc.mcprotocollib.protocol.data.game.command.properties.*;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Queue;

import static com.zenith.Globals.SERVER_LOG;

/**
 * Converts between Zenith's native Brigadier commands and the MCProtocolLib intermediary packet format
 */
@UtilityClass
public class BrigadierToMCProtocolLibConverter {
    public CommandNode[] convertNodesToMCProtocolLibNodes(CommandDispatcher<CommandContext> dispatcher) {
        final RootCommandNode<CommandContext> rootCommandNode = dispatcher.getRoot();
        final Object2IntMap<com.mojang.brigadier.tree.CommandNode<CommandContext>> object2IntMap = enumerateNodes(rootCommandNode);
        final List<CommandNode> entries = createEntries(object2IntMap);
        // root index should always be 0
        return entries.toArray(new CommandNode[0]);
    }

    private CommandType convertCommandType(com.mojang.brigadier.tree.CommandNode node) {
        return switch (node) {
            case RootCommandNode n -> CommandType.ROOT;
            case LiteralCommandNode n -> CommandType.LITERAL;
            case ArgumentCommandNode n -> CommandType.ARGUMENT;
            case null, default -> throw new RuntimeException("No valid command type found for node: " + (node == null ? "?" : node.getName()));
        };
    }

    private Object2IntMap<com.mojang.brigadier.tree.CommandNode<CommandContext>> enumerateNodes(RootCommandNode<CommandContext> rootCommandNode) {
        Object2IntMap<com.mojang.brigadier.tree.CommandNode<CommandContext>> object2IntMap = new Object2IntOpenHashMap<>();
        Queue<com.mojang.brigadier.tree.CommandNode<CommandContext>> queue = Queues.newArrayDeque();
        queue.add(rootCommandNode);

        com.mojang.brigadier.tree.CommandNode<CommandContext> commandNode;
        while((commandNode = queue.poll()) != null) {
            if (!object2IntMap.containsKey(commandNode)) {
                object2IntMap.put(commandNode, object2IntMap.size());
                queue.addAll(commandNode.getChildren());
                if (commandNode.getRedirect() != null) {
                    queue.add(commandNode.getRedirect());
                }
            }
        }
        return object2IntMap;
    }

    private List<CommandNode> createEntries(final Object2IntMap<com.mojang.brigadier.tree.CommandNode<CommandContext>> nodes) {
        ArrayList<CommandNode> nodeList = new ArrayList<>(nodes.size());
        for (int i = 0; i < nodes.size(); i++) nodeList.add(null);
        for (var entry : nodes.object2IntEntrySet()) {
            nodeList.set(entry.getIntValue(), createEntry(entry.getKey(), nodes));
        }
        return nodeList;
    }

    private CommandNode createEntry(
        com.mojang.brigadier.tree.CommandNode<CommandContext> node, Object2IntMap<com.mojang.brigadier.tree.CommandNode<CommandContext>> nodes
    ) {
        var commandType = convertCommandType(node);
        var isExecutable = node.getCommand() != null;
        var childrenIndeces = node.getChildren().stream().mapToInt(nodes::getInt).toArray();
        final OptionalInt redirectIndex = node.getRedirect() == null ? OptionalInt.empty() : OptionalInt.of(nodes.getInt(node.getRedirect()));
        String name;
        if (node instanceof CaseInsensitiveLiteralCommandNode<CommandContext> ci) {
            name = ci.getLiteralOriginalCase();
        } else {
            name = node.getName();
        }
        CommandParser parser = null;
        CommandProperties properties = null;
        String suggestionType = null;
        if (node instanceof ArgumentCommandNode<CommandContext,?> argumentNode) {
            switch (argumentNode.getType()) {
                case SerializableArgumentType t -> {
                    parser = t.commandParser();
                    properties = t.commandProperties();
                    if (t.askServerForSuggestions()) {
                        suggestionType = "minecraft:ask_server";
                    }
                }
                case BoolArgumentType t -> {
                    parser = CommandParser.BOOL;
                }
                case DoubleArgumentType t -> {
                    parser = CommandParser.DOUBLE;
                    properties = new DoubleProperties(t.getMinimum(), t.getMaximum());
                }
                case FloatArgumentType t -> {
                    parser = CommandParser.FLOAT;
                    properties = new FloatProperties(t.getMinimum(), t.getMaximum());
                }
                case LongArgumentType t -> {
                    parser = CommandParser.LONG;
                    properties = new LongProperties(t.getMinimum(), t.getMaximum());
                }
                case IntegerArgumentType t -> {
                    parser = CommandParser.INTEGER;
                    properties = new IntegerProperties(t.getMinimum(), t.getMaximum());
                }
                case StringArgumentType t -> {
                    parser = CommandParser.STRING;
                    properties = switch (t.getType()) {
                        case StringArgumentType.StringType.SINGLE_WORD -> StringProperties.SINGLE_WORD;
                        case StringArgumentType.StringType.GREEDY_PHRASE -> StringProperties.GREEDY_PHRASE;
                        case StringArgumentType.StringType.QUOTABLE_PHRASE -> StringProperties.QUOTABLE_PHRASE;
                    };
                }
                default -> {
                    SERVER_LOG.error("Unable to serialize unknown command argument type: {} : {}", argumentNode.getType(), name);
                }
            }
        }
        return new CommandNode(
            commandType,
            isExecutable,
            childrenIndeces,
            redirectIndex,
            name,
            parser,
            properties,
            suggestionType // if null, the client should never ask for suggestions from the server
        );
    }
}
