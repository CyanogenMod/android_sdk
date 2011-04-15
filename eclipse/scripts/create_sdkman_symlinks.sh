#!/bin/bash
function die() {
    echo "Error: $*"
    exit 1
}

set -e # fail early

# CD to the top android directory
D=`dirname "$0"`
cd "$D/../../../"

LIBS="swtmenubar"

echo "SDK Manager: make java libs $LIBS"
make -j3 showcommands $LIBS || die "SDK Manager: Failed to build one of $LIBS."
