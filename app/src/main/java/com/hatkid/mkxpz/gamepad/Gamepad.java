package com.hatkid.mkxpz.gamepad;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.animation.AlphaAnimation;
import android.widget.RelativeLayout;

import com.runestone.app.R;
import com.hatkid.mkxpz.utils.ViewUtils;

public class Gamepad
{
    private GamepadConfig mGamepadConfig = null;
    private boolean mInvisible = false;

    private OnKeyDownListener mOnKeyDownListener = key -> {};
    private OnKeyUpListener mOnKeyUpListener = key -> {};

    public interface OnKeyDownListener
    {
        void onKeyDown(int key);
    }

    public interface OnKeyUpListener
    {
        void onKeyUp(int key);
    }

    public void setOnKeyDownListener(OnKeyDownListener onKeyDownListener)
    {
        mOnKeyDownListener = onKeyDownListener;
    }

    public void setOnKeyUpListener(OnKeyUpListener onKeyUpListener)
    {
        mOnKeyUpListener = onKeyUpListener;
    }

    private RelativeLayout mGamepadLayout;

    // Gamepad buttons
    private GamepadButton gpadBtnA;
    private GamepadButton gpadBtnB;
    private GamepadButton gpadBtnC;
    private GamepadButton gpadBtnX;
    private GamepadButton gpadBtnY;
    private GamepadButton gpadBtnZ;
    private GamepadButton gpadBtnL;
    private GamepadButton gpadBtnR;
    private GamepadButton gpadBtnCTRL;
    private GamepadButton gpadBtnALT;
    private GamepadButton gpadBtnSHIFT;
    private GamepadButton gpadBtnDASH;
    private GamepadButton gpadBtnCONFIRM;
    private GamepadButton gpadBtnBACK;
    private View buttonsActionLayout;
    private View buttonsModLayout;
    private View buttonsSimplifiedLayout;
    private GamepadDPad gpadDPad;

    public void init(GamepadConfig gpadConfig, boolean invisible)
    {
        mGamepadConfig = gpadConfig;
        mInvisible = invisible;
    }

    @SuppressLint("ClickableViewAccessibility")
    public void attachTo(Context context, ViewGroup viewGroup)
    {
        // Setup layout of in-screen gamepad
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ViewGroup layout = (ViewGroup) inflater.inflate(R.layout.gamepad_layout, viewGroup);
        mGamepadLayout = layout.findViewById(R.id.gamepad_layout);

        if (mInvisible) {
            mGamepadLayout.setAlpha(0);
        }

        // Setup D-Pad and buttons
        gpadDPad = layout.findViewById(R.id.dpad);
        gpadBtnA = layout.findViewById(R.id.button_A);
        gpadBtnB = layout.findViewById(R.id.button_B);
        gpadBtnC = layout.findViewById(R.id.button_C);
        gpadBtnX = layout.findViewById(R.id.button_X);
        gpadBtnY = layout.findViewById(R.id.button_Y);
        gpadBtnZ = layout.findViewById(R.id.button_Z);
        gpadBtnL = layout.findViewById(R.id.button_L);
        gpadBtnR = layout.findViewById(R.id.button_R);
        gpadBtnCTRL = layout.findViewById(R.id.button_CTRL);
        gpadBtnALT = layout.findViewById(R.id.button_ALT);
        gpadBtnSHIFT = layout.findViewById(R.id.button_SHIFT);
        gpadBtnDASH = layout.findViewById(R.id.button_DASH);
        gpadBtnCONFIRM = layout.findViewById(R.id.button_CONFIRM);
        gpadBtnBACK = layout.findViewById(R.id.button_BACK);
        buttonsActionLayout = layout.findViewById(R.id.buttons_action_layout);
        buttonsModLayout = layout.findViewById(R.id.buttons_mod_layout);
        buttonsSimplifiedLayout = layout.findViewById(R.id.buttons_simplified_layout);

        // Setup in-screen gamepad listeners
        mGamepadLayout.setOnTouchListener((view, motionEvent) -> false);
        gpadDPad.setOnKeyDownListener(key -> mOnKeyDownListener.onKeyDown(key));
        gpadDPad.setOnKeyUpListener(key -> mOnKeyUpListener.onKeyUp(key));

        // Configure gamepad
        gpadDPad.isDiagonal = mGamepadConfig.diagonalMovement;

        // Setup buttons for gamepad
        initGamepadButtons();
        applyPreset();

        // Apply scale and opacity from gamepad config
        ViewUtils.resize(mGamepadLayout, mGamepadConfig.scale);
        ViewUtils.changeOpacity(mGamepadLayout, mGamepadConfig.opacity);
    }

    private void applyPreset()
    {
        boolean isFull = "FULL".equals(mGamepadConfig.preset);
        if (buttonsActionLayout != null) {
            buttonsActionLayout.setVisibility(isFull ? View.VISIBLE : View.GONE);
        }
        if (buttonsModLayout != null) {
            buttonsModLayout.setVisibility(isFull ? View.VISIBLE : View.GONE);
        }
        if (buttonsSimplifiedLayout != null) {
            buttonsSimplifiedLayout.setVisibility(isFull ? View.GONE : View.VISIBLE);
        }
    }

