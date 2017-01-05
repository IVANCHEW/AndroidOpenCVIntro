package com.example.ivan.opencvintro;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.util.Log;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.Mat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by ivan on 3/1/17.
 */
public class DisplayRenderer implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener{

    private static final String TAG = "Display Renderer";

    //FOR CAMERA

    private int                     displayWidth;
    private int                     displayHeight;
    private MainView                mView;
    private int[]                   hTex;
    private FloatBuffer             pVertex;
    private FloatBuffer             pTexCoord;
    private int                     hProgram;

    private Camera                  mCamera;
    private SurfaceTexture          mSTexture;

    private boolean                 mUpdateST = false;

    private final String vss =
            "attribute vec2 vPosition;\n" +
                    "attribute vec2 vTexCoord;\n" +
                    "varying vec2 texCoord;\n" +
                    "void main() {\n" +
                    "  texCoord = vTexCoord;\n" +
                    "  gl_Position = vec4 ( vPosition.x, vPosition.y, 0.0, 1.0 );\n" +
                    "}";

    private final String fss =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "varying vec2 texCoord;\n" +
                    "void main() {\n" +
                    "  gl_FragColor = texture2D(sTexture,texCoord);\n" +
                    "}";
    //FOR TRIANGLE DRAWING
    Triangle triangle1;

    public DisplayRenderer(MainView view, int width, int height )
    {
        Log.d(TAG,"Display Renderer Called, vertex init");
        displayHeight = height;
        displayWidth = width;
        mView = view;

        float[] vtmp = { 1.0f, -1.0f, -1.0f, -1.0f, 1.0f, 1.0f, -1.0f, 1.0f };
        float[] ttmp = { 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f };
        pVertex = ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        pVertex.put ( vtmp );
        pVertex.position(0);
        pTexCoord = ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        pTexCoord.put ( ttmp );
        pTexCoord.position(0);
    }

    @Override
    public void onSurfaceCreated(GL10 glUnused, EGLConfig config)
    {
        Log.d(TAG, "Renderer onSurfaceCreated Called");
        //START CAMERA
        initTex();
        mSTexture = new SurfaceTexture ( hTex[0] );
        mSTexture.setOnFrameAvailableListener(this);

        mCamera = Camera.open();
        try {
            Log.d(TAG,"Renderer Start Camera");
            mCamera.setPreviewTexture(mSTexture);
        } catch ( IOException ioe ) {
            Log.d(TAG,"Renderer Start Camera Error: " + ioe.toString());
        } catch (Exception e){
            Log.d(TAG,"Renderer Start Camera Error: " + e.toString());
        }

        GLES20.glClearColor ( 0.0f, 0.0f, 0.0f, 1.0f );
        hProgram = loadShader ( vss, fss );

        triangle1 = new Triangle();
    }

    @Override
    public void onSurfaceChanged(GL10 glUnused, int width, int height)
    {
        Log.d(TAG,"Renderer onSurfaceChanged Called");
        Log.d(TAG, "glSurfaceChanged Arguements, Width = " + width + " Height = " + height);
        // Set the OpenGL viewport to the same size as the surface.
        GLES20.glViewport(0, 0, width, height);
        Camera.Parameters param = mCamera.getParameters();
        //Get best Preview Size

        Camera.Size result = null;
        for (Camera.Size size : param.getSupportedPreviewSizes()){

            if (result==null){result=size;}
            else{
                int resultArea = result.width*result.height;
                int newArea = size.width*size.height;
                if (newArea>resultArea){
                    result = size;
                }
            }
            Log.d(TAG,"Avaiable Resolution: " + result.width + " by " + result.height);
        }
        param.setPreviewSize(result.width, result.height);

        //param.setPreviewSize(1980, 1080);
        param.set("orientation", "landscape");
        mCamera.setParameters ( param );
        mCamera.startPreview();

        triangle1.updateProjectionMatrix(width, height);

    }

    @Override
    public void onDrawFrame(GL10 glUnused)
    {
        Log.d(TAG,"Renderer onDrawFrame Called");
        GLES20.glClear( GLES20.GL_COLOR_BUFFER_BIT );

        GLES20.glUseProgram(hProgram);

        synchronized(this) {
            if ( mUpdateST ) {
                mSTexture.updateTexImage();
                mUpdateST = false;
            }
        }

        int ph = GLES20.glGetAttribLocation(hProgram, "vPosition");
        int tch = GLES20.glGetAttribLocation ( hProgram, "vTexCoord" );
        int th = GLES20.glGetUniformLocation ( hProgram, "sTexture" );

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, hTex[0]);
        GLES20.glUniform1i(th, 0);

        GLES20.glVertexAttribPointer(ph, 2, GLES20.GL_FLOAT, false, 4*2, pVertex);
        GLES20.glVertexAttribPointer(tch, 2, GLES20.GL_FLOAT, false, 4*2, pTexCoord );
        GLES20.glEnableVertexAttribArray(ph);
        GLES20.glEnableVertexAttribArray(tch);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glFlush();

        triangle1.draw();

    }

    public void close()
    {
       // mUpdateST = false;
        mSTexture.release();
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;
        //deleteTex();
    }

    private void initTex() {
        Log.d(TAG,"Renderer initTex Called");
        hTex = new int[1];
        GLES20.glGenTextures ( 1, hTex, 0 );
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, hTex[0]);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        Log.d(TAG,"Renderer onFrameAvailable Called");
        mUpdateST = true;
        mView.requestRender();
    }

    private static int loadShader ( String vss, String fss ) {
        int vshader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(vshader, vss);
        GLES20.glCompileShader(vshader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(vshader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e("Shader", "Could not compile vshader");
            Log.v("Shader", "Could not compile vshader:"+GLES20.glGetShaderInfoLog(vshader));
            GLES20.glDeleteShader(vshader);
            vshader = 0;
        }

        int fshader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fshader, fss);
        GLES20.glCompileShader(fshader);
        GLES20.glGetShaderiv(fshader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e("Shader", "Could not compile fshader");
            Log.v("Shader", "Could not compile fshader:"+GLES20.glGetShaderInfoLog(fshader));
            GLES20.glDeleteShader(fshader);
            fshader = 0;
        }

        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vshader);
        GLES20.glAttachShader(program, fshader);
        GLES20.glLinkProgram(program);

        return program;
    }

}


