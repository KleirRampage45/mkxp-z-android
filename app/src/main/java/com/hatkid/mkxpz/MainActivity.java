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
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
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
import com.hatkid.mkxpz.gamepad.Gamepad;
import com.hatkid.mkxpz.gamepad.GamepadConfig;
import com.grimmobile.runner.R;

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

    // Layout mode from launcher
    private String mLayoutMode = MODE_LANDSCAPE;

    // For portrait console split layout
    private FrameLayout mGamepadContainer;
    private View mSurfaceView;
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
        super.onCreate(savedInstanceState);

        // Read launcher settings
        readLauncherSettings();

        // Apply orientation
        applyOrientation();

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
        GamepadConfig gpadConfig = buildGamepadConfig();
        mGamepad.init(gpadConfig, mGamepadInvisible);
        mGamepad.setOnKeyDownListener(SDLActivity::onNativeKeyDown);
        mGamepad.setOnKeyUpListener(SDLActivity::onNativeKeyUp);

        // Attach gamepad to layout
        if (mLayout != null) {
            mGamepad.attachTo(this, mLayout);
        }

        // In portrait console mode, rearrange layout for split view
        if (mIsPortraitConsole && mLayout != null) {
            rearrangeForPortraitConsole();
        }

        // Setup FPS textview
        tvFps = new TextView(this);
        tvFps.setTextSize((8 * ((float) getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT)));
        tvFps.setTextColor(Color.argb(96, 255, 255, 255));
        tvFps.setVisibility(View.GONE);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
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
            // Find the SurfaceView (SDL creates it as first child of mLayout)
            if (mLayout.getChildCount() > 0) {
                mSurfaceView = mLayout.getChildAt(0);

                // Remove all children for re-arrangement
                mLayout.removeAllViews();

                // Create a container for the game viewport at native ratio
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                // VX Ace native resolution: 544x416 (4:3)
                int gameHeight = (int) (screenWidth * 416.0 / 544.0);

                FrameLayout viewportContainer = new FrameLayout(this);
                viewportContainer.setLayoutParams(new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    gameHeight
                ));
                viewportContainer.setBackgroundColor(Color.rgb(0, 0, 0));

                // Put the surface in the viewport container
                mSurfaceView.setLayoutParams(new FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT
                ));
                viewportContainer.addView(mSurfaceView);
                mLayout.addView(viewportContainer);

                // Create a container for the gamepad below
                mGamepadContainer = new FrameLayout(this);
                mGamepadContainer.setLayoutParams(new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    0,
                    1.0f // Fill remaining space
                ));
                mGamepadContainer.setBackgroundColor(Color.rgb(12, 11, 14));
                mLayout.addView(mGamepadContainer);

                // Move the gamepad layout into the container
                View gamepadLayout = null;
                for (int i = 0; i < mGamepadContainer.getChildCount(); i++) {
                    // After attachTo, gamepad_layout might be here
                }
                // The gamepad was already attached to mLayout before our rearrange.
                // Find it by searching through mLayout's original children.
                // We need to find the gamepad_layout ViewGroup.
                // Actually, the gamepad attachTo added it to mLayout. Now that
                // we cleared mLayout, we need to find where it went.
                // Let's search the view hierarchy.
                gamepadLayout = findViewByTagOrId(null, R.id.gamepad_layout);
                if (gamepadLayout == null) {
                    // Fallback: the gamepad might have been already removed.
                    // Re-attach it to the new container.
                    mGamepad.attachTo(this, mGamepadContainer);
                } else if (gamepadLayout.getParent() instanceof ViewGroup) {
                    ((ViewGroup) gamepadLayout.getParent()).removeView(gamepadLayout);
                    gamepadLayout.setLayoutParams(new FrameLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT
                    ));
                    mGamepadContainer.addView(gamepadLayout);
                }

                Log.i(TAG, "Portrait console layout: " + screenWidth + "x" + gameHeight + " viewport");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to rearrange for portrait console: " + e.getMessage());
        }
    }

    /** Find a view by resource ID in the entire view tree. */
    private View findViewByTagOrId(ViewGroup root, int id)
    {
        ViewGroup searchRoot = root != null ? root : (ViewGroup) getWindow().getDecorView();
        return searchRoot.findViewById(id);
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
        System.exit(0);
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
