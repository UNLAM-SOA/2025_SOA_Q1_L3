package com.example.botonapplication;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.botonapplication.mqtt.ConfigMQTT;
import com.example.botonapplication.mqtt.MqttHandler;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "APP_DEBUG";
    private MqttHandler mqttHandler;
    private TextView tvEstadoAlarma;
    private Button btnActivar;
    private BroadcastReceiver mqttReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate() - Inicializando actividad");

        tvEstadoAlarma = findViewById(R.id.tvEstadoAlarma);
        btnActivar = findViewById(R.id.btnActivar);

        // Inicializar MQTT
        Log.d(TAG, "Creando MQTT Handler");
        mqttHandler = new MqttHandler(this);
        Log.d(TAG, "Conectando MQTT...");
        mqttHandler.connect();

        // Configurar el BroadcastReceiver
        mqttReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Broadcast recibido");
                String topic = intent.getStringExtra("topic");
                String message = intent.getStringExtra("message");

                Log.d(TAG, "Topic recibido: " + topic);
                Log.d(TAG, "Mensaje recibido: " + message);

                if (topic != null && topic.equals(ConfigMQTT.TOPIC_NIVEL_ALARMA_UBIDOTS)) {
                    try {
                        JSONObject json = new JSONObject(message);
                        double value = json.getDouble("value");
                        Log.d(TAG, "Valor extraído: " + value);
                        updateAlarmStatus((int) value);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error al parsear JSON", e);
                    }
                }
            }
        };

        btnActivar.setOnClickListener(v -> {
            Log.d(TAG, "Botón presionado - Intentando activar monitoreo");
            try {
                JSONObject json = new JSONObject();
                json.put("value", 1.0); // MQTT_ACTIVO
                mqttHandler.publish(ConfigMQTT.TOPIC_ALARMA_UBIDOTS, json.toString());
                Toast.makeText(this, "Activando monitoreo", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Mensaje de activación enviado");
            } catch (JSONException e) {
                Log.e(TAG, "Error al crear JSON", e);
            }
        });
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume() - Registrando receiver");

        IntentFilter filter = new IntentFilter("MQTT_MSG_RECEIVED");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mqttReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mqttReceiver, filter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause() - Desregistrando receiver");
        unregisterReceiver(mqttReceiver);
    }

    private void updateAlarmStatus(int level) {
        Log.d(TAG, "Actualizando estado de alarma. Nivel: " + level);
        String status;
        switch (level) {
            case 0: status = "BAJO"; break;
            case 1: status = "MEDIO"; break;
            case 2: status = "ALTO"; break;
            default: status = "DESCONOCIDO";
        }
        tvEstadoAlarma.setText("Estado: " + status);
        Log.d(TAG, "Estado actualizado en UI: " + status);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy() - Desconectando MQTT");
        mqttHandler.disconnect();
    }
}