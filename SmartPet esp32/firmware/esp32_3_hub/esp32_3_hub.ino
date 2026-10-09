/*
 * =========================================================================================
 * PROYECTO: SmartPet Station
 * NODO: ESP32 #3 - Hub Maestro / Gateway Hub & Bridge
 * 
 * RESPONSABILIDAD:
 * - Orquestador central del sistema SmartPet Station:
 *   1. Servidor Socket TCP en puerto 5050 (Compatible con App Android).
 *   2. Capa Criptográfica AES-256-GCM nativa (mbedtls) con derivación de clave PBKDF2-HMAC-SHA256:
 *      - PIN usuario: "123456" (configurable)
 *      - Salt estática: "SmartPetSalt_123"
 *      - Iteraciones: 10,000 | Clave: 256 bits (32 bytes)
 *      - Formato paquete TCP: Base64(IV_12B + Ciphertext + Tag_16B) + '\n'
 *   3. Enrutamiento bidireccional de tramas:
 *      - Android -> Descifrado -> JSON inter-nodo por UART2 hacia ESP32 #2 (Actuadores).
 *      - ESP32 #1 (Sensores) por UART2 -> JSON -> Cifrado AES-GCM -> Android (Socket TCP).
 *   4. Mecanismo Fail-Safe OT: Desactivación inmediata de actuadores si se pierde la conexión
 *      inalámbrica o inactividad mayor a 5 segundos (5000 ms).
 *   5. Lógica Autónoma RTC DS3231 (I2C GPIO 21/22): Dispensado programado sin depender de red.
 * 
 * COMPATIBILIDAD: Wokwi Simulator & Hardware Real ESP32 DevKit v1
 * =========================================================================================
 */

#include <Arduino.h>
#include <WiFi.h>
#include <Wire.h>
#include <RTClib.h>
#include <ArduinoJson.h>

// Librerías criptográficas nativas de ESP-IDF / mbedtls
#include "mbedtls/md.h"
#include "mbedtls/gcm.h"
#include "mbedtls/base64.h"

// ---------------- CONFIGURACIÓN DE RED Y PUERTO ----------------
const char* AP_SSID          = "SmartPet_Station";
const char* AP_PASS          = "12345678"; // Red de acceso local directa
const int   TCP_PORT         = 5050;

WiFiServer tcpServer(TCP_PORT);
WiFiClient currentClient;

// ---------------- PARÁMETROS CRIPTOGRÁFICOS AES-256-GCM ----------------
const char* DEFAULT_PIN      = "123456";
const char* KDF_SALT         = "SmartPetSalt_123";
const int   KDF_ITERATIONS   = 10000;
const size_t KEY_SIZE_BYTES  = 32; // 256 bits
const size_t GCM_IV_LEN      = 12; // 96 bits según estándar GCM
const size_t GCM_TAG_LEN     = 16; // 128 bits de etiqueta de autenticación

uint8_t aesKey[KEY_SIZE_BYTES];
bool isKeyDerived = false;

// ---------------- ASIGNACIÓN DE PINES (SEGÚN ESPECIFICACIÓN TÉCNICA v2.2) ----------------
const int PIN_I2C_SDA        = 21; // RTC DS3231 SDA
const int PIN_I2C_SCL        = 22; // RTC DS3231 SCL
const int UART_RX            = 16; // RX2 conectado a TX de los nodos
const int UART_TX            = 17; // TX2 conectado a RX de los nodos

// ---------------- VARIABLES DE CONTROL, RTC Y FAIL-SAFE ----------------
RTC_DS3231 rtc;
bool rtcFound = false;

const int TARGET_HOUR        = 8;
const int TARGET_MIN         = 30;
const int TARGET_GRAMS       = 60;
bool dispenseExecutedToday   = false;

unsigned long lastRTCCheck   = 0;
const unsigned long RTC_INTERVAL_MS = 1000;

// Variables de Fail-Safe de conexión inalámbrica
unsigned long lastClientActivity = 0;
bool clientWasConnected      = false;
const unsigned long FAILSAFE_TIMEOUT_MS = 5000; // 5 segundos sin conexión apaga actuadores

// Últimos datos de telemetría conocidos de ESP32 #1
float lastWeightG            = 0.0;
String lastWaterStatus       = "EMPTY";
String lastFoodStatus        = "EMPTY";
unsigned long lastTelemetrySentToApp = 0;
const unsigned long APP_TELEMETRY_INTERVAL_MS = 2000;

