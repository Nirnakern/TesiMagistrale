package com.dji.videostreamdecodingsample;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
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
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


import dji.common.camera.DJICameraSettingsDef;
import dji.common.error.DJIError;
import dji.common.flightcontroller.DJIFlightControllerDataType;
import dji.common.flightcontroller.DJIVirtualStickFlightControlData;
import dji.common.flightcontroller.DJIVirtualStickFlightCoordinateSystem;
import dji.common.flightcontroller.DJIVirtualStickRollPitchControlMode;
import dji.common.flightcontroller.DJIVirtualStickVerticalControlMode;
import dji.common.product.Model;
import dji.common.util.DJICommonCallbacks;
import dji.sdk.airlink.DJILBAirLink;
import dji.sdk.base.DJIBaseProduct;
import dji.sdk.camera.DJICamera;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.flightcontroller.DJIFlightController;
import dji.sdk.products.DJIAircraft;

import static com.dji.videostreamdecodingsample.VideoDecodingApplication.getProductInstance;
import static dji.common.flightcontroller.DJIVirtualStickYawControlMode.AngularVelocity;


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
    private TextView findMarker;
    private TextView startMoving;
    private List<String> pathList = new ArrayList<>();

    private HandlerThread backgroundHandlerThread;
    public Handler backgroundHandler;

    protected DJICamera.CameraReceivedVideoDataCallback mReceivedVideoDataCallBack = null;
    protected DJILBAirLink.DJIOnReceivedVideoCallback mOnReceivedVideoCallback = null;

    //color
    int blue = 0b000000000000000011111111;
    int green = 0b000000001111111100000000;
    int red = 0b111111110000000000000000;
    int alpha = 0b111111110000000000000000000000;

    //bottoni
    int button =0;
    boolean flag_find=false;

    //fixed dimension of image
    int SizeW =1280;
    int SizeH=720;

    //point of touch
    int x_touch=0;
    int y_touch=0;

    int touched_R=0;
    int touched_G=0;
    int touched_B=0;
    boolean flag=false;

    //range colori accettabili
    double delta_min =0.5;
    double delta_max = 1.8;

    //clasterizzazione

    ArrayList<Point> centri = new ArrayList<>();
    ArrayList<Point> centri_old = new ArrayList<>();

    //range dimensioni

    double delta_min_dim =0.25;
    double delta_max_dim=4;


    //drone automatic fly

    private DJIFlightController mFlightController;


    private Timer mSendVirtualStickDataTimer;
    private SendVirtualStickDataTask mSendVirtualStickDataTask;
    private float mPitch;
    private float mRoll;
    private float mYaw;
    private float mThrottle;

    //cose per la conversione in RGB
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
        super.onDestroy();
        DJIVideoStreamDecoder.getInstance().destroy();


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
        initFlightController();

        //start_drone();
        //enable_virtual_control();

        float yaw =1f; //
        float throttle = 0.0f; //
        float pitch =0.0f; //
        float roll = 0.0f; //

       // move_drone(yaw,throttle,pitch,roll,2000);

        //move_drone(0f,1f,0f,0f,2000);
        //move_drone(0f,0f,1f,0f,2000);
        //move_drone(0f,0f,0f,1f,2000);







