package com.wzq.audioavideo.video;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES10;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * ProjectName: AudioAvideo
 * Package: com.wzq.audioavideo.video
 * ClassName: CameraGlSurfaceView
 * Description: java类作用描述
 * Author: WZQ
 * CreateDate: 2020/7/16 9:35
 * Version: 1.0
 */
public class CameraGlSurfaceView extends GLSurfaceView {
    private static final String TAG = "CameraGlSurfaceView";
    private CameraRenderer mCameraRenderer;


    private CameraHandlerThread mCameraHandlerThread = new CameraHandlerThread();
    private Handler mCameraHandler;
    private int CAMERA_ID = 1;
    private int width = 1080;
    private int height = 1920;

    public CameraGlSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);


        setEGLContextClientVersion(2);
        mCameraRenderer = new CameraRenderer();
        setRenderer(mCameraRenderer);


    }


    class CameraRenderer implements Renderer, SurfaceTexture.OnFrameAvailableListener {
        public int textureId;
        public SurfaceTexture mSurfaceTexture;
        boolean updateTexImage;

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {

            final String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);    // API >= 8
            if (!extensions.contains("OES_EGL_image_external"))
                throw new RuntimeException("This system does not support OES_EGL_image_external.");
            textureId = Draw.initText();
            mSurfaceTexture = new SurfaceTexture(textureId);
            mSurfaceTexture.setOnFrameAvailableListener(this);
            GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
            // opengl es 创建
            Draw.createConfig();
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES10.glViewport(0, 0, width, height);
            mCameraHandlerThread.startCamera();
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            if (updateTexImage) {
                // 相机更新数据 在更新纹理
                updateTexImage = false;
                mSurfaceTexture.updateTexImage();
            }
            Draw.draw();

            Log.e(TAG, "onDrawFrame");
        }

        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            // 纹理更新
            updateTexImage = true;
            Log.e(TAG, "onFrameAvailable");
        }
    }

    static class Draw {


        private static final String vss
                =
                "attribute highp vec4 aPosition;\n"
                        + "attribute highp vec4 aTextureCoord;\n"
                        + "varying highp vec2 vTextureCoord;\n"
                        + "\n"
                        + "void main() {\n"
                        + "	gl_Position = aPosition;\n"
                        + "	vTextureCoord =aTextureCoord.xy;\n"
                        + "}\n";
        private static final String fss
                = "#extension GL_OES_EGL_image_external : require\n"
                + "precision mediump float;\n"
                + "uniform samplerExternalOES sTexture;\n"
                + "varying highp vec2 vTextureCoord;\n"
                + "void main() {\n"
                + "  gl_FragColor = texture2D(sTexture, vTextureCoord);\n"
                + "}";

        // 顶点坐标
        private static final float[] VERTICES = {1.0f, 1.0f, -1.0f, 1.0f, 1.0f, -1.0f, -1.0f, -1.0f};
        // 纹理坐标
        private static final float[] TEXCOORD = {1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f};
        private static FloatBuffer pVertex;
        private static FloatBuffer pTexCoord;
        private static int program;
        static int maPositionLoc;
        static int maTextureCoordLoc;
        static int muMVPMatrixLoc;
        static int muTexMatrixLoc;
        private final float[] mMvpMatrix = new float[16];

        private static final int FLOAT_SZ = Float.SIZE / 8;
        private static final int VERTEX_NUM = 4;
        private static final int VERTEX_SZ = VERTEX_NUM * 2;

        private static int textureId;

        /**
         * 创建纹理
         *
         * @return 纹理id
         */
        public static int initText() {
            int[] text = new int[1];
            GLES20.glGenTextures(1, text, 0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, text[0]);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
            textureId = text[0];
            return text[0];
        }


        /**
         * gles 配置
         */
        public static void createConfig() {

            pVertex = ByteBuffer.allocateDirect(VERTEX_SZ * FLOAT_SZ).order(ByteOrder.nativeOrder()).asFloatBuffer();
            pVertex.put(VERTICES).flip();
            pTexCoord = ByteBuffer.allocateDirect(VERTEX_SZ * FLOAT_SZ).order(ByteOrder.nativeOrder()).asFloatBuffer();
            pTexCoord.put(TEXCOORD).flip();

            // 创建着色器程序
            program = crateProgram();

            // 使用找色器程序
            GLES20.glUseProgram(program);

            // 定位参数
            maPositionLoc = GLES20.glGetAttribLocation(program, "aPosition");
            maTextureCoordLoc = GLES20.glGetAttribLocation(program, "aTextureCoord");
            muMVPMatrixLoc = GLES20.glGetUniformLocation(program, "uMVPMatrix");
            muTexMatrixLoc = GLES20.glGetUniformLocation(program, "uTexMatrix");


            // 设置顶点数据
            GLES20.glVertexAttribPointer(maPositionLoc, 2, GLES20.GL_FLOAT, false, VERTEX_SZ, pVertex);
            GLES20.glVertexAttribPointer(maTextureCoordLoc, 2, GLES20.GL_FLOAT, false, VERTEX_SZ, pTexCoord);

            // 使用顶点
            GLES20.glEnableVertexAttribArray(maPositionLoc);
            GLES20.glEnableVertexAttribArray(maTextureCoordLoc);
        }

        /**
         * 创建着色器程序
         *
         * @return 着色器程序id
         */
        public static int crateProgram() {
            // 顶点着色
            int vertex = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
            GLES20.glShaderSource(vertex, vss);
            GLES20.glCompileShader(vertex);

            final int[] compiled = new int[1];
            GLES20.glGetShaderiv(vertex, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                Log.e(TAG, "Failed to compile vertex shader:"
                        + GLES20.glGetShaderInfoLog(vertex));
                GLES20.glDeleteShader(vertex);
                vertex = 0;
            }


            // 片段着色
            int fragment = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
            GLES20.glShaderSource(fragment, fss);
            GLES20.glCompileShader(fragment);

            GLES20.glGetShaderiv(fragment, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                Log.e(TAG, "Failed to compile fragment shader:"
                        + GLES20.glGetShaderInfoLog(fragment));
                GLES20.glDeleteShader(fragment);
                fragment = 0;
            }

            // 着色程序
            int program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vertex);
            GLES20.glAttachShader(program, fragment);
            GLES20.glLinkProgram(program);
            // 删除 顶点、片段着色
            GLES20.glDeleteShader(vertex);
            GLES20.glDeleteShader(fragment);

            return program;
        }


        public static void draw() {
            GLES20.glUseProgram(program);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_NUM);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
            GLES20.glUseProgram(0);
        }
    }


    class CameraHandlerThread {
        public void startCamera() {
            if (mCameraHandler == null) {
                HandlerThread handlerThread = new HandlerThread("CameraThread");
                handlerThread.start();
                mCameraHandler = new Handler(handlerThread.getLooper());
            }
            mCameraHandler.post(() -> {
                Camera camera = Camera.open(CAMERA_ID);
                try {
                    Camera.Parameters params = camera.getParameters();


                    // 自动对焦
                    final List<String> focusModes = params.getSupportedFocusModes();
                    if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                        params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                    } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                        params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                    } else {
                        Log.i(TAG, "Camera does not support autofocus");
                    }


                    // 设置fps
                    // let's try fastest frame rate. You will get near 60fps, but your device become hot.
                    final List<int[]> supportedFpsRange = params.getSupportedPreviewFpsRange();
//					final int n = supportedFpsRange != null ? supportedFpsRange.size() : 0;
//					int[] range;
//					for (int i = 0; i < n; i++) {
//						range = supportedFpsRange.get(i);
//						Log.i(TAG, String.format("supportedFpsRange(%d)=(%d,%d)", i, range[0], range[1]));
//					}
                    final int[] max_fps = supportedFpsRange.get(supportedFpsRange.size() - 1);
                    Log.i(TAG, String.format("fps:%d-%d", max_fps[0], max_fps[1]));
                    params.setPreviewFpsRange(max_fps[0], max_fps[1]);


                    // 设置大小
                    final Camera.Size closestSize = getClosestSupportedSize(
                            params.getSupportedPreviewSizes(), width, height);
                    params.setPreviewSize(closestSize.width, closestSize.height);
                    // request closest picture size for an aspect ratio issue on Nexus7
                    final Camera.Size pictureSize = getClosestSupportedSize(
                            params.getSupportedPictureSizes(), width, height);
                    params.setPictureSize(pictureSize.width, pictureSize.height);

                    // 设置录制模式
                    params.setRecordingHint(true);


                    // 设置旋转
                    setRotation(camera, params);
                    camera.setParameters(params);

                    camera.setPreviewTexture(mCameraRenderer.mSurfaceTexture);
                    camera.startPreview();
                } catch (IOException e) {
                    e.printStackTrace();
                }


            });
        }


        private final void setRotation(Camera camera, final Camera.Parameters params) {

            final Display display = ((WindowManager) getContext()
                    .getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
            final int rotation = display.getRotation();
            int degrees = 0;
            switch (rotation) {
                case Surface.ROTATION_0:
                    degrees = 0;
                    break;
                case Surface.ROTATION_90:
                    degrees = 90;
                    break;
                case Surface.ROTATION_180:
                    degrees = 180;
                    break;
                case Surface.ROTATION_270:
                    degrees = 270;
                    break;
            }
            // get whether the camera is front camera or back camera
            final Camera.CameraInfo info =
                    new android.hardware.Camera.CameraInfo();
            android.hardware.Camera.getCameraInfo(CAMERA_ID, info);
            boolean mIsFrontFace = (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT);
            if (mIsFrontFace) {    // front camera
                degrees = (info.orientation + degrees) % 360;
                degrees = (360 - degrees) % 360;  // reverse
            } else {  // back camera
                degrees = (info.orientation - degrees + 360) % 360;
            }
            // apply rotation setting
            camera.setDisplayOrientation(degrees);
            // XXX This method fails to call and camera stops working on some devices.
//			params.setRotation(degrees);
        }


        private Camera.Size getClosestSupportedSize(List<Camera.Size> supportedSizes, final int requestedWidth, final int requestedHeight) {
            return (Camera.Size) Collections.min(supportedSizes, new Comparator<Camera.Size>() {

                private int diff(final Camera.Size size) {
                    return Math.abs(requestedWidth - size.width) + Math.abs(requestedHeight - size.height);
                }

                @Override
                public int compare(final Camera.Size lhs, final Camera.Size rhs) {
                    return diff(lhs) - diff(rhs);
                }
            });

        }
    }
}
