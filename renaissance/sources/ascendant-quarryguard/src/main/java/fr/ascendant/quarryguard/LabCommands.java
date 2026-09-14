package fr.ascendant.quarryguard;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class LabCommands {
    private LabCommands() {}

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("modules").executes(context -> {
            try {
                LogUtils.getLogger().info("QG-LAB-RESULT: PASS {}", ModuleChecks.run(context.getSource().getServer()));
                return 1;
            } catch (Throwable failure) {
                LogUtils.getLogger().error("QG-LAB-RESULT: FAIL modules", failure);
                return 0;
            }
        }));
        root.then(Commands.literal("movement").executes(context -> {
            try {
                LogUtils.getLogger().info("QG-LAB-RESULT: PASS {}", MovementChecks.run(context.getSource().getServer()));
                return 1;
            } catch (Throwable failure) {
                LogUtils.getLogger().error("QG-LAB-RESULT: FAIL movement", failure);
                return 0;
            }
        }));
        root.then(Commands.literal("shared-chunks").executes(context -> {
            try {
                LogUtils.getLogger().info("QG-LAB-RESULT: PASS {}", SharedChunkChecks.run(context.getSource().getServer()));
                return 1;
            } catch (Throwable failure) {
                LogUtils.getLogger().error("QG-LAB-RESULT: FAIL shared-chunks", failure);
                return 0;
            }
        }));
        root.then(Commands.literal("frames").executes(context -> {
            try {
                var server = context.getSource().getServer();
                String frames = FrameChecks.run(server);
                String authority = FrameAuthorityChecks.run(server);
                LogUtils.getLogger().info("QG-LAB-RESULT: PASS {}; {}", frames, authority);
                return 1;
            } catch (Throwable failure) {
                LogUtils.getLogger().error("QG-LAB-RESULT: FAIL frames", failure);
                return 0;
            }
        }));
        root.then(Commands.literal("regression").executes(context -> {
            try {
                var server = context.getSource().getServer();
                String permissions = LabChecks.run(server);
                String geometry = GeometryChecks.run(server);
                String markerHelpers = MarkerChecks.run(server);
                String markers = MarkerPlacementChecks.run(server);
                String powered = PoweredChecks.run(server);
                String frames = FrameChecks.run(server);
                String authority = FrameAuthorityChecks.run(server);
                LogUtils.getLogger().info("QG-LAB-RESULT: PASS REGRESSION {}; {}; {}; {}; {}; {}; {}",
                    permissions, geometry, markerHelpers, markers, powered, frames, authority);
                return 1;
            } catch (Throwable failure) {
                LogUtils.getLogger().error("QG-LAB-RESULT: FAIL regression", failure);
                return 0;
            }
        }));
        root.then(Commands.literal("markers").executes(context -> {
            try {
                var server = context.getSource().getServer();
                String helper = MarkerChecks.run(server);
                String placement = MarkerPlacementChecks.run(server);
                LogUtils.getLogger().info("QG-LAB-RESULT: PASS {}; {}", helper, placement);
                return 1;
            } catch (Throwable failure) {
                LogUtils.getLogger().error("QG-LAB-RESULT: FAIL markers", failure);
                return 0;
            }
        }));
        root.then(Commands.literal("geometry").executes(context -> {
            try {
                LogUtils.getLogger().info("QG-LAB-RESULT: PASS {}", GeometryChecks.run(context.getSource().getServer()));
                return 1;
            } catch (Throwable failure) {
                LogUtils.getLogger().error("QG-LAB-RESULT: FAIL geometry", failure);
                return 0;
            }
        }));
        root.then(Commands.literal("powered").executes(context -> {
            try {
                LogUtils.getLogger().info("QG-LAB-RESULT: PASS {}", PoweredChecks.run(context.getSource().getServer()));
                return 1;
            } catch (Throwable failure) {
                LogUtils.getLogger().error("QG-LAB-RESULT: FAIL powered", failure);
                return 0;
            }
        }));
        root.then(Commands.literal("loadtest").executes(context -> {
            try {
                LogUtils.getLogger().info("QG-LAB-RESULT: PASS {}", LoadChecks.run(context.getSource().getServer()));
                return 1;
            } catch (Throwable failure) {
                LogUtils.getLogger().error("QG-LAB-RESULT: FAIL loadtest", failure);
                return 0;
            }
        }));
        for (String phase : new String[]{"legacy-prepare", "legacy-check"}) {
            root.then(Commands.literal(phase).executes(context -> {
                try {
                    LogUtils.getLogger().info("QG-LAB-RESULT: PASS {}", LegacyChecks.run(context.getSource().getServer(), phase.equals("legacy-prepare")));
                    return 1;
                } catch (Throwable failure) {
                    LogUtils.getLogger().error("QG-LAB-RESULT: FAIL " + phase, failure);
                    return 0;
                }
            }));
        }
        for (String phase : new String[]{"restart-prepare", "restart-check"}) {
            root.then(Commands.literal(phase).executes(context -> {
                try {
                    LogUtils.getLogger().info("QG-LAB-RESULT: PASS {}", RestartChecks.run(context.getSource().getServer(), phase.equals("restart-prepare")));
                    return 1;
                } catch (Throwable failure) {
                    LogUtils.getLogger().error("QG-LAB-RESULT: FAIL " + phase, failure);
                    return 0;
                }
            }));
        }
        root.then(Commands.literal("selftest").executes(context -> {
            try {
                String report = LabChecks.run(context.getSource().getServer());
                LogUtils.getLogger().info("QG-LAB-RESULT: PASS {}", report);
                return 1;
            } catch (Throwable failure) {
                LogUtils.getLogger().error("QG-LAB-RESULT: FAIL", failure);
                return 0;
            }
        }));
        for (String phase : new String[]{"adoption-prepare", "adoption-check"}) {
            root.then(Commands.literal(phase).executes(context -> {
                try {
                    var server = context.getSource().getServer();
                    LogUtils.getLogger().info("QG-LAB-RESULT: PASS {}", AdoptionRestartChecks.run(server, phase.equals("adoption-prepare")));
                    return 1;
                } catch (Throwable failure) {
                    LogUtils.getLogger().error("QG-LAB-RESULT: FAIL " + phase, failure);
                    return 0;
                }
            }));
        }
        root.then(Commands.literal("adoption").executes(context -> {
            try {
                var server = context.getSource().getServer();
                LogUtils.getLogger().info("QG-LAB-RESULT: PASS {}", AdoptionChecks.run(server));
                return 1;
            } catch (Throwable failure) {
                LogUtils.getLogger().error("QG-LAB-RESULT: FAIL adoption", failure);
                return 0;
            }
        }));
    }
}
