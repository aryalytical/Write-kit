#!/bin/sh
set -eu
# Bootstrap the official Gradle Wrapper when Gradle 9.4.1 is installed on PATH.
if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle is not on PATH. Install Gradle 9.4.1, then rerun this script." >&2
  exit 1
fi
if ! gradle --version | grep -q 'Gradle 9.4.1'; then
  echo "Please install Gradle 9.4.1 and ensure that version is first on PATH." >&2
  exit 1
fi
gradle wrapper --gradle-version 9.4.1 --distribution-type bin
echo "Wrapper generated. Verify with ./gradlew --version"
