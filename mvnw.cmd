@echo off
setlocal

set "BASE_DIR=%~dp0"
set "PROJECT_BASE=%BASE_DIR:~0,-1%"
set "WRAPPER_JAR=%PROJECT_BASE%\.mvn\wrapper\maven-wrapper.jar"

if defined JAVA_HOME (
  if exist "%JAVA_HOME%\bin\java.exe" (
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
  )
)

if not defined JAVA_EXE set "JAVA_EXE=java"

if not exist "%WRAPPER_JAR%" (
  echo Maven wrapper jar not found: %WRAPPER_JAR% 1>&2
  echo Restore .mvn\wrapper\maven-wrapper.jar before running this script. 1>&2
  exit /b 1
)

"%JAVA_EXE%" "-Dmaven.multiModuleProjectDirectory=%PROJECT_BASE%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
exit /b %ERRORLEVEL%
