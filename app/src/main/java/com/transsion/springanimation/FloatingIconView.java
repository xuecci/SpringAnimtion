package com.transsion.springanimation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Property;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * Created by xuecci on 2020/5/30.
 * Email: xuecci@gmail.com
 */

class FloatingIconView extends View implements ClipPathView {
    private static final String TAG = FloatingIconView.class.getSimpleName();
    public static final Interpolator LINEAR = new LinearInterpolator();

    public static final float SHAPE_PROGRESS_DURATION = 0.10f;
    private static final int FADE_DURATION_MS = 200;
    private static final Rect sTmpRect = new Rect();
    private static final RectF sTmpRectF = new RectF();
    private static final Object[] sTmpObjArray = new Object[1];

    // We spring the foreground drawable relative to the icon's movement in the DragLayer.
    // We then use these two factor values to scale the movement of the fg within this view.
    private static final int FG_TRANS_X_FACTOR = 60;
    private static final int FG_TRANS_Y_FACTOR = 75;

    private final SpringAnimation mFgSpringY;
    private float mFgTransY;
    private final SpringAnimation mFgSpringX;
    private float mFgTransX;

    private @Nullable Drawable mBadge;
    private @Nullable Drawable mForeground;
    private @Nullable Drawable mBackground;
    private ValueAnimator mRevealAnimator;
    private final Rect mStartRevealRect = new Rect();
    private final Rect mEndRevealRect = new Rect();
    private Path mClipPath;
    private float mTaskCornerRadius;

    private final Rect mOutline = new Rect();
    private final Rect mFinalDrawableBounds = new Rect();

    private boolean mIsVerticalBarLayout = false;
    private boolean mIsAdaptiveIcon = false;
    private boolean mIsOpening;
    private final boolean mIsRtl = false;
    private final int mBlurSizeOutline = 3;

    private static final FloatPropertyCompat<FloatingIconView> mFgTransYProperty
            = new FloatPropertyCompat<FloatingIconView>("FloatingViewFgTransY") {
        @Override
        public float getValue(FloatingIconView view) {
            return view.mFgTransY;
        }

        @Override
        public void setValue(FloatingIconView view, float transY) {
            view.mFgTransY = transY;
            view.invalidate();
        }
    };

    private static final FloatPropertyCompat<FloatingIconView> mFgTransXProperty
            = new FloatPropertyCompat<FloatingIconView>("FloatingViewFgTransX") {
        @Override
        public float getValue(FloatingIconView view) {
            return view.mFgTransX;
        }

        @Override
        public void setValue(FloatingIconView view, float transX) {
            view.mFgTransX = transX;
            view.invalidate();
        }
    };

    public static final Property<Drawable, Integer> DRAWABLE_ALPHA =
            new Property<Drawable, Integer>(Integer.TYPE, "drawableAlpha") {
                @Override
                public Integer get(Drawable drawable) {
                    return drawable.getAlpha();
                }

                @Override
                public void set(Drawable drawable, Integer alpha) {
                    drawable.setAlpha(alpha);
                }
            };

    public FloatingIconView(Context context) {
        this(context,null);
    }

