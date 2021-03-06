package com.szysky.customize.siv;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;
import android.support.annotation.DrawableRes;
import android.support.annotation.IntDef;
import android.support.annotation.IntegerRes;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.szysky.customize.siv.effect.ConcreteDrawingStrategy;
import com.szysky.customize.siv.effect.IDrawingStrategy;
import com.szysky.customize.siv.effect.NormalOnePicStrategy;
import com.szysky.customize.siv.imgprocess.ImageCompression;
import com.szysky.customize.siv.range.ILayoutManager;
import com.szysky.customize.siv.range.QQLayoutManager;
import com.szysky.customize.siv.util.LogUtil;
import com.szysky.customize.siv.util.UIUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

/**
 * Author :  suzeyu
 * Time   :  2016-11-29  下午3:15
 * Blog   :  http://szysky.com
 * GitHub :  https://github.com/suzeyu1992
 * ClassDescription : 一个功能完备的ImageView
 */

public class SImageView extends View {

    private static final String TAG = SImageView.class.getName();
    private static final Bitmap.Config BITMAP_CONFIG = Bitmap.Config.ARGB_8888;
    private ConfigInfo mInfo = new ConfigInfo();

    private Context mContext;

    private int mDrawableWidth;
    private int mDrawableHeight;

    /**
     *  对具体绘图抽象过程提供的一个可利用的画布.
     */
    private Canvas mExternalUseCanvas= new Canvas();


    /**
     *  默认单图片处理策略的开关标记  true: 关闭   false: 开启
     */
    private boolean mCloseNormalOnePicLoad = false;

    /**
     *  具体子元素的 measure布局 策略,
     *  默认下,对于一张图片会使用 mNormalOnePicStrategy 变量, 如果实现了自定义策略,
     *  并且策略内部包含了一张图片的布局逻辑, 可以通过变量强制关闭单图片的默认处理.
     */
    private ILayoutManager mLayoutManager = new QQLayoutManager();

    /**
     *  单个图片默认加载策略, 优先级高于多张图,
     *  可以通过{@link #setCloseNormalOnePicLoad(boolean)}设置为true强制关闭此策略
     */
    private NormalOnePicStrategy mNormalOnePicStrategy = new NormalOnePicStrategy();


    /**
     *  具体的子图片绘制的策略对象
     */
    private IDrawingStrategy mDrawStrategy = new ConcreteDrawingStrategy();

    private int mPaddingLeft;
    private int mPaddingRight;
    private int mPaddingTop;
    private int mPaddingBottom;
    private static final String STR_EMPTY = "";

    /**
     *  正在处理的url图片集合
     */
    public Vector<String> mUrlLoading = new Vector();


    /**
     * 控件加载图片错误的资源id
     */
    @IntegerRes private int mErrPicResID = 0;
    Bitmap mErrPicBitmap ;

    /**
     * 控件加载中的图片资源id
     */
    @IntegerRes private int mLoadingResID = 0;
    Bitmap mLoadingPicBitmap;



    /**
     *  控件属性类
     */
     public static class ConfigInfo implements Cloneable{

        public int height;                                      // 控件的高度
        public int width;                                       // 控件的宽度
        public ArrayList<Bitmap> readyBmp = new ArrayList<>();  // 需要显示的图片集合
        public float borderWidth = 0;                             // 描边宽度
        public int borderColor = Color.BLACK;                   // 描边颜色
        public ArrayList<ILayoutManager.LayoutInfoGroup> coordinates ;  // 测量过程返回的每个元素的对应位置信息
        public int displayType ;                                 // 子元素的显示类型
        public int scaleType ;                                   // 矩形的缩放类型
        public ArrayList<String> urls = new ArrayList<>();



