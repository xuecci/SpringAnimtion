package com.transsion.springanimation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.FloatArrayEvaluator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.Nullable;

/**
 * Created by xuecci on 2020/5/30.
 * Email: xuecci@gmail.com
 */

public abstract class IconShape {

    // Ratio of the diameter of an normalized circular icon to the actual icon size.
    public static final float ICON_VISIBLE_AREA_FACTOR = 0.92f;

    private static IconShape sInstance = new RoundedSquare(0.5f);
    private static Path sShapePath;
    private static float sNormalizationScale = ICON_VISIBLE_AREA_FACTOR;

    public static final int DEFAULT_PATH_SIZE = 100;

    public static IconShape getShape() {
        return sInstance;
    }

    public static Path getShapePath() {
        if (sShapePath == null) {
            Path p = new Path();
            getShape().addToPath(p, 0, 0, DEFAULT_PATH_SIZE * 0.5f);
            sShapePath = p;
        }
        return sShapePath;
    }

    public static float getNormalizationScale() {
        return sNormalizationScale;
    }

    private SparseArray<TypedValue> mAttrs;

    public boolean enableShapeDetection(){
        return false;
    };

    public abstract void drawShape(Canvas canvas, float offsetX, float offsetY, float radius,
                                   Paint paint);

    public abstract void addToPath(Path path, float offsetX, float offsetY, float radius);

    public abstract <T extends View & ClipPathView> Animator createRevealAnimator(T target,
                                                                                  Rect startRect, Rect endRect, float endRadius, boolean isReversed);

    @Nullable
    public TypedValue getAttrValue(int attr) {
        return mAttrs == null ? null : mAttrs.get(attr);
    }

    /**
     * Abstract shape where the reveal animation is a derivative of a round rect animation
     */
    private static abstract class SimpleRectShape extends IconShape {

        @Override
        public final <T extends View & ClipPathView> Animator createRevealAnimator(T target,
                                                                                   Rect startRect, Rect endRect, float endRadius, boolean isReversed) {
            return new RoundedRectRevealOutlineProvider(
                    getStartRadius(startRect), endRadius, startRect, endRect) {
                @Override
                public boolean shouldRemoveElevationDuringAnimation() {
                    return true;
                }
            }.createRevealAnimator(target, isReversed);
        }

        protected abstract float getStartRadius(Rect startRect);
    }

    /**
     * Abstract shape which draws using {@link Path}
     */
    private static abstract class PathShape extends IconShape {

        private final Path mTmpPath = new Path();

        @Override
        public final void drawShape(Canvas canvas, float offsetX, float offsetY, float radius,
                                    Paint paint) {
            mTmpPath.reset();
            addToPath(mTmpPath, offsetX, offsetY, radius);
            canvas.drawPath(mTmpPath, paint);
        }

        protected abstract AnimatorUpdateListener newUpdateListener(
                Rect startRect, Rect endRect, float endRadius, Path outPath);

        @Override
        public final <T extends View & ClipPathView> Animator createRevealAnimator(T target,
                                                                                   Rect startRect, Rect endRect, float endRadius, boolean isReversed) {
            Path path = new Path();
            AnimatorUpdateListener listener =
                    newUpdateListener(startRect, endRect, endRadius, path);

            ValueAnimator va =
                    isReversed ? ValueAnimator.ofFloat(1f, 0f) : ValueAnimator.ofFloat(0f, 1f);
            va.addListener(new AnimatorListenerAdapter() {
                private ViewOutlineProvider mOldOutlineProvider;

                public void onAnimationStart(Animator animation) {
                    mOldOutlineProvider = target.getOutlineProvider();
                    target.setOutlineProvider(null);

                    target.setTranslationZ(-target.getElevation());
                }

                public void onAnimationEnd(Animator animation) {
                    target.setTranslationZ(0);
                    target.setClipPath(null);
                    target.setOutlineProvider(mOldOutlineProvider);
                }
            });

            va.addUpdateListener((anim) -> {
                path.reset();
                listener.onAnimationUpdate(anim);
                target.setClipPath(path);
            });

            return va;
        }
    }

    public static final class Circle extends SimpleRectShape {

        @Override
        public void drawShape(Canvas canvas, float offsetX, float offsetY, float radius, Paint p) {
            canvas.drawCircle(radius + offsetX, radius + offsetY, radius, p);
        }

        @Override
        public void addToPath(Path path, float offsetX, float offsetY, float radius) {
            path.addCircle(radius + offsetX, radius + offsetY, radius, Path.Direction.CW);
        }

        @Override
        protected float getStartRadius(Rect startRect) {
            return startRect.width() / 2f;
        }

        @Override
        public boolean enableShapeDetection() {
            return true;
        }
    }

    public static class RoundedSquare extends SimpleRectShape {

        /**
         * Ratio of corner radius to half size.
         */
        private final float mRadiusRatio;

        public RoundedSquare(float radiusRatio) {
            mRadiusRatio = radiusRatio;
        }

        @Override
        public void drawShape(Canvas canvas, float offsetX, float offsetY, float radius, Paint p) {
            float cx = radius + offsetX;
            float cy = radius + offsetY;
            float cr = radius * mRadiusRatio;
            canvas.drawRoundRect(cx - radius, cy - radius, cx + radius, cy + radius, cr, cr, p);
        }

