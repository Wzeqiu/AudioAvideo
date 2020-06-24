package com.wzq.audioavideo.audio;

import android.Manifest;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Process;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.wzq.audioavideo.R;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class AudioActivity extends AppCompatActivity {

    private static String TAG = "AudioActivity";
    private static boolean DEBUG = true;


    private static final SimpleDateFormat mDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US);
    private static final String DIR_NAME = "AVRecSample";


    @BindView(R.id.button_start)
    Button buttonStart;
    @BindView(R.id.button_stop)
    Button buttonStop;

    // 修改头像需要的权限
    private String[] mPerms = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO};
    private static final int PERMISSIONS = 101; //请求码


    /**
     * 输出路径
     */
    private String mOutputPath;
    private MediaMuxer mMediaMuxer;

    /**
     * 编码
     */
    protected MediaCodec mMediaCodec;
    private static final String MIME_TYPE = "audio/mp4a-latm";
    private static final int SAMPLE_RATE = 44100;    // 44.1[KHz] is only setting guaranteed to be available on all devices.
    private static final int BIT_RATE = 64000;
    public static final int SAMPLES_PER_FRAME = 1024;    // AAC, bytes/frame/channel
    public static final int FRAMES_PER_BUFFER = 25;    // AAC, frame/buffer/sec

    private int mTrackIndex;

    /**
     * 同步
     */
    private Object mSync = new Object();

    /**
     * true 结束采集
     */
    protected volatile boolean mRequestStop;
    /**
     * 采集可用帧
     */
    private int mRequestDrain;

    /**
     * 正在采集数据
     */
    protected volatile boolean mIsCapturing;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio);
        ButterKnife.bind(this);

        requestPermission();

    }

    @OnClick({R.id.button_start, R.id.button_stop})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.button_start:
                initMediaMuxer();
                startRecording();
                break;
            case R.id.button_stop:
                stopRecording();
                break;
        }
    }

    /**
     * 开始录制
     */
    private void startRecording() {
        synchronized (mSync) {
            mIsCapturing = true;
            mRequestStop = false;
            mSync.notifyAll();
        }
        new AudioThread().start();
    }

    /**
     * 结束录制
     */
    private void stopRecording() {

    }

    private static final int[] AUDIO_SOURCES = new int[]{
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.CAMCORDER,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
    };

    private class AudioThread extends Thread {
        @Override
        public void run() {
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
//            采集次数
            int cut = 0;
            final int min_buffer_size = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int buffer_size = SAMPLES_PER_FRAME * FRAMES_PER_BUFFER;
            if (buffer_size < min_buffer_size) {
                buffer_size = ((min_buffer_size / SAMPLES_PER_FRAME) + 1) * SAMPLES_PER_FRAME * 2;
            }
            AudioRecord audioRecord = null;
            for (int audioSource : AUDIO_SOURCES) {
                try {
                    audioRecord = new AudioRecord(
                            audioSource, SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            buffer_size);
                    if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                        audioRecord = null;
                    }
                } catch (Exception e) {
                    audioRecord = null;
                }
                if (audioRecord != null) break;
            }

            if (audioRecord != null) {
                try {
                    if (mIsCapturing) {
                        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(SAMPLES_PER_FRAME);
                        int readBytes;
                        audioRecord.startRecording();
                        try {

                            for (;mIsCapturing&&!mRequestStop&&)





                        } finally {
                            audioRecord.stop();
                        }

                    }
                } finally {

                    audioRecord.release();
                }


            }


        }
    }


    /**
     * 初始化 MediaMuxer
     */
    private void initMediaMuxer() {
        try {
            mOutputPath = getCaptureFile(Environment.DIRECTORY_MOVIES, ".mp4").toString();
            mMediaMuxer = new MediaMuxer(mOutputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (final NullPointerException | IOException e) {
            throw new RuntimeException("This app has no permission of writing external storage");
        }

        new MediaMuxerThread().start();
        synchronized (mSync) {
            try {
                mSync.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        createMediaCodec();
    }


    /**
     * 创建编码器
     */
    private void createMediaCodec() {
        MediaCodecInfo mediaCodecInfo = selectAudioCodec(MIME_TYPE);
        if (mediaCodecInfo == null) {
            Log.e(TAG, "没有编码器");
            return;
        }
        try {
            final MediaFormat mediaFormat = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, 1);
            mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
            mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            mMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
            mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mMediaCodec.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * 根据mime 类型判断是否有该类型的编码器
     *
     * @param mimeType
     * @return
     */
    private static final MediaCodecInfo selectAudioCodec(final String mimeType) {
        if (DEBUG) Log.v(TAG, "selectAudioCodec:");

        MediaCodecInfo result = null;
        // get the list of available codecs
        final int numCodecs = MediaCodecList.getCodecCount();
        LOOP:
        for (int i = 0; i < numCodecs; i++) {
            final MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {    // skipp decoder
                continue;
            }
            final String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (DEBUG) Log.i(TAG, "supportedType:" + codecInfo.getName() + ",MIME=" + types[j]);
                if (types[j].equalsIgnoreCase(mimeType)) {
                    if (result == null) {
                        result = codecInfo;
                        break LOOP;
                    }
                }
            }
        }
        return result;
    }

    private class MediaMuxerThread extends Thread {
        @Override
        public void run() {
            synchronized (mSync) {
                mRequestStop = false;
                mRequestDrain = 0;
                mSync.notifyAll();
            }
            final boolean isRunning = true;
            boolean localRequestStop;
            boolean localRequestDrain;
            while (isRunning) {
                synchronized (mSync) {
                    localRequestStop = mRequestStop;
                    localRequestDrain = mRequestDrain > 0;
                    if (localRequestDrain) mRequestDrain--;
                }

                /**
                 * 结束采集
                 */
                if (localRequestStop) {


                    break;
                }


                /**
                 * 存在采集数据
                 */
                if (localRequestDrain) {
                    writeMuxer();
                } else {
                    synchronized (mSync) {
                        try {
                            mSync.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            synchronized (mSync) {
                mRequestStop = true;
                mIsCapturing = false;
            }
        }
    }


    /**
     * 数据输出到多路复用
     */
    private void writeMuxer() {


    }


    /**
     * generate output file
     *
     * @param type Environment.DIRECTORY_MOVIES / Environment.DIRECTORY_DCIM etc.
     * @param ext  .mp4(.m4a for audio) or .png
     * @return return null when this app has no writing permission to external storage.
     */
    public static final File getCaptureFile(final String type, final String ext) {
        final File dir = new File(Environment.getExternalStoragePublicDirectory(type), DIR_NAME);
        Log.d(TAG, "path=" + dir.toString());
        dir.mkdirs();
        if (dir.canWrite()) {
            return new File(dir, getDateTimeString() + ext);
        }
        return null;
    }

    /**
     * get current date and time as String
     *
     * @return
     */
    private static final String getDateTimeString() {
        final GregorianCalendar now = new GregorianCalendar();
        return mDateTimeFormat.format(now.getTime());
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
