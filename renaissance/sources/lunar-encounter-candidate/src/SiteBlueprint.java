package fr.ascendant.lunar.encounter;

import java.util.*;

/** Pure preflight: reads exactly 65*65*5 cells, never changes the supplied world. */
public final class SiteBlueprint {
    public enum Cell { AIR, SOLID_FLOOR, COPPER, OBSTRUCTION }
    public enum Material { BASALT, BORDER, LETTER, CORE, COPPER }
    public record Offset(int x,int y,int z) {}
    public record Placement(Offset position,Material material) {}
    @FunctionalInterface public interface Reader { Cell read(int x,int y,int z); }
    private SiteBlueprint() {}
    public static boolean terminal(int x,int z) {return (x==-12&&z==0)||(x==12&&z==0)||(x==0&&z==12);}
    public static List<Placement> plan(Reader reader) {
        List<Placement> plan=new ArrayList<>();
        for(int x=-32;x<=32;x++)for(int z=-32;z<=32;z++) {
            Cell floor=Objects.requireNonNull(reader.read(x,-1,z));
            if(floor==Cell.AIR)plan.add(new Placement(new Offset(x,-1,z),floor(x,z)));
            else if(floor!=Cell.SOLID_FLOOR&&floor!=Cell.COPPER)throw new IllegalStateException("Unsafe floor at offset "+x+",-1,"+z);
            for(int y=0;y<=3;y++) {
                Cell cell=Objects.requireNonNull(reader.read(x,y,z));
                if(y==0&&terminal(x,z)) {
                    if(cell==Cell.AIR)plan.add(new Placement(new Offset(x,y,z),Material.COPPER));
                    else if(cell!=Cell.COPPER)throw new IllegalStateException("Receiver obstructed at offset "+x+",0,"+z);
                } else if(cell!=Cell.AIR)throw new IllegalStateException("Clearance obstructed at offset "+x+","+y+","+z);
            }
        }
        if(plan.size()>4228)throw new IllegalStateException("Blueprint bound exceeded");
        return List.copyOf(plan);
    }
    private static Material floor(int x,int z) {
        if(Math.abs(x)==32||Math.abs(z)==32)return Material.BORDER;
        if(Math.abs(x)<=2&&Math.abs(z)<=2)return Material.CORE;
        boolean a=z>=-7&&z<=-3&&(x==-13||x==-11||(x==-12&&(z==-7||z==-5)));
        boolean b=z>=-7&&z<=-3&&(x==11||((x==12||x==13)&&(z==-7||z==-5||z==-3))||(x==13&&(z==-6||z==-4)));
        boolean c=z>=16&&z<=20&&(x==-1||((x==0||x==1)&&(z==16||z==20)));
        return a||b||c?Material.LETTER:Material.BASALT;
    }
}
