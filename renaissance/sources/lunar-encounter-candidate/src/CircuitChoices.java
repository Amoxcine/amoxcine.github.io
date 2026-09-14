package fr.ascendant.lunar.encounter;

import java.util.*;

/** Small vanilla-command vocabulary; choices are never corrected against the signal. */
public final class CircuitChoices {
    private CircuitChoices() {}
    public static RaidMachine.Action action(String value) {
        return switch(value.toLowerCase(Locale.ROOT)) {
            case "identifier" -> RaidMachine.Action.IDENTIFY;
            case "transferer" -> RaidMachine.Action.TRANSFER_CHARGE;
            case "deriver" -> RaidMachine.Action.DIVERT_OVERLOAD;
            default -> throw new IllegalArgumentException("Action : identifier, transferer ou deriver.");
        };
    }
    public static int receiver(String value) {
        return switch(value.toUpperCase(Locale.ROOT)) {
            case "A" -> 0; case "B" -> 1; case "C" -> 2;
            default -> throw new IllegalArgumentException("Recepteur : A, B ou C.");
        };
    }
    public static String word(RaidMachine.Action action) {
        return switch(action) {case IDENTIFY -> "identifier";case TRANSFER_CHARGE -> "transferer";case DIVERT_OVERLOAD -> "deriver";};
    }
    public static String label(RaidMachine.Action action) {
        return switch(action) {case IDENTIFY -> "Identifier";case TRANSFER_CHARGE -> "Transf\u00e9rer";case DIVERT_OVERLOAD -> "D\u00e9river";};
    }
    public static String command(RaidMachine.Token token,int task,int receiver,RaidMachine.Action action) {
        if(token==null||token.run()==null||token.generation()<1||task<0||task>2||receiver<0||receiver>2)
            throw new IllegalArgumentException("Choix de circuit invalide.");
        return "/lunar_encounter circuit "+token.run()+" "+token.generation()+" "+(task+1)+" "+(char)('A'+receiver)+" "+word(action);
    }
    public static List<String> telegraphs(RaidMachine.View v) {
        String phase=switch(v.phase()) {
            case READING -> "Lecture : confirmations ferm\u00e9es.";
            case ROUTING -> "Routage ouvert : r\u00e9cepteur A/B/C, puis action.";
            case EXPOSED -> "Module expos\u00e9 : attaquez-le.";
            case FINAL_WARNING -> "Protection finale en ouverture : pr\u00e9parez-vous.";
            case CORE_EXPOSED -> "C\u0153ur expos\u00e9 : neutralisez-le.";
            case SUCCESS -> "Relais lunaire stabilise : victoire enregistree, premiere recompense dans FTB Quests.";
            case FAILED -> "Op\u00e9ration arr\u00eat\u00e9e : stabilit\u00e9 perdue.";
            case ABORTED -> "Op\u00e9ration interrompue : "+stop(v.stop())+".";
        };
        List<String> lines=new ArrayList<>();
        lines.add("Relais lunaire | "+phase);
        lines.add("Stabilit\u00e9 : "+Math.max(0,3-v.errors())+"/3 | G\u00e9n\u00e9ration "+v.generation());
        if(v.phase()==RaidMachine.Phase.READING||v.phase()==RaidMachine.Phase.ROUTING) {
            for(var signal:v.signals())lines.add("Circuit "+(signal.task()+1)+" : "+label(signal.action())
                    +" vers "+(char)('A'+signal.receiver())+" | "+(signal.completed()?"valid\u00e9":signal.defenderInterrupted()?"d\u00e9fenseur interrompu":"d\u00e9fenseur actif"));
        }
        return List.copyOf(lines);
    }
    private static String stop(RaidMachine.Stop stop) {
        return switch(stop) {
            case NONE -> "arr\u00eat";case STABILITY -> "stabilit\u00e9 perdue";case TIMEOUT -> "temps limite";
            case ABSENCE -> "groupe absent";case MANUAL -> "abandon";case RESTART -> "red\u00e9marrage";case ACTOR_LOST -> "acteur perdu ou site indisponible";
        };
    }
}
