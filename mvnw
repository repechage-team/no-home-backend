#!/bin/sh

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
WRAPPER_JAR="$BASE_DIR/.mvn/wrapper/maven-wrapper.jar"

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA_EXE="$JAVA_HOME/bin/java"
else
  JAVA_EXE="java"
fi

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "Maven wrapper jar not found: $WRAPPER_JAR" >&2
  echo "Restore .mvn/wrapper/maven-wrapper.jar before running this script." >&2
  exit 1
fi

exec "$JAVA_EXE" "-Dmaven.multiModuleProjectDirectory=$BASE_DIR" -classpath "$WRAPPER_JAR" org.apache.maven.wrapper.MavenWrapperMain "$@"
