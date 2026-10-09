import socket
import threading
import time
import base64
import os
import random
import sys

# Forzar codificación UTF-8 segura en Windows para evitar caídas de charmap
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.backends import default_backend

# ---------------- CONFIGURACIÓN ----------------
HOST = '0.0.0.0'
PORT = 5050
PIN = b"123456"
SALT = b"SmartPetSalt_123"
ITERATIONS = 10000
KEY_LENGTH = 32

# ---------------- CRIPTOGRAFÍA AES-256-GCM ----------------
def get_aes_gcm():
    kdf = PBKDF2HMAC(
        algorithm=hashes.SHA256(),
        length=KEY_LENGTH,
        salt=SALT,
        iterations=ITERATIONS,
        backend=default_backend()
    )
    key = kdf.derive(PIN)
    return AESGCM(key)

aesgcm = get_aes_gcm()

def encrypt_message(plain_text: str) -> str:
    iv = os.urandom(12)
    cipher_text_and_tag = aesgcm.encrypt(iv, plain_text.encode('utf-8'), None)
    combined = iv + cipher_text_and_tag
    return base64.b64encode(combined).decode('ascii') + '\n'

def decrypt_message(base64_str: str) -> str:
    try:
        combined = base64.b64decode(base64_str.strip())
        if len(combined) < 28:
            return None
        iv = combined[:12]
        cipher_text_and_tag = combined[12:]
        plain_text_bytes = aesgcm.decrypt(iv, cipher_text_and_tag, None)
        return plain_text_bytes.decode('utf-8')
    except Exception as e:
        print(f"[Cripto] Error al descifrar: {e}", flush=True)
        return None

# ---------------- SIMULADOR DEL NODO ESP32 ----------------
actuadores = {
    "dispense": "OFF",
    "pump": "OFF"
}
peso_simulado = 85.0
agua_simulado = 90.0

def client_handler(conn, addr):
    global peso_simulado, agua_simulado
    print(f"\n[Hub] Cliente conectado desde {addr}", flush=True)
    
    send_lock = threading.Lock()
    is_active = True

    def safe_send(msg: str):
        with send_lock:
            try:
                conn.sendall(encrypt_message(msg).encode('ascii'))
            except Exception:
                pass

    # Hilo para enviar telemetría periódica dinámica
    def telemetry_task():
        global peso_simulado, agua_simulado
        while is_active:
            try:
                # Dinamismo constante y fluido en pantalla
                consumo_comida = round(random.uniform(0.3, 0.7), 1)
                peso_simulado = max(5.0, round(peso_simulado - consumo_comida, 1))
                if peso_simulado <= 10.0:
                    peso_simulado = 80.0

                # El agua baja hasta 0.0 y NO se auto-rellena sola (requiere relleno manual)
                if agua_simulado > 0.0:
                    consumo_agua = round(random.uniform(0.3, 0.8), 1)
                    agua_simulado = max(0.0, round(agua_simulado - consumo_agua, 1))

                # Enviar telemetría de peso
                safe_send(f"LECTURA;peso;{peso_simulado:.1f}")
                time.sleep(0.8)

                # Enviar telemetría de agua
                safe_send(f"LECTURA;agua;{agua_simulado:.1f}")
                time.sleep(0.8)

            except Exception:
                break
                
    telemetry_thread = threading.Thread(target=telemetry_task, daemon=True)
    telemetry_thread.start()

    try:
        buffer = ""
        while True:
            data = conn.recv(1024)
            if not data:
                break
            
            buffer += data.decode('ascii', errors='ignore')
            while '\n' in buffer:
                line, buffer = buffer.split('\n', 1)
                if line.strip():
                    try:
                        decrypted = decrypt_message(line)
                        if decrypted:
                            print(f"[Hub] Mensaje recibido: {decrypted}", flush=True)
                            parts = decrypted.split(';')
                            if len(parts) == 3 and parts[0] == "CMD":
                                comando = parts[1]
                                valor = parts[2]
                                actuadores[comando] = valor
                                print(f"[Hub] Actuador {comando} -> {valor}", flush=True)
                                
                                # Responder ACK de confirmación sin bloquear
                                safe_send(f"ACK;{comando};{valor}")
                                
                                # Si es dispensar comida, sube 30g y manda actualización inmediata
                                if comando == "dispense" and valor == "ON":
                                    peso_simulado = min(100.0, round(peso_simulado + 30.0, 1))
                                    safe_send(f"LECTURA;peso;{peso_simulado:.1f}")
                                    print(f"[Hub] [RACION] Racion dispensada (+30g). Nuevo peso: {peso_simulado:.1f}g", flush=True)
                                
                                # Relleno manual de agua detectado
                                if comando in ["refill_water", "pump"] and valor in ["ON", "95", "100"]:
                                    agua_simulado = 95.0
                                    safe_send(f"LECTURA;agua;{agua_simulado:.1f}")
                                    print(f"[Hub] [RELLENO MANUAL] Estanque rellenado manualmente. Nuevo nivel: {agua_simulado:.1f}%", flush=True)
                        else:
                            print("[Hub] Error: Trama recibida no se pudo descifrar", flush=True)
                    except Exception as cmd_err:
                        print(f"[Hub] Error procesando comando: {cmd_err}", flush=True)
    except Exception as e:
        print(f"[Hub] Conexion cerrada con error: {e}", flush=True)
    finally:
        is_active = False
        print(f"[Hub] Cliente {addr} desconectado.", flush=True)
        try:
            conn.close()
        except Exception:
            pass

