package com.example.dxscanner.Activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.app.UiModeManager;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.example.dxscanner.Adapter.GalleryAdapter;
import com.example.dxscanner.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

public class ImageGallery extends AppCompatActivity {
    private UiModeManager uiModeManager;

    private static File mImageFolder;
    private RecyclerView recyclerView;
    private ImageButton backBtn;
    private TextView appname,noOfImagesTV;
    public static boolean READ_STORAGE_PERMISSION=false;
    private static ArrayList<File> ALL_IMAGES;
    private static final int REQUEST_READ_EXTERNAL_STORAGE_PERMISSION_RESULT=5;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_gallery);
        init();
        switchDayNightMode();
        if(READ_STORAGE_PERMISSION) {
            ALL_IMAGES=getImages();
            noOfImagesTV.setText(String.valueOf(ALL_IMAGES.size()).concat(" images")); //updating the textview for showing the number of images
            GalleryAdapter galleryAdapter = new GalleryAdapter(ALL_IMAGES, ImageGallery.this, R.layout.image_item);
            recyclerView.setAdapter(galleryAdapter);
            recyclerView.setLayoutManager(new GridLayoutManager(ImageGallery.this, 4));
            recyclerView.setHasFixedSize(true);
        }
        backBtn.setOnClickListener(v->{
            onBackPressed();
        });
    }
    private void init(){
        uiModeManager = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        recyclerView=findViewById(R.id.recyclerView);
        recyclerView.setItemViewCacheSize(25);
        recyclerView.setDrawingCacheEnabled(true);
        recyclerView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
        backBtn=findViewById(R.id.backBtn);
        appname=findViewById(R.id.appname);
        noOfImagesTV=findViewById(R.id.noOfImagesTV);
        getReadStoragePermission();
    }

    private void switchDayNightMode(){
        if (uiModeManager.getNightMode() == UiModeManager.MODE_NIGHT_YES){
            appname.setTextColor(getResources().getColor(R.color.white));
            backBtn.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(android.R.color.transparent)));
            backBtn.setColorFilter(getResources().getColor(R.color.white), PorterDuff.Mode.SRC_IN);
        }else{
            appname.setTextColor(getResources().getColor(R.color.black));
            backBtn.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.white)));
            backBtn.setColorFilter(getResources().getColor(R.color.black), PorterDuff.Mode.SRC_IN);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode==REQUEST_READ_EXTERNAL_STORAGE_PERMISSION_RESULT){
            if(grantResults[0]!=PackageManager.PERMISSION_GRANTED){
                READ_STORAGE_PERMISSION=false;
                Toast.makeText(this, "This add needs read external storage permission", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void getReadStoragePermission(){
        if(Build.VERSION.SDK_INT>Build.VERSION_CODES.Q) {
            READ_STORAGE_PERMISSION=true;
            Toast.makeText(this, "Read External Storage permission granted", Toast.LENGTH_SHORT).show();
            return;
        }
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)==
                PackageManager.PERMISSION_GRANTED){
            READ_STORAGE_PERMISSION=true;
            Toast.makeText(this, "Read External Storage permission granted", Toast.LENGTH_SHORT).show();
        }else{
            if(shouldShowRequestPermissionRationale(android.Manifest.permission.READ_EXTERNAL_STORAGE)){
                Toast.makeText(this, "This app needs to be able to read images", Toast.LENGTH_SHORT).show();
            }
            READ_STORAGE_PERMISSION=false;
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},REQUEST_READ_EXTERNAL_STORAGE_PERMISSION_RESULT);
        }
    }
    private ArrayList<File> getImages(){
        File ImageFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        mImageFolder = new File(ImageFolder,"DxScanner");
        File[] files=mImageFolder.listFiles();
        ArrayList<File> fileLst = new ArrayList<>();
        assert files != null;
        if(files.length==0) return fileLst;
        fileLst.addAll(Arrays.asList(files));
        return fileLst;
    }




}