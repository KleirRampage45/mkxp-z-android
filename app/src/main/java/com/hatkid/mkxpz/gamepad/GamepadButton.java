package com.hatkid.mkxpz.gamepad;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ImageView;

import com.runestone.app.R;

public class GamepadButton extends ImageView
{
    private Drawable mBackgroundDrawable;
    private Drawable mPressedBackgroundDrawable;
    private boolean mPressed = false;
    private Drawable mForegroundDrawable;
    private String mText;
    private int mTextSize = 36;
    private final int mTextColor = Color.argb(222, 255, 255, 255); // 87% white
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int mKey = 0;

    private OnKeyDownListener mOnKeyDownListener = key -> {};
    private OnKeyUpListener mOnKeyUpListener = key -> {};

    // Edit mode drag support
    private boolean mEditMode = false;
    private float mDragStartX, mDragStartY;
    private OnDragListener mOnDragListener;

    public interface OnDragListener
    {
        void onDragStart(GamepadButton view);
        void onDragMove(GamepadButton view, int dx, int dy);
        void onDragEnd(GamepadButton view);
    }

    public void setEditMode(boolean editMode)
    {
        mEditMode = editMode;
        if (!editMode) {
            // Restore visual state
            this.setScaleX(1.0f);
            this.setScaleY(1.0f);
            this.setAlpha(1.0f);
        }
    }

    public void setOnDragListener(OnDragListener listener)
    {
        mOnDragListener = listener;
    }

    public boolean isInEditMode()
    {
        return mEditMode;
    }

    public interface OnKeyDownListener
    {
        void onKeyDown(int key);
    }

    public interface OnKeyUpListener
    {
        void onKeyUp(int key);
    }

    public GamepadButton(Context context)
    {
        super(context);
    }

    public GamepadButton(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        initGamepadButton(attrs, null);
    }

    public GamepadButton(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
        initGamepadButton(attrs, defStyleAttr);
    }

