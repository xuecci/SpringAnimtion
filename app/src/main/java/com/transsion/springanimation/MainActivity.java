package com.transsion.springanimation;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Drawable drawable = getDrawable(R.mipmap.ic_launcher);
        FloatingIconView floatingIconView = findViewById(R.id.icon);
        floatingIconView.setIcon(drawable,null, 3);

        RectF startRect = new RectF();
        startRect.set(0,0,1080,1920);
        RectF targetRect = new RectF();
        //targetRect.set(startRect);
        int[] point = new int[2];
        floatingIconView.getLocationOnScreen(point);
        targetRect.set(point[0],point[1],point[0]+floatingIconView.getWidth(),point[1]+floatingIconView.getHeight());
        Log.d("Ryan","Rect:"+startRect+" to "+ targetRect);
        findViewById(R.id.spring).setOnClickListener(v -> {
            RectFSpringAnim anim = new RectFSpringAnim(startRect, targetRect, getResources());

            // End on a "round-enough" radius so that the shape reveal doesn't have to do too much
            // rounding at the end of the animation.
            float startRadius = 0f;
            float endRadius = targetRect.width() / 2f;
            // We want the window alpha to be 0 once this threshold is met, so that the
            // FolderIconView can be seen morphing into the icon shape.
            float SHAPE_PROGRESS_DURATION = 0.10f;
            final float windowAlphaThreshold = 1f - SHAPE_PROGRESS_DURATION;
            anim.addOnUpdateListener(new RectFSpringAnim.OnUpdateListener() {

                @Override
                public void onUpdate(RectF currentRect, float progress) {

                    float cornerRadius = endRadius * progress + startRadius
                            * (1f - progress);

                    floatingIconView.update(currentRect, 1f, progress,
                            windowAlphaThreshold, cornerRadius,
                            false);
                }

                @Override
                public void onCancel() {
                }
            });
            PointF pointF = new PointF();
            pointF.set(0,-30);
            anim.start(pointF);
        });
    }
}