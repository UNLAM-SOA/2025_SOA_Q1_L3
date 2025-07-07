#include "config.h"

// --------------------- ARREGLO DE FUNCIONES ---------------------
void (*const verificar_sensor[5])() =
{
    verificarPulsador,
    verificarSensorSonido,
    verificarSensorLuz,
    verificarTemporizador,
    verificarComunicacionMQTT
};

// --------------------- FUNCIONES ---------------------
void verificarTemporizador()
{
    tiempo_actual = millis();

    if (alarma_activada && (tiempo_actual - tiempo_inicio_notificacion > DURACION_NOTIFICACION))
    {
        evento_actual = EVENTO_TIMEOUT;
        alarma_activada = false;
    }
    else
    {
        evento_actual = EVENTO_NINGUNO;
    }
}

void verificarPulsador()
{
    if (digitalRead(BOTON_PIN) == HIGH)
    {
        evento_actual = EVENTO_PULSADOR;
    }
    else
    {
        evento_actual = EVENTO_NINGUNO;
    }
}

void verificarSensorSonido()
{
    int sonido;
    vector_promedio_sonido[indice_vector_sonido] = analogRead(SENSOR_ANALOGICO);

    indice_vector_sonido = (indice_vector_sonido + 1) % 10;

    sonido = (vector_promedio_sonido[0] + vector_promedio_sonido[1] +
    vector_promedio_sonido[2] + vector_promedio_sonido[3] +
    vector_promedio_sonido[4] + vector_promedio_sonido[5] +
    vector_promedio_sonido[6] + vector_promedio_sonido[7] +
    vector_promedio_sonido[8] + vector_promedio_sonido[9] )/ 10;

    if (sonido < UMBRAL_SONIDO_BAJO)
    {
        peligro_sonido = BAJA_SONIDO;
    }
    else if (sonido < UMBRAL_SONIDO_MEDIO)
    {
        peligro_sonido = MEDIA_SONIDO;
    }
    else
    {
        peligro_sonido = ALTA_SONIDO;
    }

    calcularPeligro();
}

void verificarSensorLuz()
{
    int luz = analogRead(SENSOR_LUZ);

    if (luz >= UMBRAL_LUZ_ALTA)
    {
        peligro_luz = ALTA_LUZ;
    }
    else if (luz >= UMBRAL_LUZ_MEDIA)
    {
        peligro_luz = MEDIA_LUZ;
    }
    else
    {
        peligro_luz = BAJA_LUZ;
    }

    calcularPeligro();
}

void verificarComunicacionMQTT()
{
    static bool flag_disparo = false;

    if (valor_mqtt == MQTT_ACTIVO && !flag_disparo) {
        evento_actual = EVENTO_MQTT_ACTIVAR;
        flag_disparo = true;
        valor_mqtt = MQTT_NO_ACTIVO;  
    }
    else {
        evento_actual = EVENTO_NINGUNO;
        flag_disparo = false;
    }

}


void getEvent()
{
    tiempo_actual = millis();

    if ((tiempo_actual - tiempo_anterior) > INTERVALO_LECTURA)
    {
        verificar_sensor[indice_sensor]();
        indice_sensor = (indice_sensor + 1) % 5;
        tiempo_anterior = tiempo_actual;
    }
    else
    {
        evento_actual = EVENTO_NINGUNO;
    }
}

void calcularPeligro()
{
    if (peligro_sonido == ALTA_SONIDO || peligro_luz == BAJA_LUZ)
    {
        evento_actual = EVENTO_PELIGRO_ALTO;
    }
    else if (peligro_sonido == BAJA_SONIDO && peligro_luz == ALTA_LUZ)
    {
        evento_actual = EVENTO_PELIGRO_BAJO;
    }
    else
    {
        evento_actual = EVENTO_PELIGRO_MEDIO;
    }
}

void activarAlarma(NivelAlarma nivel_alarma)
{
    switch (nivel_alarma)
    {
        case ALARMA_BAJA:
            tone(BUZZER_PIN, FRECUENCIA_BUZZER_BAJA);
            break;
        case ALARMA_MEDIA:
            tone(BUZZER_PIN, FRECUENCIA_BUZZER_MEDIA);
            break;
        case ALARMA_ALTA:
            tone(BUZZER_PIN, FRECUENCIA_BUZZER_ALTA);
            break;
    }

    digitalWrite(LED_PIN, HIGH);
}

void desactivarAlarma()
{
    noTone(BUZZER_PIN);
    digitalWrite(LED_PIN, LOW);
}

void setup_wifi()
{
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Conectando a WiFi...");
  while (WiFi.status() != WL_CONNECTED)
  {
    delay(500);
    Serial.print(".");
  }
  Serial.println("¡Conectado a WiFi!");
}


void reconnect_mqtt() 
{
  while (!client.connected()) 
  {
    Serial.print("Conectando al broker MQTT...");
    if (client.connect(MQTT_CLIENT_ID, MQTT_USER, "")) 
    {
      Serial.println(" ¡Conectado!");
      client.subscribe(MQTT_TOPIC_CONTROL);

      Serial.print("Suscripto al topic: ");
      Serial.println(MQTT_TOPIC_CONTROL);
    } 
    else 
    {
      Serial.print("Falló. Estado: ");
      Serial.print(client.state());
      Serial.println(" — Reintentando en 5 segundos...");
      delay(5000);
    }
  }
}