        @Override
        protected Object clone() {
            ConfigInfo clone ;
            try {
                clone = (ConfigInfo) super.clone();
                if (coordinates != null){
                    clone.coordinates = (ArrayList<ILayoutManager.LayoutInfoGroup>) coordinates.clone();
                }
                clone.readyBmp = (ArrayList<Bitmap>) readyBmp.clone();

                clone.urls = (ArrayList<String>) urls.clone();
            } catch (CloneNotSupportedException e) {
                LogUtil._w(TAG, "图片信息 clone is error" );
                e.printStackTrace();
                return this;
            }
            return clone;
        }
    }



    public static final int TYPE_CIRCLE = 0;
    public static final int TYPE_RECT = 1;
    public static final int TYPE_ROUND_RECT = 2;
    public static final int TYPE_FIVE_POINTED_STAR = 3;
    public static final int TYPE_OVAL = 4;

    @IntDef({TYPE_CIRCLE, TYPE_RECT, TYPE_ROUND_RECT ,TYPE_FIVE_POINTED_STAR, TYPE_OVAL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ShapeDisplay{}

    /**显示类型,默认为上面五种定义类型**/
    private int mCurrentDisplayShape = TYPE_CIRCLE;


    public static final int SCALE_TYPE_CENTER_INSIDE = 0;  // 图片比例不变, 以最大边的为标准缩放, 可能会有留白,显示全部图片
    public static final int SCALE_TYPE_FIX_XY = 1;         // 图片比例改变, 已填充控件为主, 显示全部图片
    public static final int SCALE_TYPE_CENTER_CROP = 2;    // 图片比例不变, 已填充控件为主, 图片可能显示不全

    @IntDef({SCALE_TYPE_CENTER_INSIDE,SCALE_TYPE_FIX_XY ,SCALE_TYPE_CENTER_CROP})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ScaleType{}

    /**
     * 当显示类型为矩形的时候, 缩放类型才会生效. 并且当有描边时, 缩放类型失效,
     * 并且使用{@link #mCloseNormalOnePicLoad}的初始值通过使用单张图片的绘制逻辑才有处理效果
     * 默认为{@link #SCALE_TYPE_CENTER_INSIDE}
     **/
    private int mScaleType = SCALE_TYPE_CENTER_INSIDE;


    public SImageView(Context context) {
        super(context);
        mContext = context.getApplicationContext();
        // new 形式, 设定一些初始值
        mInfo.height = (int) UIUtils.dip2px(context.getApplicationContext(), 46);
        mInfo.width = (int) UIUtils.dip2px(context.getApplicationContext(), 46);

    }

    public SImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = getContext();

        // 获得xml属性
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.SImageView, defStyleAttr, 0);

        mInfo.borderWidth = typedArray.getDimensionPixelSize(R.styleable.SImageView_border_width, 0);
        mInfo.borderColor = typedArray.getColor(R.styleable.SImageView_border_color, Color.BLACK);
        mCurrentDisplayShape = mInfo.displayType = typedArray.getInt(R.styleable.SImageView_displayType, 0);
        mScaleType = mInfo.scaleType   = typedArray.getInt(R.styleable.SImageView_scaleType, 0);

        Drawable drawable = typedArray.getDrawable(R.styleable.SImageView_img);
        if ( null != drawable ){
            mDrawableHeight = drawable.getIntrinsicHeight();
            mDrawableWidth = drawable.getIntrinsicWidth();
            mInfo.readyBmp.clear();
            mInfo.readyBmp.add(getBitmapFromDrawable(drawable));
        }

        // padding setting
        mPaddingBottom = getPaddingBottom();
        mPaddingTop = getPaddingTop();
        mPaddingLeft = getPaddingLeft();
        mPaddingRight = getPaddingRight();

        typedArray.recycle();


    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = 0;
        int h = 0;

        final int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
        final int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);


        final int pleft = mPaddingLeft;
        final int pright = mPaddingRight;
        final int ptop = mPaddingTop;
        final int pbottom = mPaddingBottom;


