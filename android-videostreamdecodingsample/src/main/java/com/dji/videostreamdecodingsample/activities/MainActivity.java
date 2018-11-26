package com.dji.videostreamdecodingsample.activities;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.dji.videostreamdecodingsample.R;
import com.dji.videostreamdecodingsample.main.Constants;
import com.dji.videostreamdecodingsample.main.DJIApplication;
import com.dji.videostreamdecodingsample.media.DJIVideoStreamDecoder;

import com.dji.videostreamdecodingsample.media.NativeHelper;
import com.dji.videostreamdecodingsample.models.PeriodicalStateData;
import com.dji.videostreamdecodingsample.services.Assetbridge;
import com.dji.videostreamdecodingsample.services.Cache;
import com.dji.videostreamdecodingsample.services.Server;
import com.dji.videostreamdecodingsample.utils.ModuleVerificationUtil;

import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import dji.common.battery.BatteryState;
import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.ObstacleDetectionSector;
import dji.common.flightcontroller.VisionControlState;
import dji.common.flightcontroller.VisionDetectionState;
import dji.common.flightcontroller.adsb.AirSenseSystemInformation;
import dji.common.flightcontroller.flightassistant.SmartCaptureState;
import dji.common.flightcontroller.simulator.InitializationData;
import dji.common.flightcontroller.simulator.SimulatorState;
import dji.common.model.LocationCoordinate2D;
import dji.common.remotecontroller.ChargeRemaining;
import dji.common.remotecontroller.GPSData;
import dji.common.remotecontroller.HardwareState;
import dji.common.util.CommonCallbacks;
import dji.keysdk.FlightControllerKey;
import dji.keysdk.KeyManager;
import dji.sdk.base.DJIDiagnostics;
import dji.sdk.flightcontroller.FlightAssistant;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.flightcontroller.Simulator;
import dji.sdk.mobilerc.MobileRemoteController;
import dji.sdk.products.Aircraft;
import dji.sdk.remotecontroller.RemoteController;
import dji.thirdparty.afinal.core.AsyncTask;

import java.io.ByteArrayOutputStream;

import dji.common.product.Model;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.List;

public class MainActivity extends Activity implements DJICodecManager.YuvDataCallback {
    //Delay Sending each frame
    public long incomingTimeMs;
    public long outputTimeMS;
    public long timesTampNeeded;
    //Socket connection
    private Socket socket;
    //Data for period times
    Handler handlerPeriodTimeData = new Handler();
    int delay = 15*1000; //1 second=1000 miliseconds, 15*1000=15seconds
    Runnable  runnable;
    //Connection Callback
    private RemoteController remoteController;
    private FlightController flightController;
    private FlightAssistant intelligentFlightAssistant;
    private FlightControllerKey isSimulatorActived;
    private Simulator simulator;
    private MobileRemoteController mobileRemoteController;
    private BaseProduct baseProduct;
    private PeriodicalStateData periodicalStateData;
    //Image and Video
    private Activity activity=this;
    private static final String TAG = MainActivity.class.getSimpleName();
    private SurfaceHolder.Callback surfaceCallback;
    private Handler handler = new Handler();
    private boolean bProcessing =false;
    private Mat tmp;
    private YuvImage yuvImage;
    private enum DemoType { USE_TEXTURE_VIEW, USE_SURFACE_VIEW, USE_SURFACE_VIEW_DEMO_DECODER}
    private static DemoType demoType = DemoType.USE_TEXTURE_VIEW;
    public TextView infoip, msg;
    public TextView myAwesomeTextView;
    protected VideoFeeder.VideoDataCallback mReceivedVideoDataCallBack = null;
    private TextView titleTv;
    private TextureView videostreamPreviewTtView;
    private SurfaceView videostreamPreviewSf;
    private SurfaceHolder videostreamPreviewSh;
    private Camera mCamera;
    private DJICodecManager mCodecManager;
    private Button screenShot;
    private Button simulate;
    private StringBuilder stringBuilder;
    private int videoViewWidth;
    private int videoViewHeight;
    public int count;
    ByteArrayOutputStream baos =new ByteArrayOutputStream();
    private Bitmap imageA;
    ImageView imViewA;
    public ByteArrayOutputStream mFrames;
    static {
        System.loadLibrary("native-lib");
    }
    private native void computerVision(Bitmap pTarget, byte[] pSource);
    private  native void humanDetection(long addrRgba);
    @Override
    protected void onResume() {
        super.onResume();
        initSurfaceOrTextureView();
        notifyStatusChange();
    }

