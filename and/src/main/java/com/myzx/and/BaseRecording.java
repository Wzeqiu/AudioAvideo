package com.myzx.and;

import android.media.MediaCodec;

/**
 * ProjectName: AudioAvideo
 * Package: com.myzx.and
 * ClassName: BaseRecording
 * Description: java类作用描述
 * Author: WZQ
 * CreateDate: 2020/8/28 11:39
 * Version: 1.0
 */
class BaseRecording implements RecordingListener, Runnable {
    static MediaMuxerUtil mMediaMuxerUtil;


    static {
        mMediaMuxerUtil = new MediaMuxerUtil();
    }


    protected MediaCodec mMediaCodec;

    protected Object mSyn = new Object();


    public BaseRecording() {
        new Thread(this).start();
    }

    @Override
    public void startRecording() {

    }

    @Override
    public void stopRecording() {

    }

    @Override
    public void run() {


    }
}
