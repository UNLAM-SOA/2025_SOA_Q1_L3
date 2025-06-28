package com.example.botonapplication;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.botonapplication.mqtt.ConfigMQTT;
import com.example.botonapplication.mqtt.MqttService;

import org.eclipse.paho.android.service.BuildConfig;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "DevelopMain";

    // Constantes para permisos y delays
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final long BUTTON_STATE_DELAY_MS = 2000;

    // Estados posibles del botón
    private static final String BUTTON_TEXT_ACTIVAR = "ACTIVAR MONITOREO";
    private static final String BUTTON_TEXT_MONITOREANDO = "MONITOREANDO...";
    private static final String BUTTON_TEXT_MODO_CONSULTOR = "MODO CONSULTOR";
    private static final String BUTTON_TEXT_REACTIVAR = "REACTIVAR";

    // Estados del nivel de alarma
    private static final String ALARM_STATUS_BAJO = "BAJO";
    private static final String ALARM_STATUS_MEDIO = "MEDIO";
    private static final String ALARM_STATUS_ALTO = "ALTO";
    private static final String ALARM_STATUS_DESCONOCIDO = "DESCONOCIDO";

    // SharedPreferences keys
    private static final String PREFS_APP_STATE = "AppState";
    private static final String PREF_KEY_LAST_BUTTON_TEXT = "lastButtonText";
    private static final String PREF_KEY_LAST_ALARM_STATUS = "lastAlarmStatus";
    private static final String PREF_KEY_LAST_UPDATE_TIME = "lastUpdateTime";

    private TextView tvEstadoAlarma;
    private Button btnAccion;
    private Button btnHistory;

    private BroadcastReceiver mqttReceiver;
    private BroadcastReceiver shakeReceiver;

    private boolean isFirstMessage = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initLogging();
        initUIComponents();
        setupMqttReceiver();
        loadLastState();
        checkLocationPermissions();
    }

    /**
     * Verifica y solicita permiso de ubicación si es necesario.
     */
    private void checkLocationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        LOCATION_PERMISSION_REQUEST_CODE
                );
            } else {
                initServices();
            }
        } else {
            initServices();
        }
    }

    private void initLogging() {
        Log.d(TAG, "Actividad creada");
        Log.d(TAG, "Versión de la app: " + BuildConfig.VERSION_NAME);
    }

    /**
     * Inicia el servicio MQTT en primer plano o en background según versión Android.
     */
    private void initServices() {
        Intent serviceIntent = new Intent(this, MqttService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
            Log.d(TAG, "Servicio iniciado en primer plano (Android Oreo+)");
        } else {
            startService(serviceIntent);
            Log.d(TAG, "Servicio iniciado (Pre-Oreo)");
        }
    }

    /**
     * Inicializa componentes UI y asigna listeners.
     */
    private void initUIComponents() {
        tvEstadoAlarma = findViewById(R.id.tvEstadoAlarma);
        btnAccion = findViewById(R.id.btnActivar);
        btnHistory = findViewById(R.id.btnHistory);

        btnAccion.setOnClickListener(v -> handleButtonAction());
        updateButtonState(BUTTON_TEXT_ACTIVAR, true);

        btnHistory.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));

        Log.d(TAG, "Componentes UI inicializados");
    }

    /**
     * Carga el último estado guardado en SharedPreferences y actualiza UI.
     */
    private void loadLastState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_APP_STATE, MODE_PRIVATE);
        String status = prefs.getString(PREF_KEY_LAST_ALARM_STATUS, "INACTIVO");
        String time = prefs.getString(PREF_KEY_LAST_UPDATE_TIME, "");

        runOnUiThread(() -> {
            tvEstadoAlarma.setText("Estado: " + status);
            if (status.contains(ALARM_STATUS_BAJO) || status.contains(ALARM_STATUS_MEDIO) || status.contains(ALARM_STATUS_ALTO)) {
                updateButtonState(BUTTON_TEXT_MODO_CONSULTOR, true);
            } else {
                updateButtonState(BUTTON_TEXT_ACTIVAR, true);
            }
        });
    }

    /**
     * Configura los BroadcastReceivers para MQTT y eventos de shake.
     */
    private void setupMqttReceiver() {
        mqttReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                logIncomingMessage(intent);

                if (shouldIgnoreMessage(intent)) return;

                processMqttMessage(
                        intent.getStringExtra("topic"),
                        intent.getStringExtra("message")
                );
            }
        };

        shakeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("SHAKE_DETECTED".equals(intent.getAction())) {
                    Log.d(TAG, "Shake detectado - Simulando secuencia del botón");
                    handleShakeEvent();
                }
            }
        };
    }

    private void logIncomingMessage(Intent intent) {
        Log.d(TAG, "Mensaje recibido - Acción: " + intent.getAction());
        Log.d(TAG, "Topic: " + intent.getStringExtra("topic"));
    }

    /**
     * Ignora el primer mensaje MQTT recibido al conectar al broker.
     * @return true si debe ignorarse, false si procesar.
     */
    private boolean shouldIgnoreMessage(Intent intent) {
        if (isFirstMessage) {
            Log.d(TAG, "Ignorando mensaje inicial del broker");
            isFirstMessage = false;
            return true;
        }
        return false;
    }

    /**
     * Procesa el mensaje MQTT recibido, actualizando UI según tópico y contenido.
     */
    private void processMqttMessage(String topic, String message) {
        try {
            JSONObject json = new JSONObject(message);
            double value = json.getDouble("value");

            if (ConfigMQTT.TOPIC_NIVEL_ALARMA_UBIDOTS.equals(topic)) {
                handleAlarmLevel((int) value);
            } else if (ConfigMQTT.TOPIC_ALARMA_UBIDOTS.equals(topic) && value == 0.0) {
                handleTimeout();
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error procesando JSON: ", e);
        }
    }

    /**
     * Simula pulsación del botón al detectar un shake.
     */
    private void handleShakeEvent() {
        String currentText = btnAccion.getText().toString();

        runOnUiThread(() -> {
            if (BUTTON_TEXT_ACTIVAR.equals(currentText)) {
                updateButtonState(BUTTON_TEXT_MONITOREANDO, false);

                new Handler(Looper.getMainLooper()).postDelayed(() -> updateButtonState(BUTTON_TEXT_MODO_CONSULTOR, true), BUTTON_STATE_DELAY_MS);

            } else if (BUTTON_TEXT_MODO_CONSULTOR.equals(currentText)) {
                updateButtonState(BUTTON_TEXT_REACTIVAR, true);
            } else if (BUTTON_TEXT_REACTIVAR.equals(currentText)) {
                updateButtonState(BUTTON_TEXT_ACTIVAR, true);
            }
        });
    }

    private void handleTimeout() {
        runOnUiThread(() -> {
            updateButtonState(BUTTON_TEXT_ACTIVAR, true);
            tvEstadoAlarma.setText("Estado: INACTIVO");
        });
    }

    private void handleAlarmLevel(int level) {
        runOnUiThread(() -> {
            updateButtonState(BUTTON_TEXT_MODO_CONSULTOR, true);
            updateAlarmStatus(level);
        });
    }

    /**
     * Lógica para el botón principal según estado actual.
     */
    private void handleButtonAction() {
        String currentText = btnAccion.getText().toString();

        try {
            JSONObject json = new JSONObject();
            json.put("value", 1.0);

            if (BUTTON_TEXT_ACTIVAR.equals(currentText)) {
                updateButtonState(BUTTON_TEXT_MONITOREANDO, false);
            } else if (BUTTON_TEXT_MODO_CONSULTOR.equals(currentText)) {
                updateButtonState(BUTTON_TEXT_REACTIVAR, true);
            } else if (BUTTON_TEXT_REACTIVAR.equals(currentText)) {
                updateButtonState(BUTTON_TEXT_ACTIVAR, true);
            }

            sendMqttCommand(json.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error creando comando JSON: ", e);
        }
    }

    private void sendMqttCommand(String message) {
        Intent serviceIntent = new Intent(this, MqttService.class)
                .setAction("PUBLISH_MQTT_MSG")
                .putExtra("topic", ConfigMQTT.TOPIC_ALARMA_UBIDOTS)
                .putExtra("message", message);

        startService(serviceIntent);
        Log.d(TAG, "Comando MQTT enviado al topic ALARMA");
    }

    /**
     * Actualiza texto y estado habilitado del botón principal.
     */
    private void updateButtonState(String text, boolean enabled) {
        btnAccion.setText(text);
        btnAccion.setEnabled(enabled);

        getSharedPreferences(PREFS_APP_STATE, MODE_PRIVATE)
                .edit()
                .putString(PREF_KEY_LAST_BUTTON_TEXT, text)
                .apply();
    }

    /**
     * Actualiza el TextView con el estado del nivel de alarma.
     */
    private void updateAlarmStatus(int level) {
        String statusPrefix = "El nivel de peligro es ";
        String status;

        switch (level) {
            case 0: status = ALARM_STATUS_BAJO; break;
            case 1: status = ALARM_STATUS_MEDIO; break;
            case 2: status = ALARM_STATUS_ALTO; break;
            default: status = ALARM_STATUS_DESCONOCIDO; break;
        }
        tvEstadoAlarma.setText("Estado: " + statusPrefix + status);
        Log.d(TAG, "Estado actualizado: " + status);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadLastState();
        registerMqttReceiver();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerMqttReceiver() {
        IntentFilter filter = new IntentFilter("MQTT_MSG_RECEIVED");
        filter.addCategory(Intent.CATEGORY_DEFAULT);

        IntentFilter shakeFilter = new IntentFilter("SHAKE_DETECTED");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mqttReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(shakeReceiver, shakeFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mqttReceiver, filter);
            registerReceiver(shakeReceiver, shakeFilter);
        }
        Log.d(TAG, "Receiver registrado para topics MQTT");
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(mqttReceiver);
            unregisterReceiver(shakeReceiver);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Receiver ya estaba desregistrado", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PERMISSION_GRANTED) {
                initServices();
            } else {
                Toast.makeText(this, "Se requiere permiso de ubicación para funcionar correctamente", Toast.LENGTH_LONG).show();
            }
        }
    }
}
