package com.fmsnl.sslab;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.fmsnl.sslab.databinding.ActivityMainBinding;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private ActivityMainBinding binding;
    private String fileName = "";
    private MediaRecorder recorder = null;
    private boolean isRecording = false;
    private Vibrator vibrator;
    private AudioRecord audioRecord;
    private Thread recordingThread;
    private boolean permissionToRecordAccepted = false;
    private String[] permissions = {Manifest.permission.RECORD_AUDIO, Manifest.permission.VIBRATE};
    private boolean hasExceededThreshold = false;

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionToRecordAccepted = requestCode == REQUEST_RECORD_AUDIO_PERMISSION && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (!permissionToRecordAccepted) {
            finish();
        }
    }

    @SuppressLint("RestrictedApi")
    private void startRecording() {
        if (recorder != null) {
            recorder.release();
        }

        fileName = getExternalFilesDir(Environment.DIRECTORY_MUSIC).getAbsolutePath() + "/audiorecordtest.3gp";
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setOutputFile(fileName);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            recorder.prepare();
            recorder.start();
            isRecording = true;
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();

            hasExceededThreshold = false; // Reset the threshold flag
            startAudioRecordingForDecibel();

        } catch (IOException e) {
            Log.e("MainActivity", "prepare() failed");
            e.printStackTrace();
        } catch (IllegalStateException e) {
            Log.e("MainActivity", "start() failed");
            e.printStackTrace();
        }
    }

    private void startAudioRecordingForDecibel() {
        int bufferSize = AudioRecord.getMinBufferSize(
                44100,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT);

        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                44100,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT,
                bufferSize);

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e("MainActivity", "AudioRecord initialization failed");
            return;
        }

        audioRecord.startRecording();
        recordingThread = new Thread(() -> {
            short[] buffer = new short[bufferSize];
            while (isRecording) {
                int read = audioRecord.read(buffer, 0, bufferSize);
                if (read > 0) {
                    long sum = 0;
                    for (short s : buffer) {
                        sum += s * s;
                    }
                    final double amplitude = sum / (double) read;
                    final double decibel = 10 * Math.log10(amplitude);
                    Log.d("Decibel", "dB: " + decibel);

                    if (decibel > 80) {
                        hasExceededThreshold = true; // Set the threshold flag
                    }

                    if (hasExceededThreshold) {
                        runOnUiThread(() -> {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 1000, 1000}, 0));
                            } else {
                                vibrator.vibrate(new long[]{0, 1000, 1000}, 0);
                            }
                        });
                    }
                }
            }
        });
        recordingThread.start();
    }

    private void stopRecording() {
        isRecording = false;

        if (recorder != null) {
            try {
                recorder.stop();
            } catch (IllegalStateException e) {
                Log.e("MainActivity", "stop() failed");
                e.printStackTrace();
            }
            recorder.release();
            recorder = null;
        }

        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }

        if (recordingThread != null) {
            recordingThread.interrupt();
            recordingThread = null;
        }



        Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);

        binding.startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isRecording) {
                    startRecording();
                }
            }
        });

        binding.stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                vibrator.cancel(); // Stop the vibration
                hasExceededThreshold = false; // Reset the threshold flag
                stopRecording();

            }
        });
    }
}
