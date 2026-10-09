/*
 * =========================================================================================
 * PROYECTO: SmartPet Station
 * NODO: ESP32 #2 - Nodo de Actuadores (Actuator Node)
 * 
 * RESPONSABILIDAD:
 * - Control de actuadores de potencia del dispensador e hidratador:
 *   1. Servomotor MG996R (GPIO 13): Compuerta de la tolva dosificadora de alimento.
 *   2. Driver MOSFET IRF520 (GPIO 12): Bomba sumergible 12V DC para recirculación de agua.
 * - Recepción y parseo de tramas JSON recibidas desde el ESP32 #3 (Hub) por UART2.
 * - Control temporal NO BLOQUEANTE (sin delay) mediante millis().
 * - Emisión de confirmaciones (ACK) por UART2 hacia el Hub tras procesar los comandos.
 * - Mecanismo de Fail-Safe local (apagado forzado ante comando de emergencia o timeout de bomba).
 * 
 * COMPATIBILIDAD: Wokwi Simulator & Hardware Real ESP32 DevKit v1
 * =========================================================================================
 */

#include <Arduino.h>
#include <ArduinoJson.h>
#include <ESP32Servo.h>

// ---------------- ASIGNACIÓN DE PINES (SEGÚN ESPECIFICACIÓN TÉCNICA v2.2) ----------------
const int PIN_SERVO_PWM    = 13; // Señal PWM de control hacia Servomotor MG996R
const int PIN_MOSFET_PUMP  = 12; // Gate de conmutación de potencia hacia Driver IRF520 (Bomba 12V)

// Bus UART Inter-nodo desde/hacia ESP32 #3 (Hub)
const int UART_RX          = 16; // RX2
const int UART_TX          = 17; // TX2

// ---------------- PARÁMETROS DEL SERVOMOTOR Y DOSIFICACIÓN ----------------
const int SERVO_CLOSED_ANGLE = 0;   // Ángulo de compuerta cerrada
const int SERVO_OPEN_ANGLE   = 85;  // Ángulo de apertura de dosificación

Servo dispenserServo;

// Variables de estado del dispensador
bool isDispensing = false;
unsigned long dispenseStartTime = 0;
unsigned long dispenseDuration = 0;

// Variables de estado y seguridad de la bomba de agua
bool pumpState = false;
unsigned long pumpStartTime = 0;
const unsigned long MAX_PUMP_RUN_MS = 60000; // Fail-safe: Máximo 60 segundos continuos de encendido

// ---------------- FUNCIONES DE CONTROL DE HARDWARE ----------------

// Envía confirmación ACK formateada en JSON hacia el Hub
void sendAck(const char* action, const char* status, int extraValue = -1) {
  StaticJsonDocument<200> doc;
  doc["node"]   = "ESP32_2";
  doc["ack"]    = action;
  doc["status"] = status;
  if (extraValue >= 0) {
    doc["value"] = extraValue;
  }

  serializeJson(doc, Serial2);
  Serial2.println();

  Serial.print(F("[ESP32 #2 -> Hub ACK] "));
  serializeJson(doc, Serial);
  Serial.println();
}

// Inicia la maniobra de dispensado con apertura angular no bloqueante
void executeDispense(int grams) {
  if (isDispensing) {
    Serial.println(F("[ESP32 #2] AVISO: Operación de dispensado ya en curso, ignorando nueva solicitud."));
    return;
  }

  // Duración estimada: 40 ms por gramo, acotado entre 800 ms y 5000 ms por seguridad mecánica
  dispenseDuration = constrain(grams * 40, 800, 5000);
  
  Serial.printf("[ESP32 #2] Ejecutando apertura de compuerta para %d gramos (%lu ms)...\n", grams, dispenseDuration);
  dispenserServo.write(SERVO_OPEN_ANGLE);
  dispenseStartTime = millis();
  isDispensing = true;

  // Confirmar recepción e inicio de la maniobra
  sendAck("DISPENSE", "IN_PROGRESS", grams);
}

