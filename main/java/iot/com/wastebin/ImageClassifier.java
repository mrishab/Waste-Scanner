package iot.com.wastebin;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;

import java.util.List;

import iot.com.wastebin.classifier.Classifier;
import iot.com.wastebin.classifier.TensorFlowImageClassifier;

public class ImageClassifier {

    public static final String OUTPUT_NAME = "output", INPUT_NAME = "input";
    public static final int INPUT_SIZE = 224;

    private Classifier classifier;

    public ImageClassifier(AssetManager assetManager){
        classifier = TensorFlowImageClassifier.create(
                assetManager,
                "file:///android_asset/tensorflow_inception_graph.pb",
                "file:///android_asset/imagenet_comp_graph_label_strings.txt",
                INPUT_SIZE, 117, 1, INPUT_NAME, OUTPUT_NAME
        );
    }

    public String classify(Bitmap bmp){
        Classifier.Recognition bestPrediction;
        List<Classifier.Recognition> results = classifier.recognizeImage(resizeBitmap(bmp, INPUT_SIZE, INPUT_SIZE));
        if (results.size() < 1) {
            return "Unidentified";
        }
        bestPrediction = results.get(0);
        for (Classifier.Recognition result: results){
            if (bestPrediction.getConfidence() < result.getConfidence()){
                bestPrediction = result;
            }
        }
        return bestPrediction.getTitle();
    }

    public Bitmap resizeBitmap(Bitmap bmp, int newHeight, int newWidth) {
        int width = bmp.getWidth();
        int height = bmp.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // CREATE A MATRIX FOR THE MANIPULATION
        Matrix matrix = new Matrix();
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight);

        // "RECREATE" THE NEW BITMAP
        Bitmap newBitmap = Bitmap.createBitmap(bmp, 0, 0, width, height, matrix, false);
        return newBitmap ;
    }
}
