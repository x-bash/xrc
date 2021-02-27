# shellcheck shell=sh disable=SC2039,SC1090

if [ -n "$RELOAD" ] || [ -z "$X_BASH_SRC_PATH" ]; then

    if curl --version 1>/dev/null 2>&1; then
        _xrc_http_get(){
            curl --fail "${1:?Provide target URL}"; 
            local code=$?
            [ $code -eq 28 ] && return 4
            return $code
        }
    elif [ "$(x author 2>/dev/null)" = "ljh & LTeam" ]; then
        alias _xrc_http_get="x cat"
    else
        printf "boot[ERR]: Cannot found curl or x-cmd binary for web resource downloader." >&2
        return 1 || exit 1
    fi

    _xrc_debug(){            [ "$XRC_DBG_FLAG" ] &&  printf "xrc[DBG] : %s\n" "$*" >&2;              }
    _xrc_log(){                                      printf "xrc[${LEVEL:-INF}]: %s\n" "$*" >&2;     }

    xrc_curl(){
        local REDIRECT=/dev/stdout
        if [ -n "$CACHE" ]; then
            if [ -z "$UPDATE" ] && [ -f "$CACHE" ]; then
                _xrc_debug "Function xrc_curl() terminated. Because local cache existed with update flag unset: $CACHE"
                return 0
            fi
            REDIRECT=$TMPDIR.x-bash-temp-download.$RANDOM
        fi

        if _xrc_http_get "$1" 1>"$REDIRECT" 2>/dev/null; then
            if [ -n "$CACHE" ]; then
                _xrc_debug "Copy the temp file to CACHE file: $CACHE"
                mkdir -p "$(dirname "$CACHE")"
                mv "$REDIRECT" "$CACHE"
            fi
        else
            local code=$?
            LEVEL=WARN _xrc_log "_xrc_http_get $1 return code: $code. Fail to retrieve file from: $1"
            [ -n "$CACHE" ] && rm "$REDIRECT"
            return $code
        fi
    }

    X_CMD_SRC_SHELL="sh"
    if      [ -n "$ZSH_VERSION" ];  then    X_CMD_SRC_SHELL="zsh"
    elif    [ -n "$BASH_VERSION" ]; then    X_CMD_SRC_SHELL="bash"
    elif    [ -n "$KSH_VERSION" ];  then    X_CMD_SRC_SHELL="ksh"
    fi
    export X_CMD_SRC_SHELL

    TMPDIR=${TMPDIR:-$(dirname "$(mktemp -u)")/}    # It is posix standard. BUT NOT set in some cases.
    export TMPDIR

    _xrc_debug "Setting env X_BASH_SRC_PATH: $X_BASH_SRC_PATH"
    X_BASH_SRC_PATH="$HOME/.x-cmd/x-bash"           # boot will be placed in "$HOME/.x-cmd/boot"
    mkdir -p "$X_BASH_SRC_PATH"
    PATH="$(dirname "$X_BASH_SRC_PATH")/bin:$PATH"
    
    xrc(){
        [ $# -eq 0 ] && set -- "help"
        case "$1" in
            help)   cat >&2 <<A
xrc     x-bash core function.
        Uasge:  xrc <lib> [<lib>...]
        Notice, builtin command 'source' format is 'source <lib> [argument...]'"
        Please visit hosting repository for more information: https://gitee.com/x-bash/x-bash, https://github.com/x-bash/x-bash.github.io, https://gitlab.com/x-bash/x-bash/x-bash, or https://bitbucket.com/x-bash/x-bash/x-bash

Subcommand:
        cat|c       Provide cat facility
        which|w     Provide local cache file location
        update      Update file
        cache       Provide cache filepath
        clear       Clear the cache
A
                    return ;;
            c|cat)  shift;
                    eval "$(t="cat" _xrc_source_file_list_code "$@")" ;;
            w|which)  shift;
                    if [ $# -eq 0 ]; then
                        cat >&2 <<A
xrc which  Download lib files and print the local path.
        Uasge:  xrc which <lib> [<lib>...]
        Example: source "$(xrc_which std/str)"
A
                        return 1
                    fi
                    eval "$(t="echo" _xrc_source_file_list_code "$@")"  ;;
            update) shift;  UPDATE=1 xrc which "$@" 1>/dev/null 2>&1 ;;
            upgrade)shift;  eval "$(curl https://get.x-cmd.com/script)" ;;
            cache)  shift;  echo "$X_BASH_SRC_PATH" ;;
            clear)  shift;
                    if ! grep "xrc_clear()" "$X_BASH_SRC_PATH/../boot" >/dev/null 2>&1; then
                        _xrc_debug "'$X_BASH_SRC_PATH/../boot' NOT found. Please manually clear cache folder: $X_BASH_SRC_PATH"
                        return 1
                    fi
                    rm -rf "$X_BASH_SRC_PATH" ;;
            mirror) shift;
                    local fp="$X_BASH_SRC_PATH/.source.mirror.list"
                    if [ $# -ne 0 ]; then
                        local IFS="
"; 
                        echo "$*" >"$fp"
                        return
                    fi
                    cat "$fp"
                    return ;;
            *)      eval "$(t="." _xrc_source_file_list_code "$@")"
        esac
    }

    _xrc_source_file_list_code(){
        local code=""
        while [ $# -ne 0 ]; do
            if ! code="$code
            ${t:-.} \"$(_xrc_which_one "$1")\""; then
                echo "return 1"
                return 0
            fi
            shift
        done
        echo "$code"
    }

    _xrc_debug "Creating $X_BASH_SRC_PATH/.source.mirror.list"
    xrc mirror "https://x-bash.github.io" "https://x-bash.gitee.io" # "https://sh.x-cmd.com"

    _xrc_curl_gitx(){   # Simple strategy
        local IFS="
"
        local i=1
        local mirror
        local mod="${1:?Provide location like str}"
        local mirror_list
        mirror_list="$(xrc mirror)"
        for mirror in $mirror_list; do
            _xrc_debug "Fuck... $i $mirror"
            xrc_curl "$mirror/$mod"
            case $? in
                0)  if [ "$i" -ne 1 ]; then
                        _xrc_debug "Default mirror now is $mirror"
                        xrc mirror "$mirror" "$(echo "$mirror_list" | awk "NR!=$i{ print \$0 }" )"
                    fi
                    return 0;;
                4)  return 4;;
            esac
            i=$((i+1))  # Support both ash, dash, bash
        done
        return 1
    }

    _xrc_which_one(){
        local RESOURCE_NAME=${1:?Provide resource name};

        if [ "${RESOURCE_NAME#/}" != "$RESOURCE_NAME" ]; then
            echo "$RESOURCE_NAME"; return 0
        fi

        if [ "${RESOURCE_NAME#\./}" != "$RESOURCE_NAME" ] || [ "${RESOURCE_NAME#\.\./}" != "$RESOURCE_NAME" ]; then
            local tmp
            if tmp="$(cd "$(dirname "$RESOURCE_NAME")" || exit 1; pwd)"; then
                echo "$tmp/$(basename "$RESOURCE_NAME")"
                return 0
            else
                LEVEL=WARN _xrc_log "Local file not exists: $RESOURCE_NAME"
                return 1
            fi
        fi

        local TGT
        if [ "${RESOURCE_NAME#http://}" != "$RESOURCE_NAME" ] || [ "${RESOURCE_NAME#https://}" != "$RESOURCE_NAME" ]; then
            TGT="$X_BASH_SRC_PATH/BASE64-URL-$(printf "%s" "$RESOURCE_NAME" | base64 | tr -d '\r\n')"
            if ! CACHE="$TGT" xrc_curl "$RESOURCE_NAME"; then
                _xrc_debug "ERROR: Fail to load http resource due to network error or other: $RESOURCE_NAME "
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

        if ! CACHE="$TGT" _xrc_curl_gitx "$module"; then
            LEVEL=WARN _xrc_log "ERROR: Fail to load module due to network error or other: $RESOURCE_NAME"
            return 1
        fi
        echo "$TGT"
    }

    # xrc x comp/xrc comp/x
fi