    public FloatingIconView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FloatingIconView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr,0);
    }

    public FloatingIconView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        mFgSpringX = new SpringAnimation(this, mFgTransXProperty)
                .setSpring(new SpringForce()
                        .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                        .setStiffness(SpringForce.STIFFNESS_LOW));
        mFgSpringY = new SpringAnimation(this, mFgTransYProperty)
                .setSpring(new SpringForce()
                        .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                        .setStiffness(SpringForce.STIFFNESS_LOW));
    }

    /**
     * Positions this view to match the size and location of {@param rect}.
     * @param alpha The alpha to set this view.
     * @param progress A value from [0, 1] that represents the animation progress.
     * @param shapeProgressStart The progress value at which to start the shape reveal.
     * @param cornerRadius The corner radius of {@param rect}.
     */
    public void update(RectF rect, float alpha, float progress, float shapeProgressStart,
                       float cornerRadius, boolean isOpening) {
        setAlpha(alpha);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        float dX = mIsRtl
                ? rect.left
                - (/*mLauncher.getDeviceProfile().widthPx*/1080 - lp.getMarginStart() - lp.width)
                : rect.left - lp.getMarginStart();
        float dY = rect.top - lp.topMargin;
        setTranslationX(dX);
        setTranslationY(dY);

        float minSize = Math.min(lp.width, lp.height);
        float scaleX = rect.width() / minSize;
        float scaleY = rect.height() / minSize;
        float scale = Math.max(1f, Math.min(scaleX, scaleY));

        setPivotX(0);
        setPivotY(0);
        setScaleX(scale);
        setScaleY(scale);

        // shapeRevealProgress = 1 when progress = shapeProgressStart + SHAPE_PROGRESS_DURATION
        float toMax = isOpening ? 1 / SHAPE_PROGRESS_DURATION : 1f;
        float shapeRevealProgress = boundToRange(mapToRange(
                Math.max(shapeProgressStart, progress), shapeProgressStart, 1f, 0, toMax,
                LINEAR), 0, 1);

        if (mIsVerticalBarLayout) {
            mOutline.right = (int) (rect.width() / scale);
        } else {
            mOutline.bottom = (int) (rect.height() / scale);
        }

        mTaskCornerRadius = cornerRadius / scale;
        if (mIsAdaptiveIcon) {
            if (!isOpening && progress >= shapeProgressStart) {
                if (mRevealAnimator == null) {
                    mRevealAnimator = (ValueAnimator) IconShape.getShape().createRevealAnimator(
                            this, mStartRevealRect, mOutline, mTaskCornerRadius, !isOpening);
                    mRevealAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mRevealAnimator = null;
                        }
                    });
                    mRevealAnimator.start();
                    // We pause here so we can set the current fraction ourselves.
                    mRevealAnimator.pause();
                }
                mRevealAnimator.setCurrentFraction(shapeRevealProgress);
            }

            float drawableScale = (mIsVerticalBarLayout ? mOutline.width() : mOutline.height())
                    / minSize;
            setBackgroundDrawableBounds(drawableScale);
            if (isOpening) {
                // Center align foreground
                int height = mFinalDrawableBounds.height();
                int width = mFinalDrawableBounds.width();
                int diffY = mIsVerticalBarLayout ? 0
                        : (int) (((height * drawableScale) - height) / 2);
                int diffX = mIsVerticalBarLayout ? (int) (((width * drawableScale) - width) / 2)
                        : 0;
                sTmpRect.set(mFinalDrawableBounds);
                sTmpRect.offset(diffX, diffY);
                mForeground.setBounds(sTmpRect);
            } else {
                // Spring the foreground relative to the icon's movement within the DragLayer.
                int diffX = (int) (dX / 1080/*mLauncher.getDeviceProfile().availableWidthPx*/
                        * FG_TRANS_X_FACTOR);
                int diffY = (int) (dY / 1920/*mLauncher.getDeviceProfile().availableHeightPx*/
                        * FG_TRANS_Y_FACTOR);

                mFgSpringX.animateToFinalPosition(diffX);
                mFgSpringY.animateToFinalPosition(diffY);
            }
        }
        invalidate();
        invalidateOutline();
    }

    private void setBackgroundDrawableBounds(float scale) {
        sTmpRect.set(mFinalDrawableBounds);
        scaleRectAboutCenter(sTmpRect, scale);
        // Since the drawable is at the top of the view, we need to offset to keep it centered.
        if (mIsVerticalBarLayout) {
            sTmpRect.offsetTo((int) (mFinalDrawableBounds.left * scale), sTmpRect.top);
        } else {
            sTmpRect.offsetTo(sTmpRect.left, (int) (mFinalDrawableBounds.top * scale));
        }
        mBackground.setBounds(sTmpRect);
    }

    @Override
    public void setClipPath(Path clipPath) {
        mClipPath = clipPath;
        invalidate();
    }

    @Override
    public void draw(Canvas canvas) {
        int count = canvas.save();
        if (mClipPath != null) {
            canvas.clipPath(mClipPath);
        }
        super.draw(canvas);
        if (mBackground != null) {
            mBackground.draw(canvas);
        }
        if (mForeground != null) {
            int count2 = canvas.save();
            canvas.translate(mFgTransX, mFgTransY);
            mForeground.draw(canvas);
            canvas.restoreToCount(count2);
        }
        if (mBadge != null) {
            mBadge.draw(canvas);
        }
        canvas.restoreToCount(count);
    }

    /**
     * Maps t from one range to another range.
     * @param t The value to map.
     * @param fromMin The lower bound of the range that t is being mapped from.
     * @param fromMax The upper bound of the range that t is being mapped from.
     * @param toMin The lower bound of the range that t is being mapped to.
     * @param toMax The upper bound of the range that t is being mapped to.
     * @return The mapped value of t.
     */
    public static float mapToRange(float t, float fromMin, float fromMax, float toMin, float toMax,
                                   Interpolator interpolator) {
        if (fromMin == fromMax || toMin == toMax) {
            Log.e(TAG, "mapToRange: range has 0 length");
            return toMin;
        }
        float progress = getProgress(t, fromMin, fromMax);
        return mapRange(interpolator.getInterpolation(progress), toMin, toMax);
    }

    public static float getProgress(float current, float min, float max) {
        return Math.abs(current - min) / Math.abs(max - min);
    }

    public static float mapRange(float value, float min, float max) {
        return min + (value * (max - min));
    }

    /**
     * Ensures that a value is within given bounds. Specifically:
     * If value is less than lowerBound, return lowerBound; else if value is greater than upperBound,
     * return upperBound; else return value unchanged.
     */
    public static int boundToRange(int value, int lowerBound, int upperBound) {
        return Math.max(lowerBound, Math.min(value, upperBound));
    }

    /**
     * @see #boundToRange(int, int, int).
     */
    public static float boundToRange(float value, float lowerBound, float upperBound) {
        return Math.max(lowerBound, Math.min(value, upperBound));
    }

    /**
     * @see #boundToRange(int, int, int).
     */
    public static long boundToRange(long value, long lowerBound, long upperBound) {
        return Math.max(lowerBound, Math.min(value, upperBound));
    }

    public static void scaleRectAboutCenter(Rect r, float scale) {
        if (scale != 1.0f) {
            int cx = r.centerX();
            int cy = r.centerY();
            r.offset(-cx, -cy);
            scaleRect(r, scale);
            r.offset(cx, cy);
        }
    }

    public static void scaleRect(Rect r, float scale) {
        if (scale != 1.0f) {
            r.left = (int) (r.left * scale + 0.5f);
            r.top = (int) (r.top * scale + 0.5f);
            r.right = (int) (r.right * scale + 0.5f);
            r.bottom = (int) (r.bottom * scale + 0.5f);
        }
    }

    /**
     * Sets the drawables of the {@param originalView} onto this view.
     *
     * @param originalView The View that the FloatingIconView will replace.
     * @param drawable The drawable of the original view.
     * @param badge The badge of the original view.
     * @param iconOffset The amount of offset needed to match this view with the original view.
     */
    public void setIcon(@Nullable Drawable drawable, @Nullable Drawable badge,
                         int iconOffset) {
        mBadge = badge;

        mIsAdaptiveIcon = drawable instanceof AdaptiveIconDrawable;
        if (mIsAdaptiveIcon) {
            Log.d("Ryan","Adaptive Icon");
            boolean isFolderIcon = false;/*drawable instanceof FolderAdaptiveIcon;*/

            AdaptiveIconDrawable adaptiveIcon = (AdaptiveIconDrawable) drawable;
            Drawable background = adaptiveIcon.getBackground();
            if (background == null) {
                background = new ColorDrawable(Color.TRANSPARENT);
            }
            mBackground = background;
            Drawable foreground = adaptiveIcon.getForeground();
            if (foreground == null) {
                foreground = new ColorDrawable(Color.TRANSPARENT);
            }
            mForeground = foreground;

            final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
            final int originalHeight = lp.height;
            final int originalWidth = lp.width;

            int blurMargin = mBlurSizeOutline / 2;
            mFinalDrawableBounds.set(0, 0, originalWidth, originalHeight);

            if (!isFolderIcon) {
                mFinalDrawableBounds.inset(iconOffset - blurMargin, iconOffset - blurMargin);
            }
            mForeground.setBounds(mFinalDrawableBounds);
            mBackground.setBounds(mFinalDrawableBounds);

            mStartRevealRect.set(0, 0, originalWidth, originalHeight);

            if (mBadge != null) {
                mBadge.setBounds(mStartRevealRect);
                if (!mIsOpening && !isFolderIcon) {
                    DRAWABLE_ALPHA.set(mBadge, 0);
                }
            }

            scaleRectAboutCenter(mStartRevealRect,
                        IconShape.getNormalizationScale());

            float aspectRatio = 1920/1080;/*mLauncher.getDeviceProfile().aspectRatio;*/
            if (mIsVerticalBarLayout) {
                lp.width = (int) Math.max(lp.width, lp.height * aspectRatio);
            } else {
                lp.height = (int) Math.max(lp.height, lp.width * aspectRatio);
            }

            int left = mIsRtl
                    ? 1080/*mLauncher.getDeviceProfile().widthPx*/ - lp.getMarginStart() - lp.width
                    : lp.leftMargin;
            layout(left, lp.topMargin, left + lp.width, lp.topMargin + lp.height);

            float scale = Math.max((float) lp.height / originalHeight,
                    (float) lp.width / originalWidth);
            float bgDrawableStartScale;
            if (mIsOpening) {
                bgDrawableStartScale = 1f;
                mOutline.set(0, 0, originalWidth, originalHeight);
            } else {
                bgDrawableStartScale = scale;
                mOutline.set(0, 0, lp.width, lp.height);
            }
            setBackgroundDrawableBounds(bgDrawableStartScale);
            mEndRevealRect.set(0, 0, lp.width, lp.height);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(mOutline, mTaskCornerRadius);
                }
            });
            setClipToOutline(true);
        } else {
            Log.d("Ryan","Normal Icon");
            setBackground(drawable);
            setClipToOutline(false);
        }
        invalidate();
        invalidateOutline();
    }
}
