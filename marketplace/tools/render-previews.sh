#!/bin/sh
# Render production Swing components with synthetic data; no provider calls.
set -eu
TASK_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
TASK_IDE=${IDEA_HOME:-"$HOME/Applications/IntelliJ IDEA.app/Contents"}
if [ -d "$TASK_IDE/Contents" ]; then TASK_IDE="$TASK_IDE/Contents"; fi
TASK_JAVA="$TASK_IDE/jbr/Contents/Home"
if [ ! -x "$TASK_JAVA/bin/javac" ]; then TASK_JAVA=${JAVA_HOME:-"$TASK_IDE/jbr"}; fi
TASK_CLASSES=$(mktemp -d /tmp/agentbridge-gallery.XXXXXX)
trap 'rm -rf "$TASK_CLASSES"' EXIT HUP INT TERM
TASK_CP="$TASK_ROOT/build/classes:$TASK_IDE/lib/*:$TASK_IDE/plugins/terminal/lib/*"
"$TASK_JAVA/bin/javac" --release 21 -encoding UTF-8 -cp "$TASK_CP" -d "$TASK_CLASSES" "$TASK_ROOT/marketplace/tools/RenderPreviews.java"
"$TASK_JAVA/bin/java" -Djava.awt.headless=true -cp "$TASK_CLASSES:$TASK_CP" dev.agentbridge.RenderPreviews "$TASK_ROOT/marketplace/assets"
