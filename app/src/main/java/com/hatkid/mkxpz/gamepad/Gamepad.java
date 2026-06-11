package com.hatkid.mkxpz.gamepad;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.animation.AlphaAnimation;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.runestone.app.R;
import com.hatkid.mkxpz.utils.ViewUtils;

import java.util.HashMap;
import java.util.Map;

public class Gamepad
{
    private GamepadConfig mGamepadConfig = null;
    private boolean mInvisible = false;
    private Context mContext;
    private ViewGroup mParentViewGroup;

    private OnKeyDownListener mOnKeyDownListener = key -> {};
    private OnKeyUpListener mOnKeyUpListener = key -> {};
    private Runnable mOnTapConfirm = null;

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

    public void setOnTapConfirmListener(Runnable r)
    {
        mOnTapConfirm = r;
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

    // All gamepad buttons array for iteration
    private GamepadButton[] allButtons;

    // Edit mode state
    private boolean mEditMode = false;
    private Map<Integer, int[]> mDragStartPositions; // view id -> [leftMargin, topMargin]
    private Map<Integer, int[]> mDefaultPositions; // view id -> [leftMargin, topMargin]
    private OnEditModeListener mOnEditModeListener;

    public interface OnEditModeListener
    {
        void onEditModeChanged(boolean editMode);
    }

    public void setOnEditModeListener(OnEditModeListener listener)
    {
        mOnEditModeListener = listener;
    }

    public boolean isEditMode()
    {
        return mEditMode;
    }

    public void init(GamepadConfig gpadConfig, boolean invisible)
    {
        mGamepadConfig = gpadConfig;
        mInvisible = invisible;
    }

    @SuppressLint("ClickableViewAccessibility")
    public void attachTo(Context context, ViewGroup viewGroup)
    {
        mContext = context;
        mParentViewGroup = viewGroup;

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

        // Collect all buttons for iteration
        allButtons = new GamepadButton[] {
            gpadBtnA, gpadBtnB, gpadBtnC,
            gpadBtnX, gpadBtnY, gpadBtnZ,
            gpadBtnL, gpadBtnR,
            gpadBtnCTRL, gpadBtnALT, gpadBtnSHIFT,
            gpadBtnDASH, gpadBtnCONFIRM, gpadBtnBACK
        };

        // Setup drag listener only for positionable buttons (direct children of RelativeLayout)
        GamepadButton.OnDragListener dragListener = new GamepadButton.OnDragListener() {
            @Override
            public void onDragStart(GamepadButton view)
            {
                // Save the starting position of this drag
                int[] margins = getMarginPosition(view);
                if (margins != null) {
                    if (mDragStartPositions == null) {
                        mDragStartPositions = new HashMap<>();
                    }
                    mDragStartPositions.put(view.getId(), margins);
                }
            }

            @Override
            public void onDragMove(GamepadButton view, int dx, int dy)
            {
                int[] startPos = mDragStartPositions != null ? mDragStartPositions.get(view.getId()) : null;
                if (startPos != null) {
                    int newLeft = Math.max(0, startPos[0] + dx);
                    int newTop = Math.max(0, startPos[1] + dy);
                    // Constrain to parent bounds
                    if (mGamepadLayout != null) {
                        int maxLeft = mGamepadLayout.getWidth() - view.getWidth();
                        int maxTop = mGamepadLayout.getHeight() - view.getHeight();
                        newLeft = Math.min(newLeft, Math.max(0, maxLeft));
                        newTop = Math.min(newTop, Math.max(0, maxTop));
                    }
                    setMarginPosition(view, newLeft, newTop);
                }
            }

            @Override
            public void onDragEnd(GamepadButton view)
            {
                mDragStartPositions = null;
            }
        };

        // Only L, R, and DPad are direct children of the root RelativeLayout
        // and can be freely repositioned via margins. Buttons inside containers
        // (simplified/action/mod layouts) stay in their preset positions.
        if (gpadBtnL != null) gpadBtnL.setOnDragListener(dragListener);
        if (gpadBtnR != null) gpadBtnR.setOnDragListener(dragListener);
        if (gpadDPad != null) gpadDPad.setOnDragListener(dragListener);

        // Setup in-screen gamepad touch listener — simple pass-through
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

        // Save default positions and load saved positions (if any)
        saveDefaultPositions();
        loadPositions();
    }

    public boolean isTouchOnAnyButton(float rawX, float rawY)
    {
        for (GamepadButton btn : allButtons) {
            if (btn == null || btn.getVisibility() != View.VISIBLE) continue;
            int[] loc = new int[2];
            btn.getLocationOnScreen(loc);
            int left = loc[0];
            int top = loc[1];
            int right = left + btn.getWidth();
            int bottom = top + btn.getHeight();
            // Expand hit area slightly for better UX
            int margin = dp(6);
            if (rawX >= left - margin && rawX <= right + margin &&
                rawY >= top - margin && rawY <= bottom + margin) {
                return true;
            }
        }
        // Also check DPad
        if (gpadDPad != null && gpadDPad.getVisibility() == View.VISIBLE) {
            int[] loc = new int[2];
            gpadDPad.getLocationOnScreen(loc);
            int left = loc[0];
            int top = loc[1];
            int right = left + gpadDPad.getWidth();
            int bottom = top + gpadDPad.getHeight();
            int margin = dp(10);
            if (rawX >= left - margin && rawX <= right + margin &&
                rawY >= top - margin && rawY <= bottom + margin) {
                return true;
            }
        }
        return false;
    }

    // ---- Edit Mode ----

    public void setEditMode(boolean editMode)
    {
        if (mEditMode == editMode) return;
        mEditMode = editMode;

        for (GamepadButton btn : allButtons) {
            if (btn != null) {
                btn.setEditMode(editMode);
                // In edit mode, show all buttons regardless of preset
                if (editMode) {
                    btn.setVisibility(View.VISIBLE);
                }
            }
        }
        // In edit mode, show only current preset's layout (not both)
        if (editMode) {
            applyPreset();
        }
        // Apply preset visibility when exiting edit mode
        if (!editMode) {
            applyPreset();
            // Re-apply opacity
            ViewUtils.changeOpacity(mGamepadLayout, mGamepadConfig.opacity);
        }

        if (mOnEditModeListener != null) {
            mOnEditModeListener.onEditModeChanged(editMode);
        }
    }

    // ---- Position Persistence ----

    private void saveDefaultPositions()
    {
        mDefaultPositions = new HashMap<>();
        for (GamepadButton btn : allButtons) {
            if (btn == null) continue;
            int[] margins = getMarginPosition(btn);
            if (margins != null) {
                mDefaultPositions.put(btn.getId(), margins);
            }
        }
        if (gpadDPad != null) {
            int[] margins = getMarginPosition(gpadDPad);
            if (margins != null) {
                mDefaultPositions.put(gpadDPad.getId(), margins);
            }
        }
    }

    private int[] getMarginPosition(View v)
    {
        if (v == null) return null;
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
            return new int[]{mlp.leftMargin, mlp.topMargin};
        }
        return null;
    }

