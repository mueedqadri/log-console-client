@echo off
setlocal
set "PROJECT_DIR=%~dp0.."
java -jar "%PROJECT_DIR%\target\log-console.jar" --config "%PROJECT_DIR%\config\log-console.json" %*
exit /b %ERRORLEVEL%
