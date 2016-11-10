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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


import dji.common.camera.DJICameraSettingsDef;
import dji.common.error.DJIError;
import dji.common.flightcontroller.DJIFlightControllerControlMode;
import dji.common.flightcontroller.DJIFlightControllerDataType;
import dji.common.flightcontroller.DJIVirtualStickFlightControlData;
import dji.common.flightcontroller.DJIVirtualStickFlightCoordinateSystem;
import dji.common.flightcontroller.DJIVirtualStickRollPitchControlMode;
import dji.common.flightcontroller.DJIVirtualStickVerticalControlMode;
import dji.common.flightcontroller.DJIVirtualStickYawControlMode;
import dji.common.product.Model;
import dji.common.util.DJICommonCallbacks;
import dji.sdk.airlink.DJILBAirLink;
import dji.sdk.base.DJIBaseProduct;
import dji.sdk.camera.DJICamera;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.flightcontroller.DJIFlightController;
import dji.sdk.products.DJIAircraft;

import static com.dji.videostreamdecodingsample.VideoDecodingApplication.getProductInstance;
import static dji.common.flightcontroller.DJIVirtualStickYawControlMode.Angle;
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
    double delta_min =0.50;
    double delta_max = 2.0;

    //clasterizzazione

    ArrayList<Point> centri = new ArrayList<>();
    ArrayList<Point> centri_old = new ArrayList<>();

    //range dimensioni

    double delta_min_dim =0.25;
    double delta_max_dim=4;

    //blocchi

    boolean moving= false;
    //boolean photo_taken = false;


    //drone automatic fly

    private DJIFlightController mFlightController;


    private Timer mSendVirtualStickDataTimer;
    private SendVirtualStickDataTask mSendVirtualStickDataTask;
    private float mPitch;
    private float mRoll;
    private float mYaw;
    private float mThrottle;

    private float tPitch;
    private float tRoll;

    //cose per la conversione in RGB
    private RenderScript rs;
    private ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic;
    private Type.Builder yuvType, rgbaType;
    private Allocation in, out;

    //moviment value

    float move_speed = (float) 0.05; //velocità com cui mi muovo 1=>15 m/s
    int move_dur =500; //tempo in cui mi muovo
    int rotate_dur=500; //tempo in cui giro (tenere basso per non perdere controllo)

    boolean stop=true;

    //moviment and photo flag

    boolean position =false;
    boolean photo_taken = false;
    boolean angle_is_good =false;
    boolean distance_is_good = false;
    int centre_position =720-150; //from botton=> 750-pos

    Point p1 = new Point();
    Point p2 = new Point();
    boolean point_updated=false;

    //debug

    int debug_contatore=0;




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

        //mFlightController.setHorizontalCoordinateSystem(DJIVirtualStickFlightCoordinateSystem.Body);

        float yaw =0.0f; //  180gradi
        float throttle = 0.0f; //
        float pitch =01.0f; // sposto di lato
        float roll = 00.0f; // 10m/s credo sposto dritto

       //move_drone(yaw,throttle,pitch,roll,3000);

        //move_drone(0f,1f,0f,0f,2000);
        //move_drone(0f,0f,1f,0f,2000);
        //move_drone(0f,0f,0f,1f,2000);







