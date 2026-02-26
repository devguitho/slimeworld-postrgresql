@echo off
echo ========================================
echo  SlimeWorldManager - Build Rapido
echo  (Sem executar testes)
echo ========================================
echo.

REM Verificar se Maven está instalado
where mvn >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERRO] Maven nao encontrado!
    echo Execute o script 'build.bat' para ver as instrucoes.
    pause
    exit /b 1
)

REM Verificar se Spigot 1.8.8 está instalado
if not exist "%USERPROFILE%\.m2\repository\org\spigotmc\spigot\1.8.8-R0.1-SNAPSHOT" (
    echo [AVISO] Spigot 1.8.8 nao encontrado.
    echo [INFO] Execute 'build.bat' primeiro para instalar o Spigot.
    pause
    exit /b 1
)

echo [INFO] Iniciando build rapido (sem testes)...
echo.

REM Executar o build sem testes
call mvn clean install -DskipTests

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo  BUILD CONCLUIDO!
    echo ========================================
    echo.
    echo Plugin compilado em:
    echo slimeworldmanager-plugin\target\slimeworldmanager-plugin-2.2.0-SNAPSHOT.jar
    echo.
) else (
    echo.
    echo [ERRO] Build falhou! Verifique os erros acima.
    echo.
)

pause
