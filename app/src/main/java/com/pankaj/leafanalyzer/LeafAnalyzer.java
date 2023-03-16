package com.pankaj.leafanalyzer;

import androidx.annotation.*;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.*;

import com.google.firebase.database.*;
import com.pankaj.leafanalyzer.ml.PlantModel1;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class LeafAnalyzer extends AppCompatActivity {

    private Button start_btn;
    private ImageView select, open_camera, open_gallery;
    final int imageSize = 224;
    private Uri imageUri;
    FirebaseDatabase firebaseDatabase;
    DatabaseReference databaseReference;
    String nameofleaf;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaf_analyzer);

        start_btn = findViewById(R.id.start_btn);
        select = findViewById(R.id.select_image);
        open_camera = findViewById(R.id.open_camera);
        open_gallery = findViewById(R.id.open_gallery);

        firebaseDatabase = FirebaseDatabase.getInstance();

        open_camera.setOnClickListener(v -> {
            Log.d("debug", "Open Camera");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    startActivityForResult(cameraIntent, 3);
                } else {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, 100);
                }
            }
        });

        open_gallery.setOnClickListener(v -> {
            Intent galleryIntent = new Intent();
            galleryIntent.setAction(Intent.ACTION_GET_CONTENT);
            galleryIntent.setType("image/*");
            startActivityForResult(galleryIntent, 2);
        });

        start_btn.setOnClickListener(v -> {
            Bitmap image = ((BitmapDrawable) select.getDrawable()).getBitmap();
            int dimension = Math.min(image.getWidth(), image.getHeight());
            ThumbnailUtils.extractThumbnail(image, dimension, dimension);
            image = Bitmap.createScaledBitmap(image, imageSize, imageSize, false);
            classifyImage(image);
        });
    }

    private void classifyImage(Bitmap image) {

        try {
            PlantModel1 model = PlantModel1.newInstance(getApplicationContext());

            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3}, DataType.FLOAT32);
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3);
            byteBuffer.order(ByteOrder.nativeOrder());

            int[] intValue = new int[imageSize * imageSize];

            image.getPixels(intValue, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
            int pixel = 0;

            for (int i = 0; i < imageSize; i++) {
                for (int j = 0; j < imageSize; j++) {
                    int val = intValue[pixel++]; // RGB
                    byteBuffer.putFloat(((val >> 16) & 0xFF) * (1.f / 255.f));
                    byteBuffer.putFloat(((val >> 8) & 0xFF) * (1.f / 255.f));
                    byteBuffer.putFloat((val & 0xFF) * (1.f / 255.f));
                }
            }

            inputFeature0.loadBuffer(byteBuffer);

            PlantModel1.Outputs outputs = model.process(inputFeature0);
            TensorBuffer outputFeatures0 = outputs.getOutputFeature0AsTensorBuffer();

            float[] confidence = outputFeatures0.getFloatArray();
            int maxPos = 0;
           float maxConfidence = 0;
            for (int i = 0; i < confidence.length; i++) {
                if (confidence[i] > maxConfidence) {
                    maxConfidence = confidence[i];
                    maxPos = i;
                }
            }

            String[] classes = {"Neem", "Basale", "Hibiscus Rosa-sinensis", "Jasmine", "Mint", "Tulsi", "Betel", "Sandalwood",
                    "Alstonia Scholaris", "Arjun", "Chinar", "Gauva", "Jamun", "Jatropha", "Lemon", "Mango", "Pomegranate",
                    "Pongamia Pinnata", "Drumstick"};

            nameofleaf = classes[maxPos];
            get_data_from_firebase(classes[maxPos]);

            model.close();
        } catch (Exception e) {
            Log.d("Display Errors : ", "" + e);
        }
    }

    private void get_data_from_firebase(String leaf_name) {

        AlertDialog.Builder builderSingle = new AlertDialog.Builder(LeafAnalyzer.this);
        builderSingle.setIcon(R.drawable.logo);
        builderSingle.setTitle("List of Medicines can be made using " + nameofleaf + " Leaf");

        final ArrayAdapter<String> arrayAdapter =
                new ArrayAdapter<String>(LeafAnalyzer.this, android.R.layout.simple_dropdown_item_1line);

        databaseReference = firebaseDatabase.getReference("List_of_Disease").child(leaf_name);

        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot ds : dataSnapshot.getChildren()) {
//                    Log.d("TAG",ds.getKey());
                    arrayAdapter.add(ds.getKey());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(LeafAnalyzer.this, "Something Went Wrong", Toast.LENGTH_SHORT).show();
            }
        });

        builderSingle.setNegativeButton("", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        builderSingle.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String strName = arrayAdapter.getItem(which);
                getDescription(leaf_name, strName);
            }
        });
        builderSingle.show();
    }

    private void getDescription(String leaf_name, String strName) {

        databaseReference = firebaseDatabase.getReference("List_of_Disease").child(leaf_name).child(strName);
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                AlertDialog.Builder builderInner = new AlertDialog.Builder(LeafAnalyzer.this);

                String description = dataSnapshot.child("0").getValue(String.class);

                builderInner.setTitle("Description : "+strName);
                builderInner.setMessage(description);

                builderInner.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        Toast.makeText(LeafAnalyzer.this, "Hope you found it useful :)", Toast.LENGTH_LONG).show();
                    }
                });

                builderInner.setNegativeButton("Open google", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://www.google.com/search?q=" + nameofleaf + " use in " + strName)));
                        Toast.makeText(LeafAnalyzer.this, "Hope you found it useful :)", Toast.LENGTH_LONG).show();
                    }
                });

                builderInner.show();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(LeafAnalyzer.this, "Something Went Wrong", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == 2) {
                imageUri = data.getData();
                Bitmap bitmap;
                try {
                    bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                    select.setImageBitmap(bitmap);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (requestCode == 3) {

                Bitmap image = (Bitmap) data.getExtras().get("data");
                int dimension = Math.min(image.getWidth(), image.getHeight());
                image = ThumbnailUtils.extractThumbnail(image, dimension, dimension);
                select.setImageBitmap(image);
                image = Bitmap.createScaledBitmap(image, imageSize, imageSize, false);
                classifyImage(image);
            }
        } else {
            Toast.makeText(this, "Select an Image", Toast.LENGTH_SHORT).show();
        }
    }
}