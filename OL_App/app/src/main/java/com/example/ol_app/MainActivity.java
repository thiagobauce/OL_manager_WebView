package com.example.ol_app;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private static String file_type = "*/*";
    private String cam_file_data = null;
    private ValueCallback<Uri> file_data;
    private ValueCallback<Uri> file_path;
    private final static int file_req_code = 1;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        WebView webView = findViewById(R.id.webview);

        webView.setWebViewClient(new WebViewClient());
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().getAllowFileAccess();
        webView.getSettings().getAllowFileAccessFromFileURLs();
        webView.getSettings().setDomStorageEnabled(true);
        webView.loadUrl("http://sistema.player.net.br:8182/#/");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                             WebChromeClient.FileChooserParams fileChooserParams) {

                if(file_permission() && Build.VERSION.SDK_INT >= 21) {
                    file_path = (ValueCallback<Uri>) filePathCallback;
                    Intent takePictureIntent = null;
                    boolean includePhoto = false;
                    /*-- checking the accept parameter to determine which intent(s) to include --*/
                    paramCheck:
                    for (String acceptTypes : fileChooserParams.getAcceptTypes()) {
                        String[] splitTypes = acceptTypes.split(", ?+");
                        /*-- although it's an array, it still seems to be the whole value; split it out into chunks so that we can detect multiple values --*/
                        for (String acceptType : splitTypes) {
                            switch (acceptType) {
                                case "*/*":
                                    includePhoto = true;
                                    break paramCheck;
                                case "image/*":
                                    includePhoto = true;
                                    break;
                            }
                        }
                    }
                    if (fileChooserParams.getAcceptTypes().length == 0) {
                        /*-- no `accept` parameter was specified, allow both photo and video --*/
                        includePhoto = true;
                    }

                    if (includePhoto) {
                        takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        if (takePictureIntent.resolveActivity(MainActivity.this.getPackageManager()) != null) {
                            File photoFile = null;
                            try {
                                photoFile = create_image();
                                takePictureIntent.putExtra("PhotoPath", cam_file_data);
                            } catch (IOException ex) {
                            }
                            if (photoFile != null) {
                                cam_file_data = "file:" + photoFile.getAbsolutePath();
                                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
                            } else {
                                cam_file_data = null;
                                takePictureIntent = null;
                            }
                        }
                    }

                    Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
                    contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
                    contentSelectionIntent.setType(file_type);


                    Intent[] intentArray;
                    if (takePictureIntent != null) {
                        intentArray = new Intent[]{takePictureIntent};
                    } else {
                        intentArray = new Intent[0];
                    }

                    Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
                    chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
                    chooserIntent.putExtra(Intent.EXTRA_TITLE, "File chooser");
                    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);
                    startActivityForResult(chooserIntent, file_req_code);
                    return true;
                } else {
                    return false;
                }
            }
        });
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent){
        super.onActivityResult(requestCode, resultCode, intent);
        if(Build.VERSION.SDK_INT >= 21){
            Uri[] results = null;

            /*-- if file request cancelled; exited camera. we need to send null value to make future attempts workable --*/
            if (resultCode == Activity.RESULT_CANCELED) {
                file_path.onReceiveValue(null);
                return;
            }

            /*-- continue if response is positive --*/
            if(resultCode== Activity.RESULT_OK){
                if(null == file_path){
                    return;
                }
                ClipData clipData;
                String stringData;

                try {
                    clipData = intent.getClipData();
                    stringData = intent.getDataString();
                }catch (Exception e){
                    clipData = null;
                    stringData = null;
                }
                if (clipData == null && stringData == null && cam_file_data != null) {
                    results = new Uri[]{Uri.parse(cam_file_data)};
                }else{
                    if (clipData != null) {
                        final int numSelectedFiles = clipData.getItemCount();
                        results = new Uri[numSelectedFiles];
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            results[i] = clipData.getItemAt(i).getUri();
                        }
                    } else {
                        try {
                            Bitmap cam_photo = (Bitmap) intent.getExtras().get("data");
                            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                            cam_photo.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
                            stringData = MediaStore.Images.Media.insertImage(this.getContentResolver(), cam_photo, null, null);
                        }catch (Exception ignored){}

                        results = new Uri[]{Uri.parse(stringData)};
                    }
                }
            }

            file_path.onReceiveValue(results);
            file_path = null;
        }else{
            if(requestCode == file_req_code){
                if(null == file_data) return;
                Uri result = intent == null || resultCode != RESULT_OK ? null : intent.getData();
                file_data.onReceiveValue(result);
                file_data = null;
            }
        }
    }
    @Override
    public void onConfigurationChanged(Configuration newConfig){
        super.onConfigurationChanged(newConfig);
    }

    public boolean file_permission(){
        if(Build.VERSION.SDK_INT >=23 && (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA}, 1);
            return false;
        }else{
            return true;
        }
    }

    private File create_image() throws IOException{
        @SuppressLint("SimpleDateFormat") String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "img_"+timeStamp+"_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName,".jpg",storageDir);
    }

}
