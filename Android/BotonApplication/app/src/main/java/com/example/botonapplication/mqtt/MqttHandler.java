package com.example.botonapplication.mqtt;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;


import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;



public class MqttHandler {
    private static final String TAG = "MQTT_Handler";
    private MqttClient client;
    private MqttCallback callback;

    private static final long INITIAL_RECONNECT_DELAY = 1000;
    private static final long MAX_RECONNECT_DELAY = 60000;

    private long reconnectDelay = INITIAL_RECONNECT_DELAY;



    public MqttHandler(MqttCallback callback) {
        this.callback = callback;
        Log.d(TAG, "MqttHandler inicializado");
    }

    public boolean connect() {
        Log.d(TAG, "Intentando conectar a MQTT...");
        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setUserName(ConfigMQTT.USER_NAME_UBIDOTS);
            options.setPassword(ConfigMQTT.USER_PASS_UBIDOTS.toCharArray());

            client = new MqttClient(
                    ConfigMQTT.MQTT_SERVER_UBIDOTS,
                    ConfigMQTT.CLIENT_ID_UBIDOTS,
                    new MemoryPersistence()
            );

            client.setCallback(callback);
            client.connect(options);

            Log.d(TAG, "Conexión exitosa");
            subscribe(ConfigMQTT.TOPIC_NIVEL_ALARMA_UBIDOTS);
            subscribe(ConfigMQTT.TOPIC_ALARMA_UBIDOTS);
            return true;
        } catch (MqttException e) {
            Log.e(TAG, "Error en conexión MQTT: " + e.getMessage());
        }
            return false;
    }

    public void disconnect() {
        Log.d(TAG, "Intentando desconectar MQTT");
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
                Log.d(TAG, "Desconexión exitosa");
            }
        } catch (MqttException e) {
            Log.e(TAG, "Error al desconectar: " + e.getMessage());
        }
    }

    public void publish(String topic, String message) {
        Log.d(TAG, "Publicando en topic: " + topic);
        try {
            if (client != null && client.isConnected()) {
                MqttMessage mqttMessage = new MqttMessage(message.getBytes());
                mqttMessage.setQos(1);
                client.publish(topic, mqttMessage);
                Log.d(TAG, "Mensaje publicado: " + message);
            } else {
                Log.w(TAG, "Cliente no conectado. No se puede publicar.");
            }
        } catch (MqttException e) {
            Log.e(TAG, "Error al publicar: " + e.getMessage());
        }
    }

    public void subscribe(String topic) {
        Log.d(TAG, "Suscribiendo a topic: " + topic);
        try {
            if (client != null && client.isConnected()) {
                client.subscribe(topic);
                Log.d(TAG, "Suscripción exitosa");
            } else {
                Log.w(TAG, "Cliente no conectado. No se puede suscribir.");
            }
        } catch (MqttException e) {
            Log.e(TAG, "Error al suscribirse: " + e.getMessage());
        }
    }


    public boolean isConnected() {
        return client != null && client.isConnected();
    }
    private final Handler reconnectHandler = new Handler(Looper.getMainLooper());
    private final Runnable reconnectRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isConnected()) {
                Log.w(TAG, "Intentando reconectar...");
                boolean success = connect();
                if (success) {
                    reconnectDelay = INITIAL_RECONNECT_DELAY;
                } else {

                    reconnectDelay = Math.min(reconnectDelay * 2, MAX_RECONNECT_DELAY);
                    Log.w(TAG, "Reconexión fallida. Próximo intento en " + reconnectDelay + "ms");
                    reconnectHandler.postDelayed(this, reconnectDelay);
                }
            }
        }
    };


    public void scheduleReconnect() {
        reconnectHandler.removeCallbacks(reconnectRunnable);
        reconnectHandler.postDelayed(reconnectRunnable, reconnectDelay);
    }
}