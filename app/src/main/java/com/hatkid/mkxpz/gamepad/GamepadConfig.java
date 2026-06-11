package com.hatkid.mkxpz.gamepad;

import android.view.KeyEvent;

public class GamepadConfig
{
    /** In-screen gamepad settings **/

    // Opacity of view elements in percentage (default: 35 — more visible glass)
    public Integer opacity = 35;

    // View elements scale in percentage (default: 100)
    public Integer scale = 100;

    // Whether use diagonal (8-way) movement on D-Pad (default: false)
    public Boolean diagonalMovement = false;

    /** Key bindings for each RGSS input **/
    public final Integer keycodeA = KeyEvent.KEYCODE_Z;      // Confirm (RPG Maker Input C)
    public final Integer keycodeB = KeyEvent.KEYCODE_X;      // Back/Cancel (RPG Maker Input B)
    public final Integer keycodeC = KeyEvent.KEYCODE_C;      // Dash (RPG Maker Input A / Shift)
    public final Integer keycodeX = KeyEvent.KEYCODE_A;      // Extra A
    public final Integer keycodeY = KeyEvent.KEYCODE_S;      // Extra S
    public final Integer keycodeZ = KeyEvent.KEYCODE_D;      // Extra D
    public final Integer keycodeL = KeyEvent.KEYCODE_PAGE_UP; // Page Up / L shoulder
    public final Integer keycodeR = KeyEvent.KEYCODE_PAGE_DOWN; // Page Down / R shoulder
    public String preset = "SIMPLIFIED";
    public final Integer keycodeCTRL = KeyEvent.KEYCODE_CTRL_LEFT;
    public final Integer keycodeALT = KeyEvent.KEYCODE_ALT_LEFT;
    public final Integer keycodeSHIFT = KeyEvent.KEYCODE_SHIFT_LEFT; // Dash key
}