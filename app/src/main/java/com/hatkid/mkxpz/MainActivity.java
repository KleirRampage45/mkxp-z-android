package com.hatkid.mkxpz;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.os.storage.StorageManager;
import android.os.storage.OnObbStateChangeListener;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import android.util.DisplayMetrics;
import java.util.Locale;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.libsdl.app.SDLActivity;
import com.runestone.app.input.TouchOverlayView;
import com.hatkid.mkxpz.gamepad.Gamepad;
import com.hatkid.mkxpz.gamepad.GamepadConfig;
import com.runestone.app.R;
import kotlin.Unit;

public class MainActivity extends SDLActivity
{
    private static final String TAG = "mkxp-z[Activity]";
    public static final String EXTRA_GAME_PATH = "com.runestone.app.extra.GAME_PATH";
    private static final String GAME_PATH_DEFAULT = Environment.getExternalStorageDirectory() + "/mkxp-z";
    private static String GAME_PATH = GAME_PATH_DEFAULT;
    private static String OBB_MAIN_FILENAME;
    private static boolean DEBUG = false;

    // Settings extras from the Runestone launcher.
    public static final String EXTRA_LAYOUT_MODE     = "com.runestone.app.extra.LAYOUT_MODE";
    public static final String EXTRA_TOUCH_OPACITY   = "com.runestone.app.extra.TOUCH_OPACITY";
    public static final String EXTRA_TOUCH_SCALE     = "com.runestone.app.extra.TOUCH_SCALE";
    public static final String EXTRA_HAPTICS_ENABLED = "com.runestone.app.extra.HAPTICS_ENABLED";
    public static final String EXTRA_HAPTIC_INTENSITY = "com.runestone.app.extra.HAPTIC_INTENSITY";
    public static final String EXTRA_HIDE_VIRTUAL_GAMEPAD = "com.runestone.app.extra.HIDE_VIRTUAL_GAMEPAD";
    public static final String EXTRA_TEXT_SCALE      = "com.runestone.app.extra.TEXT_SCALE";
    public static final String EXTRA_INTEGER_SCALING = "com.runestone.app.extra.INTEGER_SCALING";
    public static final String EXTRA_DISPLAY_CUTOUT_MODE = "com.runestone.app.extra.DISPLAY_CUTOUT_MODE";
    public static final String EXTRA_CONTROLLER_HOME_SHORTCUT = "com.runestone.app.extra.CONTROLLER_HOME_SHORTCUT";
    public static final String EXTRA_CONTROLLER_PRESET = "com.runestone.app.extra.CONTROLLER_PRESET";

    // Layout modes (match RunnerSettings.LayoutMode)
    private static final String MODE_LANDSCAPE      = "LANDSCAPE";
    private static final String MODE_PORTRAIT_CONSOLE = "PORTRAIT_CONSOLE";
    private static final String MODE_GAMEPAD        = "GAMEPAD";
    private static final String CUTOUT_EDGE_TO_EDGE = "EDGE_TO_EDGE";

    protected boolean mStarted = false;

    protected static Handler mMainHandler;
    protected static StorageManager mStorageManager;
    protected static Vibrator mVibrator;

    protected static TextView tvFps;

    // In-screen gamepad
    private final Gamepad mGamepad = new Gamepad();
    private boolean mGamepadInvisible = false;
    private GamepadConfig mGamepadConfig;

    // Layout mode from launcher
    private String mLayoutMode = MODE_LANDSCAPE;
    private String mDisplayCutoutMode = "SAFE_AREA";
    private String mControllerHomeShortcut = "L2_R2";
    private boolean mHideVirtualGamepad = false;
    private final Set<Integer> mPressedControllerKeys = new HashSet<>();
    private boolean mTriggerHomeComboDown = false;

    // Edit mode for gamepad layout
    private boolean mEditMode = false;
    private View mEditModeOverlay;

    // Tap-to-skip: how long since last gamepad button touch

    // For portrait console split layout
    private FrameLayout mGamepadContainer;
    private TouchOverlayView mPortraitControls;
    private boolean mIsPortraitConsole = false;
    private View mRuntimeActionsOverlay;

    private void runSDLThread()
    {
        if (!mStarted) {
            Log.i(TAG, "Game path: " + GAME_PATH);
            Log.i(TAG, "Layout mode: " + mLayoutMode);
        }

        mStarted = true;

        if (mHasMultiWindow) {
            resumeNativeThread();
        }
    }