    private boolean setMarginPosition(View v, int left, int top)
    {
        if (v == null) return false;
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
            mlp.leftMargin = left;
            mlp.topMargin = top;
            // If it's a RelativeLayout child, add alignment rules
            if (mlp instanceof RelativeLayout.LayoutParams) {
                RelativeLayout.LayoutParams rlp = (RelativeLayout.LayoutParams) mlp;
                rlp.addRule(RelativeLayout.CENTER_HORIZONTAL, 0);
                rlp.addRule(RelativeLayout.CENTER_VERTICAL, 0);
                rlp.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
                rlp.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
            }
            v.setLayoutParams(lp);
            return true;
        }
        return false;
    }

    private String prefKeyForView(View v, String suffix)
    {
        // Use view ID as stable key - fallback to res name
        try {
            String resName = v.getResources().getResourceEntryName(v.getId());
            return "pos_" + resName + "_" + suffix;
        } catch (Exception e) {
            return "pos_" + v.getId() + "_" + suffix;
        }
    }

    public void savePositions()
    {
        if (mContext == null) return;
        SharedPreferences prefs = mContext.getSharedPreferences("gamepad_positions", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        for (GamepadButton btn : allButtons) {
            if (btn == null) continue;
            int[] margins = getMarginPosition(btn);
            if (margins != null) {
                editor.putInt(prefKeyForView(btn, "left"), margins[0]);
                editor.putInt(prefKeyForView(btn, "top"), margins[1]);
            }
        }
        if (gpadDPad != null) {
            int[] margins = getMarginPosition(gpadDPad);
            if (margins != null) {
                editor.putInt(prefKeyForView(gpadDPad, "left"), margins[0]);
                editor.putInt(prefKeyForView(gpadDPad, "top"), margins[1]);
            }
        }
        editor.apply();
        Toast.makeText(mContext, "Layout saved", Toast.LENGTH_SHORT).show();
    }

    private void loadPositions()
    {
        if (mContext == null) return;
        SharedPreferences prefs = mContext.getSharedPreferences("gamepad_positions", Context.MODE_PRIVATE);

        for (GamepadButton btn : allButtons) {
            if (btn == null) continue;
            String leftKey = prefKeyForView(btn, "left");
            String topKey = prefKeyForView(btn, "top");
            if (prefs.contains(leftKey) && prefs.contains(topKey)) {
                int savedLeft = prefs.getInt(leftKey, 0);
                int savedTop = prefs.getInt(topKey, 0);
                setMarginPosition(btn, savedLeft, savedTop);
            }
        }
        if (gpadDPad != null) {
            String leftKey = prefKeyForView(gpadDPad, "left");
            String topKey = prefKeyForView(gpadDPad, "top");
            if (prefs.contains(leftKey) && prefs.contains(topKey)) {
                int savedLeft = prefs.getInt(leftKey, 0);
                int savedTop = prefs.getInt(topKey, 0);
                setMarginPosition(gpadDPad, savedLeft, savedTop);
            }
        }
    }

    public void resetPositions()
    {
        if (mContext == null) return;

        // Clear saved positions
        SharedPreferences prefs = mContext.getSharedPreferences("gamepad_positions", Context.MODE_PRIVATE);
        prefs.edit().clear().apply();

        // Reset all buttons to default positions from XML
        for (Map.Entry<Integer, int[]> entry : mDefaultPositions.entrySet()) {
            View v = findViewForId(entry.getKey());
            if (v == null) continue;
            int[] defaultPos = entry.getValue();
            setMarginPosition(v, defaultPos[0], defaultPos[1]);
        }

        // Re-apply preset visibility
        applyPreset();

        Toast.makeText(mContext, "Layout reset to default", Toast.LENGTH_SHORT).show();
    }

    private View findViewForId(int id)
    {
        for (GamepadButton btn : allButtons) {
            if (btn != null && btn.getId() == id) return btn;
        }
        if (gpadDPad != null && gpadDPad.getId() == id) return gpadDPad;
        return null;
    }

    public void applyPreset()
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

    public void setInvisible(boolean invisible)
    {
        mInvisible = invisible;
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

    private int dp(int value)
    {
        if (mContext == null) return value;
        return (int) (value * mContext.getResources().getDisplayMetrics().density);
    }

    public RelativeLayout getGamepadLayout()
    {
        return mGamepadLayout;
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