    public GamepadButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes)
    {
        super(context, attrs, defStyleAttr, defStyleRes);
        initGamepadButton(attrs, defStyleAttr);
    }

    public void initGamepadButton(AttributeSet attrs, Integer defStyleAttr)
    {
        if (attrs != null) {
            TypedArray a;

            if (defStyleAttr != null)
                a = getContext().obtainStyledAttributes(attrs, R.styleable.GamepadButton, defStyleAttr, R.style.GamepadButton);
            else
                a = getContext().obtainStyledAttributes(attrs, R.styleable.GamepadButton);

            this.mBackgroundDrawable = a.getDrawable(R.styleable.GamepadButton_bgDrawable);
            this.mForegroundDrawable = a.getDrawable(R.styleable.GamepadButton_fgDrawable);
            this.mText = a.getString(R.styleable.GamepadButton_text);

            a.recycle();
        }
    }

    @Override
    public void setBackground(Drawable drawable)
    {
        this.mBackgroundDrawable = drawable;
        this.mPressedBackgroundDrawable = null;
        initBitmap();
    }

    public void setPressedBackground(Drawable drawable)
    {
        this.mPressedBackgroundDrawable = drawable;
        initBitmap();
    }

    @Override
    public void setBackgroundResource(int drawableResource)
    {
        Drawable backgroundDrawable = getContext().getResources().getDrawable(drawableResource, getContext().getTheme());
        setBackground(backgroundDrawable);
    }

    @Override
    public void setForeground(Drawable drawable)
    {
        this.mForegroundDrawable = drawable;
        this.mText = null;
        initBitmap();
    }

    public void setForegroundResource(int drawableResource)
    {
        Drawable foregroundDrawable = getContext().getResources().getDrawable(drawableResource, getContext().getTheme());
        setForeground(foregroundDrawable);
    }

    public void setForegroundText(String text)
    {
        this.mForegroundDrawable = null;
        this.mText = text;
        initBitmap();
    }

    public void initBitmap()
    {
        if (this.getWidth() < 1 || this.getHeight() < 1)
            return;

        Bitmap bitmap = Bitmap.createBitmap(this.getWidth(), this.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Drawable bg = mPressed && mPressedBackgroundDrawable != null ? mPressedBackgroundDrawable : mBackgroundDrawable;
        if (bg != null) {
            // Preserve aspect ratio: center the drawable in the view bounds
            int bw = bg.getIntrinsicWidth();
            int bh = bg.getIntrinsicHeight();
            if (bw > 0 && bh > 0) {
                float scale = Math.min(
                    (float) canvas.getWidth() / bw,
                    (float) canvas.getHeight() / bh
                );
                int scaledW = Math.round(bw * scale);
                int scaledH = Math.round(bh * scale);
                int offX = (canvas.getWidth() - scaledW) / 2;
                int offY = (canvas.getHeight() - scaledH) / 2;
                bg.setBounds(offX, offY, offX + scaledW, offY + scaledH);
            } else {
                bg.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            }
            bg.draw(canvas);
        }

        int w = (int) Math.round(canvas.getWidth() * 0.9);
        int h = (int) Math.round(canvas.getHeight() * 0.9);
        int pw = (canvas.getWidth() - w) / 2;
        int ph = (canvas.getHeight() - h) / 2;

        if (mForegroundDrawable != null) {
            // Preserve aspect ratio for foreground too
            int fw = mForegroundDrawable.getIntrinsicWidth();
            int fh = mForegroundDrawable.getIntrinsicHeight();
            if (fw > 0 && fh > 0) {
                float fScale = Math.min(
                    (float) w / fw,
                    (float) h / fh
                );
                int scaledFw = Math.round(fw * fScale);
                int scaledFh = Math.round(fh * fScale);
                int fOffX = (canvas.getWidth() - scaledFw) / 2;
                int fOffY = (canvas.getHeight() - scaledFh) / 2;
                mForegroundDrawable.setBounds(fOffX, fOffY, fOffX + scaledFw, fOffY + scaledFh);
            } else {
                mForegroundDrawable.setBounds(pw, ph, w + pw, h + ph);
            }
            mForegroundDrawable.draw(canvas);
        }

        if (mText != null) {
            initPaint();
            int x = (int) canvas.getWidth() / 2;
            int y = (int) canvas.getHeight() / 2;
            canvas.drawText(mText, 0, mText.length(), x, y - mPaint.ascent() / 2f, mPaint);
        }

        this.setImageBitmap(bitmap);
    }

    private void initPaint()
    {
        mPaint.setColor(mTextColor);
        mPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setTextSize(getTextSize());
    }

    private int getTextSize()
    {
        mPaint.setTextSize(mTextSize);

        int mIntrinsicWidth = (int) Math.round(mPaint.measureText(mText, 0, mText.length()) + .5);
        int mIntrinsicHeight = mPaint.getFontMetricsInt(null);

        while (mIntrinsicHeight > this.getHeight() * 0.85 || mIntrinsicWidth > this.getWidth() * 0.85)
        {
            mTextSize -= 2;
            mPaint.setTextSize(mTextSize);
            mIntrinsicWidth = (int) Math.round(mPaint.measureText(mText, 0, mText.length()) + .5);
            mIntrinsicHeight = mPaint.getFontMetricsInt(null);
        }

        return mTextSize;
    }

    public void setOnKeyDownListener(OnKeyDownListener onKeyDownListener)
    {
        mOnKeyDownListener = onKeyDownListener;
    }

    public void setOnKeyUpListener(OnKeyUpListener onKeyUpListener)
    {
        mOnKeyUpListener = onKeyUpListener;
    }

    public void setKey(int key)
    {
        mKey = key;
        initBitmap();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom)
    {
        super.onLayout(changed, left, top, right, bottom);
        initBitmap();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight)
    {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        initBitmap();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent evt)
    {
        if (mEditMode) {
            return handleEditModeTouch(evt);
        }

        switch (evt.getAction())
        {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                mPressed = true;
                // Scale down slightly for press effect
                this.setScaleX(0.94f);
                this.setScaleY(0.94f);
                this.setAlpha(0.92f);
                initBitmap();
                mOnKeyDownListener.onKeyDown(mKey);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                mPressed = false;
                // Restore from press
                this.setScaleX(1.0f);
                this.setScaleY(1.0f);
                this.setAlpha(1.0f);
                initBitmap();
                mOnKeyUpListener.onKeyUp(mKey);
                break;
        }

        return true;
    }

    private boolean handleEditModeTouch(MotionEvent evt)
    {
        switch (evt.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                mDragStartX = evt.getRawX();
                mDragStartY = evt.getRawY();
                // Visual feedback - slightly larger and brighter
                this.setScaleX(1.08f);
                this.setScaleY(1.08f);
                this.setAlpha(0.85f);
                if (mOnDragListener != null) {
                    mOnDragListener.onDragStart(this);
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (mOnDragListener != null) {
                    float rawX = evt.getRawX();
                    float rawY = evt.getRawY();
                    int dx = (int) (rawX - mDragStartX);
                    int dy = (int) (rawY - mDragStartY);
                    mOnDragListener.onDragMove(this, dx, dy);
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                this.setScaleX(0.94f);
                this.setScaleY(0.94f);
                this.setAlpha(1.0f);
                if (mOnDragListener != null) {
                    mOnDragListener.onDragEnd(this);
                }
                return true;
        }
        return true;
    }
}