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

# The following step temporarily disabled: we are running maven in online mode, but it
# actually picks up everything it needs from within the prebuilts or the out folder. The
# current issue is just in creating a local repository that looks as if it has been
# initialized by downloading from a remote repository.

# 2. Create a combined m2 repository that has the tools generated in step 1 and other prebuilts
# This is required so that maven can be run in offline mode in the next step.
# echo Creating a combined tools + prebuilts maven repo...
# COMBINED_M2_REPO="$ANDROID_SRC"/out/host/maven/toolsRepo
# mkdir -p "$COMBINED_M2_REPO"
# cp -r "$ANDROID_SRC"/out/repo "$COMBINED_M2_REPO"
# cp -r "$ANDROID_SRC"/prebuilts/tools/common/m2/repository/* "$COMBINED_M2_REPO"

# 3. Convert the generated Maven repository into a p2 repository
echo Converting maven repo to p2 repo...
cd "$ANDROID_SRC"/sdk/p2gen
"$MAVEN" --no-snapshot-updates \
      -P online \
      -Dmaven.repo.local=../../out/host/maven/toolsRepo \
      p2:site

# 4. Launch Tycho build
echo Launching Tycho to build ADT plugins and bundle
cd "$ANDROID_SRC"/sdk/eclipse
make -f maven.mk

echo "## Copying ADT plugins and bundle into destination folder"
cd "$ANDROID_SRC"
cp -rv out/host/maven/bundles-*/products/*.gz "$DIST"/
cp -rv out/host/maven/bundles-*/products/*.zip "$DIST"/
cp -rv out/host/maven/p2repo-*/p2repo-*.zip "$DIST"/
