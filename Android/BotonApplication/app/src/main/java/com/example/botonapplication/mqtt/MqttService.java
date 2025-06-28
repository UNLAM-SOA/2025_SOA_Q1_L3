package com.example.botonapplication.mqtt;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.icu.text.SimpleDateFormat;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.botonapplication.MainActivity;
import com.example.botonapplication.R;
import com.example.botonapplication.utils.HistoryManager;
import com.example.botonapplication.utils.MailSender;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MqttService extends Service implements MqttCallback {
    private static final String TAG = "MQTT_Service";
    private static final String CHANNEL_ID = "mqtt_service_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final int SHAKE_NOTIFICATION_ID = 2;
    private static final float SHAKE_THRESHOLD = 30.0f;
    private static final long SHAKE_DEBOUNCE_MS = 2000;
    private static final String ACTION_PUBLISH = "PUBLISH_MQTT_MSG";
    private static final String PREFS_NAME = "AppState";
    private static final String INTENT_SHAKE = "SHAKE_DETECTED";
    private static final String INTENT_MQTT_RECEIVED = "MQTT_MSG_RECEIVED";
    private static final String LOCATION_UNAVAILABLE = "Ubicacion no disponible";
    private static final String UNKNOWN_LOCATION = "Ubicacion desconocida";

    private MqttHandler mqttHandler;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private SensorEventListener sensorListener;
    private HistoryManager historyManager;

    private String lastAlarmStatus = "INACTIVO";
    private String lastUpdateTime = "";
    private String lastMailStatus = "INACTIVO";

    private boolean isFirstMessage = true;
    private boolean wasInConsultor = false;
    private long lastShakeTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service creado");
        createNotificationChannel();
        mqttHandler = new MqttHandler(this);
        historyManager = new HistoryManager(this);
        setupShakeSensor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        Log.d(TAG, "Servicio en primer plano");
        handleMqttConnection();
        if (intent != null && ACTION_PUBLISH.equals(intent.getAction())) {
            handlePublishIntent(intent);
        }
        return START_STICKY;
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

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Canal MQTT Service", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Canal para el servicio MQTT en segundo plano");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Estado Alarma: " + lastAlarmStatus)
                .setContentText("Últ. actualización: " + lastUpdateTime)
                .setSmallIcon(R.drawable.ic_notification_mqtt)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .build();
    }

    private void handleMqttConnection() {
        if (!mqttHandler.isConnected()) {
            mqttHandler.connect();
        }
    }

    private void handlePublishIntent(Intent intent) {
        String topic = intent.getStringExtra("topic");
        String message = intent.getStringExtra("message");
        if (topic != null && message != null) {
            mqttHandler.publish(topic, message);
        }
    }

    private void updateNotification() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putString("lastAlarmStatus", "El nivel de peligro es " + lastAlarmStatus)
                .putString("lastUpdateTime", lastUpdateTime)
                .apply();

        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, buildNotification());
    }

    private void setupShakeSensor() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        if (accelerometer == null) {
            Log.e(TAG, "No se encontró sensor acelerómetro!");
            return;
        }

        sensorListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                float acceleration = (float) Math.sqrt(
                        event.values[0] * event.values[0] +
                                event.values[1] * event.values[1] +
                                event.values[2] * event.values[2]);

                if (acceleration > SHAKE_THRESHOLD &&
                        System.currentTimeMillis() - lastShakeTime > SHAKE_DEBOUNCE_MS) {
                    lastShakeTime = System.currentTimeMillis();
                    triggerAlarmaPorAgitacion();
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };

        sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_UI);
    }

    private Location getLastKnownLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PERMISSION_GRANTED) return null;

        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        Location bestLocation = null;

        for (String provider : locationManager.getAllProviders()) {
            Location location = locationManager.getLastKnownLocation(provider);
            if (location != null && (bestLocation == null ||
                    location.getAccuracy() < bestLocation.getAccuracy())) {
                bestLocation = location;
            }
        }
        return bestLocation;
    }

    private void triggerAlarmaPorAgitacion() {
        try {
            JSONObject json = new JSONObject();
            json.put("value", 1.0);
            mqttHandler.publish(ConfigMQTT.TOPIC_ALARMA_UBIDOTS, json.toString());

            sendBroadcast(new Intent(INTENT_SHAKE)
                    .setPackage(getPackageName())
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND));

            showShakeNotification();

            boolean enviarMail = lastAlarmStatus.contains("INACTIVO") ||
                    (lastAlarmStatus.equals("CONSULTOR") && (wasInConsultor = true)) ||
                    (wasInConsultor && !(wasInConsultor = false));

            if (enviarMail && !lastAlarmStatus.equals(lastMailStatus)) {
                JSONObject lastEntry = historyManager.getLastEntry(this);
                if (lastEntry != null) {
                    enviarMailDeEmergencia(
                            lastEntry.getString("status"),
                            lastEntry.getDouble("lat"),
                            lastEntry.getDouble("lon"),
                            lastEntry.optBoolean("location_available", false));
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error al crear JSON para agitación", e);
        }
    }

    private void enviarMailDeEmergencia(String status, double lat, double lon, boolean tieneUbicacion) {
        String timestamp = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(new Date());
        if (tieneUbicacion) {
            getCityFromCoordinatesAsync(lat, lon, location ->
                    enviarMailConUbicacion(timestamp, status, lat, lon, location));
        } else {
            enviarMailConUbicacion(timestamp, status, lat, lon, LOCATION_UNAVAILABLE);
        }
    }

    private void enviarMailConUbicacion(String timestamp, String status, double lat, double lon, String location) {
        String mapsLink = (lat == 0.0 && lon == 0.0) ?
                "Sin coordenadas" : String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", lat, lon);

        String cuerpo = String.format(Locale.US,
                "\u23F0 [%s]\n\uD83D\uDEA8 Estado: %s\n\uD83D\uDCCD %s\n\uD83C\uDF10 %s",
                timestamp, status, location, mapsLink);

        String asunto = "\uD83D\uDCE9 ALERTA AUTOMÁTICA - " + timestamp;

        MailSender.sendEmail("tomasbeta@outlook.com", asunto, cuerpo, new MailSender.EmailCallback() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "Correo de emergencia enviado correctamente.");
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error al enviar correo: " + error);
            }
        });
    }

    private void getCityFromCoordinatesAsync(double lat, double lon, GeocodeCallback callback) {
        new Thread(() -> {
            String result = UNKNOWN_LOCATION;
            try {
                List<Address> addresses = new Geocoder(this, Locale.getDefault()).getFromLocation(lat, lon, 1);
                if (!addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    StringBuilder location = new StringBuilder();
                    if (address.getLocality() != null) location.append(address.getLocality());
                    if (address.getSubAdminArea() != null) {
                        if (location.length() > 0) location.append(", ");
                        location.append(address.getSubAdminArea());
                    }
                    result = location.toString();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error obteniendo ciudad", e);
            }
            final String finalResult = result;
            new android.os.Handler(getMainLooper()).post(() -> callback.onResult(finalResult));
        }).start();
    }

    private void showShakeNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("¡Alarma activada!")
                .setContentText("Se detectó agitación del dispositivo")
                .setSmallIcon(R.drawable.ic_notification_mqtt)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(SHAKE_NOTIFICATION_ID, builder.build());
    }

    @Override
    public void connectionLost(Throwable cause) {
        Log.w(TAG, "Conexión perdida: " + cause.getMessage());
        startForeground(NOTIFICATION_ID, buildNotification());
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
                lastAlarmStatus = (value == 0) ? "BAJO" : (value == 1) ? "MEDIO" : "ALTO";

                if (isFirstMessage) {
                    isFirstMessage = false;
                    return;
                }

                Location location = getLastKnownLocation();
                boolean hasLocation = (location != null && location.getLatitude() != 0.0 && location.getLongitude() != 0.0);
                historyManager.addEntry(this, lastAlarmStatus,
                        location != null ? location.getLatitude() : 0.0,
                        location != null ? location.getLongitude() : 0.0,
                        hasLocation);

            } else if (ConfigMQTT.TOPIC_ALARMA_UBIDOTS.equals(topic) && value == 0.0) {
                lastAlarmStatus = "INACTIVO (timeout)";
            }

            updateNotification();

        } catch (JSONException e) {
            Log.e(TAG, "Error parseando JSON", e);
        }

        sendBroadcast(new Intent(INTENT_MQTT_RECEIVED)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .setPackage(getPackageName())
                .putExtra("topic", topic)
                .putExtra("message", payload)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND));
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        Log.d(TAG, "Mensaje entregado al broker");
    }

    public interface GeocodeCallback {
        void onResult(String location);
    }
}
