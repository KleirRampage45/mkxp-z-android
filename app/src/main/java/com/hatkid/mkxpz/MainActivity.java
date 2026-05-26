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
import android.view.ViewGroup.LayoutParams;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
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

import org.libsdl.app.SDLActivity;
import com.grimmobile.runner.input.TouchOverlayView;
import com.hatkid.mkxpz.gamepad.Gamepad;
import com.hatkid.mkxpz.gamepad.GamepadConfig;
import com.grimmobile.runner.R;
import kotlin.Unit;

public class MainActivity extends SDLActivity
{
    private static final String TAG = "mkxp-z[Activity]";
    public static final String EXTRA_GAME_PATH = "com.grimmobile.runner.extra.GAME_PATH";
    private static final String GAME_PATH_DEFAULT = Environment.getExternalStorageDirectory() + "/mkxp-z";
    private static String GAME_PATH = GAME_PATH_DEFAULT;
    private static String OBB_MAIN_FILENAME;
    private static boolean DEBUG = false;

    // Settings extras (from Grimmobile launcher)
    public static final String EXTRA_LAYOUT_MODE     = "com.grimmobile.runner.extra.LAYOUT_MODE";
    public static final String EXTRA_TOUCH_OPACITY   = "com.grimmobile.runner.extra.TOUCH_OPACITY";
    public static final String EXTRA_TOUCH_SCALE     = "com.grimmobile.runner.extra.TOUCH_SCALE";
    public static final String EXTRA_HAPTICS_ENABLED = "com.grimmobile.runner.extra.HAPTICS_ENABLED";
    public static final String EXTRA_HAPTIC_INTENSITY = "com.grimmobile.runner.extra.HAPTIC_INTENSITY";
    public static final String EXTRA_SHOW_EXTRA_BUTTONS = "com.grimmobile.runner.extra.SHOW_EXTRA_BUTTONS";
    public static final String EXTRA_TEXT_SCALE      = "com.grimmobile.runner.extra.TEXT_SCALE";
    public static final String EXTRA_INTEGER_SCALING = "com.grimmobile.runner.extra.INTEGER_SCALING";

    // Layout modes (match RunnerSettings.LayoutMode)
    private static final String MODE_LANDSCAPE      = "LANDSCAPE";
    private static final String MODE_PORTRAIT_CONSOLE = "PORTRAIT_CONSOLE";
    private static final String MODE_GAMEPAD        = "GAMEPAD";

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

    // For portrait console split layout
    private FrameLayout mGamepadContainer;
    private TouchOverlayView mPortraitControls;
    private boolean mIsPortraitConsole = false;

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

        // SDL creates its SurfaceView inside super.onCreate(), so request the
        // target orientation before the native surface is created.
        applyOrientation();

        super.onCreate(savedInstanceState);

        String requestedGamePath = getIntent().getStringExtra(EXTRA_GAME_PATH);
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
            DEBUG = actInfo.metaData.getBoolean("mkxp_debug");
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
        mGamepadInvisible = (isAndroidTV() || isChromebook());
        mGamepadConfig = buildGamepadConfig();
        mGamepad.init(mGamepadConfig, mGamepadInvisible);
        mGamepad.setOnKeyDownListener(SDLActivity::onNativeKeyDown);
        mGamepad.setOnKeyUpListener(SDLActivity::onNativeKeyUp);

        // Attach gamepad after the target layout exists. Portrait mode needs a
        // dedicated lower panel; landscape keeps the upstream overlay behavior.
        if (mIsPortraitConsole && mLayout != null) {
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
    }

    private void readLauncherSettings()
    {
        Intent intent = getIntent();
        mLayoutMode = intent.getStringExtra(EXTRA_LAYOUT_MODE);
        if (mLayoutMode == null) mLayoutMode = MODE_LANDSCAPE;
        mIsPortraitConsole = MODE_PORTRAIT_CONSOLE.equals(mLayoutMode);
        Log.i(TAG, "Launcher settings: layout=" + mLayoutMode
            + " touchOpacity=" + intent.getFloatExtra(EXTRA_TOUCH_OPACITY, 0.72f)
            + " touchScale=" + intent.getFloatExtra(EXTRA_TOUCH_SCALE, 1.0f)
            + " haptics=" + intent.getBooleanExtra(EXTRA_HAPTICS_ENABLED, true)
            + " hapticIntensity=" + intent.getFloatExtra(EXTRA_HAPTIC_INTENSITY, 0.55f)
            + " showExtraButtons=" + intent.getBooleanExtra(EXTRA_SHOW_EXTRA_BUTTONS, false)
            + " textScale=" + intent.getFloatExtra(EXTRA_TEXT_SCALE, 1.0f)
            + " integerScaling=" + intent.getBooleanExtra(EXTRA_INTEGER_SCALING, false));
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
                // VX Ace native resolution: 544x416 (4:3)
                int gameHeight = (int) (screenWidth * 416.0 / 544.0);
                int maxGameHeight = (int) (screenHeight * 0.55f);
                if (maxGameHeight > 0) {
                    gameHeight = Math.min(gameHeight, maxGameHeight);
                }

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
        mPortraitControls.setShowExtraButtons(getIntent().getBooleanExtra(EXTRA_SHOW_EXTRA_BUTTONS, false));
        mPortraitControls.setOnInput((zone, pressed) -> {
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

    @Override
    public boolean dispatchTouchEvent(MotionEvent evt)
    {
        if (mGamepadInvisible) {
            mGamepad.showView();
            mGamepadInvisible = false;
        }

        return super.dispatchTouchEvent(evt);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent evt)
    {
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
