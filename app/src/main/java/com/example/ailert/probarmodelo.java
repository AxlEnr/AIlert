package com.example.ailert;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioRecord;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.tensorflow.lite.support.audio.TensorAudio;
import org.tensorflow.lite.task.audio.classifier.AudioClassifier;
import org.tensorflow.lite.task.audio.classifier.Classifications;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class probarmodelo extends AppCompatActivity {
    private static final String TAG = "AudioClassifierApp";
    private static final int REQUEST_RECORD_AUDIO = 1;

    // UI Components
    private TextView txtPrediction;
    private Button btnStart;

    // Audio classification
    private AudioClassifier classifier;
    private TensorAudio tensorAudio;
    private AudioRecord record;

    // Thread management
    private ExecutorService executorService;
    private final AtomicBoolean isListening = new AtomicBoolean(false);
    private final AtomicBoolean isModelLoaded = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_probarmodelo);

        initializeViews();
        checkAndRequestPermissions();
    }

    private void initializeViews() {
        txtPrediction = findViewById(R.id.txtPrediction);
        btnStart = findViewById(R.id.btnStart);

        btnStart.setOnClickListener(v -> toggleAudioClassification());
    }

    private void toggleAudioClassification() {
        if (!isModelLoaded.get()) {
            showToast("Modelo no cargado correctamente");
            return;
        }

        if (isListening.get()) {
            stopAudioClassification();
            btnStart.setText("Iniciar Reconocimiento");
        } else {
            if (startAudioClassification()) {
                btnStart.setText("Detener");
            }
        }
    }

    private void checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
        } else {
            initializeAudioClassifier();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeAudioClassifier();
            } else {
                updateUI("Permiso de micrófono denegado");
                showToast("Se necesita permiso de micrófono");
            }
        }
    }

    private void initializeAudioClassifier() {
        try {
            // Configuración del modelo con las últimas especificaciones
            AudioClassifier.AudioClassifierOptions options =
                    AudioClassifier.AudioClassifierOptions.builder()
                            .setMaxResults(1)  // Solo necesitamos el resultado principal
                            .setScoreThreshold(0.3f)  // Umbral mínimo de confianza
                            .build();

            classifier = AudioClassifier.createFromFileAndOptions(
                    this,
                    "sound_classifier_wd_metadata.tflite",
                    options
            );

            tensorAudio = classifier.createInputTensorAudio();
            isModelLoaded.set(true);
            Log.i(TAG, "Modelo TFLite cargado exitosamente");
        } catch (IOException e) {
            handleError("Error al cargar modelo", "Error cargando modelo TFLite", e);
        } catch (IllegalArgumentException e) {
            handleError("Modelo incompatible", "Versión de modelo no soportada", e);
        } catch (Exception e) {
            handleError("Error inicializando", "Error inesperado al inicializar", e);
        }
    }

    private boolean startAudioClassification() {
        try {
            record = classifier.createAudioRecord();
            record.startRecording();
            isListening.set(true);

            executorService = Executors.newSingleThreadExecutor();
            executorService.execute(this::classificationLoop);

            return true;
        } catch (IllegalStateException e) {
            handleError("Error grabación", "No se pudo iniciar la grabación", e);
            return false;
        } catch (Exception e) {
            handleError("Error inesperado", "Error al iniciar reconocimiento", e);
            return false;
        }
    }

    private void classificationLoop() {
        while (isListening.get()) {
            try {
                tensorAudio.load(record);
                List<Classifications> output = classifier.classify(tensorAudio);

                if (output == null || output.isEmpty()) {
                    Log.w(TAG, "Clasificación devolvió resultados vacíos");
                    continue;
                }

                Classifications classifications = output.get(0);
                if (classifications.getCategories().isEmpty()) {
                    Log.w(TAG, "No se encontraron categorías en la clasificación");
                    continue;
                }

                String label = classifications.getCategories().get(0).getLabel();
                float score = classifications.getCategories().get(0).getScore();

                updateClassificationResult(label, score);

            } catch (IllegalStateException e) {
                handleClassificationError("Error clasificando", e);
                break;
            } catch (Exception e) {
                handleClassificationError("Error procesando", e);
                break;
            }
        }
    }

    private void updateClassificationResult(String label, float score) {
        runOnUiThread(() -> {
            String resultText = score < 0.5f ?
                    "Neutral (" + (int)(score * 100) + "%)" :
                    label + " (" + (int)(score * 100) + "%)";
            txtPrediction.setText(resultText);
        });
    }

    private void stopAudioClassification() {
        isListening.set(false);

        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }

        if (record != null) {
            try {
                record.stop();
                record.release();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error al detener la grabación", e);
            } finally {
                record = null;
            }
        }
    }

    private void handleError(String uiMessage, String logMessage, Exception e) {
        Log.e(TAG, logMessage, e);
        runOnUiThread(() -> {
            txtPrediction.setText(uiMessage);
            showToast(logMessage);
        });
        isModelLoaded.set(false);
    }

    private void handleClassificationError(String message, Exception e) {
        Log.e(TAG, message, e);
        runOnUiThread(() -> txtPrediction.setText(message));
    }

    private void updateUI(String message) {
        runOnUiThread(() -> txtPrediction.setText(message));
    }

    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAudioClassification();

        if (classifier != null) {
            try {
                classifier.close();
            } catch (Exception e) {
                Log.w(TAG, "Error al cerrar el clasificador", e);
            }
        }
    }
}