        // 宽度处理
        if (widthSpecMode == MeasureSpec.UNSPECIFIED){
            // 如果是UNSPECIFIED模式需要自定义大小 内部控件match_parent 外部控件是warp_content
            if (mDrawableWidth > 0){
                w = mDrawableWidth;
            }else if (heightSize > 0){
                w = heightSize;
            }else{
                w = (int) UIUtils.dip2px(mContext, 48f);
            }

            w += pleft + pright;    // 加上padding

            w = Math.max(w, getSuggestedMinimumWidth());
        }else if (widthSpecMode == MeasureSpec.AT_MOST){
            // 如果是AT_MOST模式特殊处理  wrap_content
            if (mDrawableWidth > 0){
                w = mDrawableWidth + pleft + pright;
                w = ( w > widthSize ? widthSize : w);
            }

            if (getSuggestedMinimumWidth() > 0){
                if (getSuggestedMinimumWidth() >= widthSize){
                    w = widthSize;
                }else {
                    w = (w > getSuggestedMinimumWidth() ? w : getSuggestedMinimumWidth());
                }
            }

            // 如果既没有设置前景, 也没有背景, 设置46dp占位
            if ((mDrawableWidth <=0) && (getSuggestedMinimumWidth() <=0)){
                w = (int) UIUtils.dip2px(mContext, 46) + pleft + pright;
            }
        }else if (widthSpecMode == MeasureSpec.EXACTLY){
            w = widthSize;
        }


        // 高度测量
        if (heightSpecMode == MeasureSpec.UNSPECIFIED){
            // 如果是UNSPECIFIED模式需要自定义大小 match_parent
            if (mDrawableHeight > 0){
                h = mDrawableHeight;
            }else if (widthSize > 0){
                h = widthSize;
            }else{
                h = (int) UIUtils.dip2px(mContext, 48f);
            }
            h += ptop + pbottom; // 加上padding

            h = Math.max(h, getSuggestedMinimumHeight());
        }else if (heightSpecMode == MeasureSpec.AT_MOST){
            // 如果是AT_MOST模式特殊处理  wrap_content
            // 判断前景
            if (mDrawableHeight > 0){
                h = mDrawableHeight + ptop + pbottom;
                h = ( h > heightSize ? heightSize : h);
            }
            // 判断背景
            if (getSuggestedMinimumHeight() > 0){
                if (getSuggestedMinimumHeight() >= heightSize){
                    h = heightSize;
                }else {
                    h = (h > getSuggestedMinimumHeight() ? h : getSuggestedMinimumHeight());
                }
            }

            // 如果既没有设置前景, 也没有背景, 设置46dp占位
            if ((mDrawableHeight <=0) && (getSuggestedMinimumHeight() <=0)){
                h = (int) UIUtils.dip2px(mContext, 46) + ptop + pbottom;
            }
        }else if (heightSpecMode == MeasureSpec.EXACTLY){
            h = heightSize;
        }




        widthSize = resolveSizeAndState(w, widthMeasureSpec, 0);
        heightSize = resolveSizeAndState(h, heightMeasureSpec, 0);


        setMeasuredDimension(widthSize, heightSize);

    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        // 标记padding
        adjustPadding(left, top, right, bottom);

