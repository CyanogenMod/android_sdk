#!/bin/bash

echo "## Running $0"
# CD to the top android directory
PROG_DIR=`dirname "$0"`
cd "${PROG_DIR}/../../../"

HOST=`uname`

function die() {
  echo "Error: $*"
  exit 1
}

if [ "${HOST:0:6}" == "CYGWIN" ]; then
  PLATFORM="windows-x86"

  # We can't use symlinks under Cygwin
  function cpfile { # $1=source $2=dest
    cp -fv $1 $2/
  }

  function cpdir() { # $1=source $2=dest
    rsync -avW --delete-after $1 $2
  }
else
  if [ "$HOST" == "Linux" ]; then
    PLATFORM="linux-x86"
  elif [ "$HOST" == "Darwin" ]; then
    PLATFORM="darwin-x86"
  else
    echo "Unsupported platform ($HOST). Aborting."
    exit 1
  fi

  # For all other systems which support symlinks

  # computes the "reverse" path, e.g. "a/b/c" => "../../.."
  function back() {
    echo $1 | sed 's@[^/]*@..@g'
  }

  function cpfile { # $1=source $2=dest
    ln -svf `back $2`/$1 $2/
  }

  function cpdir() { # $1=source $2=dest
    ln -svf `back $2`/$1 $2
  }
fi

DEST="sdk/eclipse/scripts"

set -e # fail early

LIBS=""
CP_FILES=""

### ADT ###

ADT_DEST="sdk/eclipse/plugins/com.android.ide.eclipse.adt/libs"
ADT_LIBS="sdkstats androidprefs common layoutlib_api lint_api lint_checks ide_common rule_api ninepatch sdklib sdkuilib assetstudio"
ADT_PREBUILTS="\
    prebuilt/common/kxml2/kxml2-2.3.0.jar \
    prebuilt/common/commons-compress/commons-compress-1.0.jar \
    prebuilt/common/http-client/httpclient-4.1.1.jar \
    prebuilt/common/http-client/httpcore-4.1.jar \
    prebuilt/common/http-client/httpmime-4.1.1.jar \
    prebuilt/common/http-client/commons-logging-1.1.1.jar \
    prebuilt/common/http-client/commons-codec-1.4.jar"

LIBS="$LIBS $ADT_LIBS"
CP_FILES="$CP_FILES @:$ADT_DEST $ADT_LIBS $ADT_PREBUILTS"


### DDMS ###

DDMS_DEST="sdk/eclipse/plugins/com.android.ide.eclipse.ddms/libs"
DDMS_LIBS="ddmlib ddmuilib swtmenubar"

DDMS_PREBUILTS="\
    prebuilt/common/jfreechart/jcommon-1.0.12.jar \
    prebuilt/common/jfreechart/jfreechart-1.0.9.jar \
    prebuilt/common/jfreechart/jfreechart-1.0.9-swt.jar"

LIBS="$LIBS $DDMS_LIBS"
CP_FILES="$CP_FILES @:$DDMS_DEST $DDMS_LIBS $DDMS_PREBUILTS"


### TEST ###

TEST_DEST="sdk/eclipse/plugins/com.android.ide.eclipse.tests"
TEST_LIBS="easymock"
TEST_PREBUILTS="prebuilt/common/kxml2/kxml2-2.3.0.jar"

LIBS="$LIBS $TEST_LIBS"
CP_FILES="$CP_FILES @:$TEST_DEST $TEST_LIBS $TEST_PREBUILTS"


### BRIDGE ###

if [[ $PLATFORM != "windows-x86" ]]; then
  # We can't build enough of the platform on Cygwin to create layoutlib
  BRIDGE_LIBS="layoutlib ninepatch"

  LIBS="$LIBS $BRIDGE_LIBS"
fi



### HIERARCHYVIEWER ###

HV_DEST="sdk/eclipse/plugins/com.android.ide.eclipse.hierarchyviewer/libs"
HV_LIBS="hierarchyviewerlib swtmenubar"

LIBS="$LIBS $HV_LIBS"
CP_FILES="$CP_FILES @:$HV_DEST $HV_LIBS"


### TRACEVIEW ###

TV_DEST="sdk/eclipse/plugins/com.android.ide.eclipse.traceview/libs"
TV_LIBS="traceview"

LIBS="$LIBS $TV_LIBS"
CP_FILES="$CP_FILES @:$TV_DEST $TV_LIBS"


### SDKMANAGER ###

SDMAN_LIBS="swtmenubar"

LIBS="$LIBS $SDKMAN_LIBS"


### GL DEBUGGER ###

if [[ $PLATFORM != "windows-x86" ]]; then
  # liblzf doesn't build under cygwin. If necessary, this should be fixed first.
  
  GLD_DEST="sdk/eclipse/plugins/com.android.ide.eclipse.gldebugger/libs"
  GLD_LIBS="host-libprotobuf-java-2.3.0-lite liblzf sdklib ddmlib"

  LIBS="$LIBS $GLD_LIBS"
  CP_FILES="$CP_FILES @:$GLD_DEST $GLD_LIBS"
fi

# Run make on all libs

J="4"
[[ $(uname) == "Darwin" ]] && J=$(sysctl hw.ncpu | cut -d : -f 2 | tr -d ' ')
[[ $(uname) == "Linux"  ]] && J=$(cat /proc/cpuinfo | grep processor | wc -l)

echo "## Building libs: make -j$J $LIBS"
make -j${J} $LIBS

# Copy resulting files

DEST=""
for SRC in $CP_FILES; do
  if [[ "${SRC:0:2}" == "@:" ]]; then
    DEST="${SRC:2}"
    mkdir -vp "$DEST"
    continue
  fi
  if [[ ! -f "$SRC" ]]; then
    SRC="out/host/$PLATFORM/framework/$SRC.jar"
  fi
  if [[ -f "$SRC" ]]; then
    if [[ ! -d "$DEST" ]]; then
      die "Invalid cp_file dest directory: $DEST"
    fi

    cpfile "$SRC" "$DEST"
  else
    die "## Unknown file '$SRC' to copy in '$DEST'"
  fi
done

# OS-specific post operations

if [ "${HOST:0:6}" == "CYGWIN" ]; then
  chmod -v a+rx "$ADT_DEST"/*.jar
fi

echo "### $0 done"
