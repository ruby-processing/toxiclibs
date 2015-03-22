package toxi.volume;

public class ReplaceBrush implements BrushMode {

    @Override
    public final float apply(float orig, float brush) {
        return brush;
    }
}
