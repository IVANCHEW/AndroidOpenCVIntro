package com.example.ivan.opencvintro;

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
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;


public class MainActivity extends AppCompatActivity implements CvCameraViewListener2,  View.OnTouchListener {

    // Used for logging success or failure messages
    private static final String TAG = "OCVSample::Activity";

    // Loads camera view of OpenCV for us to use. This lets us see using OpenCV
    private CameraBridgeViewBase mOpenCvCameraView;

    // Used in Camera selection from menu (when implemented)
    private boolean              mIsJavaCamera = true;
    private MenuItem             mItemSwitchCamera = null;

    // These variables are used (at the moment) to fix camera orientation from 270degree to 0degree
    Mat mRgba;
    Mat mRgbaF;
    Mat mRgbaT;

    //For Color filtering
    private Mat mInRangeResult;
    private Mat mCurrentFrame;
    private Mat mFilteredFrame;
    private Mat mCurrentFrameHsv;
    private Scalar mLowerColorLimit;
    private Scalar mUpperColorLimit;
    private Point mSelectedPoint = null;
    Rect previewRect;

    // The threshold value for the lower and upper color limits
    public static final double THRESHOLD_LOW = 35;
    public static final double THRESHOLD_HIGH = 35;

    //For Contour Extraction
    private Mat mCannyOutput;
    public static final double CANNY_THRESHOLD_1 = 4000;
    public static final double CANNY_THRESHOLD_2 = 4000;

    //For Contour Extraction2
    private ColorBlobDetector    mDetector;
    private Scalar               CONTOUR_COLOR;

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
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mRgbaF = new Mat(height, width, CvType.CV_8UC4);
        mRgbaT = new Mat(width, width, CvType.CV_8UC4);
        /*mCurrentFrame = new Mat();
        mCurrentFrameHsv = new Mat();
        mFilteredFrame = new Mat();
        mInRangeResult = new Mat();*/
        mCurrentFrame = new Mat(height, width, CvType.CV_8UC4);
        mCurrentFrameHsv = new Mat(height, width, CvType.CV_8UC4);
        mFilteredFrame = new Mat(height, width, CvType.CV_8UC4);
        mInRangeResult = new Mat(height, width, CvType.CV_8UC4);
        previewRect = new Rect(0, 0, width, height);

        CONTOUR_COLOR = new Scalar(255,0,0,255);
        mDetector = new ColorBlobDetector();
    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
    }

    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        // TODO Auto-generated method stub
        //mRgba = inputFrame.rgba();

        // Rotate mRgba 90 degrees
        /*
        Core.transpose(mRgba, mRgbaT);
        Imgproc.resize(mRgbaT, mRgbaF, mRgbaF.size(), 0,0, 0);
        Core.flip(mRgbaF, mRgba, 1 );
        */
        mCurrentFrame = inputFrame.rgba();
        Imgproc.cvtColor(mCurrentFrame, mCurrentFrameHsv,Imgproc.COLOR_RGB2HSV);

        if (mLowerColorLimit == null && mUpperColorLimit == null && mSelectedPoint != null) {
            Log.d(TAG, "Start to retrieve Color limtis");
            double[] selectedColor = mCurrentFrameHsv.get((int) mSelectedPoint.x, (int) mSelectedPoint.y);
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

            //Log.d(TAG, "Selected points, X: " + mSelectedPoint.x + " Y: " + mSelectedPoint.y);
            //Log.d(TAG, "Current Matrix size, Cols: " + mCurrentFrameHsv.cols() + " Rows: " + mCurrentFrameHsv.rows());
            // ROI (Region Of Interest) is used to find the average value around the point we clicked.
            // This will reduce the risk of getting "freak" values if the pixel where we clicked has an unexpected value
            //Rect roiRect = new Rect((int) (mSelectedPoint.x - 2), (int) (mSelectedPoint.y - 2), 5, 5);
            Rect roiRect = new Rect((int) (mSelectedPoint.x-2),(int) (mSelectedPoint.y-2),5,5);
            // Get the Matrix representing the ROI
            Mat roi = mCurrentFrameHsv.submat(roiRect);
                        
            // Calculate the mean value of the the ROI matrix
            Scalar sumColor = Core.mean(roi);
            double[] sumColorValues = sumColor.val;

            // Decide on the color range based on the mean value from the ROI
            if (selectedColor != null) {
                mLowerColorLimit = new Scalar(sumColorValues[0] - THRESHOLD_LOW * 3,
                        sumColorValues[1] - THRESHOLD_LOW,
                        sumColorValues[2] - THRESHOLD_LOW);
                mUpperColorLimit = new Scalar(sumColorValues[0] + THRESHOLD_HIGH * 3,
                        sumColorValues[1] + THRESHOLD_HIGH,
                        sumColorValues[2] + THRESHOLD_HIGH);
            }
            Log.d(TAG, "Retrieved Color limits");
        }

        // If we have selected color, process the current frame using inRange function
        if (mLowerColorLimit != null && mUpperColorLimit != null) {
            Log.d(TAG, "Filtered matrix");
            // Using the color limits to generate a mask (mInRangeResult)
            Core.inRange(mCurrentFrameHsv, mLowerColorLimit, mUpperColorLimit, mInRangeResult);
            // Clear (set to black) the filtered image frame
            mFilteredFrame.setTo(new Scalar(0, 0, 0));
            // Copy the current frame in RGB to the filtered frame using the mask.
            // Only the pixels in the mask will be copied.
            mCurrentFrame.copyTo(mFilteredFrame, mInRangeResult);

            //Imgproc.Canny(mFilteredFrame, mCannyOutput,CANNY_THRESHOLD_1, CANNY_THRESHOLD_2);

            return mFilteredFrame;
            //return mCannyOutput;
        }else{
            return mCurrentFrame;
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

}
