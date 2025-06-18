package com.example.botonapplication.mqtt;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.icu.text.SimpleDateFormat;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.botonapplication.MainActivity;
import com.example.botonapplication.R;
import com.example.botonapplication.utils.HistoryManager;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.Locale;

public class MqttService extends Service implements MqttCallback {
    private static final String TAG = "MQTT_Service";
    private MqttHandler mqttHandler;
    private static final String CHANNEL_ID = "mqtt_service_channel";
    private static final int NOTIFICATION_ID = 1; // ID único para la notificación

    private String lastAlarmStatus = "INACTIVO"; // Variable para estado dinámico
    private String lastUpdateTime = ""; // Variable para estado dinámico

    private static final float UMBRAL_AGITACION = 30.0f;
    private static final long DEBOUNCE_TIME_MS = 2000; // 2 segundos entre activaciones
    private long lastShakeTime = 0;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private SensorEventListener sensorListener;

    private boolean isFirstMessage = true;
    private HistoryManager historyManager;


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service creado");
        createNotificationChannel();
        mqttHandler = new MqttHandler(this);
        setupShakeSensor();
        historyManager = new HistoryManager(this);
        isFirstMessage = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 1. Crear notificación foreground primero (prioridad crítica)
        Notification notification = buildNotification();
        startForeground(NOTIFICATION_ID, notification);
        Log.d(TAG, "Servicio en primer plano");

        // 2. Manejar conexión MQTT
        handleMqttConnection();

        // 3. Procesar intent de publicación (si existe)
        if (intent != null && "PUBLISH_MQTT_MSG".equals(intent.getAction())) {
            handlePublishIntent(intent);
        }

        return START_STICKY;
    }
    private Notification buildNotification() {
        // Intent para abrir MainActivity
        Intent openAppIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                openAppIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Estado Alarma: " + lastAlarmStatus)
                .setContentText("Últ. actualización: " + lastUpdateTime)
                .setSmallIcon(R.drawable.ic_notification_mqtt)
                .setContentIntent(pendingIntent) // Abre MainActivity al tocar
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }


    private void handleMqttConnection() {
        if (!mqttHandler.isConnected()) {
            Log.d(TAG, "Intentando conectar MQTT...");
            mqttHandler.connect();
        } else {
            Log.d(TAG, "MQTT ya conectado");
        }
    }

    private void handlePublishIntent(Intent intent) {
        String topic = intent.getStringExtra("topic");
        String message = intent.getStringExtra("message");
        if (topic != null && message != null) {
            Log.d(TAG, "Publicando mensaje. Topic: " + topic);
            mqttHandler.publish(topic, message);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Canal MQTT Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Canal para el servicio MQTT en segundo plano");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
            Log.d(TAG, "Canal de notificación creado");
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    @Override
    public void onDestroy() {

        mqttHandler.disconnect();
        if (sensorManager != null) {
            sensorManager.unregisterListener(sensorListener);
        }
        Log.d(TAG, "Service destruido");
        super.onDestroy();
    }

    private void updateNotification() {

        String statusWithPrefix = "El nivel de peligro es " + lastAlarmStatus;

        // Guardar estado para que MainActivity lo recupere al reiniciarse
        SharedPreferences prefs = getSharedPreferences("AppState", MODE_PRIVATE);
        prefs.edit()
                .putString("lastAlarmStatus", statusWithPrefix)
                .putString("lastUpdateTime", lastUpdateTime)
                .apply();


        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.notify(NOTIFICATION_ID, buildNotification());
    }

    private void setupShakeSensor() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        sensorListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];
                float acceleration = (float) Math.sqrt(x * x + y * y + z * z);

                if (acceleration > UMBRAL_AGITACION && System.currentTimeMillis() - lastShakeTime > DEBOUNCE_TIME_MS) {
                    lastShakeTime = System.currentTimeMillis();
                    triggerAlarmaPorAgitacion();
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                // No necesario, pero obligatorio implementar
            }
        };
        sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_UI);
    }

    private void triggerAlarmaPorAgitacion() {
        try {
            // publicar en MQTT (igual que el botón manual)
            JSONObject json = new JSONObject();
            json.put("value", 1.0);
            mqttHandler.publish(ConfigMQTT.TOPIC_ALARMA_UBIDOTS, json.toString());


            // para que la UX del boton se actualiza igual que manual
            Intent intent = new Intent("SHAKE_DETECTED")
                    .setPackage(getPackageName())
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            sendBroadcast(intent);


            Log.d(TAG, "Alarma activada por agitación");
            showShakeNotification();


        } catch (JSONException e) {
            Log.e(TAG, "Error al crear JSON para agitación", e);
        }
    }
    private void showShakeNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("¡Alarma activada!")
                .setContentText("Se detectó agitación del dispositivo")
                .setSmallIcon(R.drawable.ic_notification_mqtt)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID + 1, builder.build()); // ID diferente al foreground
    }


    private Location getLastKnownLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PERMISSION_GRANTED) {
            return null;
        }

        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        Location bestLocation = null;

        // Obtiene ubicación de diferentes proveedores
        for (String provider : locationManager.getAllProviders()) {
            Location location = locationManager.getLastKnownLocation(provider);
            if (location != null && (bestLocation == null ||
                    location.getAccuracy() < bestLocation.getAccuracy())) {
                bestLocation = location;
            }
        }
        return bestLocation;
    }


    // ----- Metodos de la interfaz MqttCallback -----
    @Override
    public void connectionLost(Throwable cause) {
        Log.w(TAG, "Conexión perdida: " + cause.getMessage());

        // Actualizar notificación (opcional)
        Notification notification = buildNotification();
        startForeground(NOTIFICATION_ID, notification);

        // Iniciar reconexión automática
        mqttHandler.scheduleReconnect();
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());
        Log.d(TAG, "Mensaje recibido. Topic: " + topic + " | Payload: " + payload);

        try {
            JSONObject json = new JSONObject(payload);
            double value = json.getDouble("value");

            if (ConfigMQTT.TOPIC_NIVEL_ALARMA_UBIDOTS.equals(topic)) {
                //Lógica Para niveles de alarma
                lastAlarmStatus = (value == 0) ? "BAJO" : (value == 1) ? "MEDIO" : "ALTO";

                if (isFirstMessage) {
                    isFirstMessage = false;
                    return;
                }

                Location location = getLastKnownLocation();
                historyManager.addEntry(this, lastAlarmStatus,
                        location != null ? location.getLatitude() : 0.0,
                        location != null ? location.getLongitude() : 0.0
                );

            }
            else if (ConfigMQTT.TOPIC_ALARMA_UBIDOTS.equals(topic) && value == 0.0) {
                //Manejar timeout
                lastAlarmStatus = "INACTIVO (timeout)";
            }

            updateNotification();


        } catch (JSONException e) {
            Log.e(TAG, "Error parseando JSON", e);
        }

        // Enviar broadcast a MainActivity
        Intent intent = new Intent("MQTT_MSG_RECEIVED")
                .addCategory(Intent.CATEGORY_DEFAULT)
                .setPackage(getPackageName())
                .putExtra("topic", topic)
                .putExtra("message", payload)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND); // PRIORIDAD ALTA
        sendBroadcast(intent);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        Log.d(TAG, "Mensaje entregado al broker");
    }
}