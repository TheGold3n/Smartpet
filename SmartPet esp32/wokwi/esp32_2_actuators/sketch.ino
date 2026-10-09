/*
 * =========================================================================================
 * PROYECTO: SmartPet Station
 * NODO: ESP32 #2 - Nodo de Actuadores (Actuator Node)
 * 
 * RESPONSABILIDAD:
 * - Control de servomotor MG996R (GPIO 13) para apertura de compuerta tolva (85° / 0°).
 * - Control de driver MOSFET IRF520 (GPIO 12) para bomba sumergible de agua 12V DC.
 * - Recepción y ejecución de comandos JSON desde UART2 y desde consola Serial USB.
 * - Emisión de respuestas ACK por bus UART2 y monitor serial.
 * - Temporización no bloqueante con millis() (TC-06).
 * 
 * COMPATIBILIDAD: Wokwi Simulator & Hardware Real ESP32 DevKit v1
 * =========================================================================================
 */

#include <Arduino.h>
#include <ArduinoJson.h>
#include <ESP32Servo.h>

// ---------------- ASIGNACIÓN DE PINES (SEGÚN ESPECIFICACIÓN TÉCNICA v2.2) ----------------
const int PIN_SERVO_PWM    = 13; // Señal PWM hacia Servomotor MG996R
const int PIN_MOSFET_PUMP  = 12; // Gate hacia Driver IRF520 (Bomba 12V)
const int UART_RX          = 16; // RX2
const int UART_TX          = 17; // TX2

// ---------------- PARÁMETROS DEL SERVOMOTOR ----------------
const int SERVO_CLOSED_ANGLE = 0;   // Tolva cerrada
const int SERVO_OPEN_ANGLE   = 85;  // Apertura de dosificación

Servo dispenserServo;
bool isDispensing = false;
unsigned long dispenseStartTime = 0;
unsigned long dispenseDuration = 0;

// Estado de la Bomba de Agua
bool pumpState = false;
unsigned long pumpStartTime = 0;
const unsigned long MAX_PUMP_RUN_MS = 60000;

void sendAck(const char* action, const char* status, int extraValue = -1) {
  StaticJsonDocument<200> doc;
  doc["node"]   = "ESP32_2";
  doc["ack"]    = action;
  doc["status"] = status;
  if (extraValue >= 0) doc["value"] = extraValue;

  serializeJson(doc, Serial2);
  Serial2.println();

  Serial.print(F("[ESP32 #2 -> ACK] "));
  serializeJson(doc, Serial);
  Serial.println();
}

void executeDispense(int grams) {
  if (isDispensing) {
    Serial.println(F("[ESP32 #2] Tolva ya ocupada dispensando. Solicitud descartada."));
    return;
  }

  dispenseDuration = constrain(grams * 40, 800, 5000);
  Serial.printf("[ESP32 #2] ABRIENDO TOLVA (Servo -> %d°) para %d gramos (%lu ms)...\n", 
                SERVO_OPEN_ANGLE, grams, dispenseDuration);
  dispenserServo.write(SERVO_OPEN_ANGLE);
  dispenseStartTime = millis();
  isDispensing = true;

  sendAck("DISPENSE", "IN_PROGRESS", grams);
}

void setPumpState(bool state) {
  pumpState = state;
  digitalWrite(PIN_MOSFET_PUMP, state ? HIGH : LOW);
  
  if (state) {
    pumpStartTime = millis();
    Serial.println(F("[ESP32 #2] Bomba 12V: ACTIVADA (LED azul ENCENDIDO)."));
    sendAck("PUMP", "ON");
  } else {
    Serial.println(F("[ESP32 #2] Bomba 12V: DESACTIVADA (LED azul APAGADO)."));
    sendAck("PUMP", "OFF");
  }
}

void processIncomingJson(const String& rawJson) {
  StaticJsonDocument<256> doc;
  DeserializationError err = deserializeJson(doc, rawJson);

  if (!err) {
    const char* cmd = doc["cmd"];
    if (cmd != nullptr) {
      if (strcmp(cmd, "DISPENSE") == 0) {
        int grams = doc["portion_g"] | 50;
        executeDispense(grams);
      } 
      else if (strcmp(cmd, "PUMP") == 0) {
        bool state = doc["state"] | false;
        setPumpState(state);
      }
      else if (strcmp(cmd, "EMERGENCY_STOP") == 0) {
        Serial.println(F("[ESP32 #2] ¡PARADA DE EMERGENCIA! Apagando actuadores..."));
        dispenserServo.write(SERVO_CLOSED_ANGLE);
        isDispensing = false;
        setPumpState(false);
        sendAck("EMERGENCY_STOP", "EXECUTED");
      }
    }
  } else {
    Serial.printf("[ESP32 #2] Error de JSON: %s\n", err.c_str());
  }
}

void setup() {
  Serial.begin(115200);
  delay(100);

  Serial2.begin(115200, SERIAL_8N1, UART_RX, UART_TX);

  pinMode(PIN_MOSFET_PUMP, OUTPUT);
  digitalWrite(PIN_MOSFET_PUMP, LOW);

  ESP32PWM::allocateTimer(0);
  dispenserServo.setPeriodHertz(50);
  dispenserServo.attach(PIN_SERVO_PWM, 500, 2400);
  dispenserServo.write(SERVO_CLOSED_ANGLE);

  Serial.println(F("=================================================="));
  Serial.println(F("[ESP32 #2 - Actuadores] Actuadores inicializados."));
  Serial.println(F("[ESP32 #2] Servo: 0° (Cerrado) | Bomba: OFF"));
  Serial.println(F("[ESP32 #2] Listo para recibir comandos JSON (TC-01 APROBADO)."));
  Serial.println(F("[INFO WOKWI] Puedes enviar por teclado comandos como:"));
  Serial.println(F("  {\"cmd\":\"DISPENSE\",\"portion_g\":60}"));
  Serial.println(F("  {\"cmd\":\"PUMP\",\"state\":true}"));
  Serial.println(F("  {\"cmd\":\"PUMP\",\"state\":false}"));
  Serial.println(F("=================================================="));
}

void loop() {
  unsigned long currentMillis = millis();

  // Control no bloqueante de cierre de tolva (TC-04 y TC-06)
  if (isDispensing && (currentMillis - dispenseStartTime >= dispenseDuration)) {
    dispenserServo.write(SERVO_CLOSED_ANGLE);
    isDispensing = false;
    Serial.println(F("[ESP32 #2] CERRANDO TOLVA (Servo -> 0°). Dosificación completada."));
    sendAck("DISPENSE", "COMPLETED");
  }

  // Fail-safe por tiempo máximo de bomba encendida
  if (pumpState && (currentMillis - pumpStartTime >= MAX_PUMP_RUN_MS)) {
    Serial.println(F("[ESP32 #2] ALERTA: Bomba apagada por timeout de seguridad."));
    setPumpState(false);
  }

  // 1. Recepción por bus UART2 (procedente del Hub)
  if (Serial2.available() > 0) {
    String msg = Serial2.readStringUntil('\n');
    msg.trim();
    if (msg.length() > 0) processIncomingJson(msg);
  }

  // 2. Recepción interactiva por monitor serial USB en Wokwi
  if (Serial.available() > 0) {
    String input = Serial.readStringUntil('\n');
    input.trim();
    if (input.length() > 0) processIncomingJson(input);
  }
}