        mInfo.height = getHeight() - mPaddingBottom - mPaddingTop ;
        mInfo.width = getWidth() - mPaddingLeft - mPaddingRight;
    }



    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);



        // translate padding
        canvas.translate(mPaddingLeft, mPaddingTop);

        long startCur = System.nanoTime();


        if ( mInfo.readyBmp.size() == 1 && !mCloseNormalOnePicLoad){
            long l = System.nanoTime();
            mNormalOnePicStrategy.algorithm(canvas,1,1,mInfo.readyBmp.get(0), (ConfigInfo) mInfo.clone());
            LogUtil._i(TAG, "一张图片执行时间: "+ (System.nanoTime() - l)/1000000f+"毫秒");

        }else if (mInfo.readyBmp.size() > 0 ){

            sizeMeasure();
            if (mInfo.coordinates == null) return;
            // layout 子元素布局
            Iterator<ILayoutManager.LayoutInfoGroup> iterator = mInfo.coordinates.iterator();
            int index = 0;
            while (iterator.hasNext()){
                index++;
                ILayoutManager.LayoutInfoGroup childInfo = iterator.next();

                int offsetX = childInfo.leftTopPoint.x;
                int offsetY = childInfo.leftTopPoint.y;


                Bitmap tempBmp = Bitmap.createBitmap(childInfo.innerWidth, childInfo.innerHeight, Bitmap.Config.ARGB_8888);


                // 首先关联一个bitmap, 并把关联的canvas对外提供出去
                mExternalUseCanvas.setBitmap(tempBmp);

                // **重点**. 具体实现由使用者通过mExternalUseCanvas定义.
                mDrawStrategy.algorithm(mExternalUseCanvas,mInfo.coordinates.size(),index ,mInfo.readyBmp.get(index-1), (ConfigInfo) mInfo.clone());


                canvas.drawBitmap(tempBmp,offsetX,offsetY,null);


                // 取消关联的bitmap  并 清空对外提供的canvas
                mExternalUseCanvas.setBitmap(null);
                mExternalUseCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

            }
            // 清除布局消息
            mInfo.coordinates = null;

            LogUtil._i(TAG, "多张图执行时间: "+ (System.nanoTime() - startCur)/1000000f +"毫秒");
        }

        // translate padding
        canvas.translate(-mPaddingLeft, -mPaddingTop);

    }



    /**
     * 对多张图片进行数据测量
     */
    private void sizeMeasure(){
        // measure布局参数
        mInfo.coordinates = mLayoutManager.calculate(mInfo.width, mInfo.height, mInfo.readyBmp.size());
    }



    /**
     *  对padding边界值处理
     */
    private void adjustPadding(int left, int top, int right, int bottom) {
        // 对padding进行极限值处理
        if ((mPaddingBottom + mPaddingTop) >= ( bottom - top )){
            mPaddingBottom = (int) ((( bottom - top ) >> 1) * 0.9f);
            mPaddingTop = (int) ((( bottom - top ) >> 1) * 0.9f);
        }

        if((mPaddingRight + mPaddingLeft) >= (right - left)){
            mPaddingRight = (int) (((right - left) >> 1) * 0.9f);
            mPaddingLeft = (int) (((right - left) >> 1) * 0.9f);
        }
    }

    /**
     *  返回 是否使用了默认的单张绘图显示策略.
     */
    public boolean isCloseNormalOnePicLoad() {
        return mCloseNormalOnePicLoad;
    }

    /**
     * 设置 是否关闭单张图片时, 使用特定的单图绘制策略. true为关闭, false为开启, 默认为false
     * 注明: 如果是默认值, 那么只有多个图片显示的时候才会使用{@link #mDrawStrategy}策略, 一张图片的时候会使用
     *      内置的单张图片处理策略{@link #mNormalOnePicStrategy}.
     *      如果通过{@link #setDrawStrategy(IDrawingStrategy)}实现了自定义策略, 那么单张图片开关标记将会
     *      自动设置为关闭.
     */
    public SImageView setCloseNormalOnePicLoad(boolean isClose) {
        this.mCloseNormalOnePicLoad = isClose;
        return this;
    }

    /**
     * 设置子元素 绘制图片 的具体显示策略
     */
    public SImageView setDrawStrategy(IDrawingStrategy mDrawStrategy) {
        this.mDrawStrategy = mDrawStrategy;
        if (mDrawStrategy instanceof ConcreteDrawingStrategy){
            mCloseNormalOnePicLoad = false;
        }else{
            mCloseNormalOnePicLoad = true;
        }

        return this;
    }


    /**
     * 设置 测量布局 规则
     */
    public SImageView setLayoutManager(ILayoutManager mLayoutManager) {
        this.mLayoutManager = mLayoutManager;

        // 兼容qq群组绘制的重叠场景问题
        if (mLayoutManager instanceof QQLayoutManager){
            if (mDrawStrategy instanceof ConcreteDrawingStrategy){
                ((ConcreteDrawingStrategy)mDrawStrategy).setIsPicRotate(true);
            }
        }else{
            if (mDrawStrategy instanceof ConcreteDrawingStrategy){
                ((ConcreteDrawingStrategy)mDrawStrategy).setIsPicRotate(false);
            }
        }
        return this;
    }

    /**
     *  获得当前的测量布局规则
     */
    public ILayoutManager getLayoutManager(){
        return mLayoutManager;
    }


    /**
     * 返回当前控件的显示类型 如圆形, 矩形, 五角星等等...
     */
    @ShapeDisplay
    public int getDisplayShape() {
        return mCurrentDisplayShape;
    }

    /**
     * 设置当前控件的显示类型, 如圆形, 矩形, 五角星等等...
     * @param mCurrentDisplayShape  只能@{@link ShapeDisplay}类型
     */
    public SImageView setDisplayShape(@ShapeDisplay int mCurrentDisplayShape) {
        this.mCurrentDisplayShape = mCurrentDisplayShape;
        mInfo.displayType = mCurrentDisplayShape;

        return this;
    }

    /**
     * 设置图片描边宽度
     */
    public SImageView setBorderWidth(float dp){
        mInfo.borderWidth = (int) UIUtils.dip2px(getContext(), dp);

        return this;
    }

    /**
     * 获得描边的宽度 单位dp
     */
    public float getBorderWidth(){
        return UIUtils.px2dip(getContext(), mInfo.borderWidth);
    }

    /**
     * 设置描边颜色
     */
    public SImageView setBorderColor(@ColorInt int color){
        mInfo.borderColor = color;

        return this;
    }

    public @ColorInt int getBorderColor(){
        return mInfo.borderColor;
    }


    /**
     * 设置展示图片的集合
     * @param bitmaps 接收一个图片集合
     */
    public SImageView setImages(List<Bitmap> bitmaps){
        mInfo.urls.clear();
        updateForList(bitmaps, null);

        return this;
    }

    void setImages(List<Bitmap> bitmaps, List<String> urls){
        // 对地址进行赋值
        mInfo.urls.clear();
        for (String url : urls) {
            mInfo.urls.add(url);
        }
        updateForList(bitmaps, null);
    }

    /**
     * 传入drawable资源id
     */
    public void setIdRes(@DrawableRes int id){
        if (id != 0) {
            Drawable drawable = getResources().getDrawable(id);
            mInfo.urls.clear();
            if ( null != drawable){
                updateForOne(getBitmapFromDrawable(drawable) , STR_EMPTY);
            }
        }
    }


    public void setDrawable(Drawable drawable){
        mInfo.urls.clear();
        updateForOne(getBitmapFromDrawable(drawable), STR_EMPTY );
    }

    private void updateForOne(Bitmap bitmap, String url){

        // 本地加载, 和网络加载只能共存一个. 先判断rul

        // 只有有一个url字符串和关闭了单张图片的开关才有计算的意义
        if (!url.isEmpty()){
            // 清空上一次的显示数据
            mInfo.readyBmp.clear();

            if (isCloseNormalOnePicLoad()){
                mInfo.readyBmp.add(null);
                sizeMeasure();
                ImageLoader.getInstance(mContext).setPicture(url, this, mInfo.coordinates.get(0).innerWidth, mInfo.coordinates.get(0).innerHeight);
            }else{
                int reqWid = 0;
                int reqHeight = 0;

                int minSide = mInfo.height >=  mInfo.width ? mInfo.width : mInfo.height;

                // 矩形比较特殊存在3中情况
                if (mCurrentDisplayShape == TYPE_RECT){
                    switch (mScaleType){
                        case SCALE_TYPE_CENTER_INSIDE:
                            reqHeight = reqWid = minSide;
                            break;

                        case SCALE_TYPE_CENTER_CROP:
                        case SCALE_TYPE_FIX_XY:
                            reqHeight = mInfo.height;
                            reqWid = mInfo.width;
                            break;

                        default:
                            break;
                    }
                }else{
                    // 其他图形基本都是在单张的时候, 基本都是控件的最小边
                    reqHeight = reqWid = minSide;
                }

                ImageLoader.getInstance(mContext).setPicture(url, this, reqWid, reqHeight);
            }
        }else{
            if (null != bitmap){
                mInfo.readyBmp.clear();
                mInfo.readyBmp.add(bitmap);

                invalidate();
            }
        }


    }

    private void updateForList(List<Bitmap> bitmaps, String[] urls) {

        if((urls == null) && (bitmaps == null)) return;

        mInfo.readyBmp.clear();

        // 分开网络加载 和 bitmap处理
        if ((urls != null) && (urls.length>1)){


           int temp = mInfo.height > mInfo.width ? mInfo.width : mInfo.height;

            // 这里进行简单的处理判断,  不做细分, 过多的不同分辨率进行缓存的key不一定很适用, 因为图片从磁盘或者网络获取的时候,
            // 是通过inJustDecodeBounds, 做一个笼统的2的次幂缩放. 适当的取一定情况下即可.
            if (urls.length < 4){
                temp /= 2;
            }else{
                temp /= 3;
            }

            ImageLoader.getInstance(mContext).setMulPicture(Arrays.asList(urls), this, temp, temp);

        }else if ((null != bitmaps) && (bitmaps.size() >0)){
            for (Bitmap bitmap : bitmaps) {
                mInfo.readyBmp.add(bitmap);
            }
            invalidate();
        }
    }

    /**
     * 设置展示的图像的bitmap
     */
    public void setBitmap(Bitmap bitmap){
        mInfo.urls.clear();
        updateForOne(bitmap , STR_EMPTY);
    }

    private Bitmap getBitmapFromDrawable(Drawable drawable) {
        if (drawable == null) {
            return null;
        }

        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        try {
            Bitmap bitmap;

            if (drawable instanceof ColorDrawable) {
                bitmap = Bitmap.createBitmap(1, 1, BITMAP_CONFIG);
            } else {
                bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), BITMAP_CONFIG);
            }

            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
            return bitmap;
        } catch (OutOfMemoryError e) {
            return null;
        }
    }


    /**
     * 针对某种情况下: 控件已经初始化还没有测量获得控件宽高时, 进行了url网址图片设置,
     * 如果这个时候控件的宽高是0, 那么进行延迟发送的方式
     */
    private volatile int mSafetyCurrent = 0;
    private final int MAX_SAFETY_NUM = 7;
    private int mSleepTime = 5;

    /**
     * 对外提供直接通过url来加载图片的方法
     * @param imageUrls 需要加载的图片地址数组
     */
    public void setImageUrls(final String... imageUrls) {

        // 进行了控件未正确获得宽高属性的容错
        if ((mInfo.width == 0 || mInfo.height == 0) && mSafetyCurrent < MAX_SAFETY_NUM ){
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    LogUtil._e(TAG, "run: 进行了一次没有正确的控件宽高属性的容错"  );
                    mSleepTime <<= 1;
                    mSafetyCurrent++;
                    setImageUrls(imageUrls);
                }
            }, mSleepTime);
            return;
        }


        // 对要加载的图片进行缓存
        if (imageUrls.length > 0){
            mUrlLoading.clear();
            for (String url : imageUrls) {
                mUrlLoading.add(url);
            }
        }
        if (imageUrls.length == 1){
            updateForOne(null, imageUrls[0]);
        }else if (imageUrls.length > 1){
            updateForList(null, imageUrls);
        }
    }

    @ScaleType
    public int getScaleType() {
        return mScaleType;
    }

    /**
     * 设置单张图片时, 图片的缩放类型, 只有在矩形图片和无描边的场景下有效,
     * 并且使用{@link #mCloseNormalOnePicLoad}的初始值通过使用单张图片的绘制逻辑才有处理效果
     * {@link #mScaleType}
     */
    public SImageView setScaleType(@ScaleType int mScaleType) {
        this.mScaleType = mScaleType;
        mInfo.scaleType = mScaleType;
        return this;

    }

    /**
     * 获得单张图片时 圆角矩形 的圆角弧度系数, 范围0~2; 默认为1
     */
    public float getRectRoundRadius() {
        return mNormalOnePicStrategy.getRectRoundRadius();
    }

    /**
     *  设置单张图片时 圆角矩形 的圆角弧度系数, 取值为0~2, 默认为1
     *  此设置属性不会立即生效, 需下次圆角矩形加载时才会有效.可手动invalidate刷新
     */
    public SImageView setRectRoundRadius(float mRectRoundRadius) {
        mNormalOnePicStrategy.setRectRoundRadius(mRectRoundRadius);

        return this;
    }

    /**
     * 设置单张图片 oval椭圆的宽高比.
     * 此设置属性同样不会立即生效, 需下次椭圆显示加载时才会生效, 可手动invalidate刷新
     * @param widthHeightRadio  宽高比. 只能传入大于0, 默认值为宽高比为2/1  也就是2f
     */
    public  SImageView setOvalRatio(float widthHeightRadio){
        mNormalOnePicStrategy.setOvalWidthOrHeight(widthHeightRadio);

        return this;
    }

    /**
     * 获取当前椭圆的宽高比
     */
    public float getOvalRatio(){
        return mNormalOnePicStrategy.getOvalWidthOrHeight();
    }



    public int getErrPicResID() {
        return mErrPicResID;
    }

    /**
     * 设置控件网络加载错误时候的图片
     */
    public SImageView setErrPicResID(@DrawableRes int mErrPicResID) {
        if (mInfo.width > 0 && mInfo.height > 0){
            // 解析为和控件一样大的
            mErrPicBitmap = ImageCompression.decodeFixedSizeForResources(getResources(), mErrPicResID, mInfo.width, mInfo.height);
        }else{
            // 无法获取控件宽高, 直接解析为图片的大小
            mErrPicBitmap = BitmapFactory.decodeResource(mContext.getResources(), mErrPicResID);
        }

        if (mErrPicBitmap != null){
            this.mErrPicResID = mErrPicResID;
            LogUtil._i(TAG, "控件的默认加载错误图片设置成功 图片宽高   "+ mErrPicBitmap.getWidth() + "  "+mErrPicBitmap.getHeight() );
        }else{
            Log.w(TAG, "控件的默认加载错误图片设置失败, 请检测填入的是否是图片资源ID ");
        }

        return this;
    }



    public int getLoadingResID() {
        return mLoadingResID;
    }

    /**
     * 设置网络加载中的图片资源id
     */
    public SImageView setLoadingResID(@DrawableRes int mLoadingResID) {
        if (mInfo.width > 0 && mInfo.height > 0){
            // 解析为和控件一样大的
            mLoadingPicBitmap = ImageCompression.decodeFixedSizeForResources(getResources(), mLoadingResID, mInfo.width, mInfo.height);
            LogUtil._i(TAG, "设置控件的网络加载错误的图片成功, 加载的宽高到内存的宽高: "+mInfo.width+ "    "+mInfo.height );
        }else{
            // 无法获取控件宽高, 直接解析为图片的大小
            mLoadingPicBitmap = BitmapFactory.decodeResource(mContext.getResources(), mLoadingResID);
            LogUtil._i(TAG, "设置控件的网络加载错误的图片成功, 加载的宽高到内存的宽高为源资源图片大小: "+mLoadingPicBitmap.getWidth()+ "    "+mLoadingPicBitmap.getHeight() );
        }

        if (mLoadingPicBitmap != null){
            this.mLoadingResID = mLoadingResID;
            LogUtil._i(TAG, "控件的默认加载中时图片设置成功 图片宽高   "+ mLoadingPicBitmap.getWidth() + "  "+mLoadingPicBitmap.getHeight() );
        }else{
            Log.w(TAG, "控件的默认加载中时图片设置失败, 请检测填入的是否是图片资源ID ");
        }

        return this;
    }


}
