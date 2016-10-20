package com.dji.videostreamdecodingsample;

import android.Manifest;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Bundle;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.dji.videostreamdecodingsample.media.DJIVideoStreamDecoder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import dji.common.error.DJIError;
import dji.common.flightcontroller.DJIFlightControllerDataType;
import dji.common.flightcontroller.DJIVirtualStickFlightControlData;
import dji.common.flightcontroller.DJIVirtualStickVerticalControlMode;
import dji.common.product.Model;
import dji.common.util.DJICommonCallbacks;
import dji.sdk.airlink.DJILBAirLink;
import dji.sdk.camera.DJICamera;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.base.DJIBaseProduct;
import dji.sdk.flightcontroller.DJIFlightController;
import dji.sdk.products.DJIAircraft;

import static com.dji.videostreamdecodingsample.VideoDecodingApplication.getProductInstance;


public class MainActivity extends Activity implements DJIVideoStreamDecoder.IYuvDataListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    static final int MSG_WHAT_SHOW_TOAST = 0;
    static final int MSG_WHAT_UPDATE_TITLE = 1;

    private TextView titleTv;
    private TextureView videostreamPreviewTtView;
    private SurfaceView videostreamPreviewSf;
    private SurfaceHolder videostreamPreviewSh;

    private ImageView show_image;
    String log_name=null;




    int contatore;



    private DJIBaseProduct mProduct;
    private DJICamera mCamera;
    private DJICodecManager mCodecManager;

    private TextView savePath;
    private TextView screenShot;
    private List<String> pathList = new ArrayList<>();

    private HandlerThread backgroundHandlerThread;
    public Handler backgroundHandler;

    protected DJICamera.CameraReceivedVideoDataCallback mReceivedVideoDataCallBack = null;
    protected DJILBAirLink.DJIOnReceivedVideoCallback mOnReceivedVideoCallback = null;

    /*color*/
    int blue = 0b000000000000000011111111;
    int green = 0b000000001111111100000000;
    int red = 0b111111110000000000000000;
    int alpha = 0b111111110000000000000000000000;

    /*fixed dimension of image*/
    int SizeW =1280;
    int SizeH=720;

    /*point of touch*/
    int x_touch=0;
    int y_touch=0;

    int touched_R=0;
    int touched_G=0;
    int touched_B=0;
    boolean flag=false;

    /*range colori accettabili*/
    double delta_min =0.6;
    double delta_max = 1.9;

    /*clasterizzazione*/

    ArrayList<Point> centri = new ArrayList<>();

    /*drone automatic fly*/

    private DJIFlightController mFlightController;


    private Timer mSendVirtualStickDataTimer;
    private SendVirtualStickDataTask mSendVirtualStickDataTask;
    private float mPitch;
    private float mRoll;
    private float mYaw;
    private float mThrottle;

    /*cose per la conversione in RGB*/
    private RenderScript rs;
    private ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic;
    private Type.Builder yuvType, rgbaType;
    private Allocation in, out;

    public MainActivity() {
    }

    public static DJIAircraft getAircraftInstance() {
        if (!isAircraftConnected()) return null;
        return (DJIAircraft) getProductInstance();
    }

    public static boolean isAircraftConnected() {
        return getProductInstance() != null && getProductInstance() instanceof DJIAircraft;
    }


    @Override
    protected void onResume() {
        super.onResume();
        notifyStatusChange();
        DJIVideoStreamDecoder.getInstance().resume();
    }

    @Override
    protected void onPause() {
        DJIVideoStreamDecoder.getInstance().stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        DJIVideoStreamDecoder.getInstance().destroy();
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        log_name= String.valueOf(System.currentTimeMillis());
        // When the compile and target version is higher than 22, please request the
        // following permissions at runtime to ensure the
        // SDK work well.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.VIBRATE,
                            Manifest.permission.INTERNET, Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.WAKE_LOCK, Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.SYSTEM_ALERT_WINDOW,
                            Manifest.permission.READ_PHONE_STATE,
                    }
                    , 1);
        }

        setContentView(R.layout.activity_main);

        backgroundHandlerThread = new HandlerThread("background handler thread");
        backgroundHandlerThread.start();
        backgroundHandler = new Handler(backgroundHandlerThread.getLooper());

        initUi();
        initPreviewer();
        //initFlightController();

        //start_drone();
        //enable_virtual_control();

        float yaw =0.8f;
        float throttle = 0.5f;
        float pitch =0.0f;
        float roll = 0.0f;

        //move_drone(yaw,throttle,pitch,roll);








