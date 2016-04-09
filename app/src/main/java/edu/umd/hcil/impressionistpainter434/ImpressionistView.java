package edu.umd.hcil.impressionistpainter434;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import java.text.MessageFormat;

/**
 * Created by jon on 3/20/2016.
 */
public class ImpressionistView extends View {

    private ImageView _imageView;

    private Canvas _offScreenCanvas = null;
    private Bitmap _offScreenBitmap = null;
    private Paint _paint = new Paint();

    private int _alpha = 150;
    private int _defaultRadius = 25;
    private Point _lastPoint = null;
    private long _lastPointTime = -1;
    private double _lastPointSpeed = -1;
    private boolean _useMotionSpeedForBrushStrokeSize = true;
    private Paint _paintBorder = new Paint();
    private Paint _paintBackground = new Paint();
    private BrushType _brushType = BrushType.Square;
    private float _minBrushRadius = 5;

    public ImpressionistView(Context context) {
        super(context);
        init(null, 0);
    }

    public ImpressionistView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public ImpressionistView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    /**
     * Because we have more than one constructor (i.e., overloaded constructors), we use
     * a separate initialization method
     * @param attrs
     * @param defStyle
     */
    private void init(AttributeSet attrs, int defStyle){

        // Set setDrawingCacheEnabled to true to support generating a bitmap copy of the view (for saving)
        // See: http://developer.android.com/reference/android/view/View.html#setDrawingCacheEnabled(boolean)
        //      http://developer.android.com/reference/android/view/View.html#getDrawingCache()
        this.setDrawingCacheEnabled(true);

        _paint.setColor(Color.RED);
        _paint.setAlpha(_alpha);
        _paint.setAntiAlias(true);
        _paint.setStyle(Paint.Style.STROKE);
        _paint.setStrokeWidth(4);
        _paint.setStrokeJoin(Paint.Join.ROUND);
        _paint.setStrokeCap(Paint.Cap.ROUND);

        _paintBorder.setColor(Color.BLACK);
        _paintBorder.setStrokeWidth(3);
        _paintBorder.setStyle(Paint.Style.STROKE);
        _paintBorder.setAlpha(150);

        _paintBackground.setColor(Color.WHITE);
        _paintBackground.setStyle(Paint.Style.FILL);

        //_paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh){

        Bitmap bitmap = getDrawingCache();
        Log.v("onSizeChanged", MessageFormat.format("bitmap={0}, w={1}, h={2}, oldw={3}, oldh={4}", bitmap, w, h, oldw, oldh));
        if(bitmap != null) {
            _offScreenBitmap = getDrawingCache().copy(Bitmap.Config.ARGB_8888, true);
            _offScreenCanvas = new Canvas(_offScreenBitmap);
        }
    }

    /**
     * Sets the ImageView, which hosts the image that we will paint in this view
     * @param imageView
     */
    public void setImageView(ImageView imageView){
        _imageView = imageView;
    }

    /**
     * Sets the brush type. Feel free to make your own and completely change my BrushType enum
     * @param brushType
     */
    public void setBrushType(BrushType brushType){
        _brushType = brushType;
    }

    /**
     * Clears the painting
     */
    public void clearPainting(){
        _offScreenCanvas.drawRect(0, 0, _offScreenCanvas.getWidth(), _offScreenCanvas.getHeight(), _paintBackground);
        invalidate();
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if(_offScreenBitmap != null) {
            _paint.setAlpha(255);
            canvas.drawBitmap(_offScreenBitmap, 0, 0, _paint);
        }

        canvas.drawRect(getBitmapPositionInsideImageView(_imageView), _paintBorder);
    }

    public void fill() {
        Rect rect = getBitmapPositionInsideImageView(_imageView);
        int step = 10;

        for(int i = 0; i < 500; i++) {
            int x0 = (int)(Math.random() * rect.width() + rect.left);
            int y0 = (int)(Math.random() * rect.height() + rect.top);
            int x1 = (int)(x0 + Math.random() * step * 2 - step);
            int y1 = (int)(y0 + Math.random() * step * 2 - step);
            Point prev = new Point(x0, y0);
            Point next = new Point(x1, y1);
            drawStroke(prev, next, 1.5 + Math.random() * 0.5, 1.5 + Math.random() * 0.5, _brushType);
            prev = next;
        }

        invalidate();
    }