        @Override
        public void addToPath(Path path, float offsetX, float offsetY, float radius) {
            float cx = radius + offsetX;
            float cy = radius + offsetY;
            float cr = radius * mRadiusRatio;
            path.addRoundRect(cx - radius, cy - radius, cx + radius, cy + radius, cr, cr,
                    Path.Direction.CW);
        }

        @Override
        protected float getStartRadius(Rect startRect) {
            return (startRect.width() / 2f) * mRadiusRatio;
        }
    }

    public static class TearDrop extends PathShape {

        /**
         * Radio of short radius to large radius, based on the shape options defined in the config.
         */
        private final float mRadiusRatio;
        private final float[] mTempRadii = new float[8];

        public TearDrop(float radiusRatio) {
            mRadiusRatio = radiusRatio;
        }

        @Override
        public void addToPath(Path p, float offsetX, float offsetY, float r1) {
            float r2 = r1 * mRadiusRatio;
            float cx = r1 + offsetX;
            float cy = r1 + offsetY;

            p.addRoundRect(cx - r1, cy - r1, cx + r1, cy + r1, getRadiiArray(r1, r2),
                    Path.Direction.CW);
        }

        private float[] getRadiiArray(float r1, float r2) {
            mTempRadii[0] = mTempRadii [1] = mTempRadii[2] = mTempRadii[3] =
                    mTempRadii[6] = mTempRadii[7] = r1;
            mTempRadii[4] = mTempRadii[5] = r2;
            return mTempRadii;
        }

        @Override
        protected AnimatorUpdateListener newUpdateListener(Rect startRect, Rect endRect,
                                                           float endRadius, Path outPath) {
            float r1 = startRect.width() / 2f;
            float r2 = r1 * mRadiusRatio;

            float[] startValues = new float[] {
                    startRect.left, startRect.top, startRect.right, startRect.bottom, r1, r2};
            float[] endValues = new float[] {
                    endRect.left, endRect.top, endRect.right, endRect.bottom, endRadius, endRadius};

            FloatArrayEvaluator evaluator = new FloatArrayEvaluator(new float[6]);

            return (anim) -> {
                float progress = (Float) anim.getAnimatedValue();
                float[] values = evaluator.evaluate(progress, startValues, endValues);
                outPath.addRoundRect(
                        values[0], values[1], values[2], values[3],
                        getRadiiArray(values[4], values[5]), Path.Direction.CW);
            };
        }
    }

    public static class Squircle extends PathShape {

        /**
         * Radio of radius to circle radius, based on the shape options defined in the config.
         */
        private final float mRadiusRatio;

        public Squircle(float radiusRatio) {
            mRadiusRatio = radiusRatio;
        }

        @Override
        public void addToPath(Path p, float offsetX, float offsetY, float r) {
            float cx = r + offsetX;
            float cy = r + offsetY;
            float control = r - r * mRadiusRatio;

            p.moveTo(cx, cy - r);
            addLeftCurve(cx, cy, r, control, p);
            addRightCurve(cx, cy, r, control, p);
            addLeftCurve(cx, cy, -r, -control, p);
            addRightCurve(cx, cy, -r, -control, p);
            p.close();
        }

        private void addLeftCurve(float cx, float cy, float r, float control, Path path) {
            path.cubicTo(
                    cx - control, cy - r,
                    cx - r, cy - control,
                    cx - r, cy);
        }

        private void addRightCurve(float cx, float cy, float r, float control, Path path) {
            path.cubicTo(
                    cx - r, cy + control,
                    cx - control, cy + r,
                    cx, cy + r);
        }

        @Override
        protected AnimatorUpdateListener newUpdateListener(Rect startRect, Rect endRect,
                                                           float endR, Path outPath) {

            float startCX = startRect.exactCenterX();
            float startCY = startRect.exactCenterY();
            float startR = startRect.width() / 2f;
            float startControl = startR - startR * mRadiusRatio;
            float startHShift = 0;
            float startVShift = 0;

            float endCX = endRect.exactCenterX();
            float endCY = endRect.exactCenterY();
            // Approximate corner circle using bezier curves
            // http://spencermortensen.com/articles/bezier-circle/
            float endControl = endR * 0.551915024494f;
            float endHShift = endRect.width() / 2f - endR;
            float endVShift = endRect.height() / 2f - endR;

            return (anim) -> {
                float progress = (Float) anim.getAnimatedValue();

                float cx = (1 - progress) * startCX + progress * endCX;
                float cy = (1 - progress) * startCY + progress * endCY;
                float r = (1 - progress) * startR + progress * endR;
                float control = (1 - progress) * startControl + progress * endControl;
                float hShift = (1 - progress) * startHShift + progress * endHShift;
                float vShift = (1 - progress) * startVShift + progress * endVShift;

                outPath.moveTo(cx, cy - vShift - r);
                outPath.rLineTo(-hShift, 0);

                addLeftCurve(cx - hShift, cy - vShift, r, control, outPath);
                outPath.rLineTo(0, vShift + vShift);

                addRightCurve(cx - hShift, cy + vShift, r, control, outPath);
                outPath.rLineTo(hShift + hShift, 0);

                addLeftCurve(cx + hShift, cy + vShift, -r, -control, outPath);
                outPath.rLineTo(0, -vShift - vShift);

                addRightCurve(cx + hShift, cy - vShift, -r, -control, outPath);
                outPath.close();
            };
        }
    }
}
