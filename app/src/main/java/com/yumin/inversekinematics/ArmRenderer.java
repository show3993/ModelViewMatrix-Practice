package com.yumin.inversekinematics;

import android.content.Context;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class ArmRenderer implements GLSurfaceView.Renderer {
    private Context mContext;

    private int mWidth;
    private int mHeight;
    private int mArmShader;
    private Handler mHandler;

    private FloatBuffer mLinkVertexBuffer;
    private FloatBuffer mCircleVertexBuffer;
    private ByteBuffer mPickPixelColor;
    private int mNumCircleVertices;

    // EE Target Position
    private float targetX = 0.0f;
    private float targetY = 30.0f;

    // Joint Relative (for i-1) Angle and Distance
    private float[] mJointAngle = {90, 0, 0, 0};
    private final float LINK_LENGTH = 10.0f;

    // Joint Relative (for i-1) Position
    private float[][] mJointPosition = {
            {0, 0, 0, 1},
            {0, 0, 0, 1},
            {0, 0, 0, 1},
            {0, 0, 0, 1}
    };

    // Joint Color
    private float[] mJointColor = {
            1.0f, 1.0f, 1.0f, 0.0f,
            1.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            1.0f, 0.0f, 0.0f, 0.0f
    };

    private final float[] mProjectionMatrix = new float[16];
    private final float[] mViewMatrix = new float[16];
    private final float[] mVPMatrix = new float[16];
    private final float[] mMVPMatrix = new float[16];

    private final int MAX_JOINT_NUM = 4;
    private final float[][] mModelMatrix = new float[MAX_JOINT_NUM][16];
    private final float[][] mRotateMatrix = new float[MAX_JOINT_NUM][16];
    private final float[][] mTranslateMatrix = new float[MAX_JOINT_NUM][16];

    // Oobject Picking FBO
    int[] mPickFBO = new int[1];
    int[] mPickColorBuffer = new int[1];

    // Link (Rectangle) form Joint to Joint
    private float[] linkVertices = {
            0.0f, -2.0f, 0.0f,
            10.0f, -2.0f, 0.0f,
            0.0f, 2.0f, 0.0f,
            10.0f, 2.0f, 0.0f
    };

    public ArmRenderer(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;

        mLinkVertexBuffer = ByteBuffer.allocateDirect(linkVertices.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mLinkVertexBuffer.put(linkVertices).position(0);

        mPickPixelColor = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
        mPickPixelColor.position(0);

        initCircleVertexData(360, 2.0f);
        setModelMatrix();
    }

    // Joint (Circle) Vertex
    public void initCircleVertexData(int angleVal, float radius) {
        mNumCircleVertices = angleVal + 2;

        float[] positions = new float[3 * mNumCircleVertices];

        positions[0] = 0.0f;
        positions[1] = 0.0f;
        positions[2] = 0.0f;

        int v = 1;
        for (int angle = 0; angle <= angleVal; angle++, v++) {
            float theta = (float) angle / angleVal * 2 * (float)Math.PI;
            positions[3 * v] = radius * (float) Math.cos(theta);
            positions[3 * v + 1] = radius * (float) Math.sin(theta);
            positions[3 * v + 2] = 0;
        }

        mCircleVertexBuffer = ByteBuffer.allocateDirect(mNumCircleVertices * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mCircleVertexBuffer.put(positions).position(0);
    }

    /***
     * Modle Matrix Update
     * Call when initialization and Theta update
     */
    public void setModelMatrix() {
        for (int i = 0; i < MAX_JOINT_NUM; i++)
        {
            Matrix.setIdentityM(mRotateMatrix[i], 0);
            if (i == 0)
            {
                Matrix.setIdentityM(mTranslateMatrix[i], 0);
                Matrix.setRotateM(mRotateMatrix[i], 0, mJointAngle[i], 0.0f, 0.0f, 1.0f);
                Matrix.multiplyMM(mModelMatrix[i], 0, mTranslateMatrix[i], 0, mRotateMatrix[i], 0);
            }
            else
            {
                Matrix.setIdentityM(mTranslateMatrix[i], 0);
                Matrix.translateM(mTranslateMatrix[i], 0, LINK_LENGTH, 0.0f, 0.0f);
                Matrix.setRotateM(mRotateMatrix[i], 0, mJointAngle[i], 0.0f, 0.0f, 1.0f);
                Matrix.multiplyMM(mModelMatrix[i], 0, mTranslateMatrix[i], 0, mRotateMatrix[i], 0);
                Matrix.multiplyMM(mModelMatrix[i], 0, mModelMatrix[i-1], 0, mModelMatrix[i], 0);
            }
        }
    }

    /***
     * Touched coordinate in View == End-Effector ?
     * @param touchX
     * @param touchY
     */
    public void isClickEffector(float touchX, float touchY) {
        GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 1);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, mPickFBO[0]);
        GLES30.glReadBuffer(GLES30.GL_COLOR_ATTACHMENT0);
        GLES30.glReadPixels((int)touchX, mHeight - (int)touchY, 1, 1, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, mPickPixelColor);

        byte color[] = new byte[4];
        mPickPixelColor.get(color).position(0);

        if (color[0] == -1)
        {
            mHandler.sendMessage((Message.obtain(mHandler, 1)));
        }
    }

    /***
     * When touch and drag End-Effector,
     * convert its coordinate to GL coordinate and Update target location.
     * @param touchX
     * @param touchY
     */
    public void controlClick(float touchX, float touchY) {
        float[] vec = {(2.0f * touchX) / mWidth - 1.0f, 1.0f - (2.0f * touchY) / mHeight, 1.0f, 1};
        float[] inverseVPMMat = new float[16];
        Matrix.invertM(inverseVPMMat, 0, mVPMatrix, 0);

        float[] newVec = new float[4];
        Matrix.multiplyMV(newVec, 0, inverseVPMMat, 0, vec, 0);

        float posX = newVec[0] / newVec[3];
        float posY = newVec[1] / newVec[3];

        float distance = (float) Math.sqrt(posX * posX + posY * posY);
        if (distance > 30) {
            float angle = (float) Math.atan2(posY, posX);
            posX = 30 * (float) Math.cos(angle);
            posY = 30 * (float) Math.sin(angle);
        }
        targetX = posX;
        targetY = posY;
    }

    /***
     * Update Joint angle gradually to Target location.
     */
    public void inverseKinematics() {
        float[] targetPos = {targetX, targetY, 0.0f};
        float[] BeforeToAfterVec = vectorSubtract(targetPos, mJointPosition[3], 3);
        float[] JacobianMatrix = getJacobianTransMatrix();
        float[] deltaVec = MultiplyMV3(JacobianMatrix, BeforeToAfterVec);

        for (int i = 0; i < 3; i++) {
            mJointAngle[i] = mJointAngle[i] + deltaVec[i] * 0.05f;
            mJointAngle[i] = mJointAngle[i] % 360;
        }

        setModelMatrix();
    }

    public float[] getJacobianTransMatrix() {
        float[] RotAxis = {0.0f, 0.0f, 1.0f, 1.0f};

        float[] J_A = crossProduct(RotAxis, vectorSubtract(mJointPosition[3], mJointPosition[0], 3));
        float[] J_B = crossProduct(RotAxis, vectorSubtract(mJointPosition[3], mJointPosition[1], 3));
        float[] J_C = crossProduct(RotAxis, vectorSubtract(mJointPosition[3], mJointPosition[2], 3));

        float[] jacobianM = {
                J_A[0], J_A[1], J_A[2],
                J_B[0], J_B[1], J_B[2],
                J_C[0], J_C[1], J_C[2]
        };

        return jacobianM;
    }

    public float[] MultiplyMV3(float[] matrix, float[] vector) {
        float[] result = new float[3];
        for (int i = 0 ; i < 3; i++)
            result[i] = matrix[i * 3] * vector[0] + + matrix[i * 3 + 1] * vector[1] + matrix[i * 3 + 2] * vector[2];
        return result;
    }

    public float[] vectorSubtract(float[] vec1, float[] vec2, int size) {
        float[] result = new float[size];
        for (int i = 0; i < size; i++)
            result[i] = vec1[i] - vec2[i];
        return result;
    }

    public float[] crossProduct(float[] A, float[] B) {
        float[] C = new float[3];
        C[0] = A[1] * B[2] - A[2] * B[1];
        C[1] = A[2] * B[0] - A[0] * B[2];
        C[2] = A[0] * B[1] - A[1] * B[0];
        return C;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        int vShader, fShader;

        vShader = GLUtil.loadShader(GLES30.GL_VERTEX_SHADER, GLUtil.readShaderFromAsset(mContext, "ArmShader.v"));
        fShader = GLUtil.loadShader(GLES30.GL_FRAGMENT_SHADER, GLUtil.readShaderFromAsset(mContext, "ArmShader.f"));
        mArmShader = GLUtil.loadProgram(vShader, fShader);

        if (mArmShader == 0)
        {
            Log.e("ERROR", "Arm Shader is not linked");
            return;
        }

        mPickColorBuffer[0] = 0;
        GLES30.glGenFramebuffers(1, mPickFBO, 0);
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        mWidth = width;
        mHeight = height;

        float ratio = (float) width / height;
        Matrix.frustumM(mProjectionMatrix, 0, -ratio, ratio, -1, 1, 3, 110);
        Matrix.setLookAtM(mViewMatrix, 0, 0.0f, 0.0f, 100.0f, 0.0f ,0.0f, 0.0f, 0.0f, 1.0f, 0.0f);
        Matrix.multiplyMM(mVPMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);

        if (mPickColorBuffer[0] != 0)
            GLES30.glDeleteTextures(1, mPickColorBuffer, 0);

        GLES30.glGenTextures(1, mPickColorBuffer, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mPickColorBuffer[0]);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, mPickFBO[0]);
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, mPickColorBuffer[0], 0);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES30.glViewport(0, 0, mWidth, mHeight);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
        GLES30.glUseProgram(mArmShader);

        inverseKinematics();
        drawLink();
        drawJoint();
    }

    public void drawLink() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, mLinkVertexBuffer);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttrib4f(1, 0.0f, 1.0f, 0.0f, 0.0f);

        // LINK
        for (int i = 0; i < MAX_JOINT_NUM - 1; i++)
        {
            Matrix.multiplyMM(mMVPMatrix, 0, mVPMatrix, 0, mModelMatrix[i], 0);
            GLES30.glUniformMatrix4fv(0, 1, false, mMVPMatrix, 0);
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);
        }
        mLinkVertexBuffer.position(0);
    }

    public void drawJoint() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, mCircleVertexBuffer);
        GLES30.glEnableVertexAttribArray(0);

        // JOINT
        for (int i = 0; i < MAX_JOINT_NUM; i++)
        {
            mJointPosition[i][0] = 0; mJointPosition[i][1] = 0; mJointPosition[i][2] = 0; mJointPosition[i][3] = 1.0f;
            Matrix.multiplyMV(mJointPosition[i], 0, mModelMatrix[i], 0, mJointPosition[1], 0);
            Matrix.multiplyMM(mMVPMatrix, 0, mVPMatrix, 0, mModelMatrix[i], 0);
            GLES30.glUniformMatrix4fv(0, 1, false, mMVPMatrix, 0);
            GLES30.glVertexAttrib4f(1, mJointColor[0 + (4 * i)], mJointColor[1 + (4 * i)], mJointColor[2 + (4 * i)], mJointColor[3 + (4 * i)]);
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_FAN, 0, mNumCircleVertices);

            if (i == MAX_JOINT_NUM - 1)
            {
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, mPickFBO[0]);
                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_FAN, 0, mNumCircleVertices);
            }
        }
        mCircleVertexBuffer.position(0);
    }
}
