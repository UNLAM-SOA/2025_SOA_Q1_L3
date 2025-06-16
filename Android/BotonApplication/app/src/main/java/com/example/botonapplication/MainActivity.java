package com.example.botonapplication;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;


import androidx.appcompat.app.AppCompatActivity;

import com.example.botonapplication.mqtt.ConfigMQTT;
import com.example.botonapplication.mqtt.MqttService;

import org.eclipse.paho.android.service.BuildConfig;
import org.json.JSONException;
import org.json.JSONObject;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "DevelopTomi";
    private TextView tvEstadoAlarma;
    private Button btnAccion;
    private BroadcastReceiver mqttReceiver;
    private boolean isFirstMessage = true;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initLogging();
        initServices();
        initUIComponents();
        setupMqttReceiver();
        loadLastState();
    }

    private void initLogging() {
        Log.d(TAG, "Actividad creada");
        Log.d(TAG, "Versión de la app: " + BuildConfig.VERSION_NAME);
    }

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

    private void initUIComponents() {
        tvEstadoAlarma = findViewById(R.id.tvEstadoAlarma);
        btnAccion = findViewById(R.id.btnActivar);

        btnAccion.setOnClickListener(v -> handleButtonAction());
        updateButtonState("ACTIVAR MONITOREO", true);

        Log.d(TAG, "Componentes UI inicializados");
    }

    private void loadLastState() {
        SharedPreferences prefs = getSharedPreferences("AppState", MODE_PRIVATE);
        String status = prefs.getString("lastAlarmStatus", "INACTIVO");
        String time = prefs.getString("lastUpdateTime", "");

        runOnUiThread(() -> {
            tvEstadoAlarma.setText("Estado: " + status);
            // Actualizar botón según estado
            if (status.contains("INACTIVO")) {
                updateButtonState("ACTIVAR MONITOREO", true);
            } else if (status.contains("BAJO") || status.contains("MEDIO") || status.contains("ALTO")) {
                updateButtonState("MODO CONSULTOR", true);
            }
        });
    }
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
    }

    private void logIncomingMessage(Intent intent) {
        Log.d(TAG, "Mensaje recibido - Acción: " + intent.getAction());
        Log.d(TAG, "Topic: " + intent.getStringExtra("topic"));
    }

    private boolean shouldIgnoreMessage(Intent intent) {
        if (isFirstMessage) {
            Log.d(TAG, "Ignorando mensaje inicial del broker");
            isFirstMessage = false;
            return true;
        }
        return false;
    }

   private void processMqttMessage(String topic, String message) {
        try {
            JSONObject json = new JSONObject(message);
            double value = json.getDouble("value");

            if (ConfigMQTT.TOPIC_NIVEL_ALARMA_UBIDOTS.equals(topic)) {
                Log.d(TAG, "Procesando nivel de alarma: " + value);
                handleAlarmLevel((int) value);
            }
            else if (ConfigMQTT.TOPIC_ALARMA_UBIDOTS.equals(topic) && value == 0.0) {
                Log.d(TAG, "Procesando timeout del sistema");
                handleTimeout();
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error procesando JSON: ", e);
        }
    }

    private void handleTimeout() {
        runOnUiThread(() -> {
            updateButtonState("ACTIVAR MONITOREO", true);
            tvEstadoAlarma.setText("Estado: INACTIVO");
            Log.d(TAG, "UI actualizada por timeout");
        });
    }

    private void handleAlarmLevel(int level) {
        runOnUiThread(() -> {
            updateButtonState("MODO CONSULTOR", true);
            updateAlarmStatus(level);
        });
    }

    private void handleButtonAction() {
        String currentText = btnAccion.getText().toString();
        Log.d(TAG, "Acción de botón detectada - Estado actual: " + currentText);

        try {
            JSONObject json = new JSONObject();
            json.put("value", 1.0);


            if (currentText.equals("ACTIVAR MONITOREO")) {
                json.put("value", 1.0);
                updateButtonState("MONITOREANDO...", false); // Feedback de espera
            }
            else if (currentText.equals("MODO CONSULTOR")) {
                updateButtonState("REACTIVAR", true);
            } else if (currentText.equals("REACTIVAR")) {
                updateButtonState("ACTIVAR MONITOREO", true);
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

    private void updateButtonState(String text, boolean enabled) {
        btnAccion.setText(text);
        btnAccion.setEnabled(enabled);
        Log.d(TAG, "Botón actualizado - Texto: " + text);
    }

    private void updateAlarmStatus(int level) {
        String status = "El nivel de peligro es ";
        switch (level) {
            case 0: status = status + "BAJO"; break;
            case 1: status = status + "MEDIO"; break;
            case 2: status = status + "ALTO"; break;
            default: status = "DESCONOCIDO";
        }
        tvEstadoAlarma.setText("Estado: " + status);
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mqttReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mqttReceiver, filter);
        }
        Log.d(TAG, "Receiver registrado para topics MQTT");
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mqttReceiver);
    }
}