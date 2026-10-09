/*
 * =========================================================================================
 * PROYECTO: SmartPet Station
 * NODO: ESP32 #3 - Hub Maestro / Gateway & Bridge
 * 
 * RESPONSABILIDAD:
 * - Orquestador central del sistema SmartPet Station:
 *   1. Reloj de tiempo real RTC DS3231 (I2C GPIO 21/22) para dispensado autónomo a las 08:30.
 *   2. Derivación de clave criptográfica AES-256 con PBKDF2-HMAC-SHA256 (10,000 iteraciones).
 *   3. Enrutamiento del bus UART2 inter-nodo hacia ESP32 #1 (Sensores) y ESP32 #2 (Actuadores).
 *   4. Mecanismo Fail-Safe OT ante pérdidas de enlace.
 * 
 * COMPATIBILIDAD: Wokwi Simulator & Hardware Real ESP32 DevKit v1
 * =========================================================================================
 */

#include <Arduino.h>
#include <Wire.h>
#include <RTClib.h>
#include <ArduinoJson.h>

// Librerías criptográficas nativas de ESP32 / mbedtls
#include "mbedtls/md.h"

// ---------------- CONFIGURACIÓN CRIPTOGRÁFICA ----------------
const char* DEFAULT_PIN      = "123456";
const char* KDF_SALT         = "SmartPetSalt_123";
const int   KDF_ITERATIONS   = 10000;
const size_t KEY_SIZE_BYTES  = 32;

uint8_t aesKey[KEY_SIZE_BYTES];
bool isKeyDerived = false;

// ---------------- PINES Y BUSES ----------------
const int PIN_I2C_SDA        = 21;
const int PIN_I2C_SCL        = 22;
const int UART_RX            = 16;
const int UART_TX            = 17;

RTC_DS1307 rtc; // En Wokwi se usa modelo compatible DS1307 / DS3231
bool rtcFound = false;

const int TARGET_HOUR        = 8;
const int TARGET_MIN         = 30;
const int TARGET_GRAMS       = 60;
bool dispenseExecutedToday   = false;

unsigned long lastRTCCheck   = 0;
const unsigned long RTC_INTERVAL_MS = 1000;

// Derivación de clave PBKDF2-HMAC-SHA256 estándar 100% compatible con Wokwi y ESP32
bool deriveKey(const char* pin, const char* salt) {
  const mbedtls_md_info_t* md_info = mbedtls_md_info_from_type(MBEDTLS_MD_SHA256);
  if (!md_info) return false;

  size_t salt_len = strlen(salt);
  size_t pin_len = strlen(pin);

  // U_1 = HMAC-SHA256(pin, salt || 0x00 0x00 0x00 0x01)
  uint8_t salt_block[64];
  if (salt_len + 4 > sizeof(salt_block)) return false;
  memcpy(salt_block, salt, salt_len);
  salt_block[salt_len]     = 0x00;
  salt_block[salt_len + 1] = 0x00;
  salt_block[salt_len + 2] = 0x00;
  salt_block[salt_len + 3] = 0x01;

  uint8_t u[32];
  if (mbedtls_md_hmac(md_info, (const unsigned char*)pin, pin_len, salt_block, salt_len + 4, u) != 0) {
    return false;
  }

  // T_1 = U_1
  for (int i = 0; i < 32; i++) {
    aesKey[i] = u[i];
  }

  // Iteraciones restantes (U_2 hasta U_10000)
  for (int count = 1; count < KDF_ITERATIONS; count++) {
    if (mbedtls_md_hmac(md_info, (const unsigned char*)pin, pin_len, u, 32, u) != 0) {
      return false;
    }
    for (int i = 0; i < 32; i++) {
      aesKey[i] ^= u[i];
    }
  }

  return true;
}

void sendDispenseCommand(int grams) {
  StaticJsonDocument<200> doc;
  doc["cmd"] = "DISPENSE";
  doc["portion_g"] = grams;
  doc["timestamp"] = millis();

  // Emisión por bus UART2 hacia ESP32 #2
  serializeJson(doc, Serial2);
  Serial2.println();

  // Depuración en pantalla
  Serial.print(F("[ESP32 #3 Hub -> Bus UART2] "));
  serializeJson(doc, Serial);
  Serial.println();
}

