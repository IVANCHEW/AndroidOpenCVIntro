package com.example.ivan.opencvintro;

import android.content.Context;
import android.opengl.GLSurfaceView;

/**
 * Created by ivan on 4/1/17.
 */
public class MainView extends GLSurfaceView {

    private final DisplayRenderer mRenderer;

    public MainView(Context context){
        super(context);

        // Create an OpenGL ES 2.0 context
        setEGLContextClientVersion(2);

        mRenderer = new DisplayRenderer(this, 1980, 1080);

        // Set the Renderer for drawing on the GLSurfaceView
        setRenderer(mRenderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }
}

