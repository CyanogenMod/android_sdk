#!/bin/bash
# Expected arguments:
# $1 = out_dir
# $2 = dist_dir
# $3 = build_number

# exit on error
set -e

if [ $# -ne 3 ]
then
  echo "Usage: $0 <out_dir> <dest_dir> <build_number>" > /dev/stderr
  echo "Given arguments: $*" > /dev/stderr
  exit 1
fi

PROG_DIR=$(dirname "$0")

cd "$PROG_DIR"/../../..
ANDROID_SRC="$PWD"

MAVEN="$ANDROID_SRC"/prebuilts/eclipse/maven/apache-maven-3.2.1/bin/mvn

OUT="$1"
DIST="$2"
BNUM="$3"

echo "ANDROID_SRC=$ANDROID_SRC"
echo "OUT=$OUT"
echo "DIST=$DIST"
echo "BNUM=$BNUM"

# Steps to build Eclipse
# 1. Generate Maven repository containing all tools
echo Running gradle to build tools libraries...
cd "$ANDROID_SRC"/tools
./gradlew --no-daemon publishLocal

# 2. Copy dependent jars into the libs folder of each plugin
cd "$ANDROID_SRC"/sdk/eclipse
../../tools/gradlew --no-daemon copydeps

# 3. Launch Tycho build
echo Launching Tycho to build ADT plugins and bundle
cd "$ANDROID_SRC"/sdk/eclipse
make -f maven.mk

echo "## Copying ADT plugins and bundle into destination folder"
cd "$ANDROID_SRC"
cp -rv out/host/maven/bundles-*/products/*.gz "$DIST"/
cp -rv out/host/maven/bundles-*/products/*.zip "$DIST"/
cp -rv out/host/maven/p2repo-*/p2repo-*.zip "$DIST"/
