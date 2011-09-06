#!/bin/bash
#----------------------------------------------------------------------------|
# Creates the links to use ddm{ui}lib in the eclipse-ide plugin.
# Run this from sdk/eclipse/scripts
#----------------------------------------------------------------------------|

set -e

D=`dirname "$0"`
source $D/common_setup.sh

# cd to the top android directory
cd "$D/../../../"

BASE="sdk/eclipse/plugins/com.android.ide.eclipse.ddms"
DEST=$BASE/libs

mkdir -p $DEST
for i in prebuilt/common/jfreechart/*.jar; do
  cpfile $DEST $i
done

COPY_LIBS="ddmlib ddmuilib"
ALL_LIBS="$COPY_LIBS swtmenubar"
echo "make java libs ..."
make -j3 showcommands $ALL_LIBS || die "DDMS: Fail to build one of $ALL_LIBS."

for LIB in $COPY_LIBS; do
    cpfile $DEST out/host/$PLATFORM/framework/$LIB.jar
done

if [ "${HOST:0:6}" == "CYGWIN" ]; then
    # On Windows we used to make a hard copy of the ddmlib/ddmuilib
    # under the plugin source tree. Now that we're using external JARs
    # we need to actually remove these obsolete sources.
    for i in ddmlib ddmuilib ; do
        DIR=$BASE/src/com/android/$i
        if [ -d $DIR ]; then
            rm -rfv $BASE/src/com/android/$i
        fi
    done
fi
