package com.hometurf.android.services;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;

import java.util.Arrays;

public class HomeTurfRecordAudioService {

    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_FLOAT;
    private static final int BufferElements2Rec = 1024; // want to play 2048 (2K) since 2 bytes we use only 1024
    private static final int RECORDING_MILLIS = 3500;

    private AudioRecord recorder = null;
    private Thread recordingThread = null;
    private boolean isRecording = false;
    private float[] audioBufferArray;
    private long offsetFromWebServerTimeMillis;
    private long startTimeSeconds;
    private int sampleRate;

    private final HomeTurfJavascriptService javascriptService;

    public HomeTurfRecordAudioService(HomeTurfJavascriptService javascriptService) {
        this.javascriptService = javascriptService;
    }

    public void startRecording(long timeOfRequestFromWebMillis) {
        // TODO: possibly add lag between web + native if needed here
        long requestTimeMillis = System.currentTimeMillis();
//        Log.d("Request time (web)", String.valueOf(timeOfRequestFromWebMillis));
//        Log.d("Request time (native)", String.valueOf(requestTimeMillis));
        offsetFromWebServerTimeMillis = requestTimeMillis - timeOfRequestFromWebMillis;
        sampleRate = getBestSampleRate();
        int minBufferSize = AudioRecord.getMinBufferSize(sampleRate,
                RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
//        int bufferElemSize = BufferElements2Rec * BytesPerElement;
        int bufferSize = 60000;
        audioBufferArray = new float[bufferSize];
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                sampleRate, RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING, minBufferSize);

        recorder.startRecording();
        long startTimeMillis = System.currentTimeMillis();
        startTimeSeconds = (startTimeMillis - offsetFromWebServerTimeMillis) / 1000;
        isRecording = true;
        recordingThread = new Thread(this::recordAudioToBuffer, "AudioRecorder Thread");
        recordingThread.start();
        Handler mHandler = new Handler();
        mHandler.postDelayed(this::stopRecording, RECORDING_MILLIS);
    }

    private void recordAudioToBuffer() {
        System.out.println("at record audio to buffer");
        int offset = 0;
        while (isRecording) {
            offset += recorder.read(audioBufferArray, offset, BufferElements2Rec, AudioRecord.READ_BLOCKING);
            // Calculate decibels?
//          webView.evaluateJavascript("homeTurfCallbackFromNative.execute({action: 'HANDLE_AUDIO_LEVEL', data: {decibelLevel: }})");
        }
    }

    public void stopRecording() {
        // stops the recording activity
        if (null != recorder) {
            isRecording = false;
            recorder.stop();
            recorder.release();
            recorder = null;
            recordingThread = null;
            javascriptService.executeJavaScriptActionAndRawDataInWebView("HANDLE_AUDIO", String.format("{sampleRate: %s, startTime: %s, nativeResult: \"%s\"}}",
                    sampleRate, startTimeSeconds, Arrays.toString(audioBufferArray)));
        }
    }

    private int getBestSampleRate() {
        int EMU_RECORDER_SAMPLE_RATE = 8000;
        int DEVICE_RECORDER_SAMPLE_RATE = 44100;
        // Use 8000 if on emulator, otherwise 44100
        boolean isProbablyRunningOnEmulator = (Build.FINGERPRINT.startsWith("google/sdk_gphone_")
                && Build.FINGERPRINT.endsWith(":user/release-keys")
                && Build.MANUFACTURER.equals("Google") && Build.PRODUCT.startsWith("sdk_gphone_") && Build.BRAND.equals("google")
                && Build.MODEL.startsWith("sdk_gphone_"))

                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                //bluestacks
                || "QC_Reference_Phone".equals(Build.BOARD) && !"Xiaomi".equalsIgnoreCase(Build.MANUFACTURER) //bluestacks
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.HOST.startsWith("Build") //MSI App Player
                || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                || Build.PRODUCT.equals("google_sdk");
        // another Android SDK emulator check - skipping for now
        //|| SystemProperties.getProp("ro.kernel.qemu") == "1"
        if (isProbablyRunningOnEmulator) return EMU_RECORDER_SAMPLE_RATE;
        else return DEVICE_RECORDER_SAMPLE_RATE;
    }
}
