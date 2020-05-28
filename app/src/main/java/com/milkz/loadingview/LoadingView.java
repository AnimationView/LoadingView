package com.milkz.loadingview;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * create by zuoqi on 2020/5/13 19:39
 * description: 加载动画通用控件
 */
public class LoadingView extends LinearLayout {

    private ImageView loadImageView;
    private ImageView finishImageView;
    private ImageView errorImageView;
    private TextView tvClock;

    private ScaleImageView finishScaleAnim;
    private ScaleImageView errorScaleAnim;
    private ScaleImageView loadScaleAnim;

    private Drawable loadingDrawable;
    private Drawable finishDrawable;
    private Drawable errorDrawable;

    /**
     * 加载动画一轮次时间 ms
     */
    private int duration = DEFAULT_DURATION;
    private static final int DEFAULT_DURATION = 1000;

    /**
     * 最小等待时间
     */
    private int minLoadingTime = 1000;

    /**
     * 最长等待时间
     */
    private int maxLoadingTime = 2000;

    /**
     * 转圈动画消失时间
     */
    private int animShowTime = 300;

    /**
     * 结束动画显示时间
     */
    private int animDismissTime = 300;

    /**
     * 刷新UI间隔
     */
    private final int clockDurTime = 500;

    private int clock = -1;

    private boolean ifNeedDoImpl = false;

    private int status = STATUS_LOADING;
    public static final int STATUS_LOADING = 0; // 加載中
    public static final int STATUS_FINISH = 1;  // 加载完成
    public static final int STATUS_ERROR = 2;   // 加载失败

    private ObjectAnimator objectAnimator;
    private ObjectAnimator finishScaleAnimator;
    private ObjectAnimator errorScaleAnimator;
    private ObjectAnimator loadScaleAnimator;
    private ObjectAnimator loadScaleAnimator2;
    private ObjectAnimator clockAnimator;


    private LoadViewStatusListener loadViewStatusListener;

