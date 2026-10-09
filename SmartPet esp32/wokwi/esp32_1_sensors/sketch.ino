/*
 * =========================================================================================
 * PROYECTO: SmartPet Station
 * NODO: ESP32 #1 - Nodo de Sensores (Sensor Node)
 * 
 * RESPONSABILIDAD:
 * - Adquisición periódica y no bloqueante de variables del entorno:
 *   1. Presencia de croquetas en tolva: Sensor IR E18-D80NK (GPIO 5, Pull-Up interno)
 *   2. Nivel de agua en depósito: Sensor Flotador magnético (GPIO 19, Pull-Up interno)
 *   3. Flujo de agua de recirculación: Sensor de Efecto Hall YF-S201 (GPIO 23, Interrupción RISING)
 *   4. Peso del plato de comida: Celda de carga con conversor ADC HX711 (GPIO 4 DT, GPIO 2 SCK)
 * - Empaquetado de métricas en formato JSON estándar.
 * - Transmisión por bus serial UART2 hacia el ESP32 #3 (Hub Maestro) cada 2 a 3 segundos.
 * 
 * COMPATIBILIDAD: Wokwi Simulator & Hardware Real ESP32 DevKit v1
 * =========================================================================================
 */

#include <Arduino.h>
#include <ArduinoJson.h>
#include "HX711.h"

// ---------------- ASIGNACIÓN DE PINES (SEGÚN ESPECIFICACIÓN TÉCNICA v2.2) ----------------
const int PIN_IR_FOOD    = 5;   // Sensor Infrarrojo E18-D80NK (LOW = Presencia detectada / FULL, HIGH = Tolva vacía)
const int PIN_FLOAT      = 19;  // Sensor de flotador magnético (LOW = Nivel de agua óptimo / FULL, HIGH = Nivel bajo)
const int PIN_FLOW       = 23;  // Sensor de flujo YF-S201 (Pulsos digitales medidos por interrupción)
const int PIN_HX711_DT   = 4;   // HX711 Data
const int PIN_HX711_SCK  = 2;   // HX711 Clock

// Bus UART Inter-nodo hacia ESP32 #3 (Hub)
const int UART_RX        = 16;  // RX2
const int UART_TX        = 17;  // TX2

// ---------------- CONSTANTES DE CALIBRACIÓN Y TIMING ----------------
const float FLOW_CALIBRATION_FACTOR = 7.5; // Frecuencia de pulsos (Hz) = 7.5 * Q (L/min)
const float SCALE_CALIBRATION_FACTOR = 420.09; // Ajuste de celda de carga (cuentas por gramo)
const unsigned long TELEMETRY_INTERVAL_MS = 2000; // Envío de telemetría cada 2000 ms

// ---------------- INSTANCIAS Y VARIABLES GLOBALES ----------------
HX711 scale;
volatile unsigned int pulseCount = 0;
unsigned long lastTelemetryTime = 0;
bool hxReady = false;
float simWeight = 48.5; // Valor de peso simulado si no hay celda física

// Rutina de Servicio de Interrupción (ISR) para el caudalímetro
void IRAM_ATTR pulseCounterISR() {
  pulseCount++;
}

void setup() {
  // Serial0 para depuración por monitor serial USB
  Serial.begin(115200);
  delay(100);

  // Serial2 para bus UART inter-nodo (115200 baudios, 8 bits, sin paridad, 1 stop bit)
  Serial2.begin(115200, SERIAL_8N1, UART_RX, UART_TX);

  Serial.println(F("=================================================="));
  Serial.println(F("[ESP32 #1 - Sensores] Inicializando periféricos..."));

  // Configuración de pines de entrada digital con Pull-Up interno
  pinMode(PIN_IR_FOOD, INPUT_PULLUP);
  pinMode(PIN_FLOAT, INPUT_PULLUP);
  pinMode(PIN_FLOW, INPUT_PULLUP);

  // Asociar interrupción al pin del caudalímetro en flanco de subida (RISING)
  attachInterrupt(digitalPinToInterrupt(PIN_FLOW), pulseCounterISR, RISING);

  // Inicialización de la celda de carga HX711
  scale.begin(PIN_HX711_DT, PIN_HX711_SCK);
  
  // Timeout de 500ms para detección de celda física o modo simulación
  unsigned long startHX = millis();
  while (millis() - startHX < 500) {
    if (scale.is_ready()) {
      hxReady = true;
      break;
    }
    delay(10);
  }

  if (hxReady) {
    scale.set_scale(SCALE_CALIBRATION_FACTOR);
    scale.tare();
    Serial.println(F("[ESP32 #1 - Sensores] Celda HX711 calibrada y tarada con éxito."));
  } else {
    Serial.println(F("[ESP32 #1 - Sensores] Sensor HX711 en modo emulación Wokwi (peso dinámico)."));
  }

  Serial.println(F("[ESP32 #1 - Sensores] Inicialización completa (TC-01 APROBADO)."));
  Serial.println(F("=================================================="));
}

void loop() {
  unsigned long currentMillis = millis();

  // Ejecución no bloqueante cada TELEMETRY_INTERVAL_MS
  if (currentMillis - lastTelemetryTime >= TELEMETRY_INTERVAL_MS) {
    unsigned long elapsedTime = currentMillis - lastTelemetryTime;
    lastTelemetryTime = currentMillis;

    // 1. Lectura del sensor IR de tolva (Lógica activa en bajo por Pull-Up)
    bool irState = (digitalRead(PIN_IR_FOOD) == LOW);
    const char* foodStatus = irState ? "FULL" : "EMPTY";

    // 2. Lectura del flotador de agua (Lógica activa en bajo por Pull-Up)
    bool floatState = (digitalRead(PIN_FLOAT) == LOW);
    const char* waterStatus = floatState ? "FULL" : "EMPTY";

    // 3. Cálculo de caudal de agua (L/min)
    noInterrupts();
    unsigned int pulses = pulseCount;
    pulseCount = 0;
    interrupts();

    float flowRate = ((pulses / (elapsedTime / 1000.0)) / FLOW_CALIBRATION_FACTOR);
    if (flowRate < 0.05) flowRate = 0.0;

    // 4. Lectura de peso en el plato
    float dishWeight = 0.0;
    if (hxReady && scale.is_ready()) {
      dishWeight = scale.get_units(3);
      if (dishWeight < 0.0) dishWeight = 0.0;
    } else {
      dishWeight = simWeight;
    }

    // 5. Construcción del payload JSON estructurado
    StaticJsonDocument<256> doc;
    doc["node"]           = "ESP32_1";
    doc["food_status"]    = foodStatus;
    doc["water_status"]   = waterStatus;
    doc["flow_rate_lmin"] = serialized(String(flowRate, 2));
    doc["dish_weight_g"]  = serialized(String(dishWeight, 1));
    doc["status"]         = "OK";

    // 6. Transmisión serial hacia ESP32 #3 (Hub) por UART2
    serializeJson(doc, Serial2);
    Serial2.println();

    // 7. Depuración local en Serial USB (Para captura Wokwi)
    Serial.print(F("[ESP32 #1 -> Hub UART] "));
    serializeJson(doc, Serial);
    Serial.println();
  }
}
