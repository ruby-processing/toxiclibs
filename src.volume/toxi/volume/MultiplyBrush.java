package toxi.volume;

public class MultiplyBrush implements BrushMode {

    @Override
    public final float apply(float orig, float brush) {
        return orig * brush;
    }

}