// Controla el estado digital del MOSFET de la bomba de agua
void setPumpState(bool state) {
  pumpState = state;
  digitalWrite(PIN_MOSFET_PUMP, state ? HIGH : LOW);
  
  if (state) {
    pumpStartTime = millis();
    Serial.println(F("[ESP32 #2] Bomba de agua: ENCENDIDA (12V activa)."));
    sendAck("PUMP", "ON");
  } else {
    Serial.println(F("[ESP32 #2] Bomba de agua: APAGADA."));
    sendAck("PUMP", "OFF");
  }
}

// Parada de emergencia forzada (Fail-Safe por caída de comunicación o timeout)
void emergencyStop() {
  Serial.println(F("[ESP32 #2] ¡PARADA DE EMERGENCIA ACTIVADA!"));
  dispenserServo.write(SERVO_CLOSED_ANGLE);
  isDispensing = false;
  setPumpState(false);
  sendAck("EMERGENCY_STOP", "EXECUTED");
}

void setup() {
  // Serial0 para depuración por USB
  Serial.begin(115200);
  delay(100);

  // Serial2 para comunicación inter-nodo
  Serial2.begin(115200, SERIAL_8N1, UART_RX, UART_TX);

  Serial.println(F("=================================================="));
  Serial.println(F("[ESP32 #2 - Actuadores] Inicializando actuadores..."));

  // Configuración de pin MOSFET de bomba
  pinMode(PIN_MOSFET_PUMP, OUTPUT);
  digitalWrite(PIN_MOSFET_PUMP, LOW);

  // Configuración del Servomotor mediante timers de hardware del ESP32
  ESP32PWM::allocateTimer(0);
  dispenserServo.setPeriodHertz(50); // Frecuencia estándar para servo analógico (50 Hz)
  dispenserServo.attach(PIN_SERVO_PWM, 500, 2400); // Rango de pulsos en microsegundos
  dispenserServo.write(SERVO_CLOSED_ANGLE); // Posición inicial: tolva cerrada

  Serial.println(F("[ESP32 #2 - Actuadores] Servo en 0° (Cerrado), Bomba en OFF."));
  Serial.println(F("[ESP32 #2 - Actuadores] Listo y escuchando comandos por UART2."));
  Serial.println(F("=================================================="));
}

void loop() {
  unsigned long currentMillis = millis();

  // 1. Control no bloqueante de cierre de compuerta del servo
  if (isDispensing && (currentMillis - dispenseStartTime >= dispenseDuration)) {
    dispenserServo.write(SERVO_CLOSED_ANGLE);
    isDispensing = false;
    Serial.println(F("[ESP32 #2] Dosificación completada. Tolva cerrada."));
    sendAck("DISPENSE", "COMPLETED");
  }

  // 2. Fail-safe autónomo por tiempo máximo de bomba encendida (previene desbordes o daños por trabajo en seco)
  if (pumpState && (currentMillis - pumpStartTime >= MAX_PUMP_RUN_MS)) {
    Serial.println(F("[ESP32 #2] ALERTA DE SEGURIDAD: Tiempo máximo de bomba excedido. Apagando preventivamente."));
    setPumpState(false);
  }

  // 3. Procesamiento de comandos JSON entrantes desde ESP32 #3 (Hub)
  if (Serial2.available() > 0) {
    String jsonString = Serial2.readStringUntil('\n');
    jsonString.trim();

    if (jsonString.length() > 0) {
      StaticJsonDocument<256> doc;
      DeserializationError error = deserializeJson(doc, jsonString);

      if (!error) {
        const char* cmd = doc["cmd"];

        if (cmd != nullptr) {
          if (strcmp(cmd, "DISPENSE") == 0) {
            int grams = doc["portion_g"] | 50; // 50g por defecto si no se especifica
            executeDispense(grams);
          } 
          else if (strcmp(cmd, "PUMP") == 0) {
            bool state = doc["state"] | false;
            setPumpState(state);
          } 
          else if (strcmp(cmd, "EMERGENCY_STOP") == 0) {
            emergencyStop();
          } 
          else {
            Serial.printf("[ESP32 #2] Comando no reconocido: %s\n", cmd);
          }
        }
      } else {
        Serial.printf("[ESP32 #2] Error al parsear JSON inter-nodo: %s\n", error.c_str());
      }
    }
  }
}

