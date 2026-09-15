#!/bin/sh
#
# Copyright © 2015-2021 the original authors.
# Gradle wrapper shell script — Linux/macOS

##############################################################################
# JVM settings
##############################################################################
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

APP_HOME=`pwd -P`

MAX_FD="maximum"

warn () {
    echo "$*"
}

die () {
    echo
    echo "$*"
    echo
    exit 1
}

if ! command -v unzip > /dev/null; then
    die "ERROR: unzip not found"
fi

if [ -z "$JAVA_HOME" ] ; then
    JAVA_EXE=java
else
    JAVA_EXE="$JAVA_HOME/bin/java"
fi

if [ ! -f "$JAVA_EXE" ] ; then
    JAVA_EXE=$(command -v java) || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
fi

GRADLE_WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$GRADLE_WRAPPER_JAR" ]; then
    die "ERROR: gradle-wrapper.jar not found at $GRADLE_WRAPPER_JAR"
fi

exec "$JAVA_EXE" $DEFAULT_JVM_OPTS "$@" \
  -classpath "$GRADLE_WRAPPER_JAR" \
  org.gradle.wrapper.GradleWrapperMain "$@"
