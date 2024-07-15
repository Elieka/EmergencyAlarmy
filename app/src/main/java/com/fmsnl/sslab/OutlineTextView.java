package com.fmsnl.sslab;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatTextView;

public class OutlineTextView extends AppCompatTextView {

    private Paint outlinePaint;
    private int outlineColor;
    private float outlineSize;

    public OutlineTextView(Context context) {
        super(context);
        init(null);
    }

    public OutlineTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public OutlineTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.OutlineTextView);
            outlineColor = a.getColor(R.styleable.OutlineTextView_outlineColor, getResources().getColor(android.R.color.white));
            outlineSize = a.getDimension(R.styleable.OutlineTextView_outlineSize, 10); // 기본 굵기 설정
            a.recycle();
        } else {
            outlineColor = getResources().getColor(android.R.color.white);
            outlineSize =10; // 기본 굵기 설정
        }

        outlinePaint = new Paint();
        outlinePaint.setAntiAlias(true);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setColor(outlineColor);
        outlinePaint.setStrokeWidth(outlineSize);
        outlinePaint.setTypeface(Typeface.DEFAULT_BOLD);
        outlinePaint.setTextSize(getTextSize());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        outlinePaint.setTextSize(getTextSize());
        String text = getText().toString();
        float x = (getWidth() - outlinePaint.measureText(text)) / 2;
        float y = getBaseline();

        canvas.drawText(text, x, y, outlinePaint);
        super.onDraw(canvas);
    }
}
