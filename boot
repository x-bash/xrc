# shellcheck shell=sh
# shellcheck disable=SC2039

if [ -n "$RELOAD" ] || [ -z "$X_BASH_SRC_PATH" ]; then
    if curl --version 1>/dev/null 2>&1; then
        x_http_get(){
            curl --fail "${1:?Provide target URL}"; 
            local code=$?
            [ $code -eq 28 ] && return 4
            return $code
        }
    else
        x author | grep "Edwin.JH.Lee & LTeam" 1>/dev/null 2>/dev/null || x_activate
        x_http_get(){
            x cat "${1:?Provide target URL}"
        }
    fi

    X_BASH_SRC_SHELL="sh"
    if [ -n "$ZSH_VERSION" ]; then      X_BASH_SRC_SHELL="zsh"
    elif [ -n "$BASH_VERSION" ]; then   X_BASH_SRC_SHELL="bash"
    elif [ -n "$KSH_VERSION" ]; then   X_BASH_SRC_SHELL="ksh"
    fi
    export X_BASH_SRC_SHELL

    # It is NOT set in some cases.
    TMPDIR=${TMPDIR:-$(dirname "$(mktemp -u)")/}
    export TMPDIR

    echo "[x-cmd] Start initializing." >&2

    X_BASH_SRC_PATH="$HOME/.x-cmd/x-bash"
    echo "[x-cmd] Setting env X_BASH_SRC_PATH: $X_BASH_SRC_PATH" >&2

    PATH="$(dirname "$X_BASH_SRC_PATH")/bin:$PATH"

    mkdir -p "$X_BASH_SRC_PATH"

    echo "[x-cmd] Creating $X_BASH_SRC_PATH/.source.mirror.list" >&2
    # shellcheck disable=SC2120
    xrc_mirrors(){
        local fp="$X_BASH_SRC_PATH/.source.mirror.list"
        if [ $# -ne 0 ]; then
            local IFS="$(printf "\n")"
            echo "$*" >"$fp"
            return
        fi
        cat "$fp"
    }
    xrc_mirror "https://x-bash.github.io" "https://x-bash.gitee.io" # https://sh.x-cmd.com

    xrc_clear(){
        if ! grep "xrc_clear()" "$X_BASH_SRC_PATH/boot"; then
            echo "[xrc] '$X_BASH_SRC_PATH/boot' NOT found. Please manually clear cache folder: $X_BASH_SRC_PATH" >&2
            return 1
        fi
        rm -rf "$X_BASH_SRC_PATH";
    }

    xrc_cache(){    echo "$X_BASH_SRC_PATH";    }
    x(){            xrc x/v1;       x "$@";     }
    # shellcheck disable=SC2046
    xrc_cat(){      cat $(xrc_which "$@");      }

    xrc(){
        if [ $# -eq 0 ]; then
            cat >&2 <<A
xrc     x-bash core function.
        Uasge:  xrc <lib> [<lib>...]
        Notice, builtin command 'source' format is 'source <lib> [argument...]'"
A
            return 1
        fi

        while [ $# -eq 0 ]; do
            # shellcheck disable=SC1090
            . "$(_xrc_which_one "$1")" || return
            shift
        done
    }

    xrc_curl(){
        local REDIRECT=/dev/stdout
        if [ -n "$CACHE" ]; then
            if [ -z "$UPDATE" ] && [ -f "$CACHE" ]; then
                xrc_debug "xrc_curl() terminated. Because update is NOT forced and file existed: $CACHE"
                return 0
            fi
            REDIRECT=$TMPDIR.x-bash-temp-download.$RANDOM
        fi

        if x_http_get "$1" 1>"$REDIRECT" 2>/dev/null; then
            if [ -n "$CACHE" ]; then
                xrc_debug "Copy the temp file to CACHE file: $CACHE"
                mkdir -p "$(dirname "$CACHE")"
                mv "$REDIRECT" "$CACHE"
            fi
        else
            return $?
        fi
    }

    xrc_curl_gitx(){   # Simple strategy
        local IFS="$(printf "\n")"
        local i=1
        local mirror
        local mod="${1:?Provide location like str}"
        local mirror_list
        mirror_list="$(xrc_mirrors)"
        for mirror in $mirror_list; do
            xrc_curl "$mirror/$mod"
            case $? in
                0)  [ "$i" -ne 1 ] && xrc_mirrors "$mirror" "$(echo "$mirror_list" | awk "NR!=$i{ print \$0 }" )"
                    return 0;;
                4)  return 4;;
            esac
            i=$((i+1))  # Support both ash, dash, bash
        done
        return 1
    }

    xrc_which(){
        if [ $# -eq 0 ]; then
            cat >&2 <<A
xrc_which  Download lib files and print the local path.
        Uasge:  xrc_which <lib> [<lib>...]
        Example: source "$(xrc_which std/str)"
A
            return 1
        fi
        local i
        for i in "$@"; do
            _xrc_which_one "$i" || return
        done
    }

    _xrc_which_one(){
        local RESOURCE_NAME=${1:?Provide resource name};

        if [ "${RESOURCE_NAME#/}" != "$RESOURCE_NAME" ]; then
            echo "$RESOURCE_NAME"; return 0
        fi

        if [ "${RESOURCE_NAME#\./}" = "$RESOURCE_NAME" ] || [ "${RESOURCE_NAME#\.\./}" = "$RESOURCE_NAME" ]; then
            local tmp
            if tmp="$(cd "$(dirname "$RESOURCE_NAME")" || exit 1; pwd)"; then
                echo "$tmp/$(basename "$RESOURCE_NAME")"
                return 0
            else
                echo "local file not exists: $RESOURCE_NAME" >&2
                return 1
            fi
        fi

        local TGT
        if [ "${RESOURCE_NAME#http://}" = "$RESOURCE_NAME" ] || [ "${RESOURCE_NAME#https://}" = "$RESOURCE_NAME" ]; then
            # that relies on base64?
            TGT="$X_BASH_SRC_PATH/BASE64-URL-$(echo -n "$RESOURCE_NAME" | base64 | tr -d '\r\n')"
            if ! CACHE="$TGT" xrc_curl "$RESOURCE_NAME"; then
                echo "ERROR: Fail to load http resource due to network error or other: $RESOURCE_NAME " >&2
                return 1
            fi

            echo "$TGT"
            return 0
        fi

        local module="$RESOURCE_NAME"
        if [ "${RESOURCE_NAME#*/}" = "$RESOURCE_NAME" ] ; then
            module="$module/latest"         # If it is short alias like str (short for str/latest)
        fi
        TGT="$X_BASH_SRC_PATH/$module"

        if ! CACHE="$TGT" xrc_curl_gitx "$module"; then
            echo "ERROR: Fail to load $RESOURCE_NAME due to network error or other. Do you want to load std/$RESOURCE_NAME?" >&2
            return 1
        fi
        echo "$TGT"
    }

    alias xrcw=xrc_which
    alias xrcc=xrc_cat
fi
