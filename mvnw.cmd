@echo off
setlocal
where mvn >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  mvn %*
  exit /b %ERRORLEVEL%
)
set "VERSION=3.9.11"
set "BASE_DIR=%~dp0"
set "MAVEN_HOME=%BASE_DIR%.mvn\apache-maven-%VERSION%"
if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
  if not exist "%BASE_DIR%.mvn" mkdir "%BASE_DIR%.mvn"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $v='%VERSION%'; $zip='%BASE_DIR%.mvn\apache-maven-'+$v+'-bin.zip'; Invoke-WebRequest ('https://archive.apache.org/dist/maven/maven-3/'+$v+'/binaries/apache-maven-'+$v+'-bin.zip') -OutFile $zip; Expand-Archive -Force $zip '%BASE_DIR%.mvn'"
  if errorlevel 1 exit /b 1
)
call "%MAVEN_HOME%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%
