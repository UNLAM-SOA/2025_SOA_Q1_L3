#include "config.h"


Estado estado_actual = STAND_BY;
Estado estado_anterior = STAND_BY;
Evento evento_actual = EVENTO_NINGUNO;
PeligroSonido peligro_sonido = BAJA_SONIDO;
PeligroLuz peligro_luz = BAJA_LUZ;

unsigned long tiempo_actual = 0;
unsigned long tiempo_inicio_notificacion = 0;
unsigned long tiempo_anterior = 0;
bool alarma_activada = false;
int vector_promedio_sonido[10] = {0};
int indice_vector_sonido = 0;

int indice_sensor = 0;
volatile float valor_mqtt = 0.0;

WiFiClient espClient;
PubSubClient client(espClient);