// =========================================================================================
// FUNCIONES CRIPTOGRÁFICAS (PBKDF2-HMAC-SHA256 & AES-256-GCM)
// =========================================================================================

// Deriva la clave AES de 256 bits usando PBKDF2WithHmacSHA256 (100% compatible ESP32 / Wokwi)
bool deriveKeyFromPin(const char* pin, const char* salt, uint8_t* outputKey) {
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
    outputKey[i] = u[i];
  }

  // Iteraciones restantes
  for (int count = 1; count < KDF_ITERATIONS; count++) {
    if (mbedtls_md_hmac(md_info, (const unsigned char*)pin, pin_len, u, 32, u) != 0) {
      return false;
    }
    for (int i = 0; i < 32; i++) {
      outputKey[i] ^= u[i];
    }
  }

  return true;
}

// Cifra un payload de texto con AES-256-GCM y empaqueta en Base64: IV(12) + Ciphertext + Tag(16)
String encryptAndPackage(const String& plaintext) {
  if (!isKeyDerived) return "";

  // 1. Generar IV aleatorio de 12 bytes usando el generador de entropía por hardware del ESP32
  uint8_t iv[GCM_IV_LEN];
  for (size_t i = 0; i < GCM_IV_LEN; i += 4) {
    uint32_t r = esp_random();
    memcpy(&iv[i], &r, (GCM_IV_LEN - i >= 4) ? 4 : (GCM_IV_LEN - i));
  }

  size_t ptLen = plaintext.length();
  uint8_t ciphertext[ptLen];
  uint8_t tag[GCM_TAG_LEN];

  // 2. Cifrado autenticado AES-GCM
  mbedtls_gcm_context gcm;
  mbedtls_gcm_init(&gcm);
  mbedtls_gcm_setkey(&gcm, MBEDTLS_CIPHER_ID_AES, aesKey, 256);

  int ret = mbedtls_gcm_crypt_and_tag(
    &gcm,
    MBEDTLS_GCM_ENCRYPT,
    ptLen,
    iv, GCM_IV_LEN,
    NULL, 0, // Sin datos adicionales autenticados (AAD)
    (const unsigned char*)plaintext.c_str(),
    ciphertext,
    GCM_TAG_LEN,
    tag
  );
  mbedtls_gcm_free(&gcm);

  if (ret != 0) {
    Serial.printf("[ESP32 #3 Hub] Error en mbedtls_gcm_crypt_and_tag: %d\n", ret);
    return "";
  }

  // 3. Concatenar: IV (12) + Ciphertext (ptLen) + Tag (16)
  size_t totalRawLen = GCM_IV_LEN + ptLen + GCM_TAG_LEN;
  uint8_t rawBuffer[totalRawLen];
  memcpy(rawBuffer, iv, GCM_IV_LEN);
  memcpy(rawBuffer + GCM_IV_LEN, ciphertext, ptLen);
  memcpy(rawBuffer + GCM_IV_LEN + ptLen, tag, GCM_TAG_LEN);

  // 4. Codificar en Base64
  size_t b64Len = 0;
  mbedtls_base64_encode(NULL, 0, &b64Len, rawBuffer, totalRawLen);
  unsigned char b64Buffer[b64Len + 1];
  mbedtls_base64_encode(b64Buffer, b64Len + 1, &b64Len, rawBuffer, totalRawLen);
  b64Buffer[b64Len] = '\0';

  return String((char*)b64Buffer);
}

