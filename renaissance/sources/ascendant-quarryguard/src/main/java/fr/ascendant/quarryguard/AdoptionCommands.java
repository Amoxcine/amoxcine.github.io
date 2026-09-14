package fr.ascendant.quarryguard;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;

public final class AdoptionCommands {
    private AdoptionCommands() { }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("inspect").requires(s -> s.hasPermission(2))
            .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> run(c, () ->
                AdoptionService.inspect(c.getSource(), BlockPosArgument.getBlockPos(c, "pos"))))));
        root.then(Commands.literal("adopt").requires(s -> s.hasPermission(2))
            .then(Commands.argument("pos", BlockPosArgument.blockPos())
                .then(Commands.argument("owner", UuidArgument.uuid()).executes(c -> run(c, () -> {
                    UUID owner = UuidArgument.getUuid(c, "owner");
                    UUID token = AdoptionService.preview(c.getSource(), BlockPosArgument.getBlockPos(c, "pos"), owner);
                    return "Aucune modification. Proprietaire propose : " + owner
                        + ". Apres verification, dans cette dimension sous 10 minutes : /quarryguard confirm " + token
                        + ". La quarry restera en pause apres confirmation.";
                })))));
        root.then(Commands.literal("confirm").requires(s -> s.hasPermission(2))
            .then(Commands.argument("token", UuidArgument.uuid()).executes(c -> run(c, () -> {
                AdoptionService.confirm(c.getSource(), UuidArgument.getUuid(c, "token"));
                return "Attribution enregistree, quarry toujours en pause. Inspectez-la puis utilisez /quarryguard resume <x y z>, ou cancel-adoption <x y z>.";
            }))));
        root.then(Commands.literal("resume").requires(s -> s.hasPermission(2))
            .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> run(c, () -> {
                AdoptionService.resume(c.getSource(), BlockPosArgument.getBlockPos(c, "pos"));
                return "Pause d'attribution retiree : la quarry peut reprendre sous les controles de claims habituels.";
            }))));
        root.then(Commands.literal("cancel-adoption").requires(s -> s.hasPermission(2))
            .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> run(c, () -> {
                AdoptionService.cancel(c.getSource(), BlockPosArgument.getBlockPos(c, "pos"));
                return "Attribution en attente annulee : proprietaire inconnu, quarry suspendue. Contenu et progression conserves.";
            }))));
    }

    private interface Action { String execute() throws Exception; }

    private static int run(CommandContext<CommandSourceStack> context, Action action) {
        try {
            String result = action.execute();
            context.getSource().sendSuccess(() -> Component.literal(result), false);
            return 1;
        } catch (Exception error) {
            context.getSource().sendFailure(Component.literal("QuarryGuard : " + error.getMessage()));
            if (!(error instanceof IllegalStateException)) LogUtils.getLogger().error("QuarryGuard operator action failed", error);
            return 0;
        }
    }
}