void callback_mqtt(char* topic, uint8_t* payload, unsigned int length)
{
  Serial.print("Topic: ");
  Serial.println(topic);

  
  Serial.write(payload, length);
  Serial.println();

  StaticJsonDocument<TAM_JSON> doc;
  DeserializationError error = deserializeJson(doc, payload, length);

  if (error) {
    Serial.print("Error al parsear JSON: ");
    Serial.println(error.c_str());
    return;
  }

  valor_mqtt = doc[UBIDOTS_VALUE];  
  Serial.print("Valor recibido en el callback: ");
  Serial.println(valor_mqtt);
}

void publicarOffBroker() 
{
  StaticJsonDocument<TAM_JSON> doc;
  String json;
  doc[UBIDOTS_VALUE] = MQTT_NO_ACTIVO;  
  serializeJson(doc, json);

  char charMsgToSend[json.length() + 1];
  json.toCharArray(charMsgToSend, sizeof(charMsgToSend));

  client.publish(MQTT_TOPIC_CONTROL, charMsgToSend);
}

void publicarNivelAlarma(NivelAlarma nivel)
{
  const char* nivel_str;

  switch (nivel) 
  {
      case ALARMA_BAJA: nivel_str = "El nivel de peligro es BAJO"; break;
      case ALARMA_MEDIA: nivel_str = "El nivel de peligro es MEDIO"; break;
      case ALARMA_ALTA:  nivel_str = "El nivel de peligro es ALTO"; break;
  }

  StaticJsonDocument<TAM_JSON> doc;
  String json;

  doc[UBIDOTS_VALUE] = nivel;

  
  JsonObject context = doc.createNestedObject(UBIDOTS_CONTEXT);
  context[UBIDOTS_TAG] = nivel_str;

  serializeJson(doc, json);

  char charMsgToSend[json.length() + 1];
  json.toCharArray(charMsgToSend, sizeof(charMsgToSend));

  client.publish(MQTT_TOPIC_NIVEL_ALARMA, charMsgToSend);
}


void printEstado(Estado estado)
{
  static Estado estado_anterior_impreso = STAND_BY;

  if (estado != estado_anterior_impreso)
  {
    Serial.print("→ Nuevo estado: ");
    switch (estado)
    {
      case STAND_BY:       Serial.println("STAND_BY"); break;
      case MONITOREANDO:   Serial.println("MONITOREANDO"); break;
      case PELIGRO_BAJO:   Serial.println("PELIGRO_BAJO"); break;
      case PELIGRO_MEDIO:  Serial.println("PELIGRO_MEDIO"); break;
      case PELIGRO_ALTO:   Serial.println("PELIGRO_ALTO"); break;
      case CONSULTOR:      Serial.println("CONSULTOR"); break;
    }
    estado_anterior_impreso = estado;
  }
}


void fsm()
{
  getEvent();

  printEstado(estado_actual);

  switch(estado_actual)
  {
    case STAND_BY:
      switch (evento_actual)
      {
        case EVENTO_PULSADOR:
        case EVENTO_MQTT_ACTIVAR:
          estado_actual = MONITOREANDO;
          break;
        default:
          break;
      }
      break;

    case MONITOREANDO:
      switch (evento_actual)
      {
        case EVENTO_PELIGRO_BAJO:
          estado_actual = PELIGRO_BAJO;
          activarAlarma(ALARMA_BAJA);
          publicarNivelAlarma(ALARMA_BAJA);
          break;
        case EVENTO_PELIGRO_MEDIO:
          estado_actual = PELIGRO_MEDIO;
          activarAlarma(ALARMA_MEDIA);
          publicarNivelAlarma(ALARMA_MEDIA);
          break;
        case EVENTO_PELIGRO_ALTO:
          estado_actual = PELIGRO_ALTO;
          activarAlarma(ALARMA_ALTA);
          publicarNivelAlarma(ALARMA_ALTA);
          break;
        default:
          break;
      }
      break;

    case PELIGRO_BAJO:
    case PELIGRO_MEDIO:
    case PELIGRO_ALTO:
      switch (evento_actual)
      {
        case EVENTO_PULSADOR:
        case EVENTO_MQTT_ACTIVAR:
          tiempo_inicio_notificacion = millis();
          alarma_activada = true;
          estado_actual = CONSULTOR;
          break;
        default:
          break;
      }
      break;

    case CONSULTOR:
      switch (evento_actual)
      {
        case EVENTO_PULSADOR:
        case EVENTO_MQTT_ACTIVAR:
          estado_actual = MONITOREANDO;
          break;
        case EVENTO_TIMEOUT:
          estado_actual = STAND_BY;
          desactivarAlarma();
          publicarOffBroker();
          break;
        default:
          break;
      }
      break;
  }
}


void setup()
{
  Serial.begin(115200);
  pinMode(LED_PIN, OUTPUT);
  pinMode(BOTON_PIN, INPUT);
  pinMode(BUZZER_PIN, OUTPUT);

  setup_wifi();
  client.setServer(MQTT_BROKER, MQTT_PORT);
  client.setCallback(callback_mqtt);

  tiempo_anterior = millis();
}

void loop()
{
  if (!client.connected()) 
  {
    reconnect_mqtt();
  }

  client.loop();

  fsm();
  
}
