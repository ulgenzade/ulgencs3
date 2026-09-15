#!/bin/sh

#
# Copyright © 2015-2021 the original authors.
# Licensed under the Apache License, Version 2.0
#

##############################################################################
# Gradle startup script for POSIX
##############################################################################

# Resolve APP_HOME
app_path=$0
while [ -h "$app_path" ] ; do
    ls=$(ls -ld "$app_path")
    link=$(expr "$ls" : '.*-> \(.*\)$')
    if expr "$link" : '/.*' > /dev/null; then
        app_path="$link"
    else
        app_path=$(dirname "$app_path")"/$link"
    fi
done
APP_HOME=$(dirname "$app_path")
APP_HOME=$(cd "$APP_HOME" && pwd)
APP_BASE_NAME=$(basename "$0")

MAX_FD=maximum

warn() { echo "$*"; } >&2
die() { echo; echo "$*"; echo; exit 1; } >&2

# OS detection
cygwin=false
darwin=false
msys=false
nonstop=false
case "$(uname)" in
  CYGWIN*)  cygwin=true  ;;
  Darwin*)  darwin=true  ;;
  MSYS*|MINGW*) msys=true ;;
  NONSTOP*) nonstop=true ;;
esac

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m" "-Dfile.encoding=UTF-8" "-Duser.country=US" "-Duser.language=en" "-Duser.variant"'

# Darwin dock options
if $darwin; then
    GRADLE_OPTS="$GRADLE_OPTS \"-Xdock:name=$APP_BASE_NAME\" \"-Xdock:icon=$APP_HOME/media/gradle.icns\""
fi

# Find java command
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
    fi
else
    JAVACMD=java
    command -v java > /dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command found in PATH."
fi

# Increase max file descriptors
if ! "$cygwin" && ! "$darwin" && ! "$nonstop" ; then
    case $MAX_FD in
      max*) MAX_FD=$(ulimit -H -n) || warn "Could not query max fd limit" ;;
    esac
    case $MAX_FD in
      ''|soft) : ;;
      *) ulimit -n "$MAX_FD" || warn "Could not set max fd limit to $MAX_FD" ;;
    esac
fi

# Cygwin path conversion
if $cygwin ; then
    APP_HOME=$(cygpath --path --mixed "$APP_HOME")
    CLASSPATH=$(cygpath --path --mixed "$CLASSPATH")
fi

# Build argument list — save() must be defined before use
save() {
    for i do
        printf '%s\n' "$i" | sed "s/'/'\\\\''/g;1s/^/'/;\$s/\$/'/ p"
    done
}

APP_ARGS=$(save "$@")

# Execute Gradle
eval exec '"$JAVACMD"' $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS '"-Dorg.gradle.appname=$APP_BASE_NAME"' -classpath '"$CLASSPATH"' org.gradle.wrapper.GradleWrapperMain "$APP_ARGS"