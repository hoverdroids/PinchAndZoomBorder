package com.hoverdroids.pinchandzoomborder;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Build;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.graphics.PointF;

public class PinchZoomImageView extends AppCompatImageView implements View.OnTouchListener{

    private static enum PinchZoomMode {
        NONE, DRAG, ZOOM
    }
    private PinchZoomMode mode;

    private Context context;
    private PinchZoomImageView imageView = this;
    private Paint regionPaint = new Paint();
    private Path regionPath = new Path();

    private int imgHeightOrig;
    private int imgWidthOrig;

    private int viewHeight;
    private int viewWidth;

    private float scaleCurr;//total scale relative to the first image
    private Matrix matrix = new Matrix();
    private float[] imgBoundary = new float[8];//8 because of 4 points of (x,y) to make [x0, y0, ... , x3, y3] - since matrix mapping requires it

    private ScaleGestureDetector scaleDetector;
    private PointF lastTouch = new PointF();
    private PointF startTouch = new PointF();

    private float maxScale = 3f;
    private float minScale = 0.75f;

    public PinchZoomImageView(Context context){
        super(context);
        init(context);
    }

    public PinchZoomImageView(Context context, AttributeSet attrs){
        super(context, attrs);
        init(context);
    }

    public PinchZoomImageView(Context context, AttributeSet attrs, int defStyle){
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context){
        this.context = context;

        //Initialize the paint
        regionPaint.setColor(getResources().getColor(R.color.colorAccent));
        regionPaint.setStrokeWidth(5);
        regionPaint.setStyle(Paint.Style.STROKE);
    }

    //Nothing works without a background image being set first
    public void initImageResource(final int drawableResourceId){
        /*
         * Since initImageResource can be called before the layout has dimensions, all operations that depend on the view's
         * dimensions must come after the layout is inflated
         */
        OnGlobalLayoutListener ogll = new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                //Destroy the listener before anything else - otherwise it keeps listening and triggering
                ViewTreeObserver vto = imageView.getViewTreeObserver();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                    vto.removeOnGlobalLayoutListener(this);
                } else {
                    vto.removeOnGlobalLayoutListener(this);
                }

                //Before decoding image, get resources but desity isn't required since BitmapFactory scales the image before returning it
                Resources res = context.getResources();

                //Create a bitmap from resource and get its ddimensions - dimensions are in px after being scaled using ppi
                BitmapFactory.Options dimensions = new BitmapFactory.Options();
                Bitmap bm = BitmapFactory.decodeResource(res, drawableResourceId, dimensions);

                imgHeightOrig = (int) ((float) dimensions.outHeight);//pixels after being scaled
                imgWidthOrig = (int) ((float) dimensions.outWidth);//pixels after being scaled

                //Remember that this is the view's size, not theat of the screen or image
                viewHeight = getHeight();
                viewWidth = getWidth();

                //Now that the view's dimensions are known, complete the initialization process
                //TODO First scale the image to fit the screen. This should have scaling options like fill entire screen or min dimension
                float scaleXInit = (float) viewWidth / (float) imgWidthOrig;
                float scaleYInit = (float) viewHeight / (float) imgHeightOrig;
                scaleCurr = Math.min(scaleXInit, scaleYInit);

                //Calculate top and left margin in px to center the image, whether or not the image is larger than the view
                float imgXStart = (viewWidth - imgWidthOrig) / 2;
                float imgYStart = (viewHeight - imgHeightOrig) / 2;

                //Initialize the matrix that transforms everything
                matrix.reset();//ensures the matrix start at the unity matrix
                matrix.setTranslate(imgXStart, imgYStart);
                matrix.postScale(scaleCurr, scaleCurr, viewWidth / 2, viewHeight / 2);

                //Draw the resource image for the first time
                setScaleType(ScaleType.MATRIX);//This only applies to the resource
                setImageMatrix(matrix);
                setImageBitmap(bm);//setImageBitmap instead of setImageResource because it's more intuitive
                bm = null;

                //Now that the image is ready, set the listeners for zooming and panning
                setOnTouchListener(imageView);
                scaleDetector = new ScaleGestureDetector(context, new ScaleListener());

                //Now create the points that define the border of the image. This will be used for drawing regions
                initImagePoints();

