package com.yumin.inversekinematics;

import android.opengl.GLSurfaceView;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;

public class ArmEGLConfigChooser implements GLSurfaceView.EGLConfigChooser {
    @Override
    public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
        int[] attribList = {
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 1,
                EGL10.EGL_SAMPLES, 16,
                EGL10.EGL_NONE
        };

        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfig = new int[1];
        if ( !egl.eglChooseConfig(display, attribList, configs, 1, numConfig) )
            throw new IllegalArgumentException("eglChooseConfig failed");

        EGLConfig config = numConfig[0] > 0 ? configs[0] : null;
        if (config == null)
            throw new IllegalArgumentException("No config chosen");

        return config;
    }
}
