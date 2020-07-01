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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.wzq.audioavideo.R;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.IllegalFormatCodePointException;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class AudioActivity extends AppCompatActivity {

    private static String TAG = "AudioActivity";
    private static boolean DEBUG = true;
    protected static final int TIMEOUT_USEC = 10000;    // 10[msec]

    private static final SimpleDateFormat mDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US);
    private static final String DIR_NAME = "AVRecSample";


    @BindView(R.id.button_start)
    Button buttonStart;
    @BindView(R.id.button_stop)
    Button buttonStop;




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


    /***
     * 编码器收到数据为空
     */
    private boolean mIsEOS;


    /**
     * 编码出队实例
     */
    private MediaCodec.BufferInfo mBufferInfo;


    private boolean mMuxerStarted;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio);
        ButterKnife.bind(this);


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

        synchronized (mSync) {
            if (!mIsCapturing || mRequestStop) {
                return;
            }
            mRequestStop = true;
            mSync.notifyAll();
        }


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
                            for (; mIsCapturing && !mRequestStop && !mIsEOS; ) {
                                byteBuffer.clear();
                                readBytes = audioRecord.read(byteBuffer, SAMPLES_PER_FRAME);
                                if (readBytes > 0) {
                                    byteBuffer.position(readBytes);
                                    byteBuffer.flip();
                                    encode(byteBuffer, readBytes, getPTSUs());
                                    frameAvailableSoon();
                                    cut++;
                                }
                            }
                            frameAvailableSoon();
                        } finally {
                            audioRecord.stop();
                        }

                    }
                } finally {
                    audioRecord.release();
                }
            } else {
                Log.e(TAG, "failed to initialize AudioRecord");
            }


            /**
             * 没有采集数据
             */
            if (cut == 0) {
                final ByteBuffer buf = ByteBuffer.allocate(SAMPLES_PER_FRAME);
                for (int i = 0; mIsCapturing && (i < 5); i++) {
                    buf.position(SAMPLES_PER_FRAME);
                    buf.flip();
                    try {
                        encode(buf, SAMPLES_PER_FRAME, getPTSUs());
                        frameAvailableSoon();
                    } catch (Exception e) {
                        break;
                    }

                    synchronized (this) {
                        try {
                            wait(50);
                        } catch (Exception e) {
                        }
                    }
                }
            }
        }
    }


    /**
     * 表示编码的数据即将或者已经可以使用
     *
     * @return
     */
    private boolean frameAvailableSoon() {
        synchronized (mSync) {
            if (!mIsCapturing || mRequestStop) {
                return false;
            }
            mRequestDrain++;
            mSync.notifyAll();
        }
        return true;
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
        mBufferInfo = new MediaCodec.BufferInfo();
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
        mTrackIndex = -1;
        mMuxerStarted = mIsEOS = false;
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


    /***
     * 数据编码
     * @param byteBuffer
     * @param length
     * @param presentationTimeUs
     */
    private void encode(ByteBuffer byteBuffer, int length, long presentationTimeUs) {
        if (!mIsCapturing) return;
        final ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
        while (mIsCapturing) {
            final int inputBufferIndex = mMediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
            if (inputBufferIndex >= 0) {
                final ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                if (byteBuffer != null) {
                    inputBuffer.put(byteBuffer);
                }
                if (length <= 0) {
                    mIsEOS = true;
                    mMediaCodec.queueInputBuffer(inputBufferIndex, 0, 0,
                            presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                } else {
                    mMediaCodec.queueInputBuffer(inputBufferIndex, 0, length,
                            presentationTimeUs, 0);
                }
                break;
            }
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
                    writeMuxer();
                    signalEndOfInputStream();
                    writeMuxer();
                    release();
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
     * 请求结束录制
     */
    private void signalEndOfInputStream() {
        encode(null, 0, getPTSUs());
    }

    /**
     * 数据输出到多路复用
     */
    private void writeMuxer() {
        if (mMediaCodec == null) return;
        ByteBuffer[] encoderOutputBuffers = mMediaCodec.getOutputBuffers();
        int encoderStatus, count = 0;
        if (mMediaMuxer == null) {
            Log.w(TAG, "muxer is unexpectedly null");
            return;
        }
        LOOP:
        while (mIsCapturing) {
            encoderStatus = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!mIsEOS) {
                    if (++count > 5) {
                        break LOOP;
                    }
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                encoderOutputBuffers = mMediaCodec.getOutputBuffers();
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (mMuxerStarted) {
                    throw new RuntimeException("format changed twice");
                }
                final MediaFormat format = mMediaCodec.getOutputFormat();
                mTrackIndex = mMediaMuxer.addTrack(format);
                mMediaMuxer.start();
                mMuxerStarted = true;
            } else if (encoderStatus < 0) {
                Log.w(TAG, "drain:unexpected result from encoder#dequeueOutputBuffer: " + encoderStatus);
            } else {
                final ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                }
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    mBufferInfo.size = 0;
                }
                if (mBufferInfo.size != 0) {
                    count = 0;
                    if (!mMuxerStarted) {
                        throw new RuntimeException("drain:muxer hasn't started");
                    }
                    mBufferInfo.presentationTimeUs = getPTSUs();
                    mMediaMuxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
                    prevOutputPTSUs = mBufferInfo.presentationTimeUs;
                }
                mMediaCodec.releaseOutputBuffer(encoderStatus, false);
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    mIsCapturing = false;
                    break;
                }
            }
        }
    }


    /**
     * 释放
     */
    private void release() {
        mIsCapturing = false;
        if (mMediaCodec != null) {
            try {
                mMediaCodec.stop();
                mMediaCodec.release();
                mMediaCodec = null;
            } catch (Exception e) {
                Log.e(TAG, "failed releasing MediaCodec", e);
            }

            if (mMuxerStarted) {
                if (mMediaMuxer != null) {
                    mMediaMuxer.stop();
                }
            }
        }
        mBufferInfo = null;
    }


    /**
     * previous presentationTimeUs for writing
     */
    private long prevOutputPTSUs = 0;

    /**
     * get next encoding presentationTimeUs
     *
     * @return
     */
    protected long getPTSUs() {
        long result = System.nanoTime() / 1000L;
        // presentationTimeUs should be monotonic
        // otherwise muxer fail to write
        if (result < prevOutputPTSUs)
            result = (prevOutputPTSUs - result) + result;
        return result;
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




}
