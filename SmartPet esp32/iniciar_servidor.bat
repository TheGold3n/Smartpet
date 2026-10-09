@echo off
title SmartPet Hub - Servidor Simulador ESP32
chcp 65001 >nul
cls
echo ====================================================================
echo          🐾 SMARTPET STATION - SERVIDOR SIMULADOR ESP32 🐾
echo ====================================================================
echo.
echo Verificando dependencias de Python (cryptography)...
python -m pip install cryptography >nul 2>&1

echo.
echo Iniciando servidor TCP en el puerto 5050...
echo.
cd /d "%~dp0"
python simulator_server.py
pause