                //Show that the dimensions were calculated correctly
                //Just some debug stuff to verify dimensions are correct
                Log.d("Verify Img Dims", "loadedImgVals-H:" + dimensions.outHeight + ",W:"+dimensions.outWidth);
                Log.d("Verify Img Dims", "originalImgVals-H:" + imgHeightOrig + ",W:" + imgWidthOrig);
                Log.d("Verify Img Dims", "viewH_px:"+ viewHeight + ",w_px:" + viewWidth);
                Log.d("Verify Img Dims", "initScale:" + scaleCurr + ",imgXstart:" + imgXStart + ",imgYstart" + imgYStart);
            }
        };
        ViewTreeObserver vto = getViewTreeObserver();
        vto.addOnGlobalLayoutListener(ogll);
    }

    private void initImagePoints(){
        /*
         * Define the boundary points for the image being used, before being mapped but after bitmapFactory resized due to ppi
         * These boundary points will not be modified! Instead, the matrix will hold the total mapping and then map these points
         * Not prettiest way to define the 4 boundary points, but works well since this is going to be mapped with a matrix
         */
        imgBoundary[0] = 0; //FrameLayout top-left is 0,0
        imgBoundary[1] = 0;
        
        imgBoundary[2] = imgWidthOrig;
        imgBoundary[3] = 0;

        imgBoundary[4] = imgWidthOrig;
        imgBoundary[5] = imgHeightOrig;

        imgBoundary[6] = 0;
        imgBoundary[7] = imgHeightOrig;

        //Determine the scaled height and width to verify that the points are mapping correctly
        float imgHeightScaled = imgHeightOrig * scaleCurr;
        float imgWidthScaled = imgWidthOrig * scaleCurr;
        Log.d("Initial img Bounds", "imgHeightScaled:" + imgHeightScaled + "imgWidthScaled:" + imgWidthScaled);

        //Show the initial creation of the boundary points
        int j = 0;
        for (int i = 0; i < imgBoundary.length/2; i++){
            Log.d("Initial img Bounds", "imgBoundaryPoints...x:" + imgBoundary[j] + ",y:" + imgBoundary[j+1]);
            j = j + 2;
        }

        //TODO remove this
        setRegionMatrix();
    }

    private void setRegionMatrix(){
        //Create a temp copy of the boundary points in order to map them
        float[] imgBndPts_mapped = new float[8];//same numbrer of points will be mapped since we are only mapping the image's 4 corners
        for(int i = 0; i < imgBoundary.length; i++){
            imgBndPts_mapped[i] = imgBoundary[i];
        }

        //Map the initial image boundary points using the matrix
        matrix.mapPoints(imgBndPts_mapped);

        //Use temporarily mapped point to create a path
        regionPath.reset();//reset each time or else we get a different shape than desired

        //TODO clean this so that it's a dynamically adjustable loop
        regionPath.moveTo(imgBndPts_mapped[0],imgBndPts_mapped[1]);
        regionPath.lineTo(imgBndPts_mapped[2],imgBndPts_mapped[3]);
        regionPath.lineTo(imgBndPts_mapped[4],imgBndPts_mapped[5]);
        regionPath.lineTo(imgBndPts_mapped[6],imgBndPts_mapped[7]);
        regionPath.lineTo(imgBndPts_mapped[0],imgBndPts_mapped[1]);//since its a stroke we need to return back to first point

        //Show the mapping of boundary points
        int j = 0;
        for (int i = 0; i < imgBoundary.length/2; i++){
            Log.d("Mapped img Bnds", "imgBndPts_mapped: x:"+imgBndPts_mapped[j] + ",y:"+ imgBndPts_mapped[j+1]);
            j = j +2;
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        //Ensures resource image gets drawn
        super.onDraw(canvas);

        //Draw the stroke around the image
        //TODO this is creating an error in Android 4.3
        canvas.drawPath(regionPath, regionPaint);
    }

    //OnTouchListener implementation
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        //TouchListener consumes the touch so it must get passed
        scaleDetector.onTouchEvent(event);

        //Record the current touch point relative to the iew
        PointF currentTouch = new PointF(event.getX(), event.getY());

        //Determine if the image is being moved and set the translation amount
        switch(event.getAction()){
            case MotionEvent.ACTION_DOWN:
                //Set start and last to the touchpoint when first pressed
                lastTouch.set(currentTouch);
                startTouch.set(currentTouch);
                mode = PinchZoomMode.DRAG;
                break;
            case MotionEvent.ACTION_MOVE:
                if(mode == PinchZoomMode.DRAG){
                    //Determine the amount moved from the previous position
                    float deltaX = currentTouch.x - lastTouch.x;
                    float deltaY = currentTouch.y - lastTouch.y;

                    //Prevent panning in x when image is smaller than the view
                    if(imgWidthOrig * scaleCurr <= viewWidth){
                        deltaX = 0;
                    }
                    //Prevent panning in y when image is smaller than the view
                    if(imgHeightOrig * scaleCurr <= viewHeight){
                        deltaY = 0;
                    }

                    //Translate the matrix in accordance with above calculations/input
                    matrix.postTranslate(deltaX, deltaY);

                    //Prevent panning outside of the image by pinning the boundaries
                    adjustTranslation();

                    //Finally, set the touchpoints for the next movement
                    lastTouch.set(currentTouch.x, currentTouch.y);
                }
                break;
            case MotionEvent.ACTION_UP:
                mode = PinchZoomMode.NONE;
                break;
            case MotionEvent.ACTION_POINTER_UP:
                mode = PinchZoomMode.NONE;
                break;
        }

        //Adjust the image now that its matrix has been modified
        setRegionMatrix();
        setImageMatrix(matrix);

        //Invalidate in order to redraw the image
        invalidate();

        //Indicate the event was handled
        return true;
    }

    private void adjustTranslation(){
        //Create a float array to hold the matrix values - i.e. scaling and translation info
        float[] matrixValues = new float[9];

        //Get the matrix values. These are not the translated data, just the scaling and translation amount that maps the initial data to current
        matrix.getValues(matrixValues);

        //Determine the absolute translation values in x and y
        float translationX = matrixValues[Matrix.MTRANS_X];
        float translationY = matrixValues[Matrix.MTRANS_Y];

        //Determine the image size as currently shown on the screen
        float scaledImageHeight = imgHeightOrig * scaleCurr;
        float scaledImageWidth = imgWidthOrig * scaleCurr;

        //TODO This code can be condensed but is much harder to read
        //Determine the amount of translation allowed in x, based on the scaled image size vs the view size
        float minTranslationX, maxTranslationX;
        if(scaledImageWidth <= viewWidth){//if scaled image fits in view horizontally then...
            minTranslationX = 0;//left boundary is the parent's left boundary-i.e. if the image is smaller than the parent it can only translate between the parent's bounds
            maxTranslationX = viewWidth - scaledImageWidth;//max translation is therefore the amount of space left between parent and scaled image
        }else{
            minTranslationX = viewWidth - scaledImageWidth;//the most translation that can happen is the amount of the image not currently seen on scree
            maxTranslationX = 0;//the left boundary of the scaled image cannot go past the left boundary of the view, since we know the scaled image bound must be negative
        }

        //Determine if the image needs t be adjusted in the x direction
        float updatedTranslationX = 0; //don't translate unless the x boundary is hit
        if(translationX < minTranslationX){
            updatedTranslationX = -translationX + minTranslationX;
        }else if(translationX > maxTranslationX){
            updatedTranslationX = -translationX + maxTranslationX;
        }

        //Do the same for y
        float minTranslationY, maxTranslationY;
        if(scaledImageHeight <= viewHeight){
            minTranslationY = 0;
            maxTranslationY = viewHeight - scaledImageHeight;
        }else{
            minTranslationY = viewHeight - scaledImageHeight;
            maxTranslationY = 0;
        }

        float updatedTranslationY = 0;
        if (translationY < minTranslationY){
            updatedTranslationY = -translationY + minTranslationY;
        }else if (translationY > maxTranslationY){
            updatedTranslationY = -translationY + maxTranslationY;
        }

        //Finally, if there is a required translation in either x or y, make it now
        if(updatedTranslationX != 0 || updatedTranslationY != 0){
            matrix.postTranslate(updatedTranslationX, updatedTranslationY);
        }
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener{
        //When a scaling gesture begins, don't keep translating!
        public boolean onScaleBegin(ScaleGestureDetector detector){
            mode = PinchZoomMode.ZOOM;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector){
            float scaleIncremental = detector.getScaleFactor();//this is the scale of the new image size relative to its previous state
            float originalScale = scaleCurr;

            //If the absolute scale is larget than desired, fix it
            if(scaleCurr > maxScale){
                scaleCurr = maxScale;
                scaleIncremental = maxScale / originalScale;//also need to fix this scaling for postScale since it's relative
            }else if(scaleCurr < minScale){
                scaleCurr = minScale;
                scaleIncremental = minScale / originalScale;
            }

            //If scaled image is smaller than view in x or y then scale about the view's center
            if(imgWidthOrig * scaleCurr <= viewWidth || imgHeightOrig * scaleCurr <= viewHeight){
                matrix.postScale(scaleIncremental, scaleIncremental, viewWidth/2, viewHeight/2);
            }else{
                //Scale around the pinch
                matrix.postScale(scaleIncremental, scaleIncremental, detector.getFocusX(), detector.getFocusY());
            }

            adjustTranslation();
            return true;
        }
    }
}