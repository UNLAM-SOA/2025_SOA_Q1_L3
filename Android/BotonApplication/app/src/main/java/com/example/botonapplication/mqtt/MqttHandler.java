package com.example.botonapplication.mqtt;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.botonapplication.MainActivity;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONObject;

import java.util.Arrays;

public class MqttHandler implements MqttCallback {
    private static final String TAG = "MQTT_Handler";
    private MqttClient client;
    private Context context;

    public MqttHandler(Context context) {
        this.context = context;
        Log.d(TAG, "MqttHandler inicializado");
    }

    public void connect() {
        Log.d(TAG, "Intentando conectar a MQTT...");
        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setUserName(ConfigMQTT.USER_NAME_UBIDOTS);
            options.setPassword(ConfigMQTT.USER_PASS_UBIDOTS.toCharArray());

            Log.d(TAG, "Creando cliente MQTT con URL: " + ConfigMQTT.MQTT_SERVER_UBIDOTS);
            client = new MqttClient(ConfigMQTT.MQTT_SERVER_UBIDOTS,
                    ConfigMQTT.CLIENT_ID_UBIDOTS,
                    new MemoryPersistence());

            Log.d(TAG, "Conectando...");
            client.connect(options);
            client.setCallback(this);

            Log.d(TAG, "Conexión exitosa. Suscribiendo a topic: " + ConfigMQTT.TOPIC_NIVEL_ALARMA_UBIDOTS);
            subscribe(ConfigMQTT.TOPIC_NIVEL_ALARMA_UBIDOTS);

        } catch (MqttException e) {
            Log.e(TAG, "Error en conexión MQTT: " + e.getMessage());
            Log.e(TAG, "Causa: " + e.getCause());
            Log.e(TAG, "StackTrace: ", e);
        }
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
        Log.d(TAG, "Intentando publicar en topic: " + topic);
        Log.d(TAG, "Contenido del mensaje: " + message);

        try {
            if (client != null && client.isConnected()) {
                MqttMessage mqttMessage = new MqttMessage(message.getBytes());
                mqttMessage.setQos(1);
                client.publish(topic, mqttMessage);
                Log.d(TAG, "Publicación exitosa");
            } else {
                Log.w(TAG, "Cliente no conectado, no se puede publicar");
            }
        } catch (MqttException e) {
            Log.e(TAG, "Error en publicación: " + e.getMessage());
        }
    }

    public void subscribe(String topic) {
        Log.d(TAG, "Intentando suscribirse a: " + topic);
        try {
            if (client != null && client.isConnected()) {
                client.subscribe(topic);
                Log.d(TAG, "Suscripción exitosa");
            } else {
                Log.w(TAG, "Cliente no conectado, no se puede suscribir");
            }
        } catch (MqttException e) {
            Log.e(TAG, "Error en suscripción: " + e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        Log.w(TAG, "¡Conexión MQTT perdida!");
        Log.w(TAG, "Razón: " + cause.getMessage());
        // Podrías implementar reconexión automática aquí
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());
        Log.d(TAG, "Nuevo mensaje recibido");
        Log.d(TAG, "Topic: " + topic);
        Log.d(TAG, "Mensaje: " + payload);
        Log.d(TAG, "QoS: " + message.getQos());

        // Broadcast explícito
        Intent intent = new Intent("MQTT_MSG_RECEIVED");
        intent.setPackage(context.getPackageName());
        intent.putExtra("topic", topic);
        intent.putExtra("message", payload);

        Log.d(TAG, "Enviando broadcast a la app");
        context.sendBroadcast(intent);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        Log.d(TAG, "Entrega de mensaje confirmada");
        try {
            Log.d(TAG, "Mensaje entregado a topic: " + Arrays.toString(token.getTopics()));
        } catch (Exception e) {
            Log.e(TAG, "Error al obtener topics del token", e);
        }
    }
}