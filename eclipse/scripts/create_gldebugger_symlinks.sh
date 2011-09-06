#!/bin/bash
#----------------------------------------------------------------------------|
# Creates the links to use gldebugger in the eclipse-ide plugin.
# Run this from sdk/eclipse/scripts
#----------------------------------------------------------------------------|

set -e

D=`dirname "$0"`
source $D/common_setup.sh

# cd to the top android directory
cd "$D/../../../"

BASE="sdk/eclipse/plugins/com.android.ide.eclipse.gldebugger"
DEST=$BASE/libs

mkdir -p $DEST

LIBS="host-libprotobuf-java-2.3.0-lite liblzf sdklib"
echo "make java libs ..."
make -j3 $LIBS || die "GL Debugger: Fail to build one of $LIBS."

for LIB in $LIBS; do
    cpfile $DEST out/host/$PLATFORM/framework/$LIB.jar
done