// Decodifica Base64 y descifra con verificación de integridad AES-256-GCM
bool unpackageAndDecrypt(const String& b64Message, String& outputPlaintext) {
  if (!isKeyDerived) return false;

  // 1. Decodificar Base64 a buffer binario
  size_t rawLen = 0;
  mbedtls_base64_decode(NULL, 0, &rawLen, (const unsigned char*)b64Message.c_str(), b64Message.length());

  if (rawLen < (GCM_IV_LEN + GCM_TAG_LEN)) {
    Serial.println(F("[ESP32 #3 Hub] Trama descartada: Longitud Base64 menor a cabecera IV+Tag mínima."));
    return false;
  }

  uint8_t rawBuffer[rawLen];
  if (mbedtls_base64_decode(rawBuffer, rawLen, &rawLen, (const unsigned char*)b64Message.c_str(), b64Message.length()) != 0) {
    Serial.println(F("[ESP32 #3 Hub] Error de decodificación Base64."));
    return false;
  }

  // 2. Extraer componentes: IV(12) + Ciphertext(rawLen - 28) + Tag(16)
  uint8_t iv[GCM_IV_LEN];
  memcpy(iv, rawBuffer, GCM_IV_LEN);

  size_t cipherLen = rawLen - GCM_IV_LEN - GCM_TAG_LEN;
  uint8_t ciphertext[cipherLen];
  memcpy(ciphertext, rawBuffer + GCM_IV_LEN, cipherLen);

  uint8_t tag[GCM_TAG_LEN];
  memcpy(tag, rawBuffer + GCM_IV_LEN + cipherLen, GCM_TAG_LEN);

  // 3. Descifrado autenticado y verificación del Auth Tag
  uint8_t decrypted[cipherLen + 1];
  mbedtls_gcm_context gcm;
  mbedtls_gcm_init(&gcm);
  mbedtls_gcm_setkey(&gcm, MBEDTLS_CIPHER_ID_AES, aesKey, 256);

  int ret = mbedtls_gcm_auth_decrypt(
    &gcm,
    cipherLen,
    iv, GCM_IV_LEN,
    NULL, 0,
    tag, GCM_TAG_LEN,
    ciphertext,
    decrypted
  );
  mbedtls_gcm_free(&gcm);

  if (ret != 0) {
    Serial.printf("[ESP32 #3 Hub] FALLO CRÍTICO DE AUTENTICACIÓN GCM (ret=%d): Clave incorrecta o paquete manipulado.\n", ret);
    return false;
  }

  decrypted[cipherLen] = '\0';
  outputPlaintext = String((char*)decrypted);
  return true;
}

// =========================================================================================
// RUTINAS DE COMUNICACIÓN Y FAIL-SAFE
// =========================================================================================

// Envía comando a los actuadores por UART2 inter-nodo
void sendInterNodeCommand(const String& cmdJson) {
  Serial2.println(cmdJson);
  Serial.print(F("[ESP32 #3 -> Bus UART2] "));
  Serial.println(cmdJson);
}

// Dispara la parada de emergencia en el bus inter-nodo
void triggerEmergencyStop() {
  Serial.println(F("[ESP32 #3 Hub] ¡FAIL-SAFE ACTIVADO! Caída de enlace inalámbrico (>5s). Apagando actuadores..."));
  StaticJsonDocument<128> doc;
  doc["cmd"] = "EMERGENCY_STOP";
  doc["reason"] = "COMM_TIMEOUT";
  
  String out;
  serializeJson(doc, out);
  sendInterNodeCommand(out);
}

// Enviar mensaje cifrado al cliente TCP Android
void sendEncryptedToAndroid(const String& plaintext) {
  if (currentClient && currentClient.connected()) {
    String encryptedPkg = encryptAndPackage(plaintext);
    if (encryptedPkg.length() > 0) {
      currentClient.print(encryptedPkg);
      currentClient.print("\n"); // Delimitador de línea requerido por Android
      Serial.printf("[ESP32 #3 -> Android Cifrado] Claro: \"%s\" | Pkg: %s...\n", 
                    plaintext.c_str(), encryptedPkg.substring(0, 20).c_str());
    }
  }
}

// Procesa una trama recibida desde Android en formato TIPO;CLAVE;VALOR
void processDecryptedAndroidCommand(const String& plain) {
  Serial.printf("[ESP32 #3 <- Android Descifrado] \"%s\"\n", plain.c_str());

  int firstSep = plain.indexOf(';');
  int secondSep = plain.indexOf(';', firstSep + 1);

  if (firstSep == -1 || secondSep == -1) {
    Serial.println(F("[ESP32 #3 Hub] Trama inválida: Se esperaba TIPO;CLAVE;VALOR"));
    return;
  }

  String tipo  = plain.substring(0, firstSep);
  String clave = plain.substring(firstSep + 1, secondSep);
  String valor = plain.substring(secondSep + 1);

  tipo.trim();
  clave.trim();
  valor.trim();

  if (tipo.equalsIgnoreCase("CMD")) {
    if (clave.equalsIgnoreCase("dispense")) {
      int grams = (valor.equalsIgnoreCase("ON")) ? 50 : valor.toInt();
      if (grams <= 0) grams = 50;

      // Reenviar comando JSON a ESP32 #2
      StaticJsonDocument<200> doc;
      doc["cmd"] = "DISPENSE";
      doc["portion_g"] = grams;
      doc["timestamp"] = millis();
      String out;
      serializeJson(doc, out);
      sendInterNodeCommand(out);

      // Responder ACK a Android
      sendEncryptedToAndroid("ACK;dispense;ON");
    } 
    else if (clave.equalsIgnoreCase("pump")) {
      bool pumpOn = valor.equalsIgnoreCase("ON") || valor == "1";

      StaticJsonDocument<200> doc;
      doc["cmd"] = "PUMP";
      doc["state"] = pumpOn;
      String out;
      serializeJson(doc, out);
      sendInterNodeCommand(out);

      // Responder ACK a Android
      sendEncryptedToAndroid(pumpOn ? "ACK;pump;ON" : "ACK;pump;OFF");
    }
  }
}