    private void initSurfaceOrTextureView(){
        switch (demoType) {
            case USE_SURFACE_VIEW:
            case USE_SURFACE_VIEW_DEMO_DECODER:
                initPreviewerSurfaceView();
                break;
            default:
                initPreviewerTextureView();
                break;
        }
    }

    @Override
    protected void onPause() {
        if (mCamera != null) {
            if (VideoFeeder.getInstance().getPrimaryVideoFeed() != null) {
                VideoFeeder.getInstance().getPrimaryVideoFeed().setCallback(null);
            }
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {

        handlerPeriodTimeData.removeCallbacks(runnable);
        if (mCodecManager != null) {
            mCodecManager.cleanSurface();
            mCodecManager.destroyCodec();
        }
        socket.disconnect();
        socket.off("joystickPossitionChanged", joystickPossitionChanged);
        if (mCodecManager != null) {
            mCodecManager.cleanSurface();
            mCodecManager.destroyCodec();
        }
        if (remoteController!=null) {
            remoteController.setChargeRemainingCallback(null);
            remoteController.setGPSDataCallback(null);
        }
        if (flightController!=null) {

        }
        if(baseProduct!=null){
            baseProduct.getBattery().setStateCallback(null);
        }
        Simulator simulator = ModuleVerificationUtil.getSimulator();
        if (simulator != null) {
            simulator.setStateCallback(null);
        }
        super.onDestroy();
        try {
            trimCache(this);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    private void initAllKeys() {
        isSimulatorActived = FlightControllerKey.create(FlightControllerKey.IS_SIMULATOR_ACTIVE);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Assetbridge.unpack(this);
        DJIApplication app = (DJIApplication) getApplication();
        socket = app.getSocket();
        socket.on("joystickPossitionChanged",joystickPossitionChanged);
        socket.on("takeOffChanged",takeOffChanged);
        socket.on("landingChanged",landingChanged);
        socket.on("returnToHomeChanged",returnToHomeChanged);
        socket.connect();
        periodicalStateData = new PeriodicalStateData();
        periodicalStateData.setFirstReading(true);
        handlerPeriodTimeData.postDelayed( runnable = new Runnable() {
            public void run() {

                handlerPeriodTimeData.postDelayed(runnable,delay);
            }
        }, delay);
        if (DJIApplication.isAircraftConnected()) {
            baseProduct=DJIApplication.getProductInstance();
            baseProduct.getBattery().setStateCallback(new BatteryState.Callback() {
                @Override
                public void onUpdate(BatteryState batteryState) {
                    if(batteryState.getChargeRemainingInPercent()!=periodicalStateData.getAircraftBattery()) {
                        JSONObject jsonBattery = new JSONObject();
                        try {
                            jsonBattery.put("batteryLevel", batteryState.getChargeRemainingInPercent());
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        socket.emit("newBatteryLevel", jsonBattery);
                    }
                    periodicalStateData.setAircraftBattery(batteryState.getChargeRemainingInPercent());
                }
            });

        }
        else {
            System.out.println("is Aircraft Disconnected");
        }
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {
            flightController =((Aircraft) DJIApplication.getProductInstance()).getFlightController();
/*            simulator.setStateCallback(new SimulatorState.Callback() {
                @Override
                public void onUpdate(@NonNull SimulatorState simulatorState) {
                    if(simulatorState.areMotorsOn())
                        System.out.println("Motors On");
                    else
                        System.out.println("Motors Off");
                }
            });*/
            if(flightController.isFlightAssistantSupported()){
            intelligentFlightAssistant=flightController.getFlightAssistant();
            if (intelligentFlightAssistant != null) {

                intelligentFlightAssistant.setVisionDetectionStateUpdatedCallback(new VisionDetectionState.Callback() {
                    @Override
                    public void onUpdate(@NonNull VisionDetectionState visionDetectionState) {
                            //Sensors
                            if(periodicalStateData.isSensorBeingUsedFlightAssistant()!=visionDetectionState.isSensorBeingUsed()) {
                                System.out.println("Flight Controller sensors:" + visionDetectionState.isSensorBeingUsed());
                                JSONObject jsonSensorBeingUsed = new JSONObject();
                                try {
                                    jsonSensorBeingUsed.put("sensorBeingUsed", visionDetectionState.isSensorBeingUsed());

                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                                socket.emit("newFlightAssistantState", jsonSensorBeingUsed);
                            }
                        periodicalStateData.setSensorBeingUsedFlightAssistant(visionDetectionState.isSensorBeingUsed());
                        //Obstacules
                        visionDetectionState.getObstacleDistanceInMeters();
                    }
                });
                intelligentFlightAssistant.setVisionControlStateUpdatedcallback(new VisionControlState.Callback() {
                    @Override
                    public void onUpdate(VisionControlState visionControlState) {
                    }
                });
            }
        } else {
            System.out.println("onAttachedToWindow FC NOT Available");
        }
            flightController.setStateCallback(new FlightControllerState.Callback() {
                @Override
                public void onUpdate(@NonNull FlightControllerState flightControllerState) {
                    //Satelite Counts
                    if(periodicalStateData.getFlightControllerGPSSatelliteCount()!=flightControllerState.getSatelliteCount()) {
                        System.out.println("Flight Controller GPS:" + flightControllerState.getSatelliteCount());
                        JSONObject jsonAirlink = new JSONObject();
                        try {
                            jsonAirlink.put("gpsSignalStatus", flightControllerState.getSatelliteCount());

                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        socket.emit("newGPSSignalStatus", jsonAirlink);
                    }
                    periodicalStateData.setFlightControllerGPSSatelliteCount(flightControllerState.getSatelliteCount());
                    //Battery Required to RTH
                    if(periodicalStateData.getAircraftBatteryPercentageNeededToGoHome()!=flightControllerState.getGoHomeAssessment().getBatteryPercentageNeededToGoHome()){
                        System.out.println("Flight Controller Battery Needed to RTH:" + flightControllerState.getSatelliteCount());
                        JSONObject jsonBattery = new JSONObject();
                        try {
                            jsonBattery.put("batteryNeededRTH", flightControllerState.getGoHomeAssessment().getBatteryPercentageNeededToGoHome());

                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        socket.emit("newBatteryANeededRTH", jsonBattery);
                    }
                    periodicalStateData.setAircraftBatteryPercentageNeededToGoHome(flightControllerState.getGoHomeAssessment().getBatteryPercentageNeededToGoHome());
                    //Flight Time
                    if(flightControllerState.isFlying()&&periodicalStateData.getFlightTime()!=flightControllerState.getFlightTimeInSeconds()&&flightControllerState.getFlightTimeInSeconds()%10==0){
                        JSONObject flightTime = new JSONObject();
                        try {
                            flightTime.put("flightTime", secToTime(flightControllerState.getFlightTimeInSeconds()/10));
                            System.out.println();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        socket.emit("newFlightTime", flightTime);
                    }
                }
            });
        }
        if (ModuleVerificationUtil.isRemoteControllerAvailable()) {
            remoteController =((Aircraft) DJIApplication.getProductInstance()).getRemoteController();
            remoteController.setChargeRemainingCallback(new ChargeRemaining.Callback() {
                @Override
                public void onUpdate(@NonNull ChargeRemaining chargeRemaining) {
                    if(periodicalStateData.getRemoteControllerBattery()!=chargeRemaining.getRemainingChargeInPercent()) {
                        System.out.println("Remote Controller Battery:" + chargeRemaining.getRemainingChargeInPercent());
                        JSONObject jsonRCStatus = new JSONObject();
                        try {
                            jsonRCStatus.put("batteryLevel", chargeRemaining.getRemainingChargeInPercent());
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        socket.emit("newRCConnectionStatus", jsonRCStatus);
                    }
                    periodicalStateData.setRemoteControllerBattery(chargeRemaining.getRemainingChargeInPercent());
                }
            });
            remoteController.setHardwareStateCallback(new HardwareState.HardwareStateCallback() {
                @Override
                public void onUpdate(@NonNull HardwareState hardwareState) {
                    if(periodicalStateData.getRemoteControllerSwitchMode()!=hardwareState.getFlightModeSwitch().value()){
                    System.out.println("Remote Controller Switch Mode:" +hardwareState.getFlightModeSwitch().value());
                    JSONObject jsonFlightSwitch = new JSONObject();
                    try {
                        jsonFlightSwitch.put("flightMode",hardwareState.getFlightModeSwitch().value());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    socket.emit("newFlightModeSwitch",jsonFlightSwitch);
                    }
                    periodicalStateData.setRemoteControllerSwitchMode(hardwareState.getFlightModeSwitch().value());
                }
            });

        }
        else {
            System.out.println("is Remote Controller Disconnected");
        }
        //Sending the first data collected of the sensors
        if(socket.connected()&&periodicalStateData.isFirstReading()){
            JSONObject jsonFlightSwitch = new JSONObject();
            JSONObject jsonRCStatus = new JSONObject();
            JSONObject jsonAirlink = new JSONObject();
            JSONObject jsonBattery = new JSONObject();
            JSONObject jsonSensorBeingUsed = new JSONObject();
            JSONObject jsonBatteryNRTH = new JSONObject();
            JSONObject flightTime = new JSONObject();
            try {
                jsonFlightSwitch.put("flightMode",periodicalStateData.getRemoteControllerSwitchMode());
                jsonRCStatus.put("batteryLevel", periodicalStateData.getRemoteControllerBattery());
                jsonAirlink.put("gpsSignalStatus", periodicalStateData.getFlightControllerGPSSatelliteCount());
                jsonBattery.put("batteryLevel", periodicalStateData.getAircraftBattery());
                jsonSensorBeingUsed.put("sensorBeingUsed", periodicalStateData.isSensorBeingUsedFlightAssistant());
                jsonBatteryNRTH.put("batteryNeededRTH", periodicalStateData.getAircraftBatteryPercentageNeededToGoHome());
                flightTime.put("flightTime", secToTime(periodicalStateData.getFlightTime()));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            socket.emit("newFlightModeSwitch",jsonFlightSwitch);
            socket.emit("newRCConnectionStatus", jsonRCStatus);
            socket.emit("newGPSSignalStatus", jsonAirlink);
            socket.emit("newBatteryLevel", jsonBattery);
            socket.emit("sensorBeingUsed", jsonSensorBeingUsed);
            socket.emit("newBatteryNeededRTH", jsonBatteryNRTH);
            socket.emit("newFlightTime", flightTime);
        }
        periodicalStateData.setFirstReading(false);
        setContentView(R.layout.activity_main);
        initAllKeys();
        initUi();
        Thread cThread = new Thread(new Server(this,handler));
        cThread.start();
        super.onCreate(savedInstanceState);
    }

    /*Screen Events to control the aircraft*/
    public Emitter.Listener joystickPossitionChanged = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    myAwesomeTextView.setText(args[0].toString());
                }
            });

        }
    };
    public Emitter.Listener returnToHomeChanged = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (ModuleVerificationUtil.isFlightControllerAvailable()) {
                        flightController =((Aircraft) DJIApplication.getProductInstance()).getFlightController();
                        flightController.startGoHome(new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {
                                if(djiError!=null){
                                    sendError(djiError.getDescription());
                                    myAwesomeTextView.setText(djiError.getDescription());
                                }
                            }
                        });
                    }
                    else {
                        myAwesomeTextView.setText("FlightController not available Landing Go Home");
                    }
                }
            });

        }
    };
    public Emitter.Listener landingChanged = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (ModuleVerificationUtil.isFlightControllerAvailable()) {
                        flightController =((Aircraft) DJIApplication.getProductInstance()).getFlightController();
                        if(flightController.getState().isFlying()){
                            flightController.startLanding(new CommonCallbacks.CompletionCallback() {
                                @Override
                                public void onResult(DJIError djiError) {
                                    if(djiError!=null){
                                        myAwesomeTextView.setText(djiError.getDescription());
                                        sendError(djiError.getDescription());
                                    }
                                }
                            });
                        }
                    }
                    else {
                        myAwesomeTextView.setText("FlightController not available Landing");
                    }

                }
            });

        }
    };
    public Emitter.Listener takeOffChanged = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (ModuleVerificationUtil.isFlightControllerAvailable()) {
                        flightController =((Aircraft) DJIApplication.getProductInstance()).getFlightController();
                        if(!flightController.getState().isFlying()){
                            flightController.startTakeoff(new CommonCallbacks.CompletionCallback() {
                                @Override
                                public void onResult(DJIError djiError) {
                                    if(djiError!=null){
                                        myAwesomeTextView.setText(djiError.getDescription());
                                        sendError(djiError.getDescription());
                                    }
                                }
                            });
                        }
                        else{
                            myAwesomeTextView.setText(" TakeOff, but is flying");
                        }
                    }
                    else {
                        myAwesomeTextView.setText("FlightController not available TakeOff");
                    }


                }
            });

        }
    };
    /*Ent screen events*/
    /*Sending Logs Success and Error*/
    private void sendError(String message){
        JSONObject jsonError = new JSONObject();
        try {
            jsonError.put("error", message);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        socket.emit("newError", jsonError);
    }
    /*End Logs*/


    private void showToast(String s) {
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
    }

    private void updateTitle(String s) {
        if (titleTv != null) {
            titleTv.setText(s);
        }
    }

    private void initUi() {
        screenShot = (Button) findViewById(R.id.activity_main_screen_shot);
        screenShot.setSelected(false);
        simulate=(Button)findViewById(R.id.activity_main_simulator);
        simulate.setSelected(false);
        infoip = (TextView) findViewById(R.id.infoip);
        msg = (TextView) findViewById(R.id.msg);
        titleTv = (TextView) findViewById(R.id.title_tv);
        imViewA = (ImageView) findViewById(R.id.imageViewA);
        videostreamPreviewTtView = (TextureView) findViewById(R.id.livestream_preview_ttv);
        videostreamPreviewSf = (SurfaceView) findViewById(R.id.livestream_preview_sf);
        myAwesomeTextView= (TextView)findViewById(R.id.event);
        updateUIVisibility();
    }

    private void updateUIVisibility(){
        switch (demoType) {
            case USE_SURFACE_VIEW:
            case USE_SURFACE_VIEW_DEMO_DECODER:
                videostreamPreviewSf.setVisibility(View.VISIBLE);
                videostreamPreviewTtView.setVisibility(View.GONE);
                break;

            case USE_TEXTURE_VIEW:
                videostreamPreviewSf.setVisibility(View.GONE);
                videostreamPreviewTtView.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void notifyStatusChange() {

        final BaseProduct product = DJIApplication.getProductInstance();

        Log.d(TAG, "notifyStatusChange: " + (product == null ? "Disconnect" : (product.getModel() == null ? "null model" : product.getModel().name())));
        if (product != null && product.isConnected() && product.getModel() != null) {
            updateTitle(product.getModel().name() + " Connected " + demoType.name());
            sendMessageConnected();
        } else {
            updateTitle("Disconnected");
        }

        // The callback for receiving the raw H264 video data for camera live view
        mReceivedVideoDataCallBack = new VideoFeeder.VideoDataCallback() {

            @Override
            public void onReceive(byte[] videoBuffer, int size) {
                //Log.d(TAG, "camera recv video data size: " + size);
                switch (demoType) {
                    case USE_SURFACE_VIEW:
                        if (mCodecManager != null) {
                            mCodecManager.sendDataToDecoder(videoBuffer, size);
                        }
                        break;
                    case USE_SURFACE_VIEW_DEMO_DECODER:
                        DJIVideoStreamDecoder.getInstance().parse(videoBuffer, size);
                        break;

                    case USE_TEXTURE_VIEW:
                        if (mCodecManager != null) {
                            mCodecManager.sendDataToDecoder(videoBuffer, size);
                        }
                        break;
                }

            }
        };

        if (null == product || !product.isConnected()) {
            mCamera = null;
            sendMessageDisconnected();
        } else {
            sendMessageConnected();
            if (!product.getModel().equals(Model.UNKNOWN_AIRCRAFT)) {
                mCamera = product.getCamera();
                mCamera.setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if (djiError != null) {
                            showToast("can't change mode of camera, error:"+djiError.getDescription());
                        }
                    }
                });
                if (VideoFeeder.getInstance().getPrimaryVideoFeed() != null) {
                    VideoFeeder.getInstance().getPrimaryVideoFeed().setCallback(mReceivedVideoDataCallBack);
                }
            }
        }
    }

    private void sendMessageConnected() {
        if(socket.connected()){
            JSONObject jsonRCStatus = new JSONObject();
            try {
                jsonRCStatus.put("rCStatus",true);

            } catch (JSONException e) {
                e.printStackTrace();
            }
            socket.emit("newRCConnectionStatus",jsonRCStatus);
        }
    }

    private void sendMessageDisconnected() {
        if(socket.connected()){
            JSONObject jsonRCStatus = new JSONObject();
            try {
                jsonRCStatus.put("rCStatus",false);

            } catch (JSONException e) {
                e.printStackTrace();
            }
            socket.emit("newRCConnectionStatus",jsonRCStatus);
        }
    }

    /**
     * Init a fake texture view to for the codec manager, so that the video raw data can be received
     * by the camera
     */
    private void initPreviewerTextureView() {
        videostreamPreviewTtView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Log.d(TAG, "real onSurfaceTextureAvailable");
                videoViewWidth = width;
                videoViewHeight = height;
                System.out.println("real onSurfaceTextureAvailable: width " + videoViewWidth + " height " + videoViewHeight);
                if (mCodecManager == null) {
                    mCodecManager = new DJICodecManager(getApplicationContext(), surface, width, height);
                }
                //imageA = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888);

            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                videoViewWidth = width;
                videoViewHeight = height;
                Log.d(TAG, "real onSurfaceTextureAvailable2: width " + videoViewWidth + " height " + videoViewHeight);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                if (mCodecManager != null) {
                    mCodecManager.cleanSurface();
                }
                if(imageA!=null)
                imageA.recycle();
                imageA = null;
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
    }

    /**
     * Init a surface view for the DJIVideoStreamDecoder
     */
    private void initPreviewerSurfaceView() {
        videostreamPreviewSh = videostreamPreviewSf.getHolder();
        surfaceCallback = new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.d("SURFACE", "real onSurfaceTextureAvailable");
                videoViewWidth = videostreamPreviewSf.getWidth();
                videoViewHeight = videostreamPreviewSf.getHeight();
                Log.d(TAG, "real onSurfaceTextureAvailable3: width " + videoViewWidth + " height " + videoViewHeight);
                switch (demoType) {
                    case USE_SURFACE_VIEW:
                        if (mCodecManager == null) {

                            mCodecManager = new DJICodecManager(getApplicationContext(), holder, videoViewWidth,
                                                                videoViewHeight);
                        }
                        break;
                    case USE_SURFACE_VIEW_DEMO_DECODER:
                        // This demo might not work well on P3C and OSMO.
                        NativeHelper.getInstance().init();
                        DJIVideoStreamDecoder.getInstance().init(getApplicationContext(), holder.getSurface());
                        DJIVideoStreamDecoder.getInstance().setYuvDataListener(MainActivity.this);
                        DJIVideoStreamDecoder.getInstance().resume();
                        break;
                }

            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                videoViewWidth = width;
                videoViewHeight = height;
                Log.d(TAG, "real onSurfaceTextureAvailable4: width " + videoViewWidth + " height " + videoViewHeight);
                switch (demoType) {
                    case USE_SURFACE_VIEW:
                        //mCodecManager.onSurfaceSizeChanged(videoViewWidth, videoViewHeight, 0);
                        break;
                    case USE_SURFACE_VIEW_DEMO_DECODER:
                        DJIVideoStreamDecoder.getInstance().changeSurface(holder.getSurface());
                        break;
                }

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                switch (demoType) {
                    case USE_SURFACE_VIEW:
                        if (mCodecManager != null) {
                            mCodecManager.cleanSurface();
                            mCodecManager.destroyCodec();
                            mCodecManager = null;
                        }
                        break;
                    case USE_SURFACE_VIEW_DEMO_DECODER:
                        DJIVideoStreamDecoder.getInstance().stop();
                        NativeHelper.getInstance().release();
                        break;
                }

            }
        };

        videostreamPreviewSh.addCallback(surfaceCallback);
    }


    @Override
    public void onYuvDataReceived(final ByteBuffer yuvFrame, int dataSize, final int width, final int height) {
        if (count++ % Constants.fps == 0 && yuvFrame != null) {
            incomingTimeMs=System.currentTimeMillis();
            final byte[] bytes = new byte[dataSize];
            yuvFrame.get(bytes);
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    saveYuvDataToJPEG(bytes, width, height);
                }
            });
        }
    }

    private void saveYuvDataToJPEG(byte[] yuvFrame, int width, int height){
        byte[] y = new byte[width * height];
        byte[] u = new byte[width * height / 4];
        byte[] v = new byte[width * height / 4];
        byte[] nu = new byte[width * height / 4]; //
        byte[] nv = new byte[width * height / 4];

        System.arraycopy(yuvFrame, 0, y, 0, y.length);
        for (int i = 0; i < u.length; i++) {
            v[i] = yuvFrame[y.length + 2 * i];
            u[i] = yuvFrame[y.length + 2 * i + 1];
        }
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
        screenShot(bytes, width, height);
    }


    private void screenShot(byte[] buf, int width, int height) {
        yuvImage = new YuvImage(buf,ImageFormat.NV21,width,height,null);
        baos=new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0,0,width,height), Constants.qualityPercent,baos);
        tmp = bytesToMat(baos.toByteArray());
        handler.post(DoImageProcessing);
    }
    private Runnable DoImageProcessing = new Runnable() {
        public void run() {
            humanDetection(tmp.getNativeObjAddr());
            Utils.matToBitmap(tmp,imageA);
            //imViewA.setImageBitmap(imageA);
            baos =new ByteArrayOutputStream();
            imageA.compress(Bitmap.CompressFormat.JPEG, 100, baos);
            outputTimeMS=System.currentTimeMillis();
            mFrames=baos;
            timesTampNeeded=outputTimeMS-incomingTimeMs;
        }
    };
    private Mat bytesToMat(byte[] data) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        imageA = BitmapFactory.decodeByteArray(data, 0, data.length,options);
        Mat BGRImage = new Mat (imageA.getWidth(), imageA.getHeight(), CvType.CV_8UC3);
        Utils.bitmapToMat(imageA, BGRImage);
        return BGRImage;
    }
    /**
     * Transmit buffered data into a JPG image file
     */
    public void onClick(View v) {

        if (v.getId() == R.id.activity_main_screen_shot) {
            handleYUVClick();
        }
        if(v.getId()==R.id.activity_main_simulator){
            onClickSimulator();
        }

    }
    private void onClickSimulator() {
        Simulator simulator = ModuleVerificationUtil.getSimulator();
        if (simulator == null) {
            return;
        }
        if (simulate.isSelected()) {
            simulate.setText("Flight");
            simulate.setSelected(false);

            simulator.start(InitializationData.createInstance(new LocationCoordinate2D(23, 113), 10, 10),
                    new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            JSONObject jsonError = new JSONObject();
                            try {
                                jsonError.put("error", djiError.getDescription());
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            socket.emit("newError", jsonError);
                        }
                    });
            Boolean isSimulatorOn = (Boolean) KeyManager.getInstance().getValue(isSimulatorActived);
            if (isSimulatorOn != null) {
                JSONObject jsonSuccess = new JSONObject();
                try {
                    jsonSuccess.put("information", "Simulator is On.");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                socket.emit("newInformation", jsonSuccess);
            }
            else {
                JSONObject jsonError = new JSONObject();
                try {
                    jsonError.put("error", "Simulator is Off.");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                socket.emit("newError", jsonError);
            }
        } else {
            simulate.setText("Simulate");
            simulate.setSelected(true);
            simulator.stop(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {

                }
            });
        }
    }
    private void handleYUVClick() {
        if (screenShot.isSelected()) {
            screenShot.setText("Transmit");
            screenShot.setSelected(false);

            switch (demoType) {
                case USE_SURFACE_VIEW:
                case USE_TEXTURE_VIEW:
                    mCodecManager.enabledYuvData(false);
                    mCodecManager.setYuvDataCallback(null);
                    // ToDo:
                    break;
                case USE_SURFACE_VIEW_DEMO_DECODER:
                    DJIVideoStreamDecoder.getInstance().changeSurface(videostreamPreviewSh.getSurface());
                    break;
            }
            stringBuilder = null;
        } else {
            screenShot.setText("Live here");
            screenShot.setSelected(true);

            switch (demoType) {
                case USE_TEXTURE_VIEW:
                case USE_SURFACE_VIEW:
                    mCodecManager.enabledYuvData(true);
                    mCodecManager.setYuvDataCallback(this);
                    break;
                case USE_SURFACE_VIEW_DEMO_DECODER:
                    DJIVideoStreamDecoder.getInstance().changeSurface(null);
                    break;
            }

        }
    }
    public String secToTime(int sec) {
        int seconds = sec % 60;
        int minutes = sec / 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    public void trimCache(Context context) {
        try {
            File dir = context.getCacheDir();
            if (dir != null && dir.isDirectory()) {
                deleteDir(dir);
            }
        } catch (Exception e) {
            // TODO: handle exception
        }
    }

    public static boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }

        // The directory is now empty so delete it
        return dir.delete();
    }
}
