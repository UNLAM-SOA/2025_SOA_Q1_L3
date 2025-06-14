package com.example.botonapplication.mqtt;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.botonapplication.R;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class MqttService extends Service implements MqttCallback {
    private static final String TAG = "MQTT_Service";
    private MqttHandler mqttHandler;
    private static final String CHANNEL_ID = "mqtt_service_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service creado");
        createNotificationChannel();
        mqttHandler = new MqttHandler(this); // Pasamos 'this' como Context
    }

    /*@Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Notificación para Foreground Service
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Monitoreo MQTT")
                .setContentText("Escuchando alertas del circuito")
                .setSmallIcon(R.drawable.ic_notification_mqtt) // Usa el ícono que creaste
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(1, notification);
        mqttHandler.connect();

        return START_STICKY;
    }*/

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 1. Iniciar conexión MQTT (si no está conectado)

        Log.d(TAG, "Estoy en onStartCommand");
        if (!mqttHandler.isConnected()) {
            mqttHandler.connect();

        }

        // 2. Procesar Intents de publicación (si los hay)
        if (intent != null && "PUBLISH_MQTT_MSG".equals(intent.getAction())) {
            String topic = intent.getStringExtra("topic");
            String message = intent.getStringExtra("message");
            if (topic != null && message != null) {
                mqttHandler.publish(topic, message);
            }
        }

        // Notificación para Foreground Service
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Monitoreo MQTT")
                .setSmallIcon(R.drawable.ic_notification_mqtt)
                .build();
        startForeground(1, notification);

        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Canal MQTT Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ----- Métodos de MqttCallback -----
    @Override
    public void connectionLost(Throwable cause) {
        Log.w(TAG, "Conexión MQTT perdida: " + cause.getMessage());
        // Reconexión automática podría ir aquí
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());


        // Enviar broadcast a MainActivity (si está activa)
        Intent intent = new Intent("MQTT_MSG_RECEIVED");
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setPackage(getPackageName()); //ESTA LINEA HIZO QUE FUNCIONASE EL BROADCAST!!!
        intent.putExtra("topic", topic);
        intent.putExtra("message", payload);
        sendBroadcast(intent);
        Log.d(TAG, "Broadcast enviado. Acción: " + intent.getAction() + " | Categoría: " + intent.getCategories());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        Log.d(TAG, "Mensaje entregado al broker");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mqttHandler.disconnect();
        Log.d(TAG, "Service destruido");
    }
}