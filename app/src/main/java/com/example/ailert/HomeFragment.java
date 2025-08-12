package com.example.ailert;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.tensorflow.lite.support.audio.TensorAudio;
import org.tensorflow.lite.task.audio.classifier.AudioClassifier;
import org.tensorflow.lite.task.audio.classifier.Classifications;

import android.media.AudioRecord;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class HomeFragment extends Fragment {

    private static final int REQUEST_PERMISSIONS_CODE = 100;
    private Button startServiceButton;
    private TextView detectionStatusText;
    private boolean serviceRunning = false;

    private final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECORD_AUDIO,
    };

    private SharedPreferences preferences;
    private String emergencyContactNumber = "";
    private String emergencyMessage = "";
    private FusedLocationProviderClient fusedLocationClient;
    private BroadcastReceiver smsSentReceiver;

    // Variables para el modelo de audio
    private AudioClassifier classifier;
    private TensorAudio tensorAudio;
    private AudioRecord record;
    private ExecutorService executorService;
    private final AtomicBoolean isListening = new AtomicBoolean(false);
    private final AtomicBoolean isModelLoaded = new AtomicBoolean(false);
    private Handler handler = new Handler();
    private Runnable classificationTask;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        preferences = requireActivity().getSharedPreferences("AIlertPrefs", Context.MODE_PRIVATE);

        startServiceButton = view.findViewById(R.id.startServiceButton);
        startServiceButton.setBackgroundTintList(null);
        Button requestPermissionsButton = view.findViewById(R.id.requestPermissionsButton);
        detectionStatusText = view.findViewById(R.id.detection_status);


        serviceRunning = preferences.getBoolean("service_running", false);

        startServiceButton.setOnClickListener(v -> toggleService());
        requestPermissionsButton.setOnClickListener(v -> requestPermissions());

        if (hasAllPermissions()) {
            requestPermissionsButton.setVisibility(View.GONE);
        }

        updateUI();
        return view;
    }

    private void toggleService() {
        if (!serviceRunning) {
            if (hasAllPermissions()) {
                loadEmergencyData();
                if (validateEmergencyData()) {
                    initializeAudioClassifier();
                    startAIlertService();
                    startAudioClassification();
                }
            } else {
                showToast("Concede todos los permisos primero");
                requestPermissions();
            }
        } else {
            stopAIlertService();
            stopAudioClassification();
        }
    }

    private void sendTestMessage() {
        if (!hasSmsPermission()) {
            showToast("Se necesita permiso para enviar SMS");
            requestPermissions();
            return;
        }

        loadEmergencyData();
        if (emergencyContactNumber.isEmpty()) {
            showToast("Configura un contacto de emergencia primero");
            return;
        }

        String testMessage = preferences.getString("mensaje_base", "¡Ayuda! Esta es una emergencia.");;
        sendSMS(emergencyContactNumber, testMessage);
    }

    private void initializeAudioClassifier() {
        try {
            AudioClassifier.AudioClassifierOptions options =
                    AudioClassifier.AudioClassifierOptions.builder()
                            .setMaxResults(1)
                            .setScoreThreshold(0.3f)
                            .build();

            classifier = AudioClassifier.createFromFileAndOptions(
                    requireContext(),
                    "sound_classifier_wd_metadata.tflite",
                    options
            );

            tensorAudio = classifier.createInputTensorAudio();
            isModelLoaded.set(true);
            Log.i("AIlert", "Modelo TFLite cargado exitosamente");
        } catch (IOException e) {
            Log.e("AIlert", "Error cargando modelo", e);
            isModelLoaded.set(false);
            showToast("Error cargando modelo de audio");
        }
    }

    private void startAudioClassification() {
        if (!isModelLoaded.get()) {
            showToast("Modelo no cargado");
            return;
        }

        try {
            record = classifier.createAudioRecord();
            record.startRecording();
            isListening.set(true);

            executorService = Executors.newSingleThreadExecutor();

            classificationTask = new Runnable() {
                @Override
                public void run() {
                    if (isListening.get()) {
                        executorService.execute(() -> classificationLoop());
                        handler.postDelayed(this, 2000); // cada 2 segundos
                    }
                }
            };
            handler.post(classificationTask);

        } catch (Exception e) {
            Log.e("AIlert", "Error iniciando clasificación", e);
            showToast("Error iniciando detector de sonidos");
        }
    }

    private void classificationLoop() {
        try {
            tensorAudio.load(record);
            List<Classifications> output = classifier.classify(tensorAudio);

            if (output != null && !output.isEmpty()) {
                Classifications classifications = output.get(0);
                if (!classifications.getCategories().isEmpty()) {
                    String label = classifications.getCategories().get(0).getLabel();
                    float score = classifications.getCategories().get(0).getScore();

                    requireActivity().runOnUiThread(() -> {
                        detectionStatusText.setText(label + " (" + (int) (score * 100) + "%)");
                    });

                    if (isDangerousSound(label) && score > 0.6f) {
                        String porcentaje = String.format("%.1f", score * 100);
                        Log.d("AIlert", "Sonido peligroso detectado: " + label + " con " + porcentaje + "% de certeza");
                        sendTestMessage();
                    }

                }
            }
        } catch (Exception e) {
            Log.e("AIlert", "Error en clasificación", e);
        }
    }

    private boolean isDangerousSound(String soundLabel) {
        switch(soundLabel) {
            case "gun_shot":
            case "screams":
            case "glass_breaking":
            case "crackling_fire":
            case "siren":
                return true; // Siempre retorna true para sonidos peligrosos
            default:
                return false;
        }
    }

    private void stopAudioClassification() {
        isListening.set(false);
        handler.removeCallbacks(classificationTask);

        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }

        if (record != null) {
            try {
                record.stop();
                record.release();
            } catch (IllegalStateException e) {
                Log.e("AIlert", "Error al detener grabación", e);
            }
            record = null;
        }
    }

    private boolean validateEmergencyData() {
        if (emergencyContactNumber.isEmpty() || emergencyMessage.isEmpty()) {
            showToast("Configura contacto y mensaje primero");
            return false;
        }
        return true;
    }

    private void sendEmergencyAlert(String detectedSound) {
        if (!hasSmsPermission()) {
            showToast("Se necesita permiso para enviar SMS");
            requestPermissions();
            return;
        }

        loadEmergencyData();

        String motivo;
        if (detectedSound.contains("gun_shot")) {
            motivo = "Posible sonido de disparos detectado.";
        } else if (detectedSound.contains("screams")) {
            motivo = "Posibles gritos de auxilio detectados.";
        } else if (detectedSound.contains("glass_breaking")) {
            motivo = "Posible rotura de vidrio detectada.";
        } else if (detectedSound.contains("crackling_fire")) {
            motivo = "Posible sonido de fuego detectado.";
        } else if (detectedSound.contains("siren")) {
            motivo = "Posibles sirenas de emergencia detectadas.";
        } else {
            motivo = "Alerta de seguridad activada.";
        }

        motivo += "\nDetectado: " + detectedSound;

        String mensajeBase = preferences.getString("mensaje_base", "¡Ayuda! Esta es una emergencia.");
        String mensajeFinal = mensajeBase + "\n\n" + motivo;

        if (hasLocationPermission()) {
            try {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnCompleteListener(task -> {
                            String mensajeConUbicacion = mensajeFinal;
                            if (task.isSuccessful() && task.getResult() != null) {
                                Location location = task.getResult();
                                String locationUrl = "http://maps.google.com/maps?q=" + location.getLatitude() + "," + location.getLongitude();
                                mensajeConUbicacion += "\nUbicación aproximada: " + locationUrl;
                            }
                            sendSMS(emergencyContactNumber, mensajeConUbicacion);
                        });
            } catch (SecurityException se) {
                sendSMS(emergencyContactNumber, mensajeFinal);
            }
        } else {
            sendSMS(emergencyContactNumber, mensajeFinal);
        }
    }

    private void sendSMS(String phoneNumber, String message) {
        if (phoneNumber == null || phoneNumber.isEmpty() || message == null || message.isEmpty()) {
            Log.e("AIlert", "Número de teléfono o mensaje inválido.");
            showToast("Error: Revisa el contacto de emergencia.");
            return;
        }

        try {
            SmsManager smsManager = SmsManager.getDefault();
            PendingIntent sentPI = PendingIntent.getBroadcast(requireContext(), 0, new Intent("SMS_SENT"), PendingIntent.FLAG_IMMUTABLE);
            smsManager.sendTextMessage(phoneNumber, null, message, sentPI, null);

            Log.d("AIlert", "Intento de envío de SMS a " + phoneNumber);
            showToast("Mensaje enviado.");

        } catch (Exception e) {
            Log.e("AIlert", "Error al enviar SMS", e);
            showToast("Error al enviar el mensaje.");
        }
    }

    private void loadEmergencyData() {
        emergencyContactNumber = preferences.getString("contacto_numero", "").replaceAll("[^0-9+]", "");
        emergencyMessage = preferences.getString("mensaje_base", "¡Ayuda! Esta es una emergencia.");
        Log.d("AIlert", "Datos cargados - Tel: " + emergencyContactNumber + ", Msg: " + emergencyMessage);
    }

    private boolean hasSmsPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(requireActivity(), REQUIRED_PERMISSIONS, REQUEST_PERMISSIONS_CODE);
    }

    private boolean hasAllPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void startAIlertService() {
        Intent intent = new Intent(requireContext(), AIlertService.class);
        requireContext().startForegroundService(intent);
        serviceRunning = true;
        preferences.edit().putBoolean("service_running", true).apply();
        updateUI();
        showToast("Servicio iniciado");
    }

    private void stopAIlertService() {
        requireContext().stopService(new Intent(requireContext(), AIlertService.class));
        serviceRunning = false;
        preferences.edit().putBoolean("service_running", false).apply();
        updateUI();
        showToast("Servicio detenido");
    }

    private void updateUI() {
        if (serviceRunning) {
            startServiceButton.setBackgroundTintList(null);
            Drawable drawable = ContextCompat.getDrawable(requireContext(), R.drawable.on);
            startServiceButton.setBackground(drawable);
            int color = ContextCompat.getColor(requireContext(), R.color.green);
            startServiceButton.setTextColor(color);
            detectionStatusText.setText("Estado: Activo");
        } else {
            startServiceButton.setBackgroundTintList(null);
            Drawable drawable = ContextCompat.getDrawable(requireContext(), R.drawable.off);
            startServiceButton.setBackground(drawable);
            int color = ContextCompat.getColor(requireContext(), R.color.red);
            startServiceButton.setTextColor(color);
            detectionStatusText.setText("Estado: Inactivo");
        }
    }

    private void showToast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS_CODE && !hasAllPermissions()) {
            showToast("Algunos permisos fueron denegados");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (smsSentReceiver != null) {
            try {
                requireContext().unregisterReceiver(smsSentReceiver);
            } catch (Exception e) {
                Log.e("AIlert", "Error al desregistrar receiver", e);
            }
        }
    }
}