    OnObbStateChangeListener obbListener = new OnObbStateChangeListener()
    {
        @Override
        public void onObbStateChange(String path, int state)
        {
            super.onObbStateChange(path, state);
            Log.v(TAG, "OBB state of " + path + " changed to " + state);
            switch (state)
            {
                case OnObbStateChangeListener.MOUNTED:
                    String obbPath = mStorageManager.getMountedObbPath(path);
                    Log.v(TAG, "OBB " + path + " is mounted to " + obbPath);
                    GAME_PATH = obbPath;
                    break;
                case OnObbStateChangeListener.UNMOUNTED:
                    Log.v(TAG, "OBB " + path + " is unmounted");
                    GAME_PATH = GAME_PATH_DEFAULT;
                    break;
                default:
                    Log.e(TAG, "Failed to mount OBB " + path + ": Got state " + state);
                    break;
            }
            runSDLThread();
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == 110) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                mSingleton.finish();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        // Read launcher settings
        readLauncherSettings();
        applyImmersiveMode();

        // SDL creates its SurfaceView inside super.onCreate(), so request the
        // target orientation before the native surface is created.
        applyOrientation();

        super.onCreate(savedInstanceState);
        applyImmersiveMode();

        String requestedGamePath = getIntent().getStringExtra(EXTRA_GAME_PATH);
        if (requestedGamePath == null || requestedGamePath.isEmpty()) {
            requestedGamePath = getIntent().getStringExtra("game_path");
        }
        boolean launchedWithGamePath = requestedGamePath != null && !requestedGamePath.isEmpty();
        if (launchedWithGamePath) {
            GAME_PATH = requestedGamePath;
        }

        mMainHandler = new Handler(getMainLooper());
        mStorageManager = (StorageManager) getSystemService(STORAGE_SERVICE);
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        final String obbPrefix = "main";
        final int obbVersion = 1;
        OBB_MAIN_FILENAME = getObbDir() + "/" + obbPrefix + "." + obbVersion + "." + getPackageName() + ".obb";

        try {
            ActivityInfo actInfo = getPackageManager().getActivityInfo(this.getComponentName(), PackageManager.GET_META_DATA);
            DEBUG = actInfo.metaData != null && actInfo.metaData.getBoolean("mkxp_debug", false);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Failed to set debug flag: " + e);
            e.printStackTrace();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !launchedWithGamePath) {
            if (!Environment.isExternalStorageManager()) {
                Uri uri = Uri.parse("package:" + getPackageName());
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri);
                startActivityForResult(intent, 110);
            }
        }

        // Setup in-screen gamepad
        mGamepadInvisible = mHideVirtualGamepad || isAndroidTV() || isChromebook();
        mGamepadConfig = buildGamepadConfig();
        mGamepad.init(mGamepadConfig, mGamepadInvisible);
        mGamepad.setOnKeyDownListener(SDLActivity::onNativeKeyDown);
        mGamepad.setOnKeyUpListener(SDLActivity::onNativeKeyUp);
        // Tap-to-skip: tapping empty game area sends Confirm key
        mGamepad.setOnTapConfirmListener(() -> {
            mMainHandler.post(() -> {
                SDLActivity.onNativeKeyDown(mGamepadConfig.keycodeA);
            });
            // Release after a short delay to simulate a press
            mMainHandler.postDelayed(() -> {
                SDLActivity.onNativeKeyUp(mGamepadConfig.keycodeA);
            }, 30);
        });

        // Attach gamepad after the target layout exists. Portrait mode needs a
        // dedicated lower panel; landscape keeps the upstream overlay behavior.
        if (mHideVirtualGamepad) {
            Log.i(TAG, "Virtual gamepad hidden by launcher setting");
        } else if (mIsPortraitConsole && mLayout != null) {
            rearrangeForPortraitConsole();
        } else if (mLayout != null) {
            mGamepad.attachTo(this, mLayout);
        }

        // Setup FPS textview
        tvFps = new TextView(this);
        tvFps.setTextSize((8 * ((float) getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT)));
        tvFps.setTextColor(Color.argb(96, 255, 255, 255));
        tvFps.setVisibility(View.GONE);

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.setMargins(16, 16, 0, 0);
        tvFps.setLayoutParams(params);

