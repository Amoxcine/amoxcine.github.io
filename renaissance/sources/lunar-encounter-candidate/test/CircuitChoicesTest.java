package fr.ascendant.lunar.encounter;
import java.util.*;
public final class CircuitChoicesTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Circuit choice "+checks);}
    public static void main(String[] args){
        UUID player=UUID.randomUUID(),run=UUID.randomUUID();var token=new RaidMachine.Token(run,7);
        for(var action:RaidMachine.Action.values())for(int receiver=0;receiver<3;receiver++)for(int task=0;task<3;task++){
            String command=CircuitChoices.command(token,task,receiver,action);var words=command.split(" ");
            check(words.length==7&&words[2].equals(run.toString())&&words[3].equals("7"));
            check(Integer.parseInt(words[4])==task+1&&CircuitChoices.receiver(words[5])==receiver&&CircuitChoices.action(words[6])==action);
        }
        for(int size=1;size<=8;size++){
            List<UUID> roster=new ArrayList<>();roster.add(player);for(int i=1;i<size;i++)roster.add(UUID.randomUUID());
            var r=new RaidMachine(UUID.randomUUID(),RaidMachine.Identity.SEALED_GREENHOUSE,
                    new RaidMachine.Group(player,Set.of(player),roster),RaidMachine.Rules.prototype(100,200),size);
            check(CircuitChoices.telegraphs(r.view()).size()<=5);
            r.advance(160,Set.of(player));var submitted=r.token();
            check(r.interact(player,submitted,0,r.view().signals().getFirst().receiver(),CircuitChoices.action("identifier"))==RaidMachine.Result.WRONG_ROUTE);
            check(r.view().errors()==1&&r.view().phase()==RaidMachine.Phase.READING);
            var before=r.view();check(r.interact(player,submitted,0,0,RaidMachine.Action.TRANSFER_CHARGE)==RaidMachine.Result.IGNORED);check(before.equals(r.view()));
            r.advance(160,Set.of(player));int wrong=(r.view().signals().getFirst().receiver()+1)%3;
            check(r.interact(player,r.token(),0,wrong,CircuitChoices.action("transferer"))==RaidMachine.Result.WRONG_ROUTE);
            r.advance(160,Set.of(player));
            check(r.interact(player,r.token(),0,r.view().signals().getFirst().receiver(),CircuitChoices.action("deriver"))==RaidMachine.Result.WRONG_ROUTE);
            check(r.view().phase()==RaidMachine.Phase.FAILED&&r.view().errors()==3);
        }
        for(String invalid:List.of("give","transfer_charge","auto"))try{CircuitChoices.action(invalid);throw new AssertionError("Invalid action accepted");}catch(IllegalArgumentException expected){checks++;}
        for(String invalid:List.of("D","-1","correct"))try{CircuitChoices.receiver(invalid);throw new AssertionError("Invalid receiver accepted");}catch(IllegalArgumentException expected){checks++;}
        System.out.println("PASS circuit choices: "+checks+" assertions; explicit vocabulary, generation, wrong inputs reach unchanged model");
    }
}