//        move_drone(0.5f, 0.5f ,0.5f, 0.5f);
    }



    private synchronized void  move_drone(float yaw, float angle, float velocity, float throttle, int dur){

        enable_virtual_control();

        Double heading = mFlightController.getCompass().getHeading();


        yaw = correctyaw(yaw,heading);

        correctRollPitch(heading, angle,velocity);





        float pitchJoyControlMaxSpeed = DJIFlightControllerDataType.DJIVirtualStickRollPitchControlMaxVelocity;
        float rollJoyControlMaxSpeed = DJIFlightControllerDataType.DJIVirtualStickRollPitchControlMaxVelocity;
        float verticalJoyStickControlMaxSpeed = DJIFlightControllerDataType.DJIVirtualStickVerticalControlMaxVelocity;
        //float yawJoyStickControlMaxSpeed = DJIFlightControllerDataType.DJIVirtualStickYawControlMaxAngle;

        mYaw = yaw;//(float)(yawJoyStickControlMaxSpeed * yaw);
        mThrottle = (float)( verticalJoyStickControlMaxSpeed * throttle);

        mPitch =(float)(pitchJoyControlMaxSpeed * tPitch);

        mRoll = (float)(rollJoyControlMaxSpeed * tRoll);




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
            //mFlightController.setHorizontalCoordinateSystem(DJIVirtualStickFlightCoordinateSystem.Body);
            mFlightController.setYawControlMode(DJIVirtualStickYawControlMode.Angle);
            mFlightController.setRollPitchControlMode(DJIVirtualStickRollPitchControlMode.Velocity);
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



        if (photo_taken==true){//ho appena fatto la foto, quindi mi sposto a dx

            move_drone(0,90,0.05f,0,400); //mi muovo a dx a 75 cm/s per 0.4secondi
            photo_taken=false;
            showToast("muovo a dx");
        }else {

            if ((DJIVideoStreamDecoder.getInstance().frameIndex % 180 == 0 && (button == 2 || (button == 1 && flag_find == false))) && moving == false) { //ricordati che prendi i dati a 30fps non 60
                debug_contatore++;
                writeToFile(Integer.toString(debug_contatore),log_name);

                if (position==true){
                    writeToFile("faccio foto",log_name);
                    shootSD();
                    photo_taken=true;
                    showToast("faccio foto");
                    position=false;
                }else {

                    //writeToFile("scrivo su file, button = "+Integer.toString(button),log_name);
                    if (button == 1)
                        flag_find = true;

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
        }

    }

    private void shootSD() {

        if (mCamera != null) {

            //writeToFile("salvo foto",log_name);
            DJICameraSettingsDef.CameraShootPhotoMode photoMode = DJICameraSettingsDef.CameraShootPhotoMode.Single; // Set the camera capture mode as Single mode
            mCamera.startShootPhoto(photoMode, new DJICommonCallbacks.DJICompletionCallback() {
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
            //writeToFile("foto non salvata",log_name);
        }

    }

    private void findRGB(byte[] bytes , String shotDir) {

        //writeToFile("entro in findRGB",log_name);



        //Create file for image

        /*File dir = new File(shotDir);
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
        }*/

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
        //writeToFile("sono subito dopo aver trovato i centri",log_name);
        Double angle = compute_angle();

            writeToFile("calcolo anggolo",log_name);


            if (angle>-20 && angle<20){
                angle_is_good=true;
            }else {
                angle = angle%180;
                if(angle>0 && angle<90){
                    showToast("ruoto a dx");
                }else{
                    showToast("ruoto a sx");
                }

//
//                showToast("ruoto:"+Double.toString(angle)+"da"+Double.toString(angle));
//                moving = true;
//                move_and_rotate(angle, 0);
//                moving = false;
            }
         if(angle_is_good==true && distance_is_good==false){

            writeToFile("calcolo distanza",log_name);
            int distance = compute_distance(angle);

            if (distance>-50 && distance <50){
                distance_is_good=true;
            }else{
                if (distance>0){ // => riga più in alto del pto di riferimento, perchè le righe si contano dall'alto
                    showToast("muovo indietro");
//                    moving = true;
//                    move_and_rotate(0.00, distance);
//                    moving = false;
                }else{ //=> riga più in basso
                    showToast("muovo avanti");
//                    moving = true;
//                    move_and_rotate(0.00, distance);
//                    moving = false;

                }

            }


        }else if (angle_is_good==true && distance_is_good==true){
            writeToFile("tutto ok, setto position=true",log_name);
            position=true;
            angle_is_good=false;
            distance_is_good=false;
        }
        //writeToFile("ho mosso il drone",log_name);

        //coloro di bianco i marker
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

        //writeToFile("ho cambiato i colori in bianco ",log_name);

        //coloro di rosso i centri
        for (int i=0;i<centri.size();i++){
            for (int j=0;j<14;j++){

                int c =j-(j/2);
                Point temp_center = new Point(centri.get(i).x, centri.get(i).y);

                int shifted_red_pixel=255;
                int shifted_green_pixel=0;
                int shifted_blue_pixel=0;

                int temp_center_y_plus_c=temp_center.y+c;
                int temp_center_y_minus_c=temp_center.y-c;
                int temp_center_x_plus_c=temp_center.x+c;
                int temp_center_x_minus_c=temp_center.x-c;

                if (temp_center_y_plus_c<720) {
                    pixels_red[temp_center_y_plus_c][temp_center.x] = shifted_red_pixel;
                    pixels_green[temp_center_y_plus_c][temp_center.x] = shifted_green_pixel << 8;
                    pixels_blue[temp_center_y_plus_c][temp_center.x] = shifted_blue_pixel << 16;
                }

                if (temp_center_x_plus_c<1280) {
                    pixels_red[temp_center.y][temp_center_x_plus_c] = shifted_red_pixel;
                    pixels_green[temp_center.y][temp_center_x_plus_c] = shifted_green_pixel << 8;
                    pixels_blue[temp_center.y][temp_center_x_plus_c] = shifted_blue_pixel << 16;
                }

                if(temp_center_y_minus_c>0) {
                    pixels_red[temp_center_y_minus_c][temp_center.x] = shifted_red_pixel;
                    pixels_green[temp_center_y_minus_c][temp_center.x] = shifted_green_pixel << 8;
                    pixels_blue[temp_center_y_minus_c][temp_center.x] = shifted_blue_pixel << 16;
                }

                if(temp_center_x_minus_c>0) {
                    pixels_red[temp_center.y][temp_center_x_minus_c] = shifted_red_pixel;
                    pixels_green[temp_center.y][temp_center_x_minus_c] = shifted_green_pixel << 8;
                    pixels_blue[temp_center.y][temp_center_x_minus_c] = shifted_blue_pixel << 16;
                }



            }
        }

        int centro_x=640;
        int centro_y =360;

        //coloro di blu l'angolo
        /*for (int i=0;i<30;i++){

            int shifted_red_pixel=0;
            int shifted_green_pixel=0;
            int shifted_blue_pixel=255;

            int y= (int) (Math.tan(Math.toRadians(angle))*i);


            //evito di uscire dall'array
            int centro_piu_y = centro_y+y;
            int centro_meno_y = centro_y-y;

            if (centro_piu_y<0)
                centro_piu_y=0;
            if (centro_piu_y>719)
                centro_piu_y=719;


            if (centro_meno_y<0)
                centro_meno_y=0;
            if (centro_meno_y>719)
                centro_meno_y=719;

            int centro_piu_i = centro_y+y;
            int centro_meno_i = centro_y-y;

            if (centro_piu_i<0)
                centro_piu_i=0;
            if (centro_piu_i>1279)
                centro_piu_i=1279;


            if (centro_meno_i<0)
                centro_meno_i=0;
            if (centro_meno_i>1279)
                centro_meno_i=1279;

            pixels_red[centro_piu_y][centro_piu_i]=shifted_red_pixel;
            pixels_green[centro_piu_y][centro_piu_i]=shifted_green_pixel<<8;
            pixels_blue[centro_piu_y][centro_piu_i]=shifted_blue_pixel<<16;

            pixels_red[centro_meno_y][centro_meno_i]=shifted_red_pixel;
            pixels_green[centro_meno_y][centro_meno_i]=shifted_green_pixel<<8;
            pixels_blue[centro_meno_y][centro_meno_i]=shifted_blue_pixel<<16;





        }*/

        //writeToFile("arrivo a dover mostrare img",log_name);

        //create mask

        for (int i=0;i<width;i++) {

            int shifted_red_pixel=0;
            int shifted_green_pixel=0;
            int shifted_blue_pixel=255;

            pixels_red[centre_position][i]=shifted_red_pixel;
            pixels_green[centre_position][i]=shifted_green_pixel<<8;
            pixels_blue[centre_position][i]=shifted_blue_pixel<<16;

        }

        for (int i=0;i<height;i++) {

            int shifted_red_pixel=0;
            int shifted_green_pixel=0;
            int shifted_blue_pixel=255;

            pixels_red[i][640]=shifted_red_pixel;
            pixels_green[i][640]=shifted_green_pixel<<8;
            pixels_blue[i][640]=shifted_blue_pixel<<16;

        }

        //make line

        if(point_updated==true) {

            //writeToFile("coordinate p1:"+Integer.toString(p1.x)+"-"+Integer.toString(p1.y),log_name);
            //writeToFile("coordinate p2:"+Integer.toString(p2.x)+"-"+Integer.toString(p2.y),log_name);
            float incremento = (float)(((float) (p2.y - p1.y)) / ((float)(p2.x - p1.x)));
            //writeToFile("incremento:"+Float.toString(incremento),log_name);

            contatore=0;
            for (int i = p1.x; i < p2.x; i++) {

                //writeToFile("coordinate nuovo punto:"+Integer.toString(i)+"-"+Integer.toString(p1.y+((int)(incremento*contatore))),log_name);
                int shifted_red_pixel=0;
                int shifted_green_pixel=255;
                int shifted_blue_pixel=0;

                pixels_red[p1.y+((int)(incremento*contatore))][i]=shifted_red_pixel;
                pixels_green[p1.y+((int)(incremento*contatore))][i]=shifted_green_pixel<<8;
                pixels_blue[p1.y+((int)(incremento*contatore))][i]=shifted_blue_pixel<<16;

                contatore++;

            }
            point_updated=false;
        }



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

    private int compute_distance(Double angle) {

        ArrayList<Marker_percorso> local_centri_positive = new ArrayList<>();
        ArrayList<Marker_percorso> local_centri_negative = new ArrayList<>();
        Point centro = new Point(640,720);

        for (int i=0;i<centri.size();i++){
            Marker_percorso mark = new Marker_percorso();
            mark.coordinate.set(centri.get(i).x,(centri.get(i).y));
            int distanza =(int)Math.abs( Math.sqrt(Math.pow((centro.x-mark.coordinate.x),2)+Math.pow((centro.y-mark.coordinate.y),2)));
            mark.distanza=distanza;
            if (mark.coordinate.x-centro.x>0){
                local_centri_positive.add(mark);
            }else{
                local_centri_negative.add(mark);
            }

        }

        //writeToFile("Creato oggetti",log_name);

        Collections.sort(local_centri_positive, new Comparator<Marker_percorso>() {
            @Override
            public int compare(Marker_percorso o1, Marker_percorso o2) {
                if (o1.distanza>o2.distanza)
                    return o2.distanza;
                else
                    return o1.distanza;
            }
        });

        Collections.sort(local_centri_negative, new Comparator<Marker_percorso>() {
            @Override
            public int compare(Marker_percorso o1, Marker_percorso o2) {
                if (o1.distanza>o2.distanza)
                    return o2.distanza;
                else
                    return o1.distanza;
            }
        });


        //writeToFile("nuovo giro:",log_name);
       // for (int i=0;i<local_centri_negative.size();i++){
        //    writeToFile("centro:"+Integer.toString(i)+"coordinate"+Integer.toString(local_centri_negative.get(i).coordinate.x)+"-"+Integer.toString(local_centri_negative.get(i).coordinate.y)+"distanza:"+
        //            Integer.toString(local_centri_negative.get(i).distanza),log_name);
        //}

        //writeToFile("dx:",log_name);
        //for (int i=0;i<local_centri_positive.size();i++){
        //    writeToFile("centro:"+Integer.toString(i)+"coordinate"+Integer.toString(local_centri_positive.get(i).coordinate.x)+"-"+Integer.toString(local_centri_positive.get(i).coordinate.y)+"distanza:"+
        //            Integer.toString(local_centri_positive.get(i).distanza),log_name);
       // }



        if (local_centri_negative.size()>0 && local_centri_positive.size()>0) {

            p1.set(local_centri_negative.get(local_centri_negative.size()-1).coordinate.x,local_centri_negative.get(local_centri_negative.size()-1).coordinate.y);
            p2.set(local_centri_positive.get(0).coordinate.x,local_centri_positive.get(0).coordinate.y);
            point_updated=true;

            int x_punto = local_centri_negative.get(local_centri_negative.size()-1).coordinate.x;
            int y_punto = local_centri_negative.get(local_centri_negative.size()-1).coordinate.y;

            double m = Math.tan(Math.toRadians(angle));

            int x_centro = (int) (((  640-x_punto) * m) + y_punto);

            return (x_centro - centre_position);
        }else {
            return 0;
        }




    }

    private void move_and_rotate(Double correction_angle, int distance) {
        if (correction_angle!=0) {

            if (stop == false) {  //ho il permesso di muovermi

                double temp = correction_angle;
                float myCorrectionAngle = (float) temp;

                move_drone(myCorrectionAngle, 0, 0, 0, rotate_dur); //sommo 180 perchè uso l'angolo complementare, mi muovo nella direzione opposta

                try {
                    wait(move_dur);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        }else {

            if (stop == false) {


                //move_drone(0, 0, 0.03f, 0, distance); //sommo 180 perchè uso l'angolo complementare, mi muovo nella direzione opposta


                try {
                    wait(distance);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }


            }
        }

    }


    private Double compute_angle() {




        ArrayList<Marker_percorso> local_centri_positive = new ArrayList<>();
        ArrayList<Marker_percorso> local_centri_negative = new ArrayList<>();
        Point centro = new Point(640,centre_position);

        for (int i=0;i<centri.size();i++){
            Marker_percorso mark = new Marker_percorso();
            mark.coordinate.set(centri.get(i).x,(centri.get(i).y));
            int distanza =(int)Math.abs( Math.sqrt(Math.pow((centro.x-mark.coordinate.x),2)+Math.pow((centro.y-mark.coordinate.y),2)));
            mark.distanza=distanza;
            if (mark.coordinate.x-centro.x>0){
                local_centri_positive.add(mark);
            }else{
                local_centri_negative.add(mark);
            }

        }

        //writeToFile("Creato oggetti",log_name);

        Collections.sort(local_centri_positive, new Comparator<Marker_percorso>() {
            @Override
            public int compare(Marker_percorso o1, Marker_percorso o2) {
                if (o1.distanza>o2.distanza)
                    return o2.distanza;
                else
                    return o1.distanza;
            }
        });

        Collections.sort(local_centri_negative, new Comparator<Marker_percorso>() {
            @Override
            public int compare(Marker_percorso o1, Marker_percorso o2) {
                if (o2.distanza>o1.distanza)
                    return o2.distanza;
                else
                    return o1.distanza;
            }
        });

//        writeToFile("nuovo giro:",log_name);
//        for (int i=0;i<local_centri_negative.size();i++){
//            writeToFile("centro:"+Integer.toString(i)+"coordinate"+Integer.toString(local_centri_negative.get(i).coordinate.x)+"-"+Integer.toString(local_centri_negative.get(i).coordinate.y)+"distanza:"+
//            Integer.toString(local_centri_negative.get(i).distanza),log_name);
//        }

        //writeToFile("Ordinato oggetti",log_name);

        Double angolo_attuale = 0.000;

        //writeToFile("local_centri ha dimensione"+Integer.toString(local_centri.size()),log_name);
        if (local_centri_positive.size()>0 && local_centri_negative.size()>0){


                //writeToFile("primo angolo",log_name);
                //writeToFile( local_centri.get(i).tostring()+ "   "+ local_centri.get(i+1).tostring(),log_name);

                double alpha = (double) (local_centri_negative.get(local_centri_negative.size()-1).coordinate.y-local_centri_positive.get(0).coordinate.y)/
                        (local_centri_negative.get(local_centri_negative.size()-1).coordinate.x-local_centri_positive.get(0).coordinate.x);
                //writeToFile("alpha:"+Double.toString(alpha),log_name);
                angolo_attuale =(Math.atan(alpha));
                //writeToFile("atan di alpha"+Double.toString(angolo_attuale),log_name);

                angolo_attuale = Math.toDegrees(angolo_attuale);
                //writeToFile("in degrees"+Double.toString(angolo_attuale),log_name);
                //showToast(Double.toString(angolo_attuale));

                p1.set(local_centri_negative.get(local_centri_negative.size()-1).coordinate.x,local_centri_negative.get(local_centri_negative.size()-1).coordinate.y);
                p2.set(local_centri_positive.get(0).coordinate.x,local_centri_positive.get(0).coordinate.y);
                point_updated=true;


        }
        //writeToFile("calcolato angoli",log_name);
        //showToast(Double.toString(angolo_attuale));

        return angolo_attuale;





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
        //writeToFile("trovo centri",log_name);

        for (int i=0;i<width-1;i=i+1){ //per ogni colonna
            colon_elem=1;

            //writeToFile("colon_elem="+String.valueOf(colon_elem),log_name);

            if (colon_elem>0){ // se nella colonna ci sono dei true, devo iniziare ad analizzarla, altrimenti non faccio nulla

                for (int j=0;j<height;j=j+1){
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

        if (completed_marker.size()>0) {
            for (int i = 0; i < completed_marker.size(); i++) {
                dim_media = dim_media + completed_marker.get(i).num_sum;
            }

            dim_media = dim_media / completed_marker.size();

            for (int i = 0; i < completed_marker.size(); i++) {

                if (completed_marker.get(i).num_sum > dim_media * delta_min_dim && completed_marker.get(i).num_sum < dim_media * delta_max_dim) {
                    //writeToFile("Centro:" + Integer.toString(completed_marker.get(i).compute_x()) + "-:" + Integer.toString(completed_marker.get(i).compute_y()), log_name);

                    centri.add(new Point(completed_marker.get(i).compute_x(), completed_marker.get(i).compute_y()));

                }
            }
            //writeToFile("Centri trovati dopo il controllo:"+Integer.toString(centri.size()),log_name);

        }




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
        onDestroy();

    }

    public void onClick_pause (View v){

        //fermo il movimento del drone, rischiacciando lo posso fare ripartire

        if(stop==false) {
            move_drone(0, 0, 0, 0, 1000);

            stop = true;
        }else{
            stop=false;
        }

    }



    public void onClick_start(View v) {

        start_drone();


    }

    public void onClick_rotate(View v) {

        move_drone(90,0,0,0,1000);


    }

    public void onClick_go(View v) {

        move_drone(0,45,1,0,1000);


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

                //mFlightController.setHorizontalCoordinateSystem(DJIVirtualStickFlightCoordinateSystem.Body);
                mFlightController.setHorizontalCoordinateSystem(DJIVirtualStickFlightCoordinateSystem.Ground);
                //Double heading = mFlightController.getCompass().getHeading();
                //correctHeading(heading);

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



    private float correctyaw(float inYaw, Double heading) {


               float yaw = (float) (inYaw + heading);
               yaw = ((yaw + 180) % 360) - 180;
               //writeToFile("valore di heading:" + Double.toString(heading), log_name);
               //writeToFile("valore di yaw:" + Float.toString(yaw), log_name);

               return yaw;

    }

    private void correctRollPitch(Double heading, float angle, float velocity ) {

        Double real_angle =  (heading+angle);

        tRoll = (float) (velocity*Math.cos(Math.toRadians(real_angle)));
        tPitch=(float) (velocity*Math.sin(Math.toRadians(real_angle)));

        //writeToFile("valore di heading:"+Double.toString(real_angle),log_name);
        //writeToFile("valore di real angle:"+Double.toString(heading),log_name);
        //writeToFile("valore di pitch:"+Float.toString(tPitch),log_name);
       // writeToFile("valore di roll:"+Float.toString(tRoll),log_name);

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