        mLayout.addView(tvFps);
        attachRuntimeMenuButton();
    }

    private void attachRuntimeMenuButton()
    {
        if (mLayout == null) return;
        TextView menu = floatingPill();
        menu.setOnClickListener(v -> showRuntimeActions());
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        );
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        params.addRule(RelativeLayout.CENTER_HORIZONTAL);
        params.setMargins(0, dp(8), 0, 0);
        mLayout.addView(menu, params);
    }

    private void applyImmersiveMode()
    {
        if (Build.VERSION.SDK_INT >= 19) {
            int flags = View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            getWindow().getDecorView().setSystemUiVisibility(flags);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.layoutInDisplayCutoutMode = CUTOUT_EDGE_TO_EDGE.equals(mDisplayCutoutMode)
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
            getWindow().setAttributes(params);
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        applyImmersiveMode();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveMode();
        }
    }

    private void readLauncherSettings()
    {
        Intent intent = getIntent();
        mLayoutMode = intent.getStringExtra(EXTRA_LAYOUT_MODE);
        if (mLayoutMode == null) mLayoutMode = MODE_LANDSCAPE;
        mDisplayCutoutMode = intent.getStringExtra(EXTRA_DISPLAY_CUTOUT_MODE);
        if (mDisplayCutoutMode == null) mDisplayCutoutMode = "SAFE_AREA";
        mControllerHomeShortcut = intent.getStringExtra(EXTRA_CONTROLLER_HOME_SHORTCUT);
        if (mControllerHomeShortcut == null) mControllerHomeShortcut = "L2_R2";
        mHideVirtualGamepad = intent.getBooleanExtra(EXTRA_HIDE_VIRTUAL_GAMEPAD, false);
        mIsPortraitConsole = MODE_PORTRAIT_CONSOLE.equals(mLayoutMode);
        Log.i(TAG, "Launcher settings: layout=" + mLayoutMode
            + " touchOpacity=" + intent.getFloatExtra(EXTRA_TOUCH_OPACITY, 0.72f)
            + " touchScale=" + intent.getFloatExtra(EXTRA_TOUCH_SCALE, 1.0f)
            + " haptics=" + intent.getBooleanExtra(EXTRA_HAPTICS_ENABLED, true)
            + " hapticIntensity=" + intent.getFloatExtra(EXTRA_HAPTIC_INTENSITY, 0.55f)
            + " textScale=" + intent.getFloatExtra(EXTRA_TEXT_SCALE, 1.0f)
            + " integerScaling=" + intent.getBooleanExtra(EXTRA_INTEGER_SCALING, false)
            + " hideVirtualGamepad=" + mHideVirtualGamepad
            + " cutout=" + mDisplayCutoutMode
            + " homeShortcut=" + mControllerHomeShortcut
            + " preset=" + intent.getStringExtra(EXTRA_CONTROLLER_PRESET));
    }

    private void applyOrientation()
    {
        switch (mLayoutMode) {
            case MODE_PORTRAIT_CONSOLE:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                break;
            case MODE_LANDSCAPE:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                break;
            case MODE_GAMEPAD:
            default:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                break;
        }
    }

    @Override
    public void setOrientationBis(int w, int h, boolean resizable, String hint)
    {
        if (mIsPortraitConsole) {
            Log.i(TAG, "Ignoring SDL orientation request in portrait console: width=" + w
                + " height=" + h
                + " resizable=" + resizable
                + " hint=" + hint);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            return;
        }
        super.setOrientationBis(w, h, resizable, hint);
    }

    private GamepadConfig buildGamepadConfig()
    {
        Intent intent = getIntent();
        GamepadConfig config = new GamepadConfig();

        // Read touch settings from launcher extras
        if (intent.hasExtra(EXTRA_TOUCH_OPACITY)) {
            config.opacity = (int) (intent.getFloatExtra(EXTRA_TOUCH_OPACITY, 0.72f) * 100);
        }
        if (intent.hasExtra(EXTRA_TOUCH_SCALE)) {
            config.scale = (int) (intent.getFloatExtra(EXTRA_TOUCH_SCALE, 1.0f) * 100);
        }
        String preset = intent.getStringExtra(EXTRA_CONTROLLER_PRESET);
        if (preset != null && !preset.isEmpty()) {
            config.preset = preset;
        }

        return config;
    }

    /**
     * Rearrange the SDL layout for portrait console mode.
     * The SurfaceView goes to the top at native VX Ace ratio (544:416 = 4:3),
     * and the gamepad goes below.
     */
    private void rearrangeForPortraitConsole()
    {
        try {
            if (mSurface != null) {
                mLayout.removeView(mSurface);

                // Create a container for the game viewport at native ratio
                DisplayMetrics metrics = getResources().getDisplayMetrics();
                int screenWidth = metrics.widthPixels;
                int screenHeight = metrics.heightPixels;
                // DisplayMetrics can briefly report landscape dimensions while
                // SDL is creating the surface. Base the viewport on the portrait
                // width, then preserve the native VX Ace 544x416 aspect.
                int portraitWidth = Math.min(screenWidth, screenHeight);
                int portraitHeight = Math.max(screenWidth, screenHeight);
                screenWidth = portraitWidth;
                screenHeight = portraitHeight;
                int gameHeight = (int) Math.round(screenWidth * 416.0 / 544.0);

                FrameLayout viewportContainer = new FrameLayout(this);
                int viewportId = View.generateViewId();
                viewportContainer.setId(viewportId);
                RelativeLayout.LayoutParams viewportParams = new RelativeLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    gameHeight
                );
                viewportParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                viewportContainer.setLayoutParams(viewportParams);
                viewportContainer.setBackgroundColor(Color.rgb(0, 0, 0));

                // Put the surface in the viewport container
                mSurface.setLayoutParams(new FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT
                ));
                viewportContainer.addView(mSurface);
                mLayout.addView(viewportContainer);

                // Create a container for the gamepad below
                mGamepadContainer = new FrameLayout(this);
                RelativeLayout.LayoutParams controlsParams = new RelativeLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT
                );
                controlsParams.addRule(RelativeLayout.BELOW, viewportId);
                controlsParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                mGamepadContainer.setLayoutParams(controlsParams);
                mGamepadContainer.setBackgroundColor(Color.rgb(12, 11, 14));
                mLayout.addView(mGamepadContainer);

                attachPortraitControls();

                Log.i(TAG, "Portrait console layout: viewport=" + screenWidth + "x" + gameHeight
                    + " controlsHeight~=" + (screenHeight - gameHeight)
                    + " surfaceParent=" + mSurface.getParent().getClass().getSimpleName());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to rearrange for portrait console: " + e.getMessage());
        }
    }

    private void attachPortraitControls()
    {
        mPortraitControls = new TouchOverlayView(this);
        mPortraitControls.setControlsOnly(true);
        mPortraitControls.setOpacity(Math.max(0.15f, Math.min(1.0f, mGamepadConfig.opacity / 100f)));
        mPortraitControls.setScale(Math.max(1.18f, Math.min(1.55f, mGamepadConfig.scale / 100f)));
        mPortraitControls.setHapticsEnabled(getIntent().getBooleanExtra(EXTRA_HAPTICS_ENABLED, true));
        mPortraitControls.setHapticIntensity(
            Math.max(0.0f, Math.min(1.0f, getIntent().getFloatExtra(EXTRA_HAPTIC_INTENSITY, 0.55f)))
        );
        mPortraitControls.setOnInput((zone, pressed) -> {
            if (zone == TouchOverlayView.Zone.HOME) {
                if (!pressed) {
                    finish();
                }
                return Unit.INSTANCE;
            }
            int keyCode = keyCodeForZone(zone);
            if (keyCode != 0) {
                if (pressed) {
                    SDLActivity.onNativeKeyDown(keyCode);
                } else {
                    SDLActivity.onNativeKeyUp(keyCode);
                }
            }
            return Unit.INSTANCE;
        });
        mGamepadContainer.addView(
            mPortraitControls,
            new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        );
        Log.i(TAG, "Portrait controls attached with custom layout view");
    }

    private void showRuntimeActions()
    {
        if (mLayout == null) return;
        if (mEditMode) {
            // Don't show runtime menu in edit mode
            return;
        }
        if (mRuntimeActionsOverlay != null) {
            mLayout.removeView(mRuntimeActionsOverlay);
            mRuntimeActionsOverlay = null;
            return;
        }
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.argb(75, 0, 0, 0));
        overlay.setClickable(true);
        overlay.setOnClickListener(v -> dismissRuntimeActions());

        // Main panel — glassmorphism style, positioned at top
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.argb(190, 10, 9, 14));
        bg.setStroke(dp(1), Color.argb(70, 220, 200, 160));
        bg.setCornerRadius(dp(12));
        panel.setBackground(bg);
        panel.setClickable(true);

        // Top row: RESUME | HOME
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.addView(runtimeButton("RESUME", R.drawable.ic_runtime_resume, v -> dismissRuntimeActions()), weightedParams(0, dp(6)));
        topRow.addView(runtimeButton("HOME", R.drawable.ic_runtime_home, v -> {
            dismissRuntimeActions();
            goHomePaused();
        }), weightedParams(dp(6), 0));
        panel.addView(topRow);

        // Toggle controls
        panel.addView(runtimeToggleButton(!mHideVirtualGamepad, v -> {
            toggleNativeControls();
            dismissRuntimeActions();
        }));

        // Keyboard button
        panel.addView(runtimeButton("KEYBOARD", R.drawable.ic_runtime_keyboard, v -> {
            dismissRuntimeActions();
            org.libsdl.app.SDLActivity.showTextInput(0, 0, 1, 1);
        }));

        // Divider
        View div = new View(this);
        div.setBackgroundColor(Color.argb(50, 220, 200, 160));
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, dp(1)
        );
        divParams.setMargins(0, dp(8), 0, dp(8));
        panel.addView(div, divParams);

        // Edit Layout button
        panel.addView(runtimeButton("EDIT LAYOUT", R.drawable.ic_runtime_edit, v -> {
            dismissRuntimeActions();
            enterEditMode();
        }));

        // Revert to Default button
        panel.addView(runtimeButton("REVERT", R.drawable.ic_runtime_home, v -> {
            mGamepad.resetPositions();
            dismissRuntimeActions();
            Toast.makeText(this, "Layout reset to default", Toast.LENGTH_SHORT).show();
        }));

        // Position the panel at the top, centered horizontally
        int panelWidth = Math.max(dp(280), Math.min(
            getResources().getDisplayMetrics().widthPixels - dp(40), dp(440)
        ));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
            panelWidth,
            LayoutParams.WRAP_CONTENT
        );
        panelParams.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
        panelParams.setMargins(0, dp(18), 0, 0);
        overlay.addView(panel, panelParams);
        mLayout.addView(overlay, new RelativeLayout.LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        ));
        mRuntimeActionsOverlay = overlay;
    }

    private void dismissRuntimeActions()
    {
        if (mRuntimeActionsOverlay != null && mLayout != null) {
            mLayout.removeView(mRuntimeActionsOverlay);
            mRuntimeActionsOverlay = null;
        }
    }

    private void toggleNativeControls()
    {
        mHideVirtualGamepad = !mHideVirtualGamepad;
        if (mHideVirtualGamepad) {
            if (mPortraitControls != null && mPortraitControls.getParent() instanceof FrameLayout) {
                ((FrameLayout) mPortraitControls.getParent()).removeView(mPortraitControls);
                mPortraitControls = null;
            }
            mGamepad.hideView();
            mGamepadInvisible = true;
            Toast.makeText(this, "Controls hidden", Toast.LENGTH_SHORT).show();
            return;
        }
        if (mIsPortraitConsole && mGamepadContainer != null && mPortraitControls == null) {
            attachPortraitControls();
        } else {
            mGamepad.showView();
            mGamepadInvisible = false;
        }
        Toast.makeText(this, "Controls shown", Toast.LENGTH_SHORT).show();
    }

    private TextView runtimeButton(String label, int iconRes, View.OnClickListener listener)
    {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextColor(Color.rgb(230, 220, 200));
        view.setTextSize(11);
        view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        view.setGravity(android.view.Gravity.CENTER);
        view.setPadding(dp(8), dp(8), dp(8), dp(8));
        view.setCompoundDrawablesWithIntrinsicBounds(0, iconRes, 0, 0);
        view.setCompoundDrawablePadding(dp(4));
        view.setBackground(runtimeButtonBg(false));
        view.setOnClickListener(listener);
        return view;
    }

    private TextView runtimeToggleButton(boolean enabled, View.OnClickListener listener)
    {
        TextView view = runtimeButton(enabled ? "CONTROLS ON" : "CONTROLS OFF", R.drawable.ic_runtime_controls, listener);
        view.setTextColor(enabled ? Color.rgb(245, 228, 190) : Color.rgb(170, 160, 145));
        view.setBackground(runtimeButtonBg(enabled));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(10), 0, dp(10));
        view.setLayoutParams(params);
        return view;
    }

    private android.graphics.drawable.GradientDrawable runtimeButtonBg(boolean enabled)
    {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(enabled ? Color.argb(105, 120, 95, 62) : Color.argb(70, 200, 170, 130));
        bg.setStroke(dp(1), enabled ? Color.argb(120, 225, 195, 140) : Color.argb(85, 210, 185, 145));
        bg.setCornerRadius(dp(10));
        return bg;
    }

    private LinearLayout.LayoutParams weightedParams(int left, int right)
    {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(left, 0, right, 0);
        return params;
    }

    private TextView floatingPill()
    {
        TextView view = new TextView(this);
        view.setText("☰");
        view.setTextSize(18);
        view.setTextColor(Color.argb(200, 220, 210, 190));
        view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        view.setGravity(android.view.Gravity.CENTER);
        view.setPadding(dp(16), dp(8), dp(16), dp(8));
        view.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.argb(140, 10, 9, 14));
        bg.setStroke(dp(1), Color.argb(60, 220, 200, 160));
        bg.setCornerRadius(dp(20));
        view.setBackground(bg);
        return view;
    }

    private int dp(int value)
    {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private int keyCodeForZone(TouchOverlayView.Zone zone)
    {
        switch (zone) {
            case DPAD_UP:
                return KeyEvent.KEYCODE_DPAD_UP;
            case DPAD_DOWN:
                return KeyEvent.KEYCODE_DPAD_DOWN;
            case DPAD_LEFT:
                return KeyEvent.KEYCODE_DPAD_LEFT;
            case DPAD_RIGHT:
                return KeyEvent.KEYCODE_DPAD_RIGHT;
            case BTN_A:
            case START:
                return mGamepadConfig.keycodeA;
            case BTN_B:
            case MENU:
                return mGamepadConfig.keycodeB;
            case BTN_X:
                return mGamepadConfig.keycodeX;
            case BTN_Y:
                return mGamepadConfig.keycodeY;
            case SELECT:
                return mGamepadConfig.keycodeSHIFT;
            case SETTINGS:
                return 0;
            case HOME:
                return 0;
            default:
                return 0;
        }
    }

    @Override
    protected void onStart()
    {
        super.onStart();

        if (!mStarted) {
            if (!GAME_PATH_DEFAULT.equals(GAME_PATH)) {
                Log.v(TAG, "Intent game path supplied, starting without OBB mount");
                runSDLThread();
                return;
            }

            if (new File(OBB_MAIN_FILENAME).exists()) {
                Log.v(TAG, "Main OBB file found, starting with main OBB mount");
                mStorageManager.mountObb(OBB_MAIN_FILENAME, null, obbListener);
            } else {
                Log.v(TAG, "Main OBB file not found, starting without main OBB mount");
                runSDLThread();
            }
        } else {
            runSDLThread();
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent evt)
    {
        if (handleControllerCombo(evt)) {
            return true;
        }

        if (
            evt.getKeyCode() != KeyEvent.KEYCODE_BACK &&
            evt.getKeyCode() != KeyEvent.KEYCODE_VOLUME_UP &&
            evt.getKeyCode() != KeyEvent.KEYCODE_VOLUME_DOWN &&
            evt.getKeyCode() != KeyEvent.KEYCODE_VOLUME_MUTE && 
            evt.getKeyCode() != KeyEvent.KEYCODE_HEADSETHOOK
        ) {
            if (!mGamepadInvisible) {
                mGamepad.hideView();
                mGamepadInvisible = true;
            }
        }

        if (mGamepad.processGamepadEvent(evt))
            return true;

        return super.dispatchKeyEvent(evt);
    }

    private boolean handleControllerCombo(KeyEvent evt)
    {
        if (evt.getAction() == KeyEvent.ACTION_UP) {
            mPressedControllerKeys.remove(evt.getKeyCode());
            return false;
        }
        if (evt.getAction() != KeyEvent.ACTION_DOWN) return false;
        mPressedControllerKeys.add(evt.getKeyCode());
        if (evt.getRepeatCount() > 0) return false;

        if (shortcutPressed(mControllerHomeShortcut)) {
            goHomePaused();
            return true;
        }
        return false;
    }

    private boolean shortcutPressed(String shortcut)
    {
        if ("OFF".equals(shortcut)) return false;
        if ("L2_R2".equals(shortcut)) {
            return mPressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_L2)
                && mPressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_R2);
        }
        if ("L1_R1".equals(shortcut)) {
            return mPressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_L1)
                && mPressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_R1);
        }
        if ("START_SELECT".equals(shortcut)) {
            return mPressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_START)
                && mPressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_SELECT);
        }
        if ("L2_START".equals(shortcut)) {
            return mPressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_L2)
                && mPressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_START);
        }
        if ("R2_START".equals(shortcut)) {
            return mPressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_R2)
                && mPressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_START);
        }
        return false;
    }

    private boolean handleTriggerHomeCombo(MotionEvent evt)
    {
        if (!"L2_R2".equals(mControllerHomeShortcut)) {
            mTriggerHomeComboDown = false;
            return false;
        }
        float left = Math.max(
            evt.getAxisValue(MotionEvent.AXIS_LTRIGGER),
            evt.getAxisValue(MotionEvent.AXIS_BRAKE)
        );
        float right = Math.max(
            evt.getAxisValue(MotionEvent.AXIS_RTRIGGER),
            evt.getAxisValue(MotionEvent.AXIS_GAS)
        );
        boolean bothPressed = left > 0.55f && right > 0.55f;
        if (!bothPressed) {
            mTriggerHomeComboDown = false;
            return false;
        }
        if (mTriggerHomeComboDown) return true;
        mTriggerHomeComboDown = true;
        goHomePaused();
        return true;
    }

    // ---- Edit Mode ----

    @SuppressLint("ClickableViewAccessibility")
    private void enterEditMode()
    {
        if (mEditMode) return;
        mEditMode = true;
        mGamepad.setEditMode(true);

        // Show a glassy toolbar at the bottom with SAVE, REVERT, CANCEL
        if (mLayout != null && mEditModeOverlay == null) {
            LinearLayout toolbar = new LinearLayout(this);
            toolbar.setOrientation(LinearLayout.HORIZONTAL);
            toolbar.setPadding(dp(16), dp(12), dp(16), dp(16));

            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(Color.argb(190, 10, 9, 14));
            bg.setStroke(dp(1), Color.argb(70, 220, 200, 160));
            bg.setCornerRadius(dp(16));
            toolbar.setBackground(bg);

            // SAVE button
            TextView save = new TextView(this);
            save.setText("SAVE");
            save.setTextColor(Color.argb(230, 160, 230, 140));
            save.setTextSize(14);
            save.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            save.setGravity(android.view.Gravity.CENTER);
            save.setPadding(dp(24), dp(12), dp(24), dp(12));
            android.graphics.drawable.GradientDrawable saveBg = new android.graphics.drawable.GradientDrawable();
            saveBg.setColor(Color.argb(50, 120, 190, 100));
            saveBg.setStroke(dp(1), Color.argb(90, 160, 230, 140));
            saveBg.setCornerRadius(dp(10));
            save.setBackground(saveBg);
            save.setOnClickListener(v -> {
                mGamepad.savePositions();
                exitEditMode();
            });

            // REVERT button
            TextView revertBtn = new TextView(this);
            revertBtn.setText("REVERT");
            revertBtn.setTextColor(Color.argb(210, 220, 200, 180));
            revertBtn.setTextSize(14);
            revertBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            revertBtn.setGravity(android.view.Gravity.CENTER);
            revertBtn.setPadding(dp(24), dp(12), dp(24), dp(12));
            android.graphics.drawable.GradientDrawable revertBg = new android.graphics.drawable.GradientDrawable();
            revertBg.setColor(Color.argb(50, 190, 120, 100));
            revertBg.setStroke(dp(1), Color.argb(90, 230, 160, 140));
            revertBg.setCornerRadius(dp(10));
            revertBtn.setBackground(revertBg);
            revertBtn.setOnClickListener(v -> {
                mGamepad.resetPositions();
                exitEditMode();
                Toast.makeText(this, "Layout reset to default", Toast.LENGTH_SHORT).show();
            });

            // CANCEL button
            TextView cancel = new TextView(this);
            cancel.setText("CANCEL");
            cancel.setTextColor(Color.argb(180, 180, 170, 155));
            cancel.setTextSize(13);
            cancel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            cancel.setGravity(android.view.Gravity.CENTER);
            cancel.setPadding(dp(20), dp(12), dp(20), dp(12));
            android.graphics.drawable.GradientDrawable cancelBg = new android.graphics.drawable.GradientDrawable();
            cancelBg.setColor(Color.argb(35, 180, 170, 155));
            cancelBg.setStroke(dp(1), Color.argb(55, 180, 170, 155));
            cancelBg.setCornerRadius(dp(10));
            cancel.setBackground(cancelBg);
            cancel.setOnClickListener(v -> exitEditMode());

            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            btnParams.setMargins(dp(4), 0, dp(4), 0);
            toolbar.addView(save, btnParams);
            toolbar.addView(revertBtn, btnParams);
            toolbar.addView(cancel, btnParams);

            // Wrap in a FrameLayout overlay
            mEditModeOverlay = new FrameLayout(this);
            mEditModeOverlay.setBackgroundColor(Color.argb(55, 0, 0, 0));
            mEditModeOverlay.setOnTouchListener((v, evt) -> true);

            FrameLayout.LayoutParams toolbarParams = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            );
            toolbarParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
            toolbarParams.setMargins(0, 0, 0, dp(20));
            ((FrameLayout) mEditModeOverlay).addView(toolbar, toolbarParams);

            mLayout.addView(mEditModeOverlay, new RelativeLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            ));
        }

        Toast.makeText(this, "Edit mode: drag buttons to reposition", Toast.LENGTH_SHORT).show();
    }

    private void exitEditMode()
    {
        if (!mEditMode) return;
        mEditMode = false;
        mGamepad.setEditMode(false);

        if (mEditModeOverlay != null && mLayout != null) {
            mLayout.removeView(mEditModeOverlay);
            mEditModeOverlay = null;
        }

        Toast.makeText(this, "Edit mode exited", Toast.LENGTH_SHORT).show();
    }

    // ---- End Edit Mode ----

    private void goHomePaused()
    {
        getSharedPreferences("runestone", MODE_PRIVATE).edit()
            .putBoolean("game_minimized", true)
            .putString("paused_game", GAME_PATH)
            .apply();
        Intent intent = new Intent();
        intent.setClassName(getPackageName(), "com.runestone.app.MainActivity");
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent evt)
    {
        if (!mHideVirtualGamepad && mGamepadInvisible) {
            mGamepad.showView();
            mGamepadInvisible = false;
        }

        return super.dispatchTouchEvent(evt);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent evt)
    {
        if (handleTriggerHomeCombo(evt))
            return true;

        if (mGamepad.processDPadEvent(evt))
            return true;

        return super.onGenericMotionEvent(evt);
    }

    @Override
    protected String[] getArguments()
    {
        String[] args;
        if (DEBUG) {
            args = new String[] { "debug" };
        } else if (!GAME_PATH_DEFAULT.equals(GAME_PATH) && GAME_PATH != null && !GAME_PATH.isEmpty()) {
            // mkxp-z uses argv[0] as the game folder — pass GAME_PATH so the
            // interpreter loads the requested game's Data/, Graphics/, etc.
            // instead of the default /sdcard/mkxp-z fallback.
            args = new String[] { GAME_PATH };
        } else {
            args = new String[] {};
        }
        return args;
    }

    @SuppressLint("SetTextI18n")
    @SuppressWarnings("unused")
    private static void updateFPSText(int num)
    {
        mMainHandler.post(() -> tvFps.setText(num + " FPS"));
    }

    @SuppressWarnings("unused")
    private static void setFPSVisibility(boolean visible)
    {
        mMainHandler.post(() -> {
            if (visible)
                tvFps.setVisibility(View.VISIBLE);
            else
                tvFps.setVisibility(View.INVISIBLE);
        });
    }

    @SuppressWarnings("unused")
    private static String getSystemLanguage()
    {
        return Locale.getDefault().toString();
    }

    @SuppressWarnings("unused")
    private static boolean hasVibrator()
    {
        return mVibrator.hasVibrator();
    }

    @SuppressWarnings("unused")
    private static void vibrate(int duration)
    {
        if (duration >= 10000) {
            duration = 10000;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mVibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.EFFECT_HEAVY_CLICK));
        } else {
            mVibrator.vibrate(duration);
        }
    }

    @SuppressWarnings("unused")
    private static void vibrateStop()
    {
        mVibrator.cancel();
    }

    @SuppressWarnings("unused")
    private static boolean inMultiWindow(Activity activity)
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInMultiWindowMode();
    }
}
