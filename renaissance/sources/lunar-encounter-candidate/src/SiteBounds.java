package fr.ascendant.lunar.encounter;

public final class SiteBounds {
    private SiteBounds() {}
    public static boolean contains(int cx,int cy,int cz,double x,double y,double z) {
        return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)
            &&Math.abs(x-cx)<=32&&Math.abs(z-cz)<=32&&y>=cy&&y<=cy+8;
    }
}
