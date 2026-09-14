package fr.ascendant.quarryguard;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod("ascendant_quarryguard")
public final class QuarryGuard {
    public QuarryGuard() {
        NeoForge.EVENT_BUS.addListener(this::started);
        NeoForge.EVENT_BUS.addListener(this::stopping);
        NeoForge.EVENT_BUS.addListener(this::commands);
    }

    private void started(ServerStartedEvent event) {
        GuardHooks.start(event.getServer());
    }

    private void stopping(ServerStoppingEvent event) {
        GuardHooks.stop();
    }

    private void commands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        var root = Commands.literal("quarryguard").requires(s -> s.hasPermission(2))
            .then(Commands.literal("status").executes(context -> {
                context.getSource().sendSuccess(() -> Component.literal(GuardHooks.status()), false);
                return 1;
            }));
        AdoptionCommands.register(root);
        if (Boolean.getBoolean("ascendant.quarryguard.lab")) {
            try {
                Class.forName("fr.ascendant.quarryguard.LabCommands")
                    .getMethod("register", LiteralArgumentBuilder.class).invoke(null, root);
            } catch (ReflectiveOperationException | LinkageError failure) {
                throw new IllegalStateException("QuarryGuard laboratory commands unavailable: "
                    + "use the laboratory JAR or disable -Dascendant.quarryguard.lab=true", failure);
            }
        }
        dispatcher.register(root);
    }
}
