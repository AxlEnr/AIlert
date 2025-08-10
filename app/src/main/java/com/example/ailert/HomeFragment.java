package com.example.ailert;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
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

        if(hasAllPermissions()){
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
                    startAIlertService();
                    sendEmergencyAlert();
                }
            } else {
                showToast("Concede todos los permisos primero");
                requestPermissions();
            }
        } else {
            stopAIlertService();
        }
    }

    private void loadEmergencyData() {
        emergencyContactNumber = preferences.getString("contacto_numero", "").replaceAll("[^0-9]", "");
        emergencyMessage = preferences.getString("mensaje_emergencia", "");
    }

    private boolean validateEmergencyData() {
        if (emergencyContactNumber.isEmpty() || emergencyMessage.isEmpty()) {
            showToast("Configura contacto y mensaje primero");
            return false;
        }
        return true;
    }

    private void sendEmergencyAlert() {
        if (!hasSmsPermission()) {
            showToast("Se necesita permiso para enviar SMS");
            requestPermissions();
            return;
        }
        sendSMS(emergencyContactNumber, emergencyMessage);
        if (hasLocationPermission()) {
            try {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener(location -> {
                            if (location != null) {
                                String locationUrl = "https://maps.google.com/?q=" + location.getLatitude() + "," + location.getLongitude();
                                sendSMS(emergencyContactNumber, emergencyMessage + "\nUbicación: " + locationUrl);
                            } else {
                                sendSMS(emergencyContactNumber, emergencyMessage);
                            }
                        })
                        .addOnFailureListener(e -> sendSMS(emergencyContactNumber, emergencyMessage));
            } catch (SecurityException se) {
                sendSMS(emergencyContactNumber, emergencyMessage);
            }
        } else {
            sendSMS(emergencyContactNumber, emergencyMessage);
        }

    }

    private void sendSMS(String phoneNumber, String message) {
        try {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                String SENT = "SMS_SENT";
                PendingIntent sentPI = PendingIntent.getBroadcast(requireContext(), 0, new Intent(SENT), PendingIntent.FLAG_IMMUTABLE);

                requireContext().registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (getResultCode() == Activity.RESULT_OK) {
                            Log.d("SMS_STATUS", "SMS enviado correctamente");
                        } else {
                            Log.e("SMS_STATUS", "Error al enviar SMS");
                        }
                    }
                }, new IntentFilter(SENT), Context.RECEIVER_NOT_EXPORTED);

                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(phoneNumber, null, message, sentPI, null);
                showToast("Mensaje de emergencia enviado");
            }
        } catch (Exception e) {
            Log.e("SMS_ERROR", "Error enviando SMS", e);
            showToast("Error al enviar SMS");
        }
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
}
