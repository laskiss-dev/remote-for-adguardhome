package hu.laskiss.remoteforadguardhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Kis vonaldiagram a statisztika-csempékhez, kitöltéssel, mint a webes Irányítópulton. */
final class Spark extends View {

    private float[] data = new float[0];
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    Spark(Context c) {
        super(c);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeCap(Paint.Cap.ROUND);
        fill.setStyle(Paint.Style.FILL);
    }

    void set(float[] values, int color, float density) {
        data = values == null ? new float[0] : values;
        line.setColor(color);
        line.setStrokeWidth(2 * density);
        fill.setColor((color & 0x00FFFFFF) | 0x33000000);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        int n = data.length;
        if (n < 2) return;
        float w = getWidth(), h = getHeight();
        float pad = line.getStrokeWidth();
        float max = 0;
        for (float v : data) max = Math.max(max, v);
        if (max <= 0) max = 1;
        path.reset();
        for (int i = 0; i < n; i++) {
            float x = w * i / (n - 1);
            float y = h - pad - (h - 2 * pad) * (data[i] / max);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        c.drawPath(path, line);
        path.lineTo(w, h);
        path.lineTo(0, h);
        path.close();
        c.drawPath(path, fill);
    }
}
