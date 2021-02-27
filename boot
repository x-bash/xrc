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

    xrc_debug(){            [ "$XRC_DBG_XRC" ] &&  printf "xrc[DBG] : %s\n" "$*" >&2;              }
    _xrc_log(){                                      printf "xrc[${LEVEL:-INF}]: %s\n" "$*" >&2;     }

    xrc_curl(){
        local REDIRECT=/dev/stdout
        if [ -n "$CACHE" ]; then
            if [ -z "$UPDATE" ] && [ -f "$CACHE" ]; then
                xrc_debug "Function xrc_curl() terminated. Because local cache existed with update flag unset: $CACHE"
                return 0
            fi
            REDIRECT=$TMPDIR.x-bash-temp-download.$RANDOM
        fi

        if _xrc_http_get "$1" 1>"$REDIRECT" 2>/dev/null; then
            if [ -n "$CACHE" ]; then
                xrc_debug "Copy the temp file to CACHE file: $CACHE"
                mkdir -p "$(dirname "$CACHE")"
                mv "$REDIRECT" "$CACHE"
            fi
        else
            local code=$?
            xrc_debug "_xrc_http_get $1 return code: $code. Fail to retrieve file from: $1"
            [ -n "$CACHE" ] && rm "$REDIRECT"
            return $code
        fi
    }
    
    xrc(){
        [ $# -eq 0 ] && set -- "help"
        case "$1" in
            help)   cat >&2 <<A
xrc     x-bash core function.
        Uasge:  xrc <lib> [<lib>...]
        Notice, builtin command 'source' format is 'source <lib> [argument...]'"
        Please visit following hosting repository for more information:
            https://gitee.com/x-bash/x-bash
            https://github.com/x-bash/x-bash.github.io
            https://gitlab.com/x-bash/x-bash
            https://bitbucket.com/x-bash/x-bash

Subcommand:
        cat|c           Provide cat facility
        which|w         Provide local cache file location
        update|u        Update file
        upgrade         Upgrade xrc from 'https://get.x-cmd.com/script'
        cache           Provide cache filepath
        clear           Clear the cache
        debug|d         Control debug flags.
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
                        xrc_debug "'$X_BASH_SRC_PATH/../boot' NOT found. Please manually clear cache folder: $X_BASH_SRC_PATH"
                        return 1
                    fi
                    rm -rf "$X_BASH_SRC_PATH" ;;
            debug)  shift; # xrc debug +work +work :work
                    if [ $# -eq 0 ]; then
                        cat >&2 <<A
xrc debug           Control debug files
        Uasge:      xrc debug [ +<lib> | -<lib> | :<lib> ]
        Example:    Enable debug for module json:   xrc debug +json
                    Dsiable debug for module json:  xrc debug -json
                    Generate debug function 'json_debug' for json module:  xrc debug :json
A
                        return 1
                    fi
                    local i
                    for i in "$@"; do
                        case "$i" in
                            :*) local var
                                var="$(echo "XRC_DBG_$i" | tr "[:lower:]" "[:upper:]")"
                                eval "$var=\${$var:-\$$var}"
                                eval "${i}_debug(){ [ \$$var ] && O=$i LEVEL=DBG _debug_logger \"\$@\"; }"
                                [ ! "$X_BASH_SRC_SHELL" = "sh" ] && {
                                    eval "export $var" 2>/dev/null
                                    eval "export -f ${i}_debug 2>/dev/null"  # "$i_debug_enable $i.debug_disable"
                                }    
                                ;;
                            -*) var="$(echo "XRC_DBG_${i#-}" | tr "[:lower:]" "[:upper:]")"
                                eval "$var="
                                ;;  
                            +*) var="$(echo "XRC_DBG_${i#+}" | tr "[:lower:]" "[:upper:]")"
                                echo "$var"
                                eval "$var=true"
                                ;; 
                            *)  var="$(echo "XRC_DBG_${i}" | tr "[:lower:]" "[:upper:]")"
                                eval "$var=true"
                                ;;
                        esac
                    done
                    ;;
            mirror) shift;
                    local fp="$X_BASH_SRC_PATH/.source.mirror.list"
                    if [ $# -ne 0 ]; then
                        local IFS="
";
                        echo "$*" >"$fp"
                        return
                    fi
                    [ ! -f "$fp" ] && xrc mirror "https://x-bash.github.io" "https://x-bash.gitee.io" # "https://sh.x-cmd.com"
                    cat "$fp"
                    return ;;
            *)      eval "$(t="." _xrc_source_file_list_code "$@")"
        esac
    }

    xrc debug :XRC

    X_CMD_SRC_SHELL="sh"
    if      [ -n "$ZSH_VERSION" ];  then    X_CMD_SRC_SHELL="zsh"
    elif    [ -n "$BASH_VERSION" ]; then    X_CMD_SRC_SHELL="bash"
    elif    [ -n "$KSH_VERSION" ];  then    X_CMD_SRC_SHELL="ksh"
    fi
    export X_CMD_SRC_SHELL

    TMPDIR=${TMPDIR:-$(dirname "$(mktemp -u)")/}    # It is posix standard. BUT NOT set in some cases.
    export TMPDIR

    xrc_debug "Setting env X_BASH_SRC_PATH: $X_BASH_SRC_PATH"
    X_BASH_SRC_PATH="$HOME/.x-cmd/x-bash"           # boot will be placed in "$HOME/.x-cmd/boot"
    mkdir -p "$X_BASH_SRC_PATH"
    PATH="$(dirname "$X_BASH_SRC_PATH")/bin:$PATH"

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

    xrc_debug "Creating $X_BASH_SRC_PATH/.source.mirror.list"
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
            xrc_curl "$mirror/$mod"
            case $? in
                0)  if [ "$i" -ne 1 ]; then
                        xrc_debug "Default mirror now is $mirror"
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
            xrc_debug "Resource recognized as local file: $RESOURCE_NAME"
            echo "$RESOURCE_NAME"; return 0
        fi

        if [ "${RESOURCE_NAME#\./}" != "$RESOURCE_NAME" ] || [ "${RESOURCE_NAME#\.\./}" != "$RESOURCE_NAME" ]; then
            xrc_debug "Resource recognized as local file with relative path: $RESOURCE_NAME"
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
            xrc_debug "Resource recognized as http resource: $RESOURCE_NAME"
            TGT="$X_BASH_SRC_PATH/BASE64-URL-$(printf "%s" "$RESOURCE_NAME" | base64 | tr -d '\r\n')"
            if ! CACHE="$TGT" xrc_curl "$RESOURCE_NAME"; then
                xrc_debug "ERROR: Fail to load http resource due to network error or other: $RESOURCE_NAME "
                return 1
            fi

            echo "$TGT"
            return 0
        fi

        xrc_debug "Resource recognized as x-bash library: $RESOURCE_NAME"
        local module="$RESOURCE_NAME"
        if [ "${RESOURCE_NAME#*/}" = "$RESOURCE_NAME" ] ; then
            module="$module/latest"         # If it is short alias like str (short for str/latest)
            xrc_debug "Adding latest tag by default: $module"
        fi
        TGT="$X_BASH_SRC_PATH/$module"

        if [ -f "$TGT" ]; then
            echo "$TGT"
            return
        fi

        xrc_debug "Dowoading resource=$RESOURCE_NAME to local cache: $TGT"
        if ! CACHE="$TGT" _xrc_curl_gitx "$module"; then
            LEVEL=WARN _xrc_log "ERROR: Fail to load module due to network error or other: $RESOURCE_NAME"
            return 1
        fi
        echo "$TGT"
    }

    export XRC_COLOR_LOG=1

    _debug_logger(){
        local logger=${O:-DEFAULT}
        local level=${LEVEL:-DBG}
        local IFS=
        # eval "[ \$$var ] && return 0"

        if [ $# -eq 0 ]; then
            if [ -n "$XRC_COLOR_LOG" ]; then
                # printf "\e[31m%s[%s]: " "$logger" "$level" 
                printf "\e[;2m%s[%s]: " "$logger" "$level"
                cat
                printf "\e[0m\n"
            else
                printf "%s[%s]: " "$logger" "$level"
                cat
                printf "\n"
            fi
        else
            if [ -n "$XRC_COLOR_LOG" ]; then
                printf "\e[;2m%s[%s]: %s\e[0m\n" "$logger" "$level" "$*"
            else
                printf "%s[%s]: %s\n" "$logger" "$level" "$*"
            fi
        fi >&2
        return 0
    }

    # xrc x comp/xrc comp/x
fi
