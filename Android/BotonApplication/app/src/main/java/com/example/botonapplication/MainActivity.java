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
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.botonapplication.mqtt.ConfigMQTT;
import com.example.botonapplication.mqtt.MqttService;

import org.json.JSONException;
import org.json.JSONObject;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "Develop";
    private TextView tvEstadoAlarma;
    private Button btnAccion;
    private BroadcastReceiver mqttReceiver;
    private boolean isFirstMessage = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate - Iniciando Activity");

        startService(new Intent(this, MqttService.class));
        Log.d(TAG, "Servicio MQTT iniciado");

        tvEstadoAlarma = findViewById(R.id.tvEstadoAlarma);
        btnAccion = findViewById(R.id.btnActivar);

        // Estado inicial
        actualizarBoton("ACTIVAR MONITOREO", true);
        Log.d(TAG, "UI inicial configurada");

        btnAccion.setOnClickListener(v -> {
            Log.d(TAG, "Botón presionado - Texto actual: " + btnAccion.getText());
            manejarAccionBoton();
        });

        mqttReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "onReceive - Acción: " + intent.getAction());

                if (isFirstMessage) {
                    isFirstMessage = false;
                    Log.d(TAG, "Ignorando primer mensaje automático del broker");
                    return;
                }

                String topic = intent.getStringExtra("topic");
                String message = intent.getStringExtra("message");
                Log.d(TAG, "Mensaje recibido - Topic: " + topic + " | Mensaje: " + message);

                if (ConfigMQTT.TOPIC_NIVEL_ALARMA_UBIDOTS.equals(topic)) {
                    try {
                        JSONObject json = new JSONObject(message);
                        double value = json.getDouble("value");
                        Log.d(TAG, "Valor procesado: " + value);

                        if (value == 0.0) { // Timeout
                            Log.d(TAG, "Timeout recibido - Reiniciando a estado inicial");
                            actualizarBoton("ACTIVAR MONITOREO", true);
                            tvEstadoAlarma.setText("Estado: INACTIVO (timeout)");
                        } else {
                            Log.d(TAG, "Nivel de peligro recibido: " + value);
                            actualizarBoton("MODO CONSULTOR", true);
                            updateAlarmStatus((int) value);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error al parsear JSON: " + e.getMessage());
                    }
                }
            }
        };
    }

    private void manejarAccionBoton() {
        try {
            JSONObject json = new JSONObject();
            json.put("value", 1.0);
            String accion = btnAccion.getText().toString();
            Log.d(TAG, "Preparando comando MQTT para acción: " + accion);

            if (accion.equals("MODO CONSULTOR")) {
                Log.d(TAG, "Transición a CONSULTOR");
                actualizarBoton("REACTIVAR", true);
            } else if (accion.equals("REACTIVAR")) {
                Log.d(TAG, "Reiniciando ciclo");
                actualizarBoton("ACTIVAR MONITOREO", true);
            }

            Intent serviceIntent = new Intent(this, MqttService.class);
            serviceIntent.setAction("PUBLISH_MQTT_MSG");
            serviceIntent.putExtra("topic", ConfigMQTT.TOPIC_ALARMA_UBIDOTS);
            serviceIntent.putExtra("message", json.toString());
            startService(serviceIntent);
            Log.d(TAG, "Comando MQTT enviado");

        } catch (JSONException e) {
            Log.e(TAG, "Error al crear JSON", e);
        }
    }

    private void actualizarBoton(String texto, boolean habilitado) {
        runOnUiThread(() -> {
            btnAccion.setText(texto);
            btnAccion.setEnabled(habilitado);
            Log.d(TAG, "Botón actualizado - Texto: " + texto + " | Habilitado: " + habilitado);
        });
    }

    private void updateAlarmStatus(int level) {
        String status;
        switch (level) {
            case 0: status = "BAJO"; break;
            case 1: status = "MEDIO"; break;
            case 2: status = "ALTO"; break;
            default: status = "DESCONOCIDO";
        }
        tvEstadoAlarma.setText("Estado: " + status);
        Log.d(TAG, "Estado actualizado: " + status);
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume - Registrando Receiver");

        IntentFilter filter = new IntentFilter();
        filter.addAction("MQTT_MSG_RECEIVED");
        filter.addCategory(Intent.CATEGORY_DEFAULT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mqttReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mqttReceiver, filter);
        }
        isFirstMessage = true; // Resetear bandera al volver a la actividad
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause - Desregistrando Receiver");
        unregisterReceiver(mqttReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy - Limpiando recursos");
    }
}