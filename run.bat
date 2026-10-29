@echo off
setlocal enabledelayedexpansion
title Synchro Willy - Herramientas de Diagnostico (JavaFX)
cd /d "%~dp0"

echo ===============================================================================
echo   Synchro Willy - Suite de Diagnostico de Red y Sistema para Windows
echo   Desarrollador: Bryan Espinoza
echo ===============================================================================
echo.

set MVN_CMD=mvn
where mvn >nul 2>nul
if %ERRORLEVEL% neq 0 (
    if exist "%USERPROFILE%\apache-maven\bin\mvn.cmd" set MVN_CMD="%USERPROFILE%\apache-maven\bin\mvn.cmd"
    if exist "C:\apache-maven\bin\mvn.cmd" set MVN_CMD="C:\apache-maven\bin\mvn.cmd"
    if exist "C:\Program Files\apache-maven\bin\mvn.cmd" set MVN_CMD="C:\Program Files\apache-maven\bin\mvn.cmd"
    if exist "C:\Program Files\Maven\bin\mvn.cmd" set MVN_CMD="C:\Program Files\Maven\bin\mvn.cmd"
)

where %MVN_CMD% >nul 2>nul
if %ERRORLEVEL% equ 0 goto run_mvn
if exist %MVN_CMD% goto run_mvn

goto run_direct_java

:run_mvn
echo [Synchro Willy] Ejecutando aplicacion con JavaFX Maven Plugin...
call %MVN_CMD% compile javafx:run
if %ERRORLEVEL% equ 0 goto end
echo.
echo [Synchro Willy] Intentando modo directo con Java...

:run_direct_java
set JAVA_CMD=java
where java >nul 2>nul
if %ERRORLEVEL% neq 0 (
    if exist "%USERPROFILE%\.antigravity-ide\extensions\redhat.java-1.58.0-win32-x64\jre\25.0.4.1-win32-x86_64\bin\java.exe" (
        set JAVA_CMD="%USERPROFILE%\.antigravity-ide\extensions\redhat.java-1.58.0-win32-x64\jre\25.0.4.1-win32-x86_64\bin\java.exe"
    )
)

echo [Synchro Willy] Detectando dependencias JavaFX en cache local...
set CP=target\classes
for /r "%USERPROFILE%\.m2\repository" %%F in (*.jar) do (
    set "CP=!CP!;%%F"
)

echo [Synchro Willy] Iniciando Synchro Willy...
%JAVA_CMD% -cp "!CP!" com.fixtool.Launcher

:end
if %ERRORLEVEL% neq 0 (
    echo.
    echo [Synchro Willy] Finalizo la ejecucion.
    pause
)
