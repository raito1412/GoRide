@echo off
rem Mở nhiều cửa sổ client GoRide cùng lúc (Windows).
rem   run.bat 4          -> mở 4 client (server phải đang chạy)
rem   run.bat server 4   -> mở server trong cửa sổ riêng rồi mở 4 client
rem   run.bat            -> mở 1 client
chcp 65001 >nul
cd /d "%~dp0"
setlocal enabledelayedexpansion

set START_SERVER=0
if /i "%~1"=="server" (set START_SERVER=1& shift)
set N=%~1
if "%N%"=="" set N=1
set /a CHECK=%N% 2>nul
if %CHECK% LSS 1 (echo So cua so phai tu 1 den 20. Vi du: run.bat 4& exit /b 1)
if %CHECK% GTR 20 (echo So cua so phai tu 1 den 20. Vi du: run.bat 4& exit /b 1)

echo Bien dich...
call mvn -q compile || exit /b 1

if %START_SERVER%==1 (
  start "GoRide server" cmd /k mvn -q -Pserver exec:java
  echo Doi server khoi dong 10 giay...
  timeout /t 10 /nobreak >nul
)

for /l %%i in (1,1,%CHECK%) do (
  start "GoRide client #%%i" /min cmd /c mvn -q exec:java -Dgoride.slot=%%i
  echo Da mo client #%%i
)