void setup() {
  Serial.begin(115200);
  delay(100);

  // Serial2 para bus inter-nodo con los otros 2 ESP32
  Serial2.begin(115200, SERIAL_8N1, UART_RX, UART_TX);

  Serial.println(F("=================================================="));
  Serial.println(F("[ESP32 #3 - Hub Maestro] Inicializando sistema..."));

  // 1. Derivación de clave criptográfica con PBKDF2
  Serial.println(F("[ESP32 #3 Hub] Derivando clave AES-256 con PBKDF2-HMAC-SHA256 (10,000 iteraciones)..."));
  if (deriveKeyFromPin(DEFAULT_PIN, KDF_SALT, aesKey)) {
    isKeyDerived = true;
    Serial.println(F("[ESP32 #3 Hub] Clave AES-256-GCM derivada con éxito. Criptografía lista."));
  } else {
    Serial.println(F("[ESP32 #3 Hub] ERROR CRÍTICO: Falló derivación de clave PBKDF2."));
  }

  // 2. Inicialización de RTC DS3231 por I2C
  Wire.begin(PIN_I2C_SDA, PIN_I2C_SCL);
  if (rtc.begin()) {
    rtcFound = true;
    if (rtc.lostPower()) {
      Serial.println(F("[ESP32 #3 Hub] RTC perdió energía, ajustando a fecha de compilación."));
      rtc.adjust(DateTime(F(__DATE__), F(__TIME__)));
    }
    Serial.println(F("[ESP32 #3 Hub] RTC DS3231 conectado y sincronizado."));
  } else {
    Serial.println(F("[ESP32 #3 Hub] AVISO: Módulo RTC DS3231 no detectado (usando temporizador interno)."));
  }

  // 3. Inicio del Punto de Acceso Wi-Fi y Servidor TCP
  WiFi.mode(WIFI_AP);
  WiFi.softAP(AP_SSID, AP_PASS);
  IPAddress myIP = WiFi.softAPIP();

  tcpServer.begin();
  tcpServer.setNoDelay(true);

  Serial.printf("[ESP32 #3 Hub] Wi-Fi AP Creado: \"%s\" | Pass: \"%s\"\n", AP_SSID, AP_PASS);
  Serial.printf("[ESP32 #3 Hub] Servidor TCP escuchando en %s:%d\n", myIP.toString().c_str(), TCP_PORT);
  Serial.println(F("=================================================="));
}