//        move_drone(0.5f, 0.5f ,0.5f, 0.5f);
    }

    private void move_drone(float yaw, float throttle, float pitch, float roll){

        float pitchJoyControlMaxSpeed = DJIFlightControllerDataType.DJIVirtualStickRollPitchControlMaxVelocity;
        float rollJoyControlMaxSpeed = DJIFlightControllerDataType.DJIVirtualStickRollPitchControlMaxVelocity;
        float verticalJoyStickControlMaxSpeed = DJIFlightControllerDataType.DJIVirtualStickVerticalControlMaxVelocity;
        float yawJoyStickControlMaxSpeed = DJIFlightControllerDataType.DJIVirtualStickYawControlMaxAngularVelocity;

        mYaw = (float)(verticalJoyStickControlMaxSpeed * yaw);
        mThrottle = (float)(yawJoyStickControlMaxSpeed * throttle);

        mPitch = (float)(pitchJoyControlMaxSpeed * pitch);

        mRoll = (float)(rollJoyControlMaxSpeed * roll);


        if (null == mSendVirtualStickDataTimer) {
            mSendVirtualStickDataTask = new SendVirtualStickDataTask();
            mSendVirtualStickDataTimer = new Timer();
            mSendVirtualStickDataTimer.schedule(mSendVirtualStickDataTask, 0, 200);
        }
    }

    private void start_drone() {


        if (mFlightController != null){
            mFlightController.takeOff(
                    new DJICommonCallbacks.DJICompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            if (djiError != null) {
                                //showToast(djiError.getDescription());
                            } else {
                                showToast("Take off Success");
                            }
                        }
                    }
            );


        }

    }

    public  void enable_virtual_control(){

        if (mFlightController != null) {
            mFlightController.enableVirtualStickControlMode(
                    new DJICommonCallbacks.DJICompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            if (djiError != null) {
                                showToast(djiError.getDescription());
                            } else {
                                showToast("Enable Virtual Stick Success");
                            }
                        }
                    }
            );
        }


    }

    public Handler mainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_WHAT_SHOW_TOAST:
                    Toast.makeText(getApplicationContext(), (String) msg.obj, Toast.LENGTH_SHORT).show();
                    break;
                case MSG_WHAT_UPDATE_TITLE:
                    if (titleTv != null) {
                        titleTv.setText((String) msg.obj);
                    }
                    break;
                default:
                    break;
            }
        }
    };

    private void showToast(String s) {
        mainHandler.sendMessage(
                mainHandler.obtainMessage(MSG_WHAT_SHOW_TOAST, s)
        );
    }

    private void updateTitle(String s) {
        mainHandler.sendMessage(
                mainHandler.obtainMessage(MSG_WHAT_UPDATE_TITLE, s)
        );
    }

    private void initUi() {

        show_image = (ImageView) findViewById(R.id.show_picture);




        savePath = (TextView) findViewById(R.id.activity_main_save_path);
        screenShot = (TextView) findViewById(R.id.activity_main_screen_shot);
        screenShot.setSelected(false);
        titleTv = (TextView) findViewById(R.id.title_tv);
        videostreamPreviewTtView = (TextureView) findViewById(R.id.livestream_preview_ttv);
        videostreamPreviewSf = (SurfaceView) findViewById(R.id.livestream_preview_sf);
        videostreamPreviewSh = videostreamPreviewSf.getHolder();
        videostreamPreviewSh.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                DJIVideoStreamDecoder.getInstance().init(getApplicationContext(), videostreamPreviewSh.getSurface());
                DJIVideoStreamDecoder.getInstance().setYuvDataListener(MainActivity.this);
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                DJIVideoStreamDecoder.getInstance().changeSurface(holder.getSurface());
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });



    }

    private void notifyStatusChange() {

        mProduct = getProductInstance();

        Log.d(TAG, "notifyStatusChange: " + (mProduct == null ? "Disconnect" : (mProduct.getModel() == null ? "null model" : mProduct.getModel().name())));
        if (mProduct != null && mProduct.isConnected() && mProduct.getModel() != null) {
            updateTitle(mProduct.getModel().name() + " Connected");
        } else {
            updateTitle("Disconnected");
        }

        mReceivedVideoDataCallBack = new DJICamera.CameraReceivedVideoDataCallback() {

            @Override
            public void onResult(byte[] videoBuffer, int size) {
                Log.d(TAG, "camera recv video data size: " + size);
                DJIVideoStreamDecoder.getInstance().parse(videoBuffer, size);
            }
        };
        mOnReceivedVideoCallback = new DJILBAirLink.DJIOnReceivedVideoCallback() {

            @Override
            public void onResult(byte[] videoBuffer, int size) {
                Log.d(TAG, "airlink recv video data size: " + size);
                DJIVideoStreamDecoder.getInstance().parse(videoBuffer, size);
            }
        };

        if (null == mProduct || !mProduct.isConnected()) {
            mCamera = null;
            showToast("Disconnected");
        } else {
            if (!mProduct.getModel().equals(Model.UnknownAircraft)) {
                mCamera = mProduct.getCamera();
                if (mCamera != null) {
                    mCamera.setDJICameraReceivedVideoDataCallback(mReceivedVideoDataCallBack);
                }
            } else {
                if (null != mProduct.getAirLink()) {
                    if (null != mProduct.getAirLink().getLBAirLink()) {
                        mProduct.getAirLink().getLBAirLink().setDJIOnReceivedVideoCallback(mOnReceivedVideoCallback);
                    }
                }
            }
        }
    }

    /**
     * Init a fake texture view to for the codec manager, so that the video raw data can be received
     * by the camera
     */
    private void initPreviewer() {
        videostreamPreviewTtView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Log.d(TAG, "real onSurfaceTextureAvailable");
                if (mCodecManager == null) {
                    mCodecManager = new DJICodecManager(getApplicationContext(), surface, width, height);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
    }

    @Override
    public void onYuvDataReceived(byte[] yuvFrame, int width, int height) {

        if (DJIVideoStreamDecoder.getInstance().frameIndex % 30 == 0) { /*famo la cosa ogni 30 frame*/

            /*qui mi creo degli array, nulla di che*/
            byte[] y = new byte[width * height];
            byte[] u = new byte[width * height / 4];
            byte[] v = new byte[width * height / 4];
            byte[] nu = new byte[width * height / 4]; //
            byte[] nv = new byte[width * height / 4];


            /*copio yuv frame in y per w*h posti*/
            System.arraycopy(yuvFrame, 0, y, 0, y.length);


            /*copio u e v*/
            for (int i = 0; i < u.length; i++) {
                v[i] = yuvFrame[y.length + 2 * i];
                u[i] = yuvFrame[y.length + 2 * i + 1];
            }


            /* giusto variabili sensate  */
            int uvWidth = width / 2;
            int uvHeight = height / 2;


            for (int j = 0; j < uvWidth / 2; j++) {
                for (int i = 0; i < uvHeight / 2; i++) {
                    byte uSample1 = u[i * uvWidth + j];
                    byte uSample2 = u[i * uvWidth + j + uvWidth / 2];
                    byte vSample1 = v[(i + uvHeight / 2) * uvWidth + j];
                    byte vSample2 = v[(i + uvHeight / 2) * uvWidth + j + uvWidth / 2];
                    nu[2 * (i * uvWidth + j)] = uSample1;
                    nu[2 * (i * uvWidth + j) + 1] = uSample1;
                    nu[2 * (i * uvWidth + j) + uvWidth] = uSample2;
                    nu[2 * (i * uvWidth + j) + 1 + uvWidth] = uSample2;
                    nv[2 * (i * uvWidth + j)] = vSample1;
                    nv[2 * (i * uvWidth + j) + 1] = vSample1;
                    nv[2 * (i * uvWidth + j) + uvWidth] = vSample2;
                    nv[2 * (i * uvWidth + j) + 1 + uvWidth] = vSample2;
                }
            }
            //nv21test
            byte[] bytes = new byte[yuvFrame.length];
            System.arraycopy(y, 0, bytes, 0, y.length);
            for (int i = 0; i < u.length; i++) {
                bytes[y.length + (i * 2)] = nv[i];
                bytes[y.length + (i * 2) + 1] = nu[i];
            }

            findRGB(bytes, Environment.getExternalStorageDirectory() + "/DJI_ScreenShot");


        }
    }

    private synchronized void findRGB(byte[] bytes , String shotDir) {

        //ArrayList<Point> centri = new ArrayList<>();

        /*Create file for image*/

        File dir = new File(shotDir);
        if (!dir.exists() || !dir.isDirectory()) {
            dir.mkdirs();
        }
        OutputStream outputFile;
        final String path = dir + "/ScreenShot_" + System.currentTimeMillis() + "RGB version.jpg";
        try {
            outputFile = new FileOutputStream(new File(path));
        } catch (FileNotFoundException e) {
            Log.e(TAG, "test screenShot: new bitmap output file error: " + e);
            return;
        }

        /*convert YUV to ARGB*/
        rs = RenderScript.create(this);
        yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));

        if (yuvType == null)
        {
            yuvType = new Type.Builder(rs, Element.U8(rs)).setX(bytes.length);
            in = Allocation.createTyped(rs, yuvType.create(), Allocation.USAGE_SCRIPT);

            rgbaType = new Type.Builder(rs, Element.RGBA_8888(rs)).setX(SizeW).setY(SizeH);
            out = Allocation.createTyped(rs, rgbaType.create(), Allocation.USAGE_SCRIPT);
        }

        in.copyFrom(bytes);

        yuvToRgbIntrinsic.setInput(in);
        yuvToRgbIntrinsic.forEach(out);

        final Bitmap bmpout = Bitmap.createBitmap(SizeW, SizeH, Bitmap.Config.ARGB_8888);
        out.copyTo(bmpout);

        /*copy bitmap in array*/
        int width = bmpout.getWidth();
        int height = bmpout.getHeight();

        int[] pixels_l = new int[width * height];
        int[][] pixels = new int[height] [width];
        int[][] pixels_red = new int[height] [width];
        int[][] pixels_green =  new int[height] [width];
        int[][] pixels_blue =  new int[height] [width];
        int[][] pixels_alpha =  new int[height] [width];
        boolean[][] marker =  new boolean[height] [width];
        bmpout.getPixels(pixels_l, 0, width, 0, 0, width, height);



        //create matrix of different components
        for (int i=0;i<width*height;i++){

            int pixel = pixels_l[i];

            pixels[i/width][i%width]=pixels_l[i];

            //different component
            int pixel_red = pixel & red;
            pixel_red=pixel_red>>16;
            pixels_red[i/width][i%width]=pixel_red;

            int pixel_green = pixel & green;
            pixels_green[i/width][i%width]= pixel_green;

            int pixel_blue = pixel & blue;
            pixel_blue=pixel_blue<<16;
            pixels_blue[i/width][i%width]= pixel_blue;


        }


        /*get color of touched pixel in 0-255*/
        /*Inverted becouse flipped screen*/
        if (flag==false) {
            touched_R = pixels_red[y_touch][x_touch];
            touched_G = (pixels_green[y_touch][x_touch]) >> 8;
            touched_B = (pixels_blue[y_touch][x_touch]) >> 16;
            flag=true;

        }


        for (int i=0;i<width*height;i++){


            //rimappa in 0-255
            int shifted_red_pixel=pixels_red[i/width][i%width];
            int shifted_green_pixel=(pixels_green[i/width][i%width])>>8;
            int shifted_blue_pixel=(pixels_blue[i/width][i%width])>>16;


            if ((shifted_red_pixel>=touched_R*delta_min && shifted_red_pixel<=touched_R*delta_max)
                    && (shifted_green_pixel>=touched_G*delta_min && shifted_green_pixel<=touched_G*delta_max)
                    && (shifted_blue_pixel>=touched_B*delta_min && shifted_blue_pixel<=touched_B*delta_max)) {

                marker[i/width][i%width]=true;

                shifted_red_pixel=254;
                shifted_green_pixel=254;
                shifted_blue_pixel=254;

            }else{
                marker[i/width][i%width]=false;
            }

            pixels_red[i/width][i%width]=shifted_red_pixel;
            pixels_green[i/width][i%width]=shifted_green_pixel<<8;
            pixels_blue[i/width][i%width]=shifted_blue_pixel<<16;

        }



        marker= fillin (marker,width,height);
        marker= fillin (marker,width,height);
        marker= fillin (marker,width,height);
        find_center(marker,width,height);

        for (int i=0;i<width*height;i++){

            if (marker[i/width][i%width]==true){
                int shifted_red_pixel=254;
                int shifted_green_pixel=254;
                int shifted_blue_pixel=254;

                pixels_red[i/width][i%width]=shifted_red_pixel;
                pixels_green[i/width][i%width]=shifted_green_pixel<<8;
                pixels_blue[i/width][i%width]=shifted_blue_pixel<<16;

            }

        }

        //showToast(Integer.toString(centri.get(0).x)+"-"+Integer.toString(centri.get(0).y)+" "+Integer.toString(centri.get(1).x)+"-"+Integer.toString(centri.get(1).y));

        for (int i=0;i<centri.size();i++){
            for (int j=0;j<14;j++){

                int c =j-(j/2);
                Point temp_center = new Point(centri.get(i).x, centri.get(i).y);

                int shifted_red_pixel=255;
                int shifted_green_pixel=0;
                int shifted_blue_pixel=0;

                pixels_red[temp_center.y+c][temp_center.x]=shifted_red_pixel;
                pixels_green[temp_center.y+c][temp_center.x]=shifted_green_pixel<<8;
                pixels_blue[temp_center.y+c][temp_center.x]=shifted_blue_pixel<<16;

                pixels_red[temp_center.y][temp_center.x+c]=shifted_red_pixel;
                pixels_green[temp_center.y][temp_center.x+c]=shifted_green_pixel<<8;
                pixels_blue[temp_center.y][temp_center.x+c]=shifted_blue_pixel<<16;

                pixels_red[temp_center.y-c][temp_center.x]=shifted_red_pixel;
                pixels_green[temp_center.y-c][temp_center.x]=shifted_green_pixel<<8;
                pixels_blue[temp_center.y-c][temp_center.x]=shifted_blue_pixel<<16;

                pixels_red[temp_center.y][temp_center.x-c]=shifted_red_pixel;
                pixels_green[temp_center.y][temp_center.x-c]=shifted_green_pixel<<8;
                pixels_blue[temp_center.y][temp_center.x-c]=shifted_blue_pixel<<16;



            }
        }





        /*from here code to show image taken*/

        //recreate array RGB from components matrix
        for (int i=0;i<width*height;i++) {
            pixels_l[i] = pixels_blue[i/width][i%width] + pixels_green[i/width][i%width] + pixels_red[i/width][i%width]+pixels_alpha[i/width][i%width];

               /* if (marker[i/width][i%width]==true){
                    pixels_l[i]=pixels_l[i];

                }*/


        }

        //create new bitmap from array
        final Bitmap bmpout2 = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bmpout2.copyPixelsFromBuffer(IntBuffer.wrap(pixels_l));

        //show taken and processed image
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                show_image.setImageBitmap(bmpout2);
            }
        });




        /*convert RGB bitmap to jpeg and write to file*/


        bmpout2.compress(Bitmap.CompressFormat.JPEG, 50, outputFile);
        try {
            outputFile.close();
            bmpout.recycle();
            bmpout2.recycle();
           // showToast("Saved File");

        } catch (IOException e) {
            e.printStackTrace();
        }



        //clear
        yuvType = null;


    }

    private boolean[][] fillin(boolean[][] marker, int width, int height) {

        boolean[][] marker_second =  new boolean[height] [width];

        for (int i=1;i<height-1;i++){
            for (int j=1;j<width-1;j++){
                if(marker[i][j]==false){
                    if(marker[i-1][j-1]==true || marker[i-1][j]==true || marker[i-1][j+1]==true
                            || marker[i][j-1]==true || marker[i][j+1]==true
                            || marker[i+1][j-1]==true || marker[i+1][j]==true || marker[i+1][j+1]==true){
                        marker_second[i][j]=true;

                    }
                }

            }

        }

        for (int i=1;i<height-1;i++) {
            for (int j = 1; j < width - 1; j++) {
                marker[i][j] = marker[i][j] | marker_second[i][j];
            }
        }



        return marker;
    }

    private ArrayList find_center(boolean[][] marker, int width, int height) {

        int colon_elem;
        int delta=250; //spazio di incremento di dimensione della palla


        ArrayList<marker> active_marker = new ArrayList<>(); //marker trovati ma non ancora completi
        ArrayList<marker> completed_marker = new ArrayList<>(); // marker completi

        centri.clear();
        writeToFile("trovo centri",log_name);

        for (int i=0;i<width-1;i=i+2){ //per ogni colonna
            colon_elem=1;
            /*for (int j=0;j<height;j++){  //per ogni elemento della colonna
                if (marker[j][i]==true){ //controllo se nella colonna ci sono true
                    colon_elem++;
                }

            }*/
            //writeToFile("colon_elem="+String.valueOf(colon_elem),log_name);

            if (colon_elem>0){ // se nella colonna ci sono dei true, devo iniziare ad analizzarla, altrimenti non faccio nulla

                for (int j=0;j<height;j=j+2){
                    //String posizione = "["+Integer.toString(j)+"]"+"["+Integer.toString(i)+"]"+":"+Boolean.toString(marker[j][i]);
                    if (marker[j][i]==true){ //trovo un elemento posto a true
                        if (active_marker.size()==0){ // non ho marker attivi, quindi creo un nuovo marker per il punto
                            //writeToFile(posizione+": creo nuovo marker",log_name);
                            marker mymarker = new marker();// creo nuovo marker

                            mymarker.x_sum= mymarker.x_sum+i;
                            mymarker.y_sum= mymarker.y_sum+j;
                            mymarker.num_sum = mymarker.num_sum+1;

                            mymarker.min=j; //imposto minimo (per ogni ciclo elemento, tanto mi sposto in basso)
                            if (mymarker.max_set==false) { //controllo se non ho già impostato come massimo il pixel sopra
                                mymarker.max = j;     //imposto massimo
                                mymarker.max_set=true;
                            }

                            mymarker.active_this_column=true;

                            active_marker.add(mymarker);


                        }else { //ho almeno un marker gia attivo
                            //writeToFile(posizione+ ": ho un marker attivo",log_name);
                            int marker_index=-1;
                            for (int k=0;k<active_marker.size();k++){ //ciclo sui marker attivi
                                if (j>(active_marker.get(k).min-delta) && j< (active_marker.get(k).max+delta)){ //la pallina è nell'intorno di un marker attivo
                                    marker_index=k; //mi salvo il marker
                                    break;

                                }


                            }

                            if(marker_index==-1){ //significa che il punto non era all'interno di nessun marker attivo
                               // writeToFile(posizione+": il pto non era all'interno di un marker attivo",log_name);

                                marker mymarker = new marker();// creo nuovo marker

                                mymarker.x_sum= mymarker.x_sum+i;
                                mymarker.y_sum= mymarker.y_sum+j;
                                mymarker.num_sum = mymarker.num_sum+1;

                                mymarker.min=j; //imposto minimo (per ogni ciclo elemento, tanto mi sposto in basso)
                                if (mymarker.max_set==false) { //controllo se non ho già impostato come massimo il pixel sopra
                                    mymarker.max = j;     //imposto massimo
                                    mymarker.max_set=true;
                                }
                                mymarker.active_this_column=true;

                                active_marker.add(mymarker);

                            }else { // il punto era in un marker attivo
                                //writeToFile(posizione+": aggiungo a marker attivo",log_name);

                                active_marker.get(marker_index).x_sum=active_marker.get(marker_index).x_sum+i;
                                active_marker.get(marker_index).y_sum=active_marker.get(marker_index).y_sum+j;
                                active_marker.get(marker_index).num_sum=active_marker.get(marker_index).num_sum+1;

                                active_marker.get(marker_index).min=j;
                                if (active_marker.get(marker_index).max_set==false){
                                    active_marker.get(marker_index).max=j;
                                    active_marker.get(marker_index).max_set=true;
                                }
                                active_marker.get(marker_index).active_this_column=true;


                            }


                        }



                    }
                }



                //a questo punto ho passato tutta la colonna creando e aggiornando

                int elements = active_marker.size();

                ArrayList<Integer> to_be_removed = new ArrayList<>();
                for (int k=0;k<elements;k++){

                    //come prima cosa controllo se dei marker sono diventati inattivi
                    if(active_marker.get(k).active_this_column==false){//significa che questo turno non ho aggiunto nulla
                        //writeToFile("rimuovo",log_name);
                        completed_marker.add(active_marker.get(k)); //copio il marker in completed

                        to_be_removed.add(k);


                    }

                    //poi resetto tutte quelle variabili di controllo sulla colonna
                    active_marker.get(k).reset();

                }

                for (int k=0;k<to_be_removed.size();k++){
                    //writeToFile("rimuovo:"+Integer.toString((to_be_removed.get(k)-k))+"dove gli attivi sono:"+Integer.toString(active_marker.size()),log_name);
                    active_marker.remove(to_be_removed.get(k)-k);
                }

                to_be_removed.clear();


            }
            //writeToFile("colonna:"+Integer.toString(i),log_name);

        }

        //popolo centri
        //showToast(Integer.toString(active_marker.size()));

        writeToFile("Centri trovati:"+Integer.toString(completed_marker.size()),log_name);

        for (int i=0;i<completed_marker.size();i++){

            writeToFile("Centro:"+Integer.toString(completed_marker.get(i).compute_x())+"-:"+Integer.toString(completed_marker.get(i).compute_y()),log_name);

            centri.add(new Point(completed_marker.get(i).compute_x(),completed_marker.get(i).compute_y()));

        }





     return centri;
    }

    private ArrayList find_center_old(boolean[][] marker, int width, int height) {

        centri.clear();

        ArrayList<Point> centers = new ArrayList<>();
        ArrayList<Point> marked = new ArrayList<>();

        int contatore_temp=0;
        boolean area = false;
        ArrayList<Integer> inizio_area = new ArrayList<>();
        ArrayList<Integer> fine_area = new ArrayList<>();

        /*with this we find different areas*/
        for (int i=0;i<width;i++){
            for (int j=0;j<height;j++){
                if (marker[j][i]==true)
                    contatore_temp++;
            }

            if (contatore_temp>20 && area==false){
                inizio_area.add(i);
                area=true;
               // showToast(Integer.toString(i));

            }else if(contatore_temp<=20 && area==true ){

                fine_area.add(i);
                area=false;

            }
            contatore_temp=0;

        }

        //showToast(Integer.toString(zone.size()));

        //showToast(Integer.toString(inizio_area.get(0))+" "+Integer.toString(fine_area.get(0))+" "+Integer.toString(inizio_area.get(1))+" "+
          //      Integer.toString(fine_area.get(1))+" "+Integer.toString(inizio_area.get(2))+" "+Integer.toString(fine_area.get(2)));
        for (int i=0;i<inizio_area.size();i++){
            int sum_x=0;
            int sum_y=0;
            int elem=0;

            for (int j=inizio_area.get(i);j<fine_area.get(i);j++) {
                for (int k = 0; k < height; k++) {
                    if (marker[k][j]==true){
                        sum_x=sum_x+j;
                        sum_y=sum_y+k;
                        elem++;
                    }
                }
            }
            sum_x=sum_x/elem;
            sum_y=sum_y/elem;
            //showToast(Integer.toString(sum_x)+" "+Integer.toString(sum_y));
            Point centro = new Point(sum_x,sum_y);
            centers.add(centro);

        }

        for (int i=0;i<centers.size();i++){
            centri.add(centers.get(i));
        }







        return centers;


    }

    public void onClick(View v) {
        if (screenShot.isSelected()) {
            screenShot.setText("Screen Shot");
            screenShot.setSelected(false);
            DJIVideoStreamDecoder.getInstance().changeSurface(videostreamPreviewSh.getSurface());
            savePath.setText("");
            savePath.setVisibility(View.INVISIBLE);
            show_image.setVisibility(View.INVISIBLE);
            videostreamPreviewTtView.setVisibility(View.VISIBLE);
            videostreamPreviewSf.setVisibility(View.VISIBLE);
        } else {
            screenShot.setText("Live Stream");
            screenShot.setSelected(true);
            DJIVideoStreamDecoder.getInstance().changeSurface(null);
            savePath.setText("");
            savePath.setVisibility(View.VISIBLE);
            show_image.setVisibility(View.VISIBLE);
            videostreamPreviewTtView.setVisibility(View.INVISIBLE);
            videostreamPreviewSf.setVisibility(View.INVISIBLE);

            pathList.clear();
        }
    }

    private void displayPath(String path){
        path = path + "\n\n";
        if(pathList.size() < 6){
            pathList.add(path);
        }else{
            pathList.remove(0);
            pathList.add(path);
        }
        StringBuilder stringBuilder = new StringBuilder();
        for(int i = 0 ;i < pathList.size();i++){
            stringBuilder.append(pathList.get(i));
        }
        savePath.setText(stringBuilder.toString());
    }

    @Override
    public synchronized boolean onTouchEvent(MotionEvent event) {



        int[] loc = new int[2];
        videostreamPreviewSf.getLocationOnScreen(loc);

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        x_touch = (int) event.getX(); //x touch position
        y_touch = (int) event.getY(); //y touch position

        x_touch = x_touch-loc[0];   //offset picture
        y_touch = y_touch-loc[1];   //offset picture

        x_touch = x_touch*1280/size.x;  //normalization on picture dimension
        y_touch = y_touch*720/size.y;   //normalization on picture dimension

        DJIVideoStreamDecoder.getInstance().changeSurface(null);
        flag=false;

        DJIVideoStreamDecoder.getInstance().changeSurface(videostreamPreviewSh.getSurface());


       // showToast("color selected");

        //showToast(Integer.toString(x_touch)+"  "+Integer.toString(y_touch)+" "+Integer.toString(loc[0])+" "+Integer.toString(loc[1])+" "+Integer.toString(size.x)+Integer.toString(size.y));

        return false;
    }


    private void initFlightController() {
        DJIAircraft aircraft = MainActivity.getAircraftInstance();
        if (aircraft == null || !aircraft.isConnected()) {
            showToast("Disconnected");
            mFlightController = null;
            return;
        } else {
            mFlightController = aircraft.getFlightController();
        }
    }

    class SendVirtualStickDataTask extends TimerTask {

        @Override
        public void run() {

            if (mFlightController != null) {
                mFlightController.sendVirtualStickFlightControlData(
                        new DJIVirtualStickFlightControlData(
                                mPitch, mRoll, mYaw, mThrottle
                        ), new DJICommonCallbacks.DJICompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {

                            }
                        }
                );
            }
        }
    }

    private void writeToFile(String content, String name){

        // Find the root of the external storage.
        // See http://developer.android.com/guide/topics/data/data-  storage.html#filesExternal

        File root = android.os.Environment.getExternalStorageDirectory();


        // See http://stackoverflow.com/questions/3551821/android-write-to-sd-card-folder

        File dir = new File (root.getAbsolutePath() + "/DJI_log");
        dir.mkdirs();
        File file = new File(dir, name+"log.txt");

        try {

            FileOutputStream f = new FileOutputStream(file,true);
            OutputStreamWriter OutDataWriter  = new OutputStreamWriter(f);
            PrintWriter pw = new PrintWriter(f);
            OutDataWriter.append(content);
            OutDataWriter.append("\n");
            OutDataWriter.flush();
            OutDataWriter.close();
            f.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.i(TAG, "******* File not found. Did you" +
                    " add a WRITE_EXTERNAL_STORAGE permission to the   manifest?");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}