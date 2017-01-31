package com.example.ivan.opencvintro;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.hardware.Camera;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;

import org.opencv.android.JavaCameraView;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity implements CvCameraViewListener2, View.OnTouchListener{

    // Used for logging success or failure messages
    private static final String TAG = "OCVSample::Activity";

    // Loads camera view of OpenCV for us to use. This lets us see using OpenCV
    private CameraBridgeViewBase mOpenCvCameraView;

    // Used in Camera selection from menu (when implemented)
    private boolean              mIsJavaCamera = true;
    private MenuItem             mItemSwitchCamera = null;
    private int                  itemSelected = 1;

    // These variables are used (at the moment) to fix camera orientation from 270degree to 0degree
    Mat mRgba;
    Mat mRgbaF;
    Mat mRgbaT;

    //For Color filtering
    private Mat                 mInRangeResult;
    private Mat                 mCurrentFrame;
    private Mat                 mFilteredFrame;
    private Mat                 mCurrentFrameHsv;
    private Scalar              mLowerColorLimit;
    private Scalar              mUpperColorLimit;
    private Point               mSelectedPoint = null;
    Rect                        previewRect;

    // The threshold value for the lower and upper color limits
    public static final double THRESHOLD_LOW = 35;
    public static final double THRESHOLD_HIGH = 35;

    //For Contour Extraction2
    private ColorBlobDetector    mDetector;
    private Scalar               CONTOUR_COLOR;
    private Scalar               POINT_COLOR;
    private Scalar               mBlobColorRgba;
    private Scalar               mBlobColorHsv;
    private Mat                  mSpectrum;
    private Size                 SPECTRUM_SIZE;
    private Boolean              mIsColorSelected;
    private Size                 OriginalSize;
    private int                  mHeight;
    private int                  mWidth;

    //For Camera Callibration
    private int                 numCornersHor;
    private int                 numCornersVer;
    private Size                mPatternSize;
    private MatOfPoint3f        obj;
    private int                 numSquares;
    private int                 mCornersSize;
    private boolean             mPatternWasFound;
    private MatOfPoint2f        mCorners;
    private List<Mat>           imagePoints;
    private List<Mat>           objectPoints;
    private Mat                 intrinsic;
    private Mat                 distCoeffs;
    private int                 callibrationFrameCount;
    private boolean             callibratedCamera;
    /*
    private List<Mat>           mCornersBuffer = new ArrayList<Mat>();
    private boolean             mIsCalibrated = false;
    private int                 mFlags;
    private double              mRms;
    private double              mSquareSize = 0.0181;
    private Size                mImageSize;
    */
    private Mat                 greyScale;

    //For Pose Estimation
    private Mat                  cameraMatrix;

    //For openGL
    private MainView mGLSurfaceView;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public MainActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "called onCreate");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        /*
        // Check if the system supports OpenGL ES 2.0.
        final ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        final ConfigurationInfo configurationInfo = activityManager.getDeviceConfigurationInfo();
        final boolean supportsEs2 = configurationInfo.reqGlEsVersion >= 0x20000;
        if (supportsEs2)
        {
            Log.i(TAG, "OpenGL ES2 Supported");
        }
        else
        {
            // This is where you could create an OpenGL ES 1.x compatible
            // renderer if you wanted to support both ES 1 and ES 2.
            return;
        }
        mGLSurfaceView = new MainView(this);
        setContentView(mGLSurfaceView);
        */

        setContentView(R.layout.show_camera);

        mOpenCvCameraView = (JavaCameraView) findViewById(R.id.show_camera_activity_java_surface_view);

        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);

        mOpenCvCameraView.setCvCameraViewListener(this);

        mOpenCvCameraView.setOnTouchListener(this);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        Log.d(TAG, "Width: " + width + " Height: " + height);
        mHeight=height;
        mWidth=width;

        //COLOR FILTER PARAMETERS
        mRgba                   = new Mat(height, width, CvType.CV_8UC4);
        mRgbaF                  = new Mat(height, width, CvType.CV_8UC4);
        mRgbaT                  = new Mat(width, width, CvType.CV_8UC4);
        mCurrentFrame           = new Mat(height, width, CvType.CV_8UC4);
        mCurrentFrameHsv        = new Mat(height, width, CvType.CV_8UC4);
        mFilteredFrame          = new Mat(height, width, CvType.CV_8UC4);
        mInRangeResult          = new Mat(height, width, CvType.CV_8UC4);
        previewRect             = new Rect(0, 0, width, height);
        OriginalSize            = new Size(width, height);
        CONTOUR_COLOR           = new Scalar(255,0,0,255);
        POINT_COLOR             = new Scalar(0, 255, 0 ,255);
        mBlobColorRgba          = new Scalar(255);
        mBlobColorHsv           = new Scalar(255);
        mDetector               = new ColorBlobDetector();
        mSpectrum               = new Mat();
        SPECTRUM_SIZE           = new Size(200, 64);
        mIsColorSelected        = false;

        //CAMERA CALLIBRATION PARAMETERS
        greyScale               = new Mat(height, width, CvType.CV_8UC4);
        callibrationFrameCount  = 0;
        callibratedCamera       = false;
        numCornersHor           = 9;
        numCornersVer           = 6;
        numSquares              = numCornersHor * numCornersVer;
        obj                     = new MatOfPoint3f();
        for (int j = 0; j < numSquares; j++)
            obj.push_back(new MatOfPoint3f(new Point3(j / numCornersHor, j % numCornersVer, 0.0f)));
        mPatternSize            = new Size(numCornersHor, numCornersVer);
        mCornersSize            = (int)(mPatternSize.width * mPatternSize.height);
        mPatternWasFound        = false;
        mCorners                = new MatOfPoint2f();
        imagePoints             = new ArrayList<>();
        objectPoints            = new ArrayList<>();
        intrinsic               = new Mat(3, 3, CvType.CV_32FC1);
        distCoeffs              = new Mat();
    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
    }

    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        // TODO Auto-generated method stub
        mCurrentFrame = inputFrame.rgba();

        //COLOR SELECTING CODE
        if (mIsColorSelected==false && mSelectedPoint!=null && itemSelected==1) {
            Log.d(TAG, "Start to retrieve Color limtis");
            Imgproc.cvtColor(mCurrentFrame, mCurrentFrameHsv,Imgproc.COLOR_RGB2HSV);

            // We check the colors in a 5x5 pixels square (Region Of Interest) and get the average from that
            if (mSelectedPoint.x < 2) {
                mSelectedPoint.x = 2;
            } else if (mSelectedPoint.x >= (previewRect.width - 2)) {
                mSelectedPoint.x = previewRect.width - 2;
            }
            if (mSelectedPoint.y < 2) {
                mSelectedPoint.y = 2;
            } else if (mSelectedPoint.y >= (previewRect.height - 2)) {
                mSelectedPoint.y = previewRect.height - 2;
            }

            double[] selectedColor = mCurrentFrameHsv.get((int) mSelectedPoint.x, (int) mSelectedPoint.y);
            Rect roiRect = new Rect((int) (mSelectedPoint.x-2),(int) (mSelectedPoint.y-2),5,5);
            Mat roi = mCurrentFrameHsv.submat(roiRect);

            //COLOR BLOB SEGMENTATION EXAMPLE
            mBlobColorHsv = Core.sumElems(roi);
            int pointCount = roiRect.width*roiRect.height;
            for (int i = 0; i < mBlobColorHsv.val.length; i++)
                mBlobColorHsv.val[i] /= pointCount;
            mBlobColorRgba = convertScalarHsv2Rgba(mBlobColorHsv);
            Log.i(TAG, "ROI rgba color: (" + mBlobColorRgba.val[0] + ", " + mBlobColorRgba.val[1] +
                    ", " + mBlobColorRgba.val[2] + ", " + mBlobColorRgba.val[3] + ")");
            mDetector.setHsvColor(mBlobColorHsv);
            Imgproc.resize(mDetector.getSpectrum(), mSpectrum, SPECTRUM_SIZE);
            mIsColorSelected = true;

            Log.d(TAG, "Retrieved Color limits");
            mLowerColorLimit = mDetector.getmLowerBound();
            mUpperColorLimit = mDetector.getmUpperBound();
        }

        //CAMERA CALLIBRATION
        if (callibrationFrameCount==11 && !callibratedCamera){

            List<Mat> rvecs = new ArrayList<>();
            List<Mat> tvecs = new ArrayList<>();
            intrinsic.put(0, 0, 1);
            intrinsic.put(1, 1, 1);
            Calib3d.calibrateCamera(objectPoints, imagePoints, greyScale.size(), intrinsic, distCoeffs, rvecs, tvecs);
            callibratedCamera = true;
            Log.d(TAG, "Camera Callibrated, intrinsic parameters: ");


            int tempcount = 0;
            for (int i=0 ; i<intrinsic.size().height ; i++) {
                for(int j=0 ; j<intrinsic.size().width ; j++){
                    double tempPrint[] = intrinsic.get(i,j);
                    Log.d(TAG, "Length of double: " + tempPrint.length);
                    for (int k=0 ; k<tempPrint.length ; k++) {
                        Log.d(TAG, "Param: " + tempcount + " " + tempPrint[k]);
                        tempcount = tempcount + 1;
                    }
                }
            }

            Log.d(TAG, "Distortion Coefficients: ");
            tempcount = 0;
            for (int i=0 ; i<distCoeffs.size().height ; i++) {
                for(int j=0 ; j<distCoeffs.size().width ; j++){
                    double tempPrint[] = distCoeffs.get(i,j);
                    Log.d(TAG, "Length of double: " + tempPrint.length);
                    for (int k=0 ; k<tempPrint.length ; k++) {
                        Log.d(TAG, "Param: " + tempcount + " " + tempPrint[k]);
                        tempcount = tempcount + 1;
                    }
                }
            }

        }

        //IMAGE PROCESSING SCHEME ONCE SEGMENTATION COLOR IS DETERMINED
        if (mIsColorSelected) {
            //Log.d(TAG, "Color selected, on camera frame");

            //STEP 1: FILTER COLOUR FROM ORIGINAL IMAGE
            Imgproc.cvtColor(mCurrentFrame, mCurrentFrameHsv,Imgproc.COLOR_RGB2HSV);
            Core.inRange(mCurrentFrameHsv, mLowerColorLimit, mUpperColorLimit, mInRangeResult);
            mFilteredFrame.setTo(new Scalar(0, 0, 0));
            mCurrentFrame.copyTo(mFilteredFrame, mInRangeResult);

            //STEP 2: RETRIEVE AND DISPLAY CONTOURS
            mDetector.process(mInRangeResult);
            List<MatOfPoint> contours = mDetector.getContours();

            for (int i =0; i<contours.size(); i++){
                MatOfPoint c = new MatOfPoint(contours.get(i));
                Point[] p = c.toArray();
                Log.d(TAG,"Size of contour " + i + " is: " + p.length);
                Log.d(TAG,"Points of contour are: ");
                for (int j=0; j<p.length; j++) {
                    Log.d(TAG, "Point " + j + " " + p[j]);
                    Imgproc.circle(mFilteredFrame, p[j], 4, POINT_COLOR, 4);
                }
            }
            Imgproc.drawContours(mFilteredFrame, contours, -1, CONTOUR_COLOR, 3);

            Mat colorLabel = mFilteredFrame.submat(4, 68, 4, 68);
            colorLabel.setTo(mBlobColorRgba);
            Mat spectrumLabel = mFilteredFrame.submat(4, 4 + mSpectrum.rows(), 70, 70 + mSpectrum.cols());
            mSpectrum.copyTo(spectrumLabel);
            //STEP 3: RETURN PROCESSED IMAGE FOR DISPLAY
            return mFilteredFrame;
        }

        else if (mSelectedPoint != null && itemSelected == 0 && callibrationFrameCount<11) {
            //IMAGE CALLIBRATION TEST
            Log.d(TAG,"Attempt to detect pattern size: " + mPatternSize.width + " by " + mPatternSize.height);
            Imgproc.cvtColor(mCurrentFrame, greyScale, Imgproc.COLOR_BGR2GRAY);
            //mPatternWasFound = Calib3d.findChessboardCorners(greyScale, mPatternSize, mCorners);
            //mPatternWasFound = Calib3d.findCirclesGrid(greyScale, mPatternSize, mCorners, Calib3d.CALIB_CB_ASYMMETRIC_GRID);
            mPatternWasFound = Calib3d.findChessboardCorners(greyScale, mPatternSize, mCorners,
                    Calib3d.CALIB_CB_ADAPTIVE_THRESH + Calib3d.CALIB_CB_NORMALIZE_IMAGE + Calib3d.CALIB_CB_FAST_CHECK);
            if(mPatternWasFound){
                Calib3d.drawChessboardCorners(greyScale, mPatternSize, mCorners, mPatternWasFound);
                imagePoints.add(mCorners);
                objectPoints.add(obj);
                callibrationFrameCount = callibrationFrameCount + 1;
                Log.d(TAG,"Pattern found");
                Log.d(TAG,"Size of corners: " + mCorners.size());
                Log.d(TAG, "Number of callibration frames: " + callibrationFrameCount);
            }else{
                Log.d(TAG,"Unable to find pattern");
                Log.d(TAG,"Size of corners: " + mCorners.size());
            }
            mPatternWasFound = false;
            return greyScale;
        }

        else{

            return  mCurrentFrame;

        }
    }


    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        int xSelected = (int) motionEvent.getX();
        int ySelected = (int) motionEvent.getY();
        Log.d(TAG, "Motion event detected, X: " + xSelected + " Y: " + ySelected);
        setSelectedPoint(xSelected, ySelected);
        return false;
    }


    public void setSelectedPoint(double x, double y) {
        mLowerColorLimit = null;
        mUpperColorLimit = null;
        mSelectedPoint = new Point(x, y);
    }

    private Scalar convertScalarHsv2Rgba(Scalar hsvColor) {
        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4);
        return new Scalar(pointMatRgba.get(0, 0));
    }

}
