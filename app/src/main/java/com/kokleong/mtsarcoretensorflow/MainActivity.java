package com.kokleong.mtsarcoretensorflow;

import androidx.appcompat.app.AppCompatActivity;

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
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
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
    private boolean shouldAddModel = true;
    private boolean shouldTakePhoto = true;
    private RenderHandler render;
    //Tensorflow required variables
    private static final String MODEL_PATH = "medicine_quant.tflite";
    private static final boolean QUANT = false;
    private static final String LABEL_PATH =  "labels.txt";
    private static int INPUT_SIZE = 128;
    final int IMAGE_MAX_SIZE = 1200000;
    private Classifier classifier;

    private Executor executor  =  Executors.newSingleThreadExecutor();






    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initTensorFlowAndLoadModel();
        render = new RenderHandler(getApplicationContext());
        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.sceneform_fragment);

        arFragment.getArSceneView().getScene().addOnUpdateListener(this::onUpdateFrame);



    }





    private void onUpdateFrame(FrameTime frameTime) {
        Session session = arFragment.getArSceneView().getSession();
        Config config = new Config(session);
        config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
        config.setFocusMode(Config.FocusMode.AUTO);
        session.configure(config);
        //arFragment.getArSceneView().setupSession(session);
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
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
                        outputStream.flush();
                        outputStream.close();

                        List<Classifier.Recognition> results = classifier.recognizeImage(scaledBitmap);
                        //change according with your model
                        if (results.get(0).getConfidence() > 0.995 && shouldAddModel) {

                            Log.i("results", results.toString());
                            for (Plane plane : frame.getUpdatedTrackables(Plane.class)) {
                                float[] pos = {0, 0, -1};
                                float[] rotation = {0, 0, 0, 1};
                                if (results.get(0).getTitle().equals("Anarex")) {
                                    Anchor anchor = arFragment.getArSceneView().getSession().createAnchor(plane.getCenterPose());
                                    render.placeObject(anchor, arFragment, Uri.parse("Airplane.sfb"));
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