//        move_drone(0.5f, 0.5f ,0.5f, 0.5f);
    }



    private synchronized void  move_drone(float yaw, float throttle, float pitch, float roll, int dur){

        enable_virtual_control();

        float pitchJoyControlMaxSpeed = DJIFlightControllerDataType.DJIVirtualStickRollPitchControlMaxVelocity;
        float rollJoyControlMaxSpeed = DJIFlightControllerDataType.DJIVirtualStickRollPitchControlMaxVelocity;
        float verticalJoyStickControlMaxSpeed = DJIFlightControllerDataType.DJIVirtualStickVerticalControlMaxVelocity;
        float yawJoyStickControlMaxSpeed = DJIFlightControllerDataType.DJIVirtualStickYawControlMaxAngularVelocity;

        mYaw = (float)(verticalJoyStickControlMaxSpeed * yaw);
        mThrottle = (float)(yawJoyStickControlMaxSpeed * throttle);

        mPitch =(float)(pitchJoyControlMaxSpeed * pitch);

        mRoll = (float)(rollJoyControlMaxSpeed * roll);




        if (null == mSendVirtualStickDataTimer) {
            mSendVirtualStickDataTask = new SendVirtualStickDataTask();
            mSendVirtualStickDataTimer = new Timer();
            mSendVirtualStickDataTimer.schedule(mSendVirtualStickDataTask, 0, 200);
        }

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                disable_virtual_control();
            }
        }, dur);


    }

    private void disable_virtual_control(){
        if (mFlightController != null){
            mFlightController.disableVirtualStickControlMode(
                    new DJICommonCallbacks.DJICompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            if (djiError != null) {
                                showToast(djiError.getDescription());
                            } else {
                                //showToast("Disable Virtual Stick Success");
                            }
                        }
                    }
            );
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
                                //showToast("Enable Virtual Stick Success");
                            }
                        }
                    }
            );
            mFlightController.setVirtualStickAdvancedModeEnabled(true);
            mFlightController.setYawControlMode(AngularVelocity);
            mFlightController.setRollPitchControlMode(DJIVirtualStickRollPitchControlMode.Velocity);
            mFlightController.setHorizontalCoordinateSystem(DJIVirtualStickFlightCoordinateSystem.Body);
            mFlightController.setVerticalControlMode(DJIVirtualStickVerticalControlMode.Velocity);
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
        findMarker = (TextView) findViewById(R.id.activity_main_find_marker);
        findMarker.setSelected(false);
        startMoving = (TextView) findViewById(R.id.activity_main_start_moving);
        startMoving.setSelected(false);
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

        if (DJIVideoStreamDecoder.getInstance().frameIndex % 120 == 0 && (button==2 || (button ==1 && flag_find==false))) { //famo la cosa ogni 30 frame

            writeToFile("chiedo di fare foto su SD",log_name);
            shootSD();

            writeToFile("scrivo su file, button = "+Integer.toString(button),log_name);
            if (button==1)
                flag_find=true;

            //qui mi creo degli array, nulla di che
            byte[] y = new byte[width * height];
            byte[] u = new byte[width * height / 4];
            byte[] v = new byte[width * height / 4];
            byte[] nu = new byte[width * height / 4]; //
            byte[] nv = new byte[width * height / 4];


            //copio yuv frame in y per w*h posti
            System.arraycopy(yuvFrame, 0, y, 0, y.length);


            //copio u e v
            for (int i = 0; i < u.length; i++) {
                v[i] = yuvFrame[y.length + 2 * i];
                u[i] = yuvFrame[y.length + 2 * i + 1];
            }


            // giusto variabili sensate
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

    private void shootSD() {
        DJICameraSettingsDef.CameraMode cameraMode = DJICameraSettingsDef.CameraMode.ShootPhoto;
        DJICamera camera = mProduct.getCamera();
        if (camera != null) {
            writeToFile("salvo foto",log_name);
            DJICameraSettingsDef.CameraShootPhotoMode photoMode = DJICameraSettingsDef.CameraShootPhotoMode.Single; // Set the camera capture mode as Single mode
            camera.startShootPhoto(photoMode, new DJICommonCallbacks.DJICompletionCallback() {
                @Override
                public void onResult(DJIError error) {
                    if (error == null) {
                        //showToast("take photo: success");
                    } else {
                        showToast(error.getDescription());
                    }
                }
            }); // Execute the startShootPhoto API
        }else {
            writeToFile("foto non salvata",log_name);
        }

    }

    private void findRGB(byte[] bytes , String shotDir) {

        writeToFile("entro in findRGB",log_name);



        //Create file for image

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

        //convert YUV to ARGB
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

        //copy bitmap in array
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


        //get color of touched pixel in 0-255
        //Inverted becouse flipped screen
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

        find_center(marker, width, height);


        writeToFile("sono subito dopo aver trovato i centri",log_name);
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

        writeToFile("ho cambiato i colori in bianco ",log_name);


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

        writeToFile("arrivo a dover mostrare img",log_name);


        //from here code to show image taken

        //recreate array RGB from components matrix
        for (int i=0;i<width*height;i++) {
            pixels_l[i] = pixels_blue[i/width][i%width] + pixels_green[i/width][i%width] + pixels_red[i/width][i%width]+pixels_alpha[i/width][i%width];



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




        //convert RGB bitmap to jpeg and write to file


       /* bmpout2.compress(Bitmap.CompressFormat.JPEG, 50, outputFile);
        try {
            outputFile.close();

            bmpout.recycle();
            bmpout2.recycle();
           // showToast("Saved File");

        } catch (IOException e) {
            e.printStackTrace();
        }*/



        //clear
        yuvType = null;
        rs.finish();
        rs.destroy();
        yuvToRgbIntrinsic.destroy();
        in.destroy();
        out.destroy();

    }

    private void fast_find_center(boolean[][] marker, int width, int height) {
        writeToFile( "trovo centri",log_name);
        int raggio_pallina=150;

        centri_old.clear();
        for (int i=0; i<centri.size();i++){
            centri_old.add(centri.get(i));
        }
        centri.clear();

        int x_sum=0;
        int y_sum=0;
        int num_sum=0;
        ArrayList<Point> centri_temp = new ArrayList<>();


        writeToFile("inizio analisi di "+Integer.toString(centri_old.size())+"centri",log_name);

        //      1   2   3
        //      4   p   5
        //      6   7   8

        for (int j=0;j<centri_old.size();j++){
            Point uno = new Point();
            Point due = new Point();
            Point tre = new Point();
            Point quattro = new Point();
            Point cinque = new Point();
            Point sei = new Point();
            Point sette = new Point();
            Point otto = new Point();
            Boolean[] flags = new Boolean[]{false,false,false,false};


            Point p = centri_old.get(j);
            int i=0;
            while(true){


                //due
                if (marker[p.y+i][p.x]==true) {
                    due.set(p.x, p.y + i);
                    flags[0]=false;
                }
                else
                    flags[0]=true;
                //quattro
                if (marker[p.y][p.x-i]==true) {
                    quattro.set(p.x - i, p.y);
                    flags[1]=false;
                }
                else
                    flags[1]=true;

                //cinque
                if (marker[p.y][p.x+i]==true) {
                    cinque.set(p.x + i, p.y);
                    flags[2]=false;
                }
                else
                    flags[2]=true;
                //sette
                if (marker[p.y-i][p.x]==true) {
                    sette.set(p.x, p.y - i);
                    flags[3]=false;
                }
                else
                    flags[3]=true;

                i++;
                if ((flags[0]==true && flags[1]==true && flags[2]==true && flags[3]==true) || i>250 ){
                    writeToFile("i="+Integer.toString(i),log_name);
                    break;
                }


            }

            int temp_x = (due.x+quattro.x+cinque.x+sette.x)/4;
            int temp_y = (due.y+quattro.y+cinque.y+sette.y)/4;
            centri_temp.add(new Point(temp_x,temp_y));


        }



        for (int j=0;j<centri_temp.size();j++){
            Point uno = new Point();
            Point due = new Point();
            Point tre = new Point();
            Point quattro = new Point();
            Point cinque = new Point();
            Point sei = new Point();
            Point sette = new Point();
            Point otto = new Point();
            Boolean[] flags = new Boolean[]{false,false,false,false,false,false,false,false};


            Point p = centri_temp.get(j);
            int i=0;
            while(true){

                //uno
                if (marker[p.y+i][p.x-i]==true) {
                    uno.set(p.x - i, p.y + i);
                    flags[0]=false;
                }
                else
                    flags[0]=true;

                //due
                if (marker[p.y+i][p.x]==true) {
                    due.set(p.x, p.y + i);
                    flags[1]=false;
                }
                else
                    flags[1]=true;

                //tre
                if (marker[p.y+i][p.x+i]==true) {
                    tre.set(p.x + i, p.y + i);
                    flags[2]=false;
                }
                else
                    flags[2]=true;

                //quattro
                if (marker[p.y][p.x-i]==true) {
                    quattro.set(p.x - i, p.y);
                    flags[3]=false;
                }
                else
                    flags[3]=true;

                //cinque
                if (marker[p.y][p.x+i]==true) {
                    cinque.set(p.x + i, p.y);
                    flags[4]=false;
                }
                else
                    flags[4]=true;

                //sei
                if (marker[p.y-i][p.x-i]==true) {
                    sei.set(p.x - i, p.y - i);
                    flags[5]=false;
                }
                else
                    flags[5]=true;

                //sette
                if (marker[p.y-i][p.x]==true) {
                    sette.set(p.x, p.y - i);
                    flags[6]=false;
                }
                else
                    flags[6]=true;

                //otto
                if (marker[p.y-i][p.x+i]==true) {
                    otto.set(p.x + i, p.y - i);
                    flags[7]=false;
                }
                else
                    flags[7]=true;


                i++;
                if ((flags[0]==true && flags[1]==true && flags[2]==true && flags[3]==true && flags[4]==true && flags[5]==true && flags[6]==true
                        && flags[7]==true) || i>250 ) {
                    writeToFile("i="+Integer.toString(i),log_name);
                    break;
                }
            }

            int temp_x = (uno.x+due.x+tre.x+quattro.x+cinque.x+sei.x+sette.x+otto.x)/8;
            int temp_y = (uno.y+due.y+tre.y+quattro.y+cinque.y+sei.y+sette.y+otto.y)/8;
            centri.add(new Point(temp_x,temp_y));


        }




        /*for (int i=0;i<centri_old.size();i++){

            int y_min=0;
            int y_max=718;

            if(centri_old.get(i).y-raggio_pallina>0)
                y_min=centri_old.get(i).y-raggio_pallina;
            if(centri_old.get(i).y+raggio_pallina<720)
                y_max=centri_old.get(i).y+raggio_pallina;

            int x_min=0;
            int x_max=1278;



            if(centri_old.get(i).x-raggio_pallina>0)
                x_min=centri_old.get(i).x-raggio_pallina;
            if(centri_old.get(i).x+raggio_pallina<1280)
                x_max=centri_old.get(i).x+raggio_pallina;

            writeToFile("analisi spazio y:"+Integer.toString(y_min)+"-"+Integer.toString(y_max)+" x:"+
                    Integer.toString(x_min)+"-"+Integer.toString(x_max),log_name);

            for (int j=y_min;j<y_max;j++){
                for (int k=x_min;k<x_max;k++) {

                    //writeToFile("controllo"+Integer.toString(j)+"-"+Integer.toString(k)+"\n",log_name);
                    if (marker[j][k]==true){
                        x_sum=x_sum+k;
                        y_sum=y_sum+j;
                        num_sum=num_sum+1;
                    }
                }
            }
            if (num_sum>0){
                writeToFile("aggiungo centro",log_name);
                Point pto = new Point(x_sum/num_sum,y_sum/num_sum);
                centri.add(pto);
            }
            x_sum=0;
            y_sum=0;
            num_sum=0;


        }*/
        writeToFile("numero di centri trovati velocemente:"+Integer.toString(centri.size()),log_name);
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
        int delta=200; //spazio di incremento di dimensione della palla


        ArrayList<marker> active_marker = new ArrayList<>(); //marker trovati ma non ancora completi
        ArrayList<marker> completed_marker = new ArrayList<>(); // marker completi

        centri.clear();
        writeToFile("trovo centri",log_name);

        for (int i=0;i<width-1;i=i+2){ //per ogni colonna
            colon_elem=1;

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

        //writeToFile("Centri trovati prima del controllo:"+Integer.toString(completed_marker.size()),log_name);

        //controlliamo se tutti sono sensati

        long dim_media=0;
        for (int i=0;i<completed_marker.size();i++){
            dim_media=dim_media+completed_marker.get(i).num_sum;
        }

        dim_media=dim_media/completed_marker.size();

        for (int i=0;i<completed_marker.size();i++){

            if (completed_marker.get(i).num_sum>dim_media*delta_min_dim && completed_marker.get(i).num_sum<dim_media*delta_max_dim) {
                //writeToFile("Centro:" + Integer.toString(completed_marker.get(i).compute_x()) + "-:" + Integer.toString(completed_marker.get(i).compute_y()), log_name);

                centri.add(new Point(completed_marker.get(i).compute_x(), completed_marker.get(i).compute_y()));

            }
        }
        //writeToFile("Centri trovati dopo il controllo:"+Integer.toString(centri.size()),log_name);






        return centri;
    }


    public void onClick_find(View v) {
        button=1;
        flag_find=false;

        if (findMarker.isSelected()) {

            findMarker.setText("Find Marker");
            findMarker.setSelected(false);
            DJIVideoStreamDecoder.getInstance().changeSurface(videostreamPreviewSh.getSurface());
            savePath.setText("");
            savePath.setVisibility(View.INVISIBLE);
            show_image.setVisibility(View.INVISIBLE);
            videostreamPreviewTtView.setVisibility(View.VISIBLE);
            videostreamPreviewSf.setVisibility(View.VISIBLE);
        } else {
            findMarker.setText("marker found");
            findMarker.setSelected(true);
            DJIVideoStreamDecoder.getInstance().changeSurface(null);
            savePath.setText("");
            savePath.setVisibility(View.VISIBLE);
            show_image.setVisibility(View.VISIBLE);
            videostreamPreviewTtView.setVisibility(View.INVISIBLE);
            videostreamPreviewSf.setVisibility(View.INVISIBLE);

            pathList.clear();

        }
    }

    public void onClick_move(View v) {
        button = 2;

        if (startMoving.isSelected()) {
            startMoving.setText("Start Moving");
            startMoving.setSelected(false);
        } else {
            startMoving.setText("drone moving");
            startMoving.setSelected(true);
           // DJIVideoStreamDecoder.getInstance().changeSurface(null);

        }
    }

    public void onClick_land(View v) {

        startLanding();

    }

    public void onClick_panic(View v) {

        disable_virtual_control();

    }

    private void startLanding() {

        if (mFlightController != null){
            mFlightController.autoLanding(
                    new DJICommonCallbacks.DJICompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            if (djiError != null) {
                                //showToast(djiError.getDescription());
                            } else {
                                showToast("Landing Success");
                            }
                        }
                    }
            );


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