package com.wzq.audioavideo;

import androidx.appcompat.app.AppCompatActivity;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.wzq.audioavideo.audio.AudioActivity;
import com.wzq.audioavideo.video.VideoActivity;

public class MainActivity extends AppCompatActivity {
    // 修改头像需要的权限
    private String[] mPerms = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO};
    private static final int PERMISSIONS = 101; //请求码
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.Audio).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, AudioActivity.class));
            }
        });
        findViewById(R.id.Video).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, VideoActivity.class));
            }
        });

        requestPermission();

    }



    @AfterPermissionGranted(PERMISSIONS)
    private void requestPermission() {
        if (EasyPermissions.hasPermissions(this, mPerms)) {
            // 已经存在权限
        } else {
            EasyPermissions.requestPermissions(this, "获取权限", PERMISSIONS, mPerms);
        }
    }
}