    @IntDef({STATUS_LOADING, STATUS_ERROR, STATUS_FINISH})
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.SOURCE)
    @interface STATUS {
    }

    public LoadingView(Context context) {
        this(context, null);
    }

    public LoadingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        if (attrs != null) {
            TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.LoadingView);
            this.loadingDrawable = typedArray.getDrawable(R.styleable.LoadingView_load_view_load_drawable);
            this.finishDrawable = typedArray.getDrawable(R.styleable.LoadingView_load_view_finish_drawable);
            this.errorDrawable = typedArray.getDrawable(R.styleable.LoadingView_load_view_error_drawable);
            this.duration = typedArray.getInt(R.styleable.LoadingView_load_view_duration, DEFAULT_DURATION);
            this.animShowTime = typedArray.getInt(R.styleable.LoadingView_load_view_anim_show_time, 300);
            this.animDismissTime = typedArray.getInt(R.styleable.LoadingView_load_view_anim_dismiss_time, 300);
            typedArray.recycle();
        }

        init(context);
    }

    private void init(Context context) {
        initView(context);
        initAnim();
    }

    private void initView(Context context) {
        FrameLayout mainLayout = new FrameLayout(context);
        mainLayout.setLayoutParams(new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        loadImageView = new ImageView(context);
        loadImageView.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        finishImageView = new ImageView(context);
        finishImageView.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        errorImageView = new ImageView(context);
        errorImageView.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        tvClock = new TextView(context);
        tvClock.setVisibility(GONE);

        loadScaleAnim = new ScaleImageView(loadImageView);
        finishScaleAnim = new ScaleImageView(finishImageView);
        errorScaleAnim = new ScaleImageView(errorImageView);

        setDrawable();

        mainLayout.addView(tvClock);
        mainLayout.addView(finishImageView);
        mainLayout.addView(errorImageView);
        mainLayout.addView(loadImageView);
        this.setGravity(Gravity.CENTER);
        this.addView(mainLayout);
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    private void setDrawable() {
        if (loadingDrawable != null) {
            loadImageView.setImageDrawable(loadingDrawable);
        }
        if (finishDrawable != null) {
            finishImageView.setImageDrawable(finishDrawable);
        }
        if (errorDrawable != null) {
            errorImageView.setImageDrawable(errorDrawable);
        }
    }

    private void initAnim() {
        // 用于计时的动画
        clockAnimator = ObjectAnimator.ofFloat(tvClock, "scaleX", 0f, 359f);
        clockAnimator.setDuration(clockDurTime);
        clockAnimator.setInterpolator(new LinearInterpolator());
        clockAnimator.setRepeatCount(ValueAnimator.INFINITE);
        clockAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                if (ifNeedDoImpl) {
                    status = STATUS_LOADING;
                    changeStatus();
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {

            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (ifNeedDoImpl) {
                    clock = -1;
                    changeStatus();
                    if (loadViewStatusListener != null) {
                        loadViewStatusListener.onEnd();
                    }
                }
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
                if (ifNeedDoImpl && clock != -1) {
                    int nowTime = clock * clockDurTime;
                    if (nowTime < minLoadingTime) {
                        return;
                    }

                    if (maxLoadingTime != -1 && nowTime > maxLoadingTime) {
                        clockAnimator.cancel();
                        status = STATUS_ERROR;
                        return;
                    }

                    if (status != STATUS_LOADING) {
                        clockAnimator.cancel();
                    }
                    clock++;
                }
            }
        });

        objectAnimator = ObjectAnimator.ofFloat(loadImageView, "rotation", 359f, 0f);
        objectAnimator.setDuration(duration);
        objectAnimator.setInterpolator(new LinearInterpolator());
        objectAnimator.setRepeatCount(ValueAnimator.INFINITE);
        objectAnimator.start();

        loadScaleAnimator = ObjectAnimator.ofFloat(loadScaleAnim, "scale", 1f, 0f);
        loadScaleAnimator.setDuration(animShowTime);
        loadScaleAnimator.setInterpolator(new AccelerateInterpolator());
        loadScaleAnimator2 = ObjectAnimator.ofFloat(loadScaleAnim, "scale", 0f, 1f);
        loadScaleAnimator2.setDuration(animShowTime);
        loadScaleAnimator2.setInterpolator(new AccelerateInterpolator());
        finishScaleAnimator = ObjectAnimator.ofFloat(finishScaleAnim, "scale", 0f, 1f);
        finishScaleAnimator.setDuration(animDismissTime);
        finishScaleAnimator.setInterpolator(new AccelerateInterpolator());
        errorScaleAnimator = ObjectAnimator.ofFloat(errorScaleAnim, "scale", 0f, 1f);
        errorScaleAnimator.setDuration(animDismissTime);
        errorScaleAnimator.setInterpolator(new AccelerateInterpolator());
    }

    /**
     * 更改状态
     */
    private void changeStatus() {
        switch (status) {
            case STATUS_LOADING: {
                if (loadScaleAnimator.isRunning()) {
                    loadScaleAnimator.cancel();
                }
                loadScaleAnimator2.start();
                loadImageView.setVisibility(VISIBLE);
                finishImageView.setVisibility(INVISIBLE);
                errorImageView.setVisibility(INVISIBLE);
            }
            break;
            case STATUS_FINISH: {
                loadScaleAnimator.start();
                loadScaleAnimator.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        loadImageView.setVisibility(INVISIBLE);
                        finishImageView.setVisibility(VISIBLE);
                        finishScaleAnimator.start();
                        errorImageView.setVisibility(INVISIBLE);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {

                    }

                    @Override
                    public void onAnimationRepeat(Animator animation) {

                    }
                });
            }
            break;
            case STATUS_ERROR: {
                loadScaleAnimator.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        loadImageView.setVisibility(INVISIBLE);
                        errorScaleAnimator.start();
                        finishImageView.setVisibility(INVISIBLE);
                        errorImageView.setVisibility(VISIBLE);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {

                    }

                    @Override
                    public void onAnimationRepeat(Animator animation) {

                    }
                });
                loadScaleAnimator.start();
            }
            break;
        }
    }

    /**
     * 更换当前动画运行状态
     *
     * @param mode 加载转圈动画{@link #STATUS_LOADING},
     *             加载成功后展示图{@link #STATUS_FINISH},
     *             加载失败后展示图{@link #STATUS_ERROR}
     */
    public void changeMode(@STATUS int mode) {
        this.status = mode;
        changeStatus();
    }

    /**
     * 开始动画
     */
    public void start() {
        clock = 0;
        ifNeedDoImpl = false;
        if (clockAnimator.isRunning()) {
            clockAnimator.cancel();
        }
        ifNeedDoImpl = true;
        clockAnimator.start();
        if (loadViewStatusListener != null) {
            loadViewStatusListener.onStart();
        }
    }

    public void setLoadingDrawable(Drawable loadingDrawable) {
        this.loadingDrawable = loadingDrawable;
        setDrawable();
    }

    public void setFinishDrawable(Drawable finishDrawable) {
        this.finishDrawable = finishDrawable;
        setDrawable();
    }

    public void setErrorDrawable(Drawable errorDrawable) {
        this.errorDrawable = errorDrawable;
        setDrawable();
    }

    public void setLoadViewStatusListener(LoadViewStatusListener loadViewStatusListener) {
        this.loadViewStatusListener = loadViewStatusListener;
    }

    public interface LoadViewStatusListener {
        /**
         * 动画开始时候触发
         */
        void onStart();

        /**
         * 动画结束时候触发
         */
        void onEnd();
    }

    class ScaleImageView {
        private ImageView imageView;

        private float scale;

        public ScaleImageView(ImageView imageView) {
            this.imageView = imageView;
        }

        @SuppressLint("AnimatorKeep")
        public void setScale(float scale) {
            imageView.setScaleX(scale);
            imageView.setScaleY(scale);
        }

        public float getScale() {
            return scale;
        }
    }
}
