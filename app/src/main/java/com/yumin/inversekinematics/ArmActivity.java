package com.yumin.inversekinematics;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ConfigurationInfo;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;

/*
 * Joint angle is not free when End-Effector moves to opposite end.
 * Maybe It seems that something is problem mathematical calculation.
 * But this is just for Translate-Rotate-Scale and Model-View-Matrix practice, not mathematical study.
 * So debug later.
 */

public class ArmActivity extends Activity {
    private GLSurfaceView mGLSurfaceView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        if (detectOpenGLES30())
            mGLSurfaceView = new ArmView(this);
        else
        {
            Log.e("ERROR", "OpenGL ES 3.0 not supported on device.");
            finish();
        }
        setContentView(mGLSurfaceView);
    }

    public boolean detectOpenGLES30() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ConfigurationInfo info = am.getDeviceConfigurationInfo();
        return (info.reqGlEsVersion >= 0x30000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGLSurfaceView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mGLSurfaceView.onPause();
    }
}
