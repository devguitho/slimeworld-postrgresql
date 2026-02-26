@echo off
echo ========================================
echo  SlimeWorldManager Build Script
echo  Com suporte ao PostgreSQL
echo ========================================
echo.

REM Verificar se Maven está instalado
where mvn >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERRO] Maven nao encontrado!
    echo.
    echo Por favor, instale o Maven:
    echo 1. Baixe em: https://maven.apache.org/download.cgi
    echo 2. Extraia para C:\Program Files\Apache\maven
    echo 3. Adicione ao PATH: C:\Program Files\Apache\maven\bin
    echo 4. Reinicie o terminal e execute este script novamente
    echo.
    pause
    exit /b 1
)

REM Verificar se Java está instalado
where java >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERRO] Java nao encontrado!
    echo.
    echo Por favor, instale o Java JDK 17:
    echo https://adoptium.net/temurin/releases/?version=17
    echo.
    pause
    exit /b 1
)

echo [INFO] Maven encontrado: 
call mvn --version | findstr "Apache Maven"
echo.

echo [INFO] Java encontrado:
java -version 2>&1 | findstr "version"
echo.

REM Verificar se Spigot 1.8.8 está instalado
if not exist "%USERPROFILE%\.m2\repository\org\spigotmc\spigot\1.8.8-R0.1-SNAPSHOT" (
    echo [AVISO] Spigot 1.8.8 nao encontrado no repositorio Maven local.
    echo [INFO] Baixando e compilando Spigot 1.8.8...
    echo [INFO] Isso pode demorar 5-10 minutos na primeira vez.
    echo.
    
    if not exist "buildtools" mkdir buildtools
    cd buildtools
    
    echo [INFO] Baixando BuildTools...
    curl -o BuildTools.jar https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar
    
    if %ERRORLEVEL% NEQ 0 (
        echo [INFO] Tentando com PowerShell...
        powershell -Command "Invoke-WebRequest -Uri 'https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar' -OutFile 'BuildTools.jar'"
    )
    
    echo [INFO] Compilando Spigot 1.8.8...
    java -jar BuildTools.jar --rev 1.8.8
    
    cd ..
    echo.
)

echo [INFO] Iniciando build...
echo.

REM Executar o build
call mvn clean install -DskipTests

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo  BUILD CONCLUIDO COM SUCESSO!
    echo ========================================
    echo.
    echo O plugin foi compilado em:
    echo slimeworldmanager-plugin\target\slimeworldmanager-plugin-2.2.0-SNAPSHOT.jar
    echo.
    echo Copie este arquivo para a pasta 'plugins' do seu servidor.
    echo.
) else (
    echo.
    echo ========================================
    echo  BUILD FALHOU!
    echo ========================================
    echo.
    echo Verifique os erros acima.
    echo.
)

pause
