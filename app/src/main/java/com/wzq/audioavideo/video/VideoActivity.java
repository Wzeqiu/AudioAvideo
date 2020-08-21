package com.wzq.audioavideo.video;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.wzq.audioavideo.R;

public class VideoActivity extends AppCompatActivity {
    private ConstraintLayout mConstraintLayout;
    private View rootView;

    private boolean isFull;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);
        mConstraintLayout = findViewById(R.id.cl_view);
        rootView = findViewById(R.id.camera_surface);

        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(isFull){
                    hideFull();
                }else {
                    showFull();
                }

            }
        });
    }


    protected void showFull() {
        isFull = true;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        mConstraintLayout.removeView(rootView);


        ViewGroup viewGroup = getViewGroup();
        viewGroup.addView(rootView);
    }

    protected void hideFull() {
        isFull = false;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        ViewGroup viewGroup = getViewGroup();
        viewGroup.removeView(rootView);


        mConstraintLayout.addView(rootView);
    }

    private ViewGroup getViewGroup() {
        return (ViewGroup) (CommonUtil.scanForActivity(this)).findViewById(Window.ID_ANDROID_CONTENT);
    }

}
