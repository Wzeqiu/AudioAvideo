package com.myzx.and;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;

import java.io.IOException;

/**
 * ProjectName: AudioAvideo
 * Package: com.myzx.and
 * ClassName: AudioRecording
 * Description: java类作用描述
 * Author: WZQ
 * CreateDate: 2020/8/28 11:37
 * Version: 1.0
 */
class AudioRecording extends BaseRecording {
    private static final String MIME_TYPE = "audio/mp4a-latm";

    // 音频采样率
    private static final int SAMPLE_RATE = 44100;

    // 比特率
    private static final int BIT_RATE = 64000;


    private static final MediaCodecInfo selectAudioCodec(final String mimeType) {
        MediaCodecInfo result = null;

        // 可用编解码器数量
        final int numCodecs = MediaCodecList.getCodecCount();
        Loop:
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo mediaCodecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!mediaCodecInfo.isEncoder()) {
                // 如果是解码器跳过
                continue;
            }
            // 编码器支持的类型
            String[] mimeTypes = mediaCodecInfo.getSupportedTypes();
            for (String type : mimeTypes) {
                if (type.equalsIgnoreCase(mimeType)) {
                    result = mediaCodecInfo;
                    break Loop;
                }
            }
        }
        return result;
    }

    @Override
    public void startRecording() {
        super.startRecording();
        try {
            final MediaCodecInfo mediaCodecInfo = selectAudioCodec(MIME_TYPE);
            if (mediaCodecInfo == null) {
                // 没有找到匹配的编码器
                return;
            }
            final MediaFormat audioFormat = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, 1);
            audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            audioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);
            // 比特率  码率
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
            audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);

            mMediaCodec = MediaCodec.createDecoderByType(MIME_TYPE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void stopRecording() {
        super.stopRecording();
    }
}