void loop() {
  unsigned long currentMillis = millis();

  // ---------------- GESTIÓN DEL CLIENTE TCP (ANDROID) ----------------
  if (tcpServer.hasClient()) {
    if (!currentClient || !currentClient.connected()) {
      currentClient = tcpServer.available();
      clientWasConnected = true;
      lastClientActivity = currentMillis;
      Serial.printf("[ESP32 #3 Hub] ¡Nueva conexión TCP establecida desde %s!\n", currentClient.remoteIP().toString().c_str());
    } else {
      // Rechazar conexiones concurrentes secundarias
      WiFiClient rejected = tcpServer.available();
      rejected.stop();
    }
  }

  // Si hay un cliente conectado, verificar recepción de paquetes cifrados
  if (currentClient && currentClient.connected()) {
    clientWasConnected = true;

    if (currentClient.available()) {
      lastClientActivity = currentMillis; // Actualizar watchdog Fail-Safe
      String incomingB64 = currentClient.readStringUntil('\n');
      incomingB64.trim();

      if (incomingB64.length() > 0) {
        String decryptedPlaintext;
        if (unpackageAndDecrypt(incomingB64, decryptedPlaintext)) {
          processDecryptedAndroidCommand(decryptedPlaintext);
        } else {
          Serial.println(F("[ESP32 #3 Hub] Paquete recibido descartado por error criptográfico."));
        }
      }
    }

    // Envío periódico de telemetría consolidada hacia Android
    if (currentMillis - lastTelemetrySentToApp >= APP_TELEMETRY_INTERVAL_MS) {
      lastTelemetrySentToApp = currentMillis;
      sendEncryptedToAndroid("LECTURA;peso;" + String(lastWeightG, 1));
      sendEncryptedToAndroid("LECTURA;agua;" + lastWaterStatus);
      sendEncryptedToAndroid("LECTURA;comida;" + lastFoodStatus);
    }
  } else {
    // Si el cliente se desconectó abruptamente
    if (clientWasConnected) {
      clientWasConnected = false;
      Serial.println(F("[ESP32 #3 Hub] Cliente TCP desconectado."));
      triggerEmergencyStop();
    }
  }

  // ---------------- FAIL-SAFE POR INACTIVIDAD DE RED ----------------
  // Si el cliente sigue conectado pero no envía datos por más de FAILSAFE_TIMEOUT_MS (5s)
  if (currentClient && currentClient.connected()) {
    if (currentMillis - lastClientActivity >= FAILSAFE_TIMEOUT_MS) {
      Serial.println(F("[ESP32 #3 Hub] ALERTA: Inactividad TCP > 5 segundos detectada."));
      triggerEmergencyStop();
      lastClientActivity = currentMillis; // Evita inundar el log
    }
  }

  // ---------------- RECEPCIÓN DESDE EL BUS SERIAL2 (SENSORES Y ACKS) ----------------
  if (Serial2.available() > 0) {
    String uartMessage = Serial2.readStringUntil('\n');
    uartMessage.trim();

    if (uartMessage.length() > 0) {
      StaticJsonDocument<300> doc;
      DeserializationError err = deserializeJson(doc, uartMessage);

      if (!err) {
        const char* node = doc["node"];

        // 1. Datos procedentes de ESP32 #1 (Sensores)
        if (node && strcmp(node, "ESP32_1") == 0) {
          lastFoodStatus  = doc["food_status"] | "EMPTY";
          lastWaterStatus = doc["water_status"] | "EMPTY";
          lastWeightG     = doc["dish_weight_g"] | 0.0;

          Serial.printf("[ESP32 #3 <- Sensores] Comida: %s | Agua: %s | Peso: %.1fg\n",
                        lastFoodStatus.c_str(), lastWaterStatus.c_str(), lastWeightG);
        }
        // 2. Confirmaciones procedentes de ESP32 #2 (Actuadores)
        else if (node && strcmp(node, "ESP32_2") == 0) {
          const char* ack = doc["ack"];
          const char* status = doc["status"];
          Serial.printf("[ESP32 #3 <- Actuadores ACK] Acción: %s | Estado: %s\n", ack, status);

          // Reenviar ACK a Android si corresponde
          if (ack && strcmp(ack, "DISPENSE") == 0 && status && strcmp(status, "COMPLETED") == 0) {
            sendEncryptedToAndroid("ACK;dispense;COMPLETED");
          }
        }
      }
    }
  }

  // ---------------- HORARIO AUTÓNOMO RTC DS3231 ----------------
  if (currentMillis - lastRTCCheck >= RTC_INTERVAL_MS) {
    lastRTCCheck = currentMillis;

    if (rtcFound) {
      DateTime now = rtc.now();
      if (now.hour() == TARGET_HOUR && now.minute() == TARGET_MIN) {
        if (!dispenseExecutedToday) {
          Serial.printf("[ESP32 #3 Hub RTC] Disparo horario programado: %02d:%02d -> Dispensando %dg\n",
                        TARGET_HOUR, TARGET_MIN, TARGET_GRAMS);
          
          StaticJsonDocument<200> doc;
          doc["cmd"] = "DISPENSE";
          doc["portion_g"] = TARGET_GRAMS;
          doc["timestamp"] = now.unixtime();
          String out;
          serializeJson(doc, out);
          sendInterNodeCommand(out);

          dispenseExecutedToday = true;
        }
      } else {
        dispenseExecutedToday = false;
      }
    }
  }
}

