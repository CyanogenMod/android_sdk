function die() {
    echo "Error: $*"
    exit 1
}

HOST=`uname`

if [ "${HOST:0:6}" == "CYGWIN" ]; then
    PLATFORM="windows-x86"

    # We can't use symlinks under Cygwin
    function cpfile { # $1=dest $2=source
        cp -fv $2 $1/
    }

    function cpdir() { # $1=dest $2=source
        rsync -avW --delete-after $2 $1
    }
else
    if [ "$HOST" == "Linux" ]; then
        PLATFORM="linux-x86"
    elif [ "$HOST" == "Darwin" ]; then
        PLATFORM="darwin-x86"
    else
        echo "Unsupported platform ($HOST). Nothing done."
    fi

    # For all other systems which support symlinks

    # computes the "reverse" path, e.g. "a/b/c" => "../../.."
    function back() {
        echo $1 | sed 's@[^/]*@..@g'
    }

    function cpfile { # $1=dest $2=source
        ln -svf `back $1`/$2 $1/
    }

    function cpdir() { # $1=dest $2=source
        ln -svf `back $1`/$2 $1
    }
fi
