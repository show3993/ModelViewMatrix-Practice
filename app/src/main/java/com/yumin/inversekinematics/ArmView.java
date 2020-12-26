package com.yumin.inversekinematics;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;

public class ArmView extends GLSurfaceView {
    Context mContext;
    ArmRenderer mRenderer;
    Handler mHandler;

    private boolean isClick = false;
    private float pressedX;
    private float pressedY;

    class clickHandler extends Handler {
        public void handleMessage (Message msg) {
            if (msg.what == 1)
                isClick = true;
        }
    }

    public ArmView(Context context) {
        super(context);

        mContext = context;

        setEGLContextClientVersion(3);
        setEGLConfigChooser(new ArmEGLConfigChooser());

        mHandler = new clickHandler();
        mRenderer = new ArmRenderer(context, mHandler);
        setRenderer(mRenderer);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction())
        {
            case MotionEvent.ACTION_DOWN:
            {
                pressedX = event.getX();
                pressedY = event.getY();
                queueEvent(new Runnable() {
                    public void run() {
                        mRenderer.isClickEffector(pressedX, pressedY);
                    }
                });
                break;
            }
            case MotionEvent.ACTION_MOVE:
            {
                if (isClick == true)
                {
                    final float posX = event.getX();
                    final float posY = event.getY();
                    queueEvent(new Runnable() {
                        public void run() {
                            mRenderer.controlClick(posX, posY);
                        }
                    });
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            {
                isClick = false;
                break;
            }
        }

        return true;
    }
}
