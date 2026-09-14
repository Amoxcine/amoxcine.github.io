package fr.ascendant.lunar.encounter;

import java.nio.file.*;
import java.util.*;

public final class SuccessOnlyLedgerTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Success-only ledger "+checks);}
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]);Files.createDirectories(root);
        var team=UUID.randomUUID();var player=UUID.randomUUID();var group=new RaidMachine.Group(team,Set.of(player),List.of(player));
        var runs=new ArrayList<UUID>();
        try(var journal=new SqliteRaidJournal(root.resolve("ledger"))) {
            for(int attempt=0;attempt<3;attempt++) {
                var raid=new RaidMachine(UUID.randomUUID(),RaidMachine.Identity.SEALED_GREENHOUSE,group,LunarConfig.defaults(true).rules(),attempt);
                journal.reserve(raid,0);raid.advance(160,Set.of(player));
                var signal=raid.view().signals().getFirst();raid.defenderInterrupted(raid.token(),0);
                raid.interact(player,raid.token(),0,signal.receiver(),signal.action());raid.damage(player,raid.token(),10000);
                raid.advance(100,Set.of(player));raid.damage(player,raid.token(),10000);
                check(raid.view().phase()==RaidMachine.Phase.SUCCESS);journal.settle(raid);journal.settle(raid);
                var entry=journal.entry(raid.view().run());runs.add(entry.run());
                check(entry.status()==RewardLedger.Status.AVAILABLE&&entry.reservedCoolant()==0);
                check(entry.operation()==null&&entry.claimant()==null&&entry.group().equals(group));
            }
            var abandoned=new RaidMachine(UUID.randomUUID(),RaidMachine.Identity.SEALED_GREENHOUSE,group,LunarConfig.defaults(true).rules(),0);
            journal.reserve(abandoned,0);abandoned.abort();journal.settle(abandoned);
            var entry=journal.entry(abandoned.view().run());
            check(entry.status()==RewardLedger.Status.REFUNDABLE&&entry.reservedCoolant()==0&&entry.operation()==null);
            check(journal.runCount()==4&&journal.healthy());
        }
        try(var journal=new SqliteRaidJournal(root.resolve("ledger"))) {
            for(var run:runs) {
                var entry=journal.entry(run);
                check(entry.status()==RewardLedger.Status.AVAILABLE&&entry.operation()==null&&entry.claimant()==null);
            }
            check(journal.runCount()==4&&journal.healthy());
        }
        System.out.println("PASS success-only ledger: "+checks+" checks; repeatable victories, frozen roster, zero escrow, no withdrawal or REVIEW generated.");
    }
}
