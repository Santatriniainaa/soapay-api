#!/bin/sh
# Lightweight Maven Wrapper bootstrapper. Downloads the pinned Maven distribution on first use.
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROPERTIES_FILE="$SCRIPT_DIR/.mvn/wrapper/maven-wrapper.properties"
DISTRIBUTION_URL=$(sed -n 's/^distributionUrl=//p' "$PROPERTIES_FILE" | head -n 1)

if [ -z "$DISTRIBUTION_URL" ]; then
    echo "Maven Wrapper: distributionUrl is missing from $PROPERTIES_FILE" >&2
    exit 1
fi

ARCHIVE_NAME=${DISTRIBUTION_URL##*/}
DISTRIBUTION_DIR=${ARCHIVE_NAME%-bin.zip}
MAVEN_USER_HOME=${MAVEN_USER_HOME:-"${HOME}/.m2"}
CACHE_DIR="$MAVEN_USER_HOME/wrapper/dists/$DISTRIBUTION_DIR"
MAVEN_HOME="$CACHE_DIR/$DISTRIBUTION_DIR"
MAVEN_BIN="$MAVEN_HOME/bin/mvn"

if [ ! -x "$MAVEN_BIN" ]; then
    mkdir -p "$CACHE_DIR"
    ARCHIVE_PATH="$CACHE_DIR/$ARCHIVE_NAME"
    echo "Downloading Apache Maven from $DISTRIBUTION_URL"

    if command -v curl >/dev/null 2>&1; then
        curl --fail --location --silent --show-error "$DISTRIBUTION_URL" --output "$ARCHIVE_PATH"
    elif command -v wget >/dev/null 2>&1; then
        wget --quiet "$DISTRIBUTION_URL" --output-document="$ARCHIVE_PATH"
    else
        echo "Maven Wrapper: curl or wget is required to download Maven." >&2
        exit 1
    fi

    if ! command -v unzip >/dev/null 2>&1; then
        echo "Maven Wrapper: unzip is required to extract Maven." >&2
        exit 1
    fi
    unzip -q -o "$ARCHIVE_PATH" -d "$CACHE_DIR"
    rm -f "$ARCHIVE_PATH"
fi

exec "$MAVEN_BIN" "$@"