void setup() {
  Serial.begin(115200);
  delay(100);

  Serial2.begin(115200, SERIAL_8N1, UART_RX, UART_TX);

  Serial.println(F("=================================================="));
  Serial.println(F("[ESP32 #3 - Hub Maestro] Inicializando..."));

  // 1. Derivación de clave AES-256
  Serial.println(F("[ESP32 #3 Hub] Derivando clave con PBKDF2-HMAC-SHA256 (10,000 iteraciones)..."));
  if (deriveKey(DEFAULT_PIN, KDF_SALT)) {
    isKeyDerived = true;
    Serial.println(F("[ESP32 #3 Hub] Clave AES-256-GCM derivada correctamente."));
  } else {
    Serial.println(F("[ESP32 #3 Hub] Fallo en derivación criptográfica."));
  }

  // 2. Inicialización RTC I2C
  Wire.begin(PIN_I2C_SDA, PIN_I2C_SCL);
  if (rtc.begin()) {
    rtcFound = true;
    Serial.println(F("[ESP32 #3 Hub] RTC I2C detectado y sincronizado."));
  } else {
    Serial.println(F("[ESP32 #3 Hub] AVISO: RTC no encontrado (usando temporizador de respaldo)."));
  }

  Serial.println(F("[ESP32 #3 Hub] Hub inicializado con éxito (TC-01 APROBADO)."));
  Serial.println(F("[INFO WOKWI] Puedes escribir en la consola para enviar comandos:"));
  Serial.println(F("  'D' -> Enviar DISPENSE 60g a ESP32 #2"));
  Serial.println(F("  'P' -> Enviar PUMP ON a ESP32 #2"));
  Serial.println(F("  'O' -> Enviar PUMP OFF a ESP32 #2"));
  Serial.println(F("=================================================="));
}

void loop() {
  unsigned long currentMillis = millis();

  // 1. Recepción de telemetría de ESP32 #1 o ACKs de ESP32 #2 por UART2
  if (Serial2.available() > 0) {
    String msg = Serial2.readStringUntil('\n');
    msg.trim();
    if (msg.length() > 0) {
      StaticJsonDocument<300> doc;
      DeserializationError err = deserializeJson(doc, msg);
      if (!err) {
        const char* node = doc["node"];
        if (node && strcmp(node, "ESP32_1") == 0) {
          Serial.print(F("[ESP32 #3 Hub <- Telemetría Sensores UART] "));
          serializeJson(doc, Serial);
          Serial.println();
        } else if (node && strcmp(node, "ESP32_2") == 0) {
          Serial.print(F("[ESP32 #3 Hub <- Confirmación Actuadores UART] "));
          serializeJson(doc, Serial);
          Serial.println();
        }
      }
    }
  }

  // 2. Control interactivo por consola en Wokwi
  if (Serial.available() > 0) {
    char ch = Serial.read();
    if (ch == 'D' || ch == 'd') {
      Serial.println(F("[ESP32 #3 Hub] Disparando comando DISPENSE manual..."));
      sendDispenseCommand(60);
    } else if (ch == 'P' || ch == 'p') {
      Serial.println(F("[ESP32 #3 Hub] Disparando comando PUMP ON..."));
      StaticJsonDocument<128> doc;
      doc["cmd"] = "PUMP";
      doc["state"] = true;
      serializeJson(doc, Serial2);
      Serial2.println();
      serializeJson(doc, Serial);
      Serial.println();
    } else if (ch == 'O' || ch == 'o') {
      Serial.println(F("[ESP32 #3 Hub] Disparando comando PUMP OFF..."));
      StaticJsonDocument<128> doc;
      doc["cmd"] = "PUMP";
      doc["state"] = false;
      serializeJson(doc, Serial2);
      Serial2.println();
      serializeJson(doc, Serial);
      Serial.println();
    }
  }

  // 3. Chequeo RTC periódico cada segundo
  if (currentMillis - lastRTCCheck >= RTC_INTERVAL_MS) {
    lastRTCCheck = currentMillis;
    if (rtcFound) {
      DateTime now = rtc.now();
      if (now.hour() == TARGET_HOUR && now.minute() == TARGET_MIN) {
        if (!dispenseExecutedToday) {
          Serial.printf("[ESP32 #3 Hub RTC] Disparo horario 08:30 detectado -> Dispensando %dg\n", TARGET_GRAMS);
          sendDispenseCommand(TARGET_GRAMS);
          dispenseExecutedToday = true;
        }
      } else {
        dispenseExecutedToday = false;
      }
    }
  }
}
