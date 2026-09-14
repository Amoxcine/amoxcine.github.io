package fr.ascendant.lunar.encounter;
import java.io.IOException;
import java.util.UUID;
public interface WithdrawalJournal {
    RewardLedger.Entry begin(UUID run, UUID delegate, UUID operation) throws IOException;
}
