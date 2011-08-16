#!/bin/bash
function die() {
    echo "Error: $*"
    exit 1
}

set -e # fail early

# CD to the top android directory
D=`dirname "$0"`
cd "$D/../../../"

DEST="sdk/eclipse/plugins/com.android.ide.eclipse.adt/libs"
# computes "../.." from DEST to here (in /android)
BACK=`echo $DEST | sed 's@[^/]*@..@g'`

mkdir -p $DEST

LIBS="sdkstats androidprefs common layoutlib_api ide_common rule_api ninepatch sdklib sdkuilib assetstudio"

echo "make java libs ..."
make -j3 showcommands $LIBS || die "ADT: Fail to build one of $LIBS."

echo "Copying java libs to $DEST"

# Prebuilts required by sdklib & co, to be linked/copied in the ADT libs folder
PREBUILTS="\
    prebuilt/common/kxml2/kxml2-2.3.0.jar \
    prebuilt/common/commons-compress/commons-compress-1.0.jar \
    prebuilt/common/http-client/httpclient-4.1.1.jar \
    prebuilt/common/http-client/httpcore-4.1.jar \
    prebuilt/common/http-client/httpmime-4.1.1.jar \
    prebuilt/common/http-client/commons-logging-1.1.1.jar \
    prebuilt/common/http-client/commons-codec-1.4.jar \
    "


HOST=`uname`
if [ "$HOST" == "Linux" ]; then
    for LIB in $LIBS; do
        ln -svf $BACK/out/host/linux-x86/framework/$LIB.jar "$DEST/"
    done

    for P in $PREBUILTS; do
        ln -svf $BACK/$P "$DEST/"
    done
  
elif [ "$HOST" == "Darwin" ]; then
    for LIB in $LIBS; do
        ln -svf $BACK/out/host/darwin-x86/framework/$LIB.jar "$DEST/"
    done

    for P in $PREBUILTS; do
        ln -svf $BACK/$P "$DEST/"
    done

elif [ "${HOST:0:6}" == "CYGWIN" ]; then
    for LIB in $LIBS; do
        cp -vf  out/host/windows-x86/framework/$LIB.jar "$DEST/"
    done

    cp -v $PREBUILTS "$DEST/"
    chmod -v a+rx "$DEST"/*.jar
else
    echo "Unsupported platform ($HOST). Nothing done."
fi
