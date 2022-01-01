package com.zltd.ocr;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.googlecode.tesseract.android.TessBaseAPI;
import com.zltd.ocr.databinding.ActivityMainBinding;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // Used to load the 'ocr' library on application startup.

    private String TAG = "bds";
    private ActivityMainBinding binding;

    private FlleUtils mFlleUtils;
    private Bitmap bitmap;
    private TextView tv;
    private String path = null;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE};

    public static void verifyStoragePermissions(Activity activity) {
        //权限检查
        if (ContextCompat.checkSelfPermission(activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            //申请权限
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
        //权限检查
        if (ContextCompat.checkSelfPermission(activity,
                Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            //申请权限
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tv = findViewById(R.id.tv);

        Button reg = findViewById(R.id.recog);
        verifyStoragePermissions(MainActivity.this);
        mFlleUtils = new FlleUtils();
        findViewById(R.id.bt).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Uri uri = Uri.parse("content://com.android.externalstorage.documents/document/primary:Pictures/Screenshots");
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri);
                startActivityForResult(intent, 1);
            }
        });
        Log.d(TAG, "model=" + Build.MODEL);
        String dir = null;
        if (Build.MODEL.equals("MI 6X")) {
            dir = "sdcard/DCIM/Screenshots";
        } else {
            dir = "sdcard/Pictures/Screenshots";
        }
        List<File> list = mFlleUtils.listFileSortByModifyTime(dir);
        for (File file : list) {
            if (mFlleUtils.isImageFile(file.getPath())) {
                path = file.getPath();
                Log.d(TAG, "fisrt file=" + path);
                break;
            }
        }
        if (path != null) {
            reg.setVisibility(View.VISIBLE);
            reg.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    bitmap = BitmapFactory.decodeFile(path);
                    Log.d(TAG, "wi=" + bitmap.getWidth());
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            detectText(bitmap);
                        }
                    }).start();

                }
            });
        } else {
            reg.setVisibility(View.GONE);
        }
//        String filePath = "/storage/emulated/0/at/Screenshot_20211213_091406_com.foreverht.newland.workplus.jpg";
//        bitmap = BitmapFactory.decodeFile(filePath);
//        Log.d(TAG, "wi=" + bitmap.getWidth());
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                detectText(bitmap);
//            }
//        }).start();


    }

    private BitmapFactory.Options getBitmapOption(int inSampleSize) {
        System.gc();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPurgeable = true;
        options.inSampleSize = inSampleSize;
        return options;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        String path = null;
        if (resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            if ("file".equalsIgnoreCase(uri.getScheme())) {//使用第三方应用打开
                path = uri.getPath();
                return;
            }
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {//4.4以后
                path = mFlleUtils.getPath(this, uri);
            } else {//4.4以下下系统调用方法
                path = mFlleUtils.getRealPathFromURI(this, uri);
            }
        }
        if (path != null) {
            Log.d(TAG, "path=" + path);
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(path);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            bitmap = BitmapFactory.decodeStream(fis);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    detectText(bitmap);
                }
            }).start();

        }
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            String result = (String) msg.obj;
            tv.setText(result);
            Toast.makeText(MainActivity.this, "result=" + result, Toast.LENGTH_LONG).show();
        }
    };


    public void detectText(Bitmap bitmap) {
        Log.d(TAG, "Initialization of TessBaseApi");
        TessDataManager.initTessTrainedData(MainActivity.this);
        TessBaseAPI tessBaseAPI = new TessBaseAPI();
        String path = TessDataManager.getTesseractFolder();
        Log.d(TAG, "Tess folder: " + path);
        tessBaseAPI.setDebug(true);
        tessBaseAPI.init(path, "eng");
        // 白名单
        tessBaseAPI.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-:");
        // 黑名单
        tessBaseAPI.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, "!@#$%^&*()_+=[]}{;'\"\\|~`,./<>?");
        tessBaseAPI.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO_OSD);
        Log.d(TAG, "Ended initialization of TessEngine");
        Log.d(TAG, "Running inspection on bitmap");
        tessBaseAPI.setImage(bitmap);
        String result = tessBaseAPI.getUTF8Text();
        String lines[] = result.split("\\r?\\n");
        int summary = 0;
        for (String str : lines) {
            Log.d(TAG, "result values= " + str);
            if (TextUtils.isEmpty(str) || !str.contains(":") || !str.contains(" ")) continue;
            if (str.startsWith("09")||str.contains("-")) {
                String tmp[] = str.trim().split(" ");
                if(tmp.length < 2)continue;
                String HourMin = null;
                if(tmp[0].contains("-")) {
                    HourMin = tmp[1];
                }else {
                    HourMin = tmp[0];
                }
                if(!HourMin.startsWith("09")) continue;
                String time[] = HourMin.trim().split(":");
                if (TextUtils.isDigitsOnly(time[1])) {
                    int min = Integer.valueOf(time[1]);
                    summary += min;
                    Log.d(TAG, "result values= " + summary);
                }
            }
        }
        tessBaseAPI.end();
        System.gc();
        Message msg = new Message();
        msg.obj = String.valueOf(summary);
        mHandler.sendMessage(msg);
    }

    /**
     * A native method that is implemented by the 'ocr' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}