import webbrowser

def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"

def generar_html_qr(ip, pin="123456"):
    qr_data = f"SMARTPET:{pin}:{ip}"
    html_content = f"""<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Vinculación SmartPet Station</title>
    <style>
        body {{
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background-color: #FFF9F2;
            color: #4A3428;
            display: flex;
            justify-content: center;
            align-items: center;
            min-height: 100vh;
            margin: 0;
            padding: 20px;
            box-sizing: border-box;
        }}
        .card {{
            background: white;
            padding: 34px 40px;
            border-radius: 28px;
            box-shadow: 0 12px 35px rgba(74, 52, 40, 0.12);
            text-align: center;
            max-width: 440px;
            border: 2px solid #FFE0B2;
        }}
        h1 {{ color: #FF8A3D; font-size: 26px; margin: 8px 0 4px 0; }}
        p {{ color: #666; font-size: 14px; margin-top: 0; line-height: 1.4; }}
        .qr-box {{
            background: #fff;
            padding: 16px;
            border-radius: 20px;
            display: inline-block;
            border: 3px solid #FF8A3D;
            margin: 14px 0;
        }}
        .data-box {{
            background: #FFF9F2;
            border: 1px solid #FFE0B2;
            border-radius: 14px;
            padding: 14px;
            font-weight: bold;
            font-size: 15px;
            color: #4A3428;
            margin-top: 10px;
        }}
        .badge {{
            display: inline-block;
            background: #E8F5E9;
            color: #2E7D32;
            padding: 6px 16px;
            border-radius: 20px;
            font-size: 12px;
            font-weight: bold;
        }}
        .ip-highlight {{ color: #0288D1; }}
        .pin-highlight {{ color: #FF8A3D; }}
    </style>
</head>
<body>
    <div class="card">
        <span class="badge">● SERVIDOR SIMULADOR ESP32 ACTIVO</span>
        <h1>🐾 SmartPet Station</h1>
        <p>Abre la app en tu celular, presiona <b>📷 Escanear Código QR</b> y enfoca este código:</p>
        <div class="qr-box">
            <img src="https://api.qrserver.com/v1/create-qr-code/?size=260x260&data={qr_data}" alt="Código QR SmartPet" width="260" height="260">
        </div>
        <div class="data-box">
            Wi-Fi IP: <span class="ip-highlight">{ip}</span> &nbsp;|&nbsp; PIN: <span class="pin-highlight">{pin}</span>
        </div>
        <p style="margin-top: 16px; font-size: 12px; color: #888;">
            💡 Asegúrate de que el celular y esta PC estén conectados a la misma red Wi-Fi. No requiere cable USB ni depuración.
        </p>
    </div>
</body>
</html>"""
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "vincular_estacion.html")
    with open(path, "w", encoding="utf-8") as f:
        f.write(html_content)
    return path

def start_server():
    local_ip = get_local_ip()
    pin_str = PIN.decode('ascii')
    html_path = generar_html_qr(local_ip, pin_str)

    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind((HOST, PORT))
    server.listen(1)

    print("\n" + "=" * 62, flush=True)
    print("      🐾 SMARTPET STATION - SERVIDOR SIMULADOR ESP32 🐾", flush=True)
    print("=" * 62, flush=True)
    print(f"📡 IP en tu red Wi-Fi Local : {local_ip}", flush=True)
    print(f"🔌 Puerto de Comunicación   : {PORT}", flush=True)
    print(f"🔐 PIN de Cifrado AES-256   : {pin_str}", flush=True)
    print(f"📷 Código QR generado en    : {html_path}", flush=True)
    print("=" * 62, flush=True)
    print("[Hub] Esperando conexion de la aplicacion movil...", flush=True)

    # Abrir el navegador para mostrar el código QR en pantalla
    try:
        webbrowser.open(f"file:///{os.path.abspath(html_path)}")
    except Exception:
        pass

    while True:
        conn, addr = server.accept()
        threading.Thread(target=client_handler, args=(conn, addr), daemon=True).start()

if __name__ == "__main__":
    start_server()
