package com.kokleong.mtsarcoretensorflow;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.ar.core.Config;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.kokleong.mtsarcoretensorflow.arcore.render.RenderHandler;
import com.kokleong.mtsarcoretensorflow.tensorflow.lite.Classifier;
import com.kokleong.mtsarcoretensorflow.tensorflow.lite.TensorFlowImageClassifier;


import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.ar.core.Frame;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.ux.ArFragment;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    //ar required variables
    private ArFragment arFragment;
    private boolean shouldTakePhoto = true;
//    private RenderHandler render;
    //Tensorflow required variables
    private static final String MODEL_PATH = "medicine_quant.tflite";
    private static final boolean QUANT = false;
    private static final String LABEL_PATH =  "labels.txt";
    private static int INPUT_SIZE = 128;
    private Classifier classifier;
    private Executor executor  =  Executors.newSingleThreadExecutor();

    //UI variables
    private BottomSheetBehavior sheetBehavior;
    private LinearLayout bottomSheet;
    private Button bottomButton;
    private TextView mMedicineName;






    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initTensorFlowAndLoadModel();
        bottomButton = findViewById(R.id.bottom_btn);
        bottomSheet = findViewById(R.id.bottom_sheet);
        mMedicineName = findViewById(R.id.medicine_name);
        sheetBehavior = BottomSheetBehavior.from(bottomSheet);

        bottomButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                bottomSheet.setVisibility(View.GONE);
                shouldTakePhoto=true;

            }
        });

        sheetBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View view, int newState) {
                switch (newState) {
                    case BottomSheetBehavior.STATE_HIDDEN:
                        break;
                    case BottomSheetBehavior.STATE_EXPANDED: {
                        bottomButton.setText("Close");
                    }
                    break;
                    case BottomSheetBehavior.STATE_COLLAPSED: {
                        bottomButton.setText("Expand");
                    }
                    break;
                    case BottomSheetBehavior.STATE_DRAGGING:
                        break;
                    case BottomSheetBehavior.STATE_SETTLING:
                        break;
                }
            }

            @Override
            public void onSlide(@NonNull View view, float v) {

            }
        });


       // render = new RenderHandler(MainActivity.this);
        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.sceneform_fragment);
        arFragment.getArSceneView().getScene().addOnUpdateListener(this::onUpdateFrame);

    }





    private void onUpdateFrame(FrameTime frameTime) {
        Session session = arFragment.getArSceneView().getSession();
        Config config = new Config(session);
        config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
        config.setFocusMode(Config.FocusMode.AUTO);
        session.configure(config);
        Frame frame = arFragment.getArSceneView().getArFrame();

        //if there is no frame don't process anything
        if(frame == null){
            return;
        }
        // If Arcore is not tracking yet then don't process anything
        if(frame.getCamera().getTrackingState() != TrackingState.TRACKING){
            return;
        }
        //if ARCore is tracking get start processing
        if(frame.getCamera().getTrackingState() ==  TrackingState.TRACKING) {
            if (shouldTakePhoto) {

                try {
                    //take photo convert photo to Bitmap format so that it could be use in the TensorflowImageClassifierClass for detection
                    Image img = frame.acquireCameraImage();
                    byte[] nv21;
                    ContextWrapper cw = new ContextWrapper(getApplicationContext());
                    String fileName = "test.jpg";
                    File dir = cw.getDir("imageDir", Context.MODE_PRIVATE);
                    File file = new File(dir, fileName);
                    FileOutputStream outputStream;
                    try {

                        outputStream = new FileOutputStream(file);
                        ByteBuffer yBuffer = img.getPlanes()[0].getBuffer();
                        ByteBuffer uBuffer = img.getPlanes()[1].getBuffer();
                        ByteBuffer vBuffer = img.getPlanes()[2].getBuffer();

                        int ySize = yBuffer.remaining();
                        int uSize = uBuffer.remaining();
                        int vSize = vBuffer.remaining();

                        nv21 = new byte[ySize + uSize + vSize];

                        yBuffer.get(nv21, 0, ySize);
                        vBuffer.get(nv21, ySize, vSize);
                        uBuffer.get(nv21, ySize + vSize, uSize);

                        int width = img.getWidth();
                        int height = img.getHeight();

                        img.close();


                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
                        yuv.compressToJpeg(new Rect(0, 0, width, height), 100, out);
                        byte[] byteArray = out.toByteArray();

                        Bitmap bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
                        Matrix matrix = new Matrix();
                        matrix.postRotate(90);
                        if (bitmap != null) {
                            Log.i("bitmap ", "contains data");

                        }
                        Bitmap portraitBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);


                        Bitmap scaledBitmap = Bitmap.createScaledBitmap(portraitBitmap, INPUT_SIZE, INPUT_SIZE, false);
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                        outputStream.flush();
                        outputStream.close();

                        List<Classifier.Recognition> results = classifier.recognizeImage(scaledBitmap);
                        //change according with your model
                        if (results.get(0).getConfidence() > 0.995) {
                            Log.i("results", results.toString());
                            for (Plane plane : frame.getUpdatedTrackables(Plane.class)) {
                                if (results.get(0).getTitle().equals("Anarex")) {
                                    shouldTakePhoto = false;
                                    mMedicineName.setText(results.get(0).getTitle());
                                    bottomSheet.setVisibility(View.VISIBLE);
                                    //Anchor anchor = arFragment.getArSceneView().getSession().createAnchor(plane.getCenterPose());
                                    //render.placeObject(anchor, arFragment, results.get(0).getTitle());
                                }
                                else if(results.get(0).getTitle().equals("Charcoal")){
                                    shouldTakePhoto = false;
                                    mMedicineName.setText(results.get(0).getTitle());
                                    //sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                                    bottomSheet.setVisibility(View.VISIBLE);
                                    //Anchor anchor = arFragment.getArSceneView().getSession().createAnchor(plane.getCenterPose());
                                    //render.placeObject(anchor, arFragment, results.get(0).getTitle());

                                }
                                else if(results.get(0).getTitle().equals("Dhamotil")){
                                    shouldTakePhoto = false;
                                    mMedicineName.setText(results.get(0).getTitle());
                                   // sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                                    bottomSheet.setVisibility(View.VISIBLE);
                                    //Anchor anchor = arFragment.getArSceneView().getSession().createAnchor(plane.getCenterPose());
                                    //render.placeObject(anchor, arFragment, results.get(0).getTitle());

                                }
                                else if(results.get(0).getTitle().equals("Fucon")){
                                    shouldTakePhoto = false;
                                    mMedicineName.setText(results.get(0).getTitle());
                                    //sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                                    bottomSheet.setVisibility(View.VISIBLE);
                                   // Anchor anchor = arFragment.getArSceneView().getSession().createAnchor(plane.getCenterPose());
                                   // render.placeObject(anchor, arFragment, results.get(0).getTitle());
                                }
                            }
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } catch (NotYetAvailableException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private void initTensorFlowAndLoadModel(){
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try{
                    classifier = TensorFlowImageClassifier.create(
                            getAssets(),
                            MODEL_PATH,
                            LABEL_PATH,
                            INPUT_SIZE,
                            QUANT);
                }catch(IOException e){
                    e.printStackTrace();
                    throw new RuntimeException("Error initializing TensorFlow!", e);
                }
            }
        });
    }





}