    private void setGamepadButtonKey(GamepadButton gpadBtn, Integer keycode, String label)
    {
        if (gpadBtn == null) return;
        String btnLabel = label;
        if (btnLabel == null || btnLabel.isEmpty()) {
            btnLabel = KeyEvent.keyCodeToString(keycode)
                .replace("KEYCODE_", "")
                .replace("_LEFT", "")
                .replace("_RIGHT", "");
        }
        gpadBtn.setForegroundText(btnLabel);
        gpadBtn.setKey(keycode);
        gpadBtn.setOnKeyDownListener(key -> mOnKeyDownListener.onKeyDown(key));
        gpadBtn.setOnKeyUpListener(key -> mOnKeyUpListener.onKeyUp(key));
    }

    private void setGamepadButtonKey(GamepadButton gpadBtn, Integer keycode)
    {
        setGamepadButtonKey(gpadBtn, keycode, null);
    }

    public void showView()
    {
        if (mGamepadLayout != null) {
            if (mGamepadLayout.getAlpha() == 0)
                mGamepadLayout.setAlpha(1);

            AlphaAnimation anim = new AlphaAnimation(0.0f, 1.0f);
            anim.setDuration(250);
            anim.setFillAfter(true);
            mGamepadLayout.startAnimation(anim);
        }
    }

    public void hideView()
    {
        if (mGamepadLayout != null) {
            AlphaAnimation anim = new AlphaAnimation(1.0f, 0.0f);
            anim.setDuration(500);
            anim.setFillAfter(true);
            mGamepadLayout.startAnimation(anim);
        }
    }

    private void initGamepadButtons()
    {
        // Simplified mode labels (action-based)
        setGamepadButtonKey(gpadBtnCONFIRM, mGamepadConfig.keycodeA, "✓");
        setGamepadButtonKey(gpadBtnBACK, mGamepadConfig.keycodeB, "◁");
        setGamepadButtonKey(gpadBtnDASH, mGamepadConfig.keycodeSHIFT, "Dash");

        // Full mode labels (key-based)
        setGamepadButtonKey(gpadBtnA, mGamepadConfig.keycodeA, "Confirm");
        setGamepadButtonKey(gpadBtnB, mGamepadConfig.keycodeB, "Back");
        setGamepadButtonKey(gpadBtnC, mGamepadConfig.keycodeC, "Dash");
        setGamepadButtonKey(gpadBtnX, mGamepadConfig.keycodeX, "A");
        setGamepadButtonKey(gpadBtnY, mGamepadConfig.keycodeY, "S");
        setGamepadButtonKey(gpadBtnZ, mGamepadConfig.keycodeZ, "D");

        // Shoulder buttons (both modes)
        setGamepadButtonKey(gpadBtnL, mGamepadConfig.keycodeL, "L");
        setGamepadButtonKey(gpadBtnR, mGamepadConfig.keycodeR, "R");

        // Modifiers (full mode only)
        setGamepadButtonKey(gpadBtnCTRL, mGamepadConfig.keycodeCTRL, "Ctrl");
        setGamepadButtonKey(gpadBtnALT, mGamepadConfig.keycodeALT, "Alt");
        setGamepadButtonKey(gpadBtnSHIFT, mGamepadConfig.keycodeSHIFT, "Shift");
    }

    public boolean processGamepadEvent(KeyEvent evt)
    {
        InputDevice device = evt.getDevice();

        if (device == null)
            return false;

        int sources = device.getSources();

        if (
            ((sources & InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD) &&
            ((sources & InputDevice.SOURCE_DPAD) != InputDevice.SOURCE_DPAD)
        )
            return false;

        int keycode = evt.getKeyCode();

        switch (evt.getAction())
        {
            case MotionEvent.ACTION_DOWN:
                mOnKeyDownListener.onKeyDown(keycode);
                break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                mOnKeyUpListener.onKeyUp(keycode);
                break;
        }

        return true;
    }

    public boolean processDPadEvent(MotionEvent evt)
    {
        InputDevice device = evt.getDevice();

        if (device == null)
            return false;

        int sources = device.getSources();

        if (((sources & InputDevice.SOURCE_DPAD) != InputDevice.SOURCE_DPAD))
            return false;

        float xAxis = evt.getAxisValue(MotionEvent.AXIS_HAT_X);
        float yAxis = evt.getAxisValue(MotionEvent.AXIS_HAT_Y);

        Integer keycode = null;

        if (Float.compare(yAxis, -1.0f) == 0)
            keycode = KeyEvent.KEYCODE_DPAD_UP;
        else if (Float.compare(yAxis, 1.0f) == 0)
            keycode = KeyEvent.KEYCODE_DPAD_DOWN;
        else if (Float.compare(xAxis, -1.0f) == 0)
            keycode = KeyEvent.KEYCODE_DPAD_LEFT;
        else if (Float.compare(xAxis, 1.0f) == 0)
            keycode = KeyEvent.KEYCODE_DPAD_RIGHT;

        if (keycode == null)
            return false;

        switch (evt.getAction())
        {
            case MotionEvent.ACTION_DOWN:
                mOnKeyDownListener.onKeyDown(keycode);
                break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                mOnKeyUpListener.onKeyUp(keycode);
                break;
        }

        return true;
    }
}