    public void drawStroke(Point from, Point to, double fromSpeed, double toSpeed, BrushType type) {
        boolean splatter = false;

        if (type == BrushType.Circle) {
            _paint.setStrokeJoin(Paint.Join.ROUND);
            _paint.setStrokeCap(Paint.Cap.ROUND);
        } else if (type == BrushType.Square) {
            _paint.setStrokeJoin(Paint.Join.MITER);
            _paint.setStrokeCap(Paint.Cap.SQUARE);
        } else if (type == BrushType.CircleSplatter) {
            _paint.setStrokeJoin(Paint.Join.ROUND);
            _paint.setStrokeCap(Paint.Cap.ROUND);
            splatter = true;
        }

        double distance = getDistance(from, to);
        int steps = Math.max(1, (int)(distance / 10));
        Point prev = to;
        for (int i = 0; i < steps; i++) {
            double alpha = (i + 1) / (double)steps;
            double speed = interpolate(fromSpeed, toSpeed, alpha);
            Point next = interpolate(from, to, alpha);
            drawSegment(prev, next, speed, splatter);
            prev = next;
        }
    }

    private void drawSegment(Point prev, Point next, double speed, boolean splatter) {
        _paint.setColor(getSourceColor(next));
        _paint.setAlpha(_alpha);
        if (splatter) {
            double span = speed * 20;
            int offsetX = (int)(Math.random() * span - span / 2);
            int offsetY = (int)(Math.random() * span - span / 2);
            next = new Point(next.x + offsetX, next.y + offsetY);
            _paint.setStyle(Paint.Style.FILL);
            _offScreenCanvas.drawCircle(next.x, next.y, (float)Math.sqrt(speed * 50), _paint);
        } else {
            _paint.setStrokeWidth((float) speed * 10);
            _paint.setStyle(Paint.Style.STROKE);
            _offScreenCanvas.drawLine(prev.x, prev.y, next.x, next.y, _paint);
        }
    }

    private int getSourceColor(Point point) {
        Rect bounds = getBitmapPositionInsideImageView(_imageView);
        Bitmap bitmap = ((BitmapDrawable)_imageView.getDrawable()).getBitmap();
        int x = (int)(bitmap.getWidth() * (point.x - bounds.left) / (double)(bounds.width()));
        int y = (int)(bitmap.getHeight() * (point.y - bounds.top) / (double)(bounds.height()));
        x = Math.max(0, Math.min(x, bitmap.getWidth() - 1));
        y = Math.max(0, Math.min(y, bitmap.getHeight() - 1));
        return bitmap.getPixel(x, y);
    }

    private Point interpolate(Point from, Point to, double alpha) {
        return new Point((int)interpolate(from.x, to.x, alpha),
                         (int)interpolate(from.y, to.y, alpha));
    }

    private double interpolate(double u, double v, double alpha) {
        return u * alpha + v * (1 - alpha);
    }

    private double getSpeed(Point from, Point to, long delta) {
        double distance = getDistance(from, to);
        return distance / delta;
    }

    private double getDistance(Point from, Point to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {

        //Basically, the way this works is to listen for Touch Down and Touch Move events and determine where those
        //touch locations correspond to the bitmap in the ImageView. You can then grab info about the bitmap--like the pixel color--
        //at that location

        Point point = new Point((int)motionEvent.getX(), (int)motionEvent.getY());
        long time = System.currentTimeMillis();

        if (motionEvent.getActionMasked() == MotionEvent.ACTION_MOVE) {
            double speed = getSpeed(_lastPoint, point, time - _lastPointTime);
            drawStroke(_lastPoint, point, _lastPointSpeed, speed, _brushType);
            _lastPointSpeed = speed;
        }

        _lastPoint = point;
        _lastPointTime = time;

        invalidate();

        return true;
    }

    public Bitmap getBitmap() {
        return _offScreenBitmap;
    }

    /**
     * This method is useful to determine the bitmap position within the Image View. It's not needed for anything else
     * Modified from:
     *  - http://stackoverflow.com/a/15538856
     *  - http://stackoverflow.com/a/26930938
     * @param imageView
     * @return
     */
    private static Rect getBitmapPositionInsideImageView(ImageView imageView){
        Rect rect = new Rect();

        if (imageView == null || imageView.getDrawable() == null) {
            return rect;
        }

        // Get image dimensions
        // Get image matrix values and place them in an array
        float[] f = new float[9];
        imageView.getImageMatrix().getValues(f);

        // Extract the scale values using the constants (if aspect ratio maintained, scaleX == scaleY)
        final float scaleX = f[Matrix.MSCALE_X];
        final float scaleY = f[Matrix.MSCALE_Y];

        // Get the drawable (could also get the bitmap behind the drawable and getWidth/getHeight)
        final Drawable d = imageView.getDrawable();
        final int origW = d.getIntrinsicWidth();
        final int origH = d.getIntrinsicHeight();

        // Calculate the actual dimensions
        final int widthActual = Math.round(origW * scaleX);
        final int heightActual = Math.round(origH * scaleY);

        // Get image position
        // We assume that the image is centered into ImageView
        int imgViewW = imageView.getWidth();
        int imgViewH = imageView.getHeight();

        int top = (int) (imgViewH - heightActual)/2;
        int left = (int) (imgViewW - widthActual)/2;

        rect.set(left, top, left + widthActual, top + heightActual);

        return rect;
    }
}