package iot.com.wastebin;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

public class ResultDisplay extends Activity {

    private ImageView objectImage, garbageBinImage;
    private TextView objectNameText;

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setLayoutPerOrientation();
        loadFromIntent();
    }

    @Override
    public void onStart(){
        super.onStart();
        setLayoutPerOrientation();
        loadFromIntent();
    }

    private void setLayoutPerOrientation(){
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
            setContentView(R.layout.activity_result_landscape);
        } else {
            setContentView(R.layout.activity_result_potrait);
        }
        initViews();
        loadFromIntent();
    }

    public void initViews(){
        objectImage = findViewById(R.id.object);
        garbageBinImage = findViewById(R.id.garbage_bin);
        objectNameText = findViewById(R.id.objectName);
    }

    public void loadFromIntent(){
        Bundle intentBundle = getIntent().getExtras();
        loadResult(intentBundle);
    }

    public void loadResult(Bundle bundle){
        String objectInImage = bundle.getString(Camera.CLASSIFICATION_RESULT_INTENT_KEY);
        Bitmap image = bundle.getParcelable(Camera.IMAGE_CAPTURED_INTENT_KEY);
        String garbageBin = determineGarbageBin(objectInImage);
        String resultText = getResources().getString(R.string.result_text);
        resultText = String.format(resultText, objectInImage, garbageBin);
        objectImage.setImageBitmap(image);
        setGarbageBinImage(garbageBin);
        objectNameText.setText(resultText);
    }

    private String determineGarbageBin(String object){
        return "landfill";
    }

    private void setGarbageBinImage(String garbageBinName){
        int imageResourceId = 0;
        switch (garbageBinName){
            case "landfill":
                imageResourceId = R.drawable.landfill;
                break;
            case "recycle":
                imageResourceId = R.drawable.recycle;
                break;
            case "toxic":
                imageResourceId = R.drawable.toxic;
                break;
            case "green":
                imageResourceId = R.drawable.green;
                break;
        }
        garbageBinImage.setImageResource(imageResourceId);
    }
}
