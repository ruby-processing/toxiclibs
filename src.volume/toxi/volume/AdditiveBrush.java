package toxi.volume;

public class AdditiveBrush implements BrushMode {

    @Override
    public final float apply(float orig, float brush) {
        return orig + brush;
    }
}
