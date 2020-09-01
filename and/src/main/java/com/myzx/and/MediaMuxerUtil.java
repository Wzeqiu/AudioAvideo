package com.myzx.and;

import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.Locale;

/**
 * ProjectName: AudioAvideo
 * Package: com.myzx.and
 * ClassName: MediaMuxerUtil
 * Description: java类作用描述
 * Author: WZQ
 * CreateDate: 2020/8/28 11:45
 * Version: 1.0
 */
class MediaMuxerUtil {
    private static final String TAG = "MediaMuxerUtil";
    private static final String DIR_NAME = "AV";
    private static final SimpleDateFormat mDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US);
    private MediaMuxer mMediaMuxer;

    public MediaMuxer getMediaMuxer() {
        return mMediaMuxer;
    }

    public void setMediaMuxer(MediaMuxer mediaMuxer) {
        mMediaMuxer = mediaMuxer;
    }

    public MediaMuxerUtil() {
        try {
            File outPuPath = getCaptureFile(Environment.DIRECTORY_MOVIES, ".mp4");
            mMediaMuxer = new MediaMuxer(outPuPath.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int addTrack(MediaFormat mediaFormat) {
        return mMediaMuxer.addTrack(mediaFormat);
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
