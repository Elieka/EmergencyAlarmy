package com.fmsnl.sslab;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.fmsnl.sslab.databinding.ActivityMainBinding;

import java.io.IOException;
import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private ActivityMainBinding binding;
    private String fileName = "";
    private MediaRecorder recorder = null;
    private boolean isFlashOn = false;
    private boolean isRecording = false;
    private boolean isVibrating = false;
    private boolean permissionToRecordAccepted = false;
    private boolean hasExceededThreshold = false;
    private Vibrator vibrator;
    private AudioRecord audioRecord;
    private Thread recordingThread;
    private String[] permissions = {Manifest.permission.RECORD_AUDIO, Manifest.permission.VIBRATE};
    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSIONS = 200;
    private Handler handler;
    private ConstraintLayout layout;
    private Runnable flashRunnable;
    private Runnable colorRunnable;
    private Runnable vibrationRunnable;
    private CameraManager cameraManager;
    private String cameraId = null;
    private WeakReference<Context> context;
    private Button controlButton;
    private TextView decibelTextView;
    private boolean isFirstClick = true;
    private static final double DECIBEL_THRESHOLD = 70.0;
    private Runnable initialFlashRunnable;
    private Runnable initialColorRunnable;
    private Runnable initialVibrationRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        layout = findViewById(R.id.mainLayout); // 레이아웃 초기화 추가
        handler = new Handler();
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        context = new WeakReference<>(this);

        decibelTextView = findViewById(R.id.decibelTextView); // TextView 초기화

        if (!hasPermissions(permissions)) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
        }

        controlButton = findViewById(R.id.controlButton);

        controlButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isFirstClick) {
                    startRecording();
                    controlButton.setText("정지");
                    isFirstClick = false;
                } else {
                    resetAppToInitialState();
                    isFirstClick = true;
                    controlButton.setText("시작");
                }
            }
        });

        initFlash(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (hasPermissions(permissions)) {
                startEffects();
            } else {
                Log.e(TAG, "Permissions not granted");
                finish();
            }
        }
    }

    private boolean hasPermissions(String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
            return;
        }

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
            Log.e(TAG, "prepare() failed");
            e.printStackTrace();
        } catch (IllegalStateException e) {
            Log.e(TAG, "start() failed");
            e.printStackTrace();
        }
    }

    private void startAudioRecordingForDecibel() {
        int bufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed");
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

                    runOnUiThread(() -> {
                        String formattedDecibel = String.format("%.1f", decibel);
                        decibelTextView.setText("데시벨 크기 : " + formattedDecibel);
                    });

                    if (decibel > DECIBEL_THRESHOLD) {
                        hasExceededThreshold = true;
                        runOnUiThread(this::startEffects); // 임계치를 초과할 경우 startEffects 호출
                    } else {
                        hasExceededThreshold = false;
                        runOnUiThread(this::stopAllEffects); // 임계치 미만일 경우 stopAllEffects 호출
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
                Log.e(TAG, "stop() failed");
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

    private void startEffects() {
        if (hasExceededThreshold) {
            if (flashRunnable == null && colorRunnable == null && vibrationRunnable == null) {
                startFlashEffect();
                startScreenColorEffect();
                startVibrationEffect();
            }
        }
    }

    private void startFlashEffect() {
        flashRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    toggleFlashlight();
                } catch (Exception e) {
                    Log.e(TAG, "Error in flash effect", e);
                }
                handler.postDelayed(this, 500); // 0.5초 간격으로 플래시 반짝임
            }
        };
        handler.post(flashRunnable);
    }

    private void startScreenColorEffect() {
        colorRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    int color = Color.rgb(
                            (int) (Math.random() * 256),
                            (int) (Math.random() * 256),
                            (int) (Math.random() * 256)
                    );
                    layout.setBackgroundColor(color);
                } catch (Exception e) {
                    Log.e(TAG, "Error in screen color effect", e);
                }
                handler.postDelayed(this, 500); // 0.5초 간격으로 색 변경
            }
        };
        handler.post(colorRunnable);
    }

    private void startVibrationEffect() {
        vibrationRunnable = new Runnable() {
            @Override
            public void run() {
                if (vibrator != null && !isVibrating) {
                    long[] pattern = {0, 100, 100}; // 진동 패턴 설정
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                    isVibrating = true;
                }
                handler.postDelayed(this, 500); // 0.5초 간격으로 진동
            }
        };
        handler.post(vibrationRunnable);
    }

    private void stopAllEffects() {
        stopFlashEffect();
        stopScreenColorEffect();
        stopVibrationEffect();
    }

    private void stopFlashEffect() {
        if (flashRunnable != null) {
            handler.removeCallbacks(flashRunnable);
            flashRunnable = null;
        }

        try {
            turnOffFlashlight();
        } catch (Exception e) {
            Log.e(TAG, "Error stopping flash effect", e);
        }
    }

    private void stopScreenColorEffect() {
        if (colorRunnable != null) {
            handler.removeCallbacks(colorRunnable);
            colorRunnable = null;
        }

        try {
            layout.setBackgroundColor(Color.WHITE); // 기본 색상으로 되돌리기
        } catch (Exception e) {
            Log.e(TAG, "Error stopping screen color effect", e);
        }
    }

    private void stopVibrationEffect() {
        if (vibrationRunnable != null) {
            handler.removeCallbacks(vibrationRunnable);
            vibrationRunnable = null;
        }

        if (vibrator != null && isVibrating) {
            vibrator.cancel();
            isVibrating = false;
        }
    }

    @SuppressLint("MissingPermission")
    private void initFlash(Context context) {
        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = cameraManager.getCameraIdList()[0];
        } catch (CameraAccessException e) {
            Log.e(TAG, "Camera access exception: " + e.getMessage());
        }
    }

    private void toggleFlashlight() {
        if (cameraManager != null && cameraId != null) {
            try {
                if (isFlashOn) {
                    cameraManager.setTorchMode(cameraId, false);
                    isFlashOn = false;
                } else {
                    cameraManager.setTorchMode(cameraId, true);
                    isFlashOn = true;
                }
            } catch (CameraAccessException e) {
                Log.e(TAG, "Error toggling flashlight", e);
            }
        }
    }

    private void turnOffFlashlight() {
        if (cameraManager != null && cameraId != null && isFlashOn) {
            try {
                cameraManager.setTorchMode(cameraId, false);
                isFlashOn = false;
            } catch (CameraAccessException e) {
                Log.e(TAG, "Error turning off flashlight", e);
            }
        }
    }

    private void resetAppToInitialState() {
        stopRecording();
        stopAllEffects();

        // 초기 상태로 되돌리기
        if (initialFlashRunnable != null) {
            handler.removeCallbacks(initialFlashRunnable);
        }

        if (initialColorRunnable != null) {
            handler.removeCallbacks(initialColorRunnable);
        }

        if (initialVibrationRunnable != null) {
            handler.removeCallbacks(initialVibrationRunnable);
        }

        layout.setBackgroundColor(Color.WHITE);
        decibelTextView.setText("데시벨 크기 : 0.0");

        isFirstClick = true;
        controlButton.setText("START");
    }
}
