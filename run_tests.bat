@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo ================================================================================
echo   ZKTeco Automated Test Runner
echo ================================================================================
echo [1/3] Checking bin directory...
if not exist bin mkdir bin
echo [2/3] Compiling Java source files...
javac -encoding UTF-8 -d bin -sourcepath src src/main/*.java src/main/zk/*.java
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Compilation failed with exit code %ERRORLEVEL%!
    exit /b %ERRORLEVEL%
)
echo [OK] Compilation successful!
echo.
echo [3/3] Running main.Main test suite...
java -cp bin main.Main
echo.
echo ================================================================================
