// config.h
#ifndef CONFIG_H
#define CONFIG_H

#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>

// --------------------- CONSTANTES ---------------------
#define BOTON_PIN               15
#define SENSOR_ANALOGICO        35
#define SENSOR_LUZ              34
#define LED_PIN                 2
#define BUZZER_PIN              4

#define UMBRAL_SONIDO_BAJO      500
#define UMBRAL_SONIDO_MEDIO     1000
#define UMBRAL_LUZ_ALTA         3000
#define UMBRAL_LUZ_MEDIA        2000

#define DURACION_NOTIFICACION   3000
#define INTERVALO_LECTURA       100

#define FRECUENCIA_BUZZER_BAJA   500
#define FRECUENCIA_BUZZER_MEDIA  1000
#define FRECUENCIA_BUZZER_ALTA   2000

#define WIFI_SSID           "tomaso"
#define WIFI_PASSWORD       "tfup8648"
#define MQTT_BROKER         "industrial.api.ubidots.com"
#define MQTT_PORT           1883
#define MQTT_USER           "BBUS-GvjZYx9q0dvsWlYvjV2JNRBjHPWQzo"
#define MQTT_CLIENT_ID      "esp32_grupoL3"
#define MQTT_TOPIC_CONTROL  "/v1.6/devices/grupol3/alarma"
#define MQTT_TOPIC_NIVEL_ALARMA "/v1.6/devices/grupol3/nivel_alarma"
#define MQTT_ACTIVO         1.00
#define MQTT_NO_ACTIVO      0.00

#define TAM_JSON            256

#define UBIDOTS_VALUE       "value"
#define UBIDOTS_CONTEXT     "context"
#define UBIDOTS_TAG         "texto"

// --------------------- ENUMERACIONES ---------------------
enum Estado {
    STAND_BY, MONITOREANDO, PELIGRO_ALTO, PELIGRO_MEDIO, PELIGRO_BAJO, CONSULTOR
};

enum Evento {
    EVENTO_NINGUNO, EVENTO_PULSADOR, EVENTO_PELIGRO_BAJO,
    EVENTO_PELIGRO_MEDIO, EVENTO_PELIGRO_ALTO, EVENTO_TIMEOUT, EVENTO_MQTT_ACTIVAR
};

enum PeligroSonido {
    BAJA_SONIDO, MEDIA_SONIDO, ALTA_SONIDO
};

enum PeligroLuz {
    BAJA_LUZ, MEDIA_LUZ, ALTA_LUZ
};

enum NivelAlarma {
    ALARMA_BAJA, ALARMA_MEDIA, ALARMA_ALTA
};

// --------------------- VARIABLES GLOBALES ---------------------
extern Estado estado_actual;
extern Estado estado_anterior;
extern Evento evento_actual;
extern PeligroSonido peligro_sonido;
extern PeligroLuz peligro_luz;

extern unsigned long tiempo_actual;
extern unsigned long tiempo_inicio_notificacion;
extern unsigned long tiempo_anterior;
extern bool alarma_activada;
extern int vector_promedio_sonido[10];
extern int indice_vector_sonido;
extern int indice_sensor;
extern volatile float valor_mqtt;

extern WiFiClient espClient;
extern PubSubClient client;

// --------------------- PROTOTIPOS ---------------------
void verificarPulsador();
void verificarSensorSonido();
void verificarSensorLuz();
void verificarTemporizador();
void verificarComunicacionMQTT();
void getEvent();
void calcularPeligro();
void activarAlarma(NivelAlarma nivel_alarma);
void desactivarAlarma();
void setup_wifi();
void reconnect_mqtt();
void callback_mqtt(char* topic, uint8_t* payload, unsigned int length);
void publicarOffBroker();
void publicarNivelAlarma(NivelAlarma nivel);
void printEstado(Estado estado, Evento evento);
void fsm();

#endif
