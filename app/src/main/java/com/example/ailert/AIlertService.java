package com.example.ailert;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class AIlertService extends Service {

    public static final String CHANNEL_ID = "AIlertServiceChannel";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("AIlert Activo")
                .setContentText("Monitoreando sonidos y listo para enviar alertas")
                .setSmallIcon(R.drawable.ic_shield)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();

        startForeground(1, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Aquí podrías iniciar tareas como sensores o reconocimiento de sonido
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Reinicia el servicio si se detiene inesperadamente
        Intent restartServiceIntent = new Intent(getApplicationContext(), AIlertService.class);
        startForegroundService(restartServiceIntent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "AIlert Servicio en segundo plano",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
