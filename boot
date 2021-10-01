# shellcheck shell=sh disable=SC2039,SC1090,SC3043,SC2263


RELOAD=1
if [ -n "$RELOAD" ] || [ -z "$___X_CMD_XRC_PATH" ]; then

    # Section: network

    if curl --version 1>/dev/null 2>&1; then
        [ -n "$KSH_VERSION" ] && alias local=typeset
        ___xcmd_http_get(){
            # Other solution: --speed-time 5 --speed-limit 10, disconnect if less than 10kb, and last for 5 seconds.
            xrc_log debug "curl ${XRC_MAX_TIME+--max-time $XRC_MAX_TIME} --fail ${1:?Provide target URL}"
            eval curl ${XRC_MAX_TIME+--max-time $XRC_MAX_TIME} --fail "\"\${1}\"" 2>/dev/null
            local code=$?
            # TODO: figure out a way to distinguish timeout or network failure
            # [ $code -eq 28 ] && return 4
            return $code
        }
    elif [ "$(x author 2>/dev/null)" = "ljh & LTeam" ]; then
        [ -n "$KSH_VERSION" ] && alias local=typeset
        # TODO: Under the situation of container
        alias ___xcmd_http_get="x cat"
    else
        printf "boot[ERR]: Cannot found curl or x-cmd binary for web resource downloader." >&2
        return 1 || exit 1
    fi

    ___xcmd_curl() {
        local REDIRECT=/dev/stdout
        if [ -n "$CACHE" ]; then
            if [ -z "$___XRC_UPDATE" ] && [ -f "$CACHE" ]; then
                xrc_log debug "Function ___xcmd_curl() terminated. Because local cache existed with update flag unset: $CACHE"
                return 0
            fi
            # First make sure it works before webservice. Fail fast.
            mkdir -p "$(dirname "$CACHE")"
            REDIRECT="$TMPDIR/x-bash-temp-download.$RANDOM"
        fi

        if ___xcmd_http_get "$1" 1>"$REDIRECT"; then
            if [ -n "$CACHE" ]; then
                xrc_log debug "Copy the temp file to CACHE file: $CACHE"
                mv "$REDIRECT" "$CACHE"
            fi
        else
            local code=$?
            xrc_log debug "___xcmd_http_get $1 return code: $code. Fail to retrieve file from: $1"
            [ -n "$CACHE" ] && rm -f "$REDIRECT"    # In centos, file in "$REDIRECT" is write protected.
            return $code
        fi
    }

    # EndSection

    ___xcmd_search_path(){
        local cur="${1:?Provide starting path}"

        cur="$(cd "$cur" 1>/dev/null 2>&1 && pwd)"

        local relative_filepath="${2:?Provide relative filepath}"
        while [ ! "$cur" = "" ]; do
            if [ -f "$cur/$relative_filepath" ]; then
                printf "%s" "$cur"
                return 0
            fi
            cur=${cur%/*}
        done
        return 1
    }

    # Section: log

    XRC_LOG_COLOR=1
    XRC_LOG_TIMESTAMP=      # "+%H:%M:%S"      # Enable Timestamp.
    ___xcmd_logger(){
        local logger="${O:-DEFAULT}"
        local IFS=
        local level="${1:?Please provide logger level}"
        local FLAG_NAME=${FLAG_NAME:?WRONG}

        local color="\e[32;2m"
        local level_code=0
        case "$level" in
            debug|DEBUG|verbose)    level="DBG";    shift ;;
            info|INFO)              level="INF";    level_code=1;   color="\e[36m";     shift ;;
            warn|WARN)              level="WRN";    level_code=2;   color="\e[33m";     shift ;;
            error|ERROR)            level="ERR";    level_code=3;   color="\e[31m";     shift ;;
            *)                      level="DBG"     ;;
        esac

        eval "[ $level_code -lt \"\${$FLAG_NAME:-1}\" ]" && return 0

        local timestamp=
        [ -n "$XRC_LOG_TIMESTAMP" ] && timestamp=" [$(date "${XRC_LOG_TIMESTAMP}")]"

        if [ -n "$XRC_LOG_COLOR" ]; then

            if [ $# -eq 0 ]; then
                printf "${color}%s[%s]${timestamp}: " "$logger" "$level"
                cat | awk 'NR==1{ print($0) }; NR>=2{ print("> " $0); }; END{ printf("%s", "\033[0m"); }'
            else
                printf "${color}%s[%s]${timestamp}: %s\e[0m\n" "$logger" "$level" "$*"
            fi
        else
            if [ $# -eq 0 ]; then
                printf "%s[%s]${timestamp}: " "$logger" "$level"
                cat | awk 'NR==1{ print($0) }; NR>=2{ print("> " $0); }; }'
            else
                printf "%s[%s]${timestamp}: %s\n" "$logger" "$level" "$*"
            fi
        fi >&2
    }
    # EndSection

    # Section: logctl consider extracting it into individual repository
    _xrc_log() {
        if [ $# -eq 0 ]; then
            cat >&2 <<A
xrc log     log control facility
        Usage:
            xrc log init [ module ]
            xrc log [... +module | -module | module/log-level ]
Subcommand:
        init <module>:                  Generate function '<module>_log'
        timestamp < on | off | <format> >:
                                        off, default setting. shutdown the timestamp output in log
                                        on, default format is +%H:%M:%S
                                        <format>, customized timestamp format like "+%H:%M:%S", "+%m/%d-%H:%M:%S"
Example:
        Enable debug log for module json:
                xrc log +json          or   xrc log json
                xrc log json/verbose   or   xrc log json/v
                xrc log json/debug     or   xrc log json/d
        Dsiable debug log for module json:
                xrc log -json
                xrc log json/info
A
                        return 1
        fi
        local var
        local level_code=0

        case "$1" in
            init)
                shift;
                for i in "$@"; do
                    var="$(echo "XRC_LOG_LEVEL_${i}" | tr "[:lower:]" "[:upper:]")"
                    eval "${i}_log(){     O=$i FLAG_NAME=$var    ___xcmd_logger \"\$@\";   }"
                done
                return 0 ;;
            timestamp)
                case "$2" in
                    on)     XRC_LOG_TIMESTAMP="+%H:%M:%S";      return 0   ;;
                    off)    XRC_LOG_TIMESTAMP= ;                return 0   ;;
                    *)      printf "Try customized timestamp format wit date command:\n"
                            if date "$2"; then
                                XRC_LOG_TIMESTAMP="$2"
                                return 0
                            fi
                            return 1    ;;
                esac
        esac

        local level
        while [ $# -ne 0 ]; do
            case "$1" in
                -*) var="$(echo "XRC_LOG_LEVEL_${1#-}" | tr "[:lower:]" "[:upper:]")"
                    eval "$var=1"
                    xrc_log info "Level of logger [${1#-} is set to [info]" ;;
                +*) var="$(echo "XRC_LOG_LEVEL_${1#+}" | tr "[:lower:]" "[:upper:]")"
                    eval "$var=0"
                    xrc_log info "Level of logger [${1#+}] is set to [debug]" ;;
                *)
                    level="${1#*/}"
                    var="${1%/*}"
                    case "$level" in
                        debug|dbg|verbose|v)        level=debug;    level_code=0 ;;
                        info|INFO|i)                level=info;     level_code=1 ;;
                        warn|WARN|w)                level=warn;     level_code=2 ;;
                        error|ERROR|e)              level=error;    level_code=3 ;;
                        none|n|no)                  level=none;     level_code=4 ;;
                        *)                          level=debug;    level_code=0 ;;
                    esac
                    xrc_log info "Level of logger [$var] is set to [$level]"
                    var="$(echo "XRC_LOG_LEVEL_${var}" | tr "[:lower:]" "[:upper:]")"
                    eval "$var=$level_code" ;;
            esac
            shift
        done
    }
    # EndSection

    # Section: mirror
    _xrc_mirror(){
        local fp="$___X_CMD_XRC_PATH/.source.mirror.list"
        if [ $# -ne 0 ]; then
            mkdir -p "$(dirname "$fp")"
            local IFS="
";
            printf "%s" "$*" >"$fp"
            return
        fi
        if [ ! -f "$fp" ]; then
            _xrc_mirror \
                "https://raw.githubusercontent.com/%s/%s/master/%s" \
                "https://gitee.com/%s/%s/raw/master/%s"
                # "https://x-bash.github.io/%s/%s"
                # "https://x-bash.gitee.io/%s/%s"
                # "https://sh.x-cmd.com"
        fi
        cat "$fp"
    }
    # EndSection

    # Section: account

    ___XCMD_SERVICE_URL="http://47.115.207.205"
    # ___XCMD_SERVICE_URL="https://hub.x-cmd.com"
    # ___XCMD_SERVICE_URL="http://127.0.0.1:3000"

    ___xcmd_login(){
        local email="${1}"
        if [ -z "$email" ]; then
            printf "%s" "Email: "
            read -r email
        fi

        local result
        result="$(curl "$___XCMD_SERVICE_URL/api/v0/account/login/$email" 2>/dev/null)"
        printf "Result: %s\n" "$result" >&2

        if [ "${result#ERR=}" = "$result" ]; then
            ___xcmd_verify "$email"
        else
            printf "%s\n" "$result"
        fi
    }

    ___xcmd_verify(){
        local email="${1:?email}"

        local code
        printf "%s" "Code: "
        read -r code

        result="$(curl "$___XCMD_SERVICE_URL/api/v0/account/verify/$email/$code" 2>/dev/null)"

        local token=${result#TOKEN=}
        if [ "$token" != "$result" ]; then
            mkdir -p "$___X_CMD_XRC_PATH/env"
            printf "%s" "$email" > "$___X_CMD_XRC_PATH/env/.me"
            printf "%s" "$token" > "$___X_CMD_XRC_PATH/env/.token"
            printf "%s\n" "Login Success."
        else
            printf "%s\n" "$result"
        fi
    }

    mkdir -p "$___X_CMD_XRC_PATH/env"

    # shellcheck disable=SC2120
    ___xcmd_token(){
        if [ -z "$1" ]; then
            cat "$___X_CMD_XRC_PATH/env/.token" 2>/dev/null
        else
            if [ "$1" = "${1#*/}" ]; then
                printf "%s" "${1%/*}" > "$___X_CMD_XRC_PATH/env/.me"
                printf "%s" "${1#*/}" > "$___X_CMD_XRC_PATH/env/.token"
                return
            fi

            local res
            if res=$(___xcmd_curl "$___XCMD_SERVICE_URL/api/v0/token/info/email?token=$(___xcmd_token)"); then
                printf "%s" "$res" > "$___X_CMD_XRC_PATH/env/.me"
                printf "%s" "$1" > "$___X_CMD_XRC_PATH/env/.token"
            fi
        fi
    }

    ___xcmd_login_user(){
        cat "$___X_CMD_XRC_PATH/env/.me" 2>/dev/null
    }

    ___xcmd_register(){
        local email=${1:-""}
        if [ -z "$email" ]; then
            printf "%s" "Email: "
            read -r email
        fi

        local result
        result="$(curl "$___XCMD_SERVICE_URL/api/v0/account/register/$email" 2>/dev/null)"
        local msg=${result#ERR=}

        if [ "$msg" = "$result" ]; then
            ___xcmd_verify "$email"
        else
            printf "%s\n" "$result"
        fi
    }

    # EndSection

    # Section: file service

    ___xcmd_file_ls(){
        local respath="${1:-@me/}"
        respath="$(___xcmd_file_normalize_respath "$respath")"

        curl \
            "$___XCMD_SERVICE_URL/api/v0/file/ls?token=$(___xcmd_token)&res=${respath}"
    }

    ___xcmd_file_which(){
        local respath="${1:?Provide respath}"
        # case "$respath" in
        #     @me/*|@i/*|@/*)
        #         local user
        #         user=$(___xcmd_login_user)
        #         if [ -z "$user" ]; then
        #             printf "Cannot not find out login user for resource: %s" "$respath" >&2
        #             return 1
        #         fi
        #         respath="$user/${respath#@*/}"
        # esac

        respath="$(___xcmd_file_normalize_respath "$respath")"

        local CACHE="${___X_CMD_XRC_PATH%/}/${respath#/}"
        if ___xcmd_curl "$___XCMD_SERVICE_URL/api/v0/file/cat?token=$(___xcmd_token)&res=${respath}"; then
            printf "%s" "$CACHE"
        else
            printf "Failed: %s" "$CACHE" 2>/dev/null
            return 1
        fi
    }

    ___xcmd_file_normalize_respath(){
        local respath="${1}"
        case "$respath" in
            @me/*|@i/*|@/*)
                local user
                user=$(___xcmd_login_user)
                if [ -z "$user" ]; then
                    printf "Cannot not find out login user for resource: %s" "$respath" >&2
                    return 1
                fi
                printf "%s" "$user/${respath#@*/}"
                ;;
            *@*)    printf "%s" "$respath" ;;
            *)      printf "%s" "$(___xcmd_login_user)/${respath#/}" ;;
        esac
    }

    ___xcmd_file_upload(){
        local localfp="${1:?Provide local file}"

        [ ! -f "$localfp" ] && {
            printf "File Not Existed: %s" "$localfp" >&2
            return
        }

        local respath="${2:?Please provide path}"
        respath="$(___xcmd_file_normalize_respath "$respath")"

        curl \
            -F "file=@$localfp" \
            "$___XCMD_SERVICE_URL/api/v0/file/upload?token=$(___xcmd_token)&res=${respath}"
    }

    ___xcmd_file_share(){
        local respath="${1:?Please provide path}"
        respath="$(___xcmd_file_normalize_respath "$respath")"

        echo curl "$___XCMD_SERVICE_URL/api/v0/file/share/true?token=$(___xcmd_token)&res=${respath}"
        curl "$___XCMD_SERVICE_URL/api/v0/file/share/true?token=$(___xcmd_token)&res=${respath}"
    }

    ___xcmd_file_private(){
        local respath="${1:?Please provide path}"
        respath="$(___xcmd_file_normalize_respath "$respath")"

        curl "$___XCMD_SERVICE_URL/api/v0/file/share/false?token=$(___xcmd_token)&res=${respath}"
    }

    # EndSection

    xrc(){
        [ $# -eq 0 ] && set -- "help"
        case "$1" in
            help)       _xrc_help;  return ;;
            c|cat)      shift;
                        eval "$(t="cat" ___xcmd_xrc_source_file_list_code "$@")" ;;
            w|which)    shift;
                        if [ $# -eq 0 ]; then
                            cat >&2 <<A
xrc which  Download lib files and print the local path.
        Uasge:  xrc which <lib> [<lib>...]
        Example: source "$(xrc which std/str)"
A
                            return 1
                        fi
                        eval "$(t="echo" ___xcmd_xrc_source_file_list_code "$@")"  ;;
            update)     shift;  ( xrc x-bash/xrc/update/v0;  xrc_update "$@" ) ;;
            upgrade)    shift;  eval "$(curl https://get.x-cmd.com/script)" ;;
            cache)      shift;  echo "$___X_CMD_XRC_PATH" ;;
            initrc)     shift;  _xrc_initrc "$@" ;;
            export-all) ___xcmd_xrc_export_all ;;
            clear)      shift;
                        if ! grep "___xcmd_http_get()" "$___X_CMD_XRC_PATH/../boot" >/dev/null 2>&1; then
                            xrc_log debug "'$___X_CMD_XRC_PATH/../boot' NOT found. Please manually clear cache folder: $___X_CMD_XRC_PATH"
                            return 1
                        fi
                        rm -rf "$___X_CMD_XRC_PATH" ;;
            reinstall)
                        xrc clear
                        RELOAD=1 xrc upgrade
                        ;;
            log)        shift;  _xrc_log "$@" ;;
            mirror)     shift;  _xrc_mirror "$@" ; return ;;
            reload)     shift
                        if [ "$#" != 0 ]; then
                            local ___XRC_RELOAD=1
                            eval "$(t="." ___xcmd_xrc_source_file_list_code "$@")"
                        else
                            RELOAD=1 . "$___X_CMD_XRC_PATH/../boot"
                        fi
                        ;;
            *)          eval "$(t="." ___xcmd_xrc_source_file_list_code "$@")"
        esac
    }


    # Section: initialize
    xrc log init xrc

    X_CMD_SRC_SHELL="sh"
    if      [ -n "$ZSH_VERSION" ];  then    X_CMD_SRC_SHELL="zsh";  setopt aliases
    elif    [ -n "$BASH_VERSION" ]; then    X_CMD_SRC_SHELL="bash"; shopt -s expand_aliases
    elif    [ -n "$KSH_VERSION" ];  then    X_CMD_SRC_SHELL="ksh"
    fi

    TMPDIR=${TMPDIR:-$(dirname "$(mktemp -u)")/}    # It is posix standard. BUT NOT set in some cases.

    xrc_log debug "Setting env ___X_CMD_XRC_PATH: $___X_CMD_XRC_PATH"
    X_CMD_SRC_PATH="$HOME/.x-cmd"                  # TODO: Using X_CMD_SRC_PATH
    ___X_CMD_XRC_PATH="$HOME/.x-cmd/x-bash"           # boot will be placed in "$HOME/.x-cmd/boot"
    mkdir -p "$___X_CMD_XRC_PATH"
    PATH="$(dirname "$___X_CMD_XRC_PATH")/bin:$PATH"

    # EndSection
    ___xcmd_xrc_source_file_list_code(){
        local code=""
        local file
        local exec=${t:-.}
        while [ $# -ne 0 ]; do
            # What if the ___xcmd_which_one contains '"'

            local XRC_MAX_TIME=3        # Consider one file is less than 100KB, bandwidth at least 35KB/s.
            if ! file="$(___xcmd_which_one "$1")"; then
                echo "return 1"
                return 0
            fi

            if [ "$exec" != "." ]; then
                code="$code
$exec \"$file\""
            else
                if [ -z "$___XRC_RELOAD" ]; then
                    if [ "${___X_CMD_XRC_MODULE_IMPORTED#*$file}" != "${___X_CMD_XRC_MODULE_IMPORTED}" ]; then
                        shift
                        continue    # exixted already. skip
                    fi
                fi
                code="$code
$exec \"$file\" && \
___X_CMD_XRC_MODULE_IMPORTED=\"\$___X_CMD_XRC_MODULE_IMPORTED
$file\""
            fi
            shift
        done
        echo "$code"
    }

    case "$(xrc mirror|head -n1)" in
        *gitee*)    XRC_CHINA_NET=1 ;;
        *)          XRC_CHINA_NET=
    esac

    xrc_log debug "Creating $___X_CMD_XRC_PATH/.source.mirror.list"

    ___xcmd_curl_gitx(){   # Simple strategy
        local repo="${1:?Provide reponame}"
        local mod="${2:?Provide location like str}"
        local mod_repo=${mod%%/*}
        local mod_subpath=${mod#*/}

        local IFS

        local mirror_list
        mirror_list="$(xrc mirror)"

        local mirror
        local lineno=1
        local urlpath
        while read -r mirror; do
            # shellcheck disable=SC2059
            urlpath="$(printf "$mirror" "$repo" "$mod_repo" "$mod_subpath")"
            xrc_log debug "Trying: $urlpath"
            ___xcmd_curl "$urlpath"

            case $? in
                0)  if [ "$lineno" -ne 1 ]; then
                        xrc_log debug "Current default mirror is $mirror"
                        xrc mirror "$mirror" "$(echo "$mirror_list" | awk "NR!=$lineno{ print \$0 }" )"

                        # Set CHINA_NET FLAG
                        case "$mirror" in
                            *gitee*)    XRC_CHINA_NET=1 ;;
                            *)          XRC_CHINA_NET=
                        esac
                    fi
                    return 0;;
                4)  xrc_log debug "Network unavailable."
                    return 4;;
                *)  xrc_log debug "Mirror is down: $urlpath"
            esac
            lineno=$((lineno+1))  # Support both ash, dash, bash
        done <<A
${mirror_list}
A
        return 1
    }

    ___xcmd_which_one(){
        local RESOURCE_NAME=${1:?Provide resource name}

        local TGT
        case "$RESOURCE_NAME" in
            /*)
                xrc_log debug "Resource recognized as local file: $RESOURCE_NAME"
                printf "%s" "$RESOURCE_NAME"
                return 0
                ;;
            http://*|https://*)
                ___xcmd_which_one_http "$RESOURCE_NAME"
                ;;
            *@*/*)
                ___xcmd_file_which "$RESOURCE_NAME"
                ;;
            ./*|../*)
                xrc_log debug "Resource recognized as local file with relative path: $RESOURCE_NAME"
                local tmp
                if tmp="$(cd "$(dirname "$RESOURCE_NAME")" || exit 1; pwd)"; then
                    printf "%s" "$tmp/$(basename "$RESOURCE_NAME")"
                    return 0
                else
                    xrc_log warn "Local file not exists: $RESOURCE_NAME"
                    return 1
                fi
                ;;
            *)
                [ -f "$RESOURCE_NAME" ] && printf "%s" "$RESOURCE_NAME" && return      # local file

                if TGT="$(___xcmd_search_path . ".x-cmd/$RESOURCE_NAME")"; then
                    printf "%s" "$TGT/.x-cmd/$RESOURCE_NAME"
                    return                   # .x-cmd
                fi

                # x-bash module
                xrc_log debug "Resource recognized as x-bash module: $RESOURCE_NAME"
                local module="$RESOURCE_NAME"
                if [ "${RESOURCE_NAME#*/}" = "$RESOURCE_NAME" ] ; then
                    module="$module/latest"         # If it is short alias like str (short for str/latest)
                    xrc_log debug "Version suffix unavailable. Using \"latest\" by default: $module"
                fi
                TGT="$___X_CMD_XRC_PATH/$module"

                if [ -z "$___XRC_UPDATE" ] && [ -f "$TGT" ]; then
                    printf "%s" "$TGT"
                    return
                fi

                xrc_log info "Dowloading resource=$RESOURCE_NAME to local cache: $TGT"
                if ! CACHE="$TGT" ___xcmd_curl_gitx "x-bash" "$module"; then
                    xrc_log warn "ERROR: Fail to load module due to network error or other: $RESOURCE_NAME"
                    return 1
                fi
                printf "%s" "$TGT"
        esac
    }

    ___xcmd_which_one_http(){
        local RESOURCE_NAME="${1:?Provide resource name}"
        xrc_log debug "Resource recognized as http resource: $RESOURCE_NAME"
        if [ -z "$NOWARN" ]; then
            echo "Sourcing script from unknown location: " "$RESOURCE_NAME"
            cat >&2 <<A
SECURITY WARNING! Sourcing script from unknown location: $RESOURCE_NAME
If you confirm this script is secure and want to skip this warning for some purpose, use the following code.
    > NOWARN=1 xrc "$RESOURCE_NAME"

A
            printf "Input yes to continue. Otherwise exit > " >&2
            local input
            read -r input

            if [ "$input" != "yes" ]; then
                echo "Exit becaause detect a non yes output: $input" >&2
                return 1
            fi
        fi

        TGT="$___X_CMD_XRC_PATH/BASE64-URL-$(printf "%s" "$RESOURCE_NAME" | base64 | tr -d '\r\n')"
        if ! CACHE="$TGT" ___xcmd_curl "$RESOURCE_NAME"; then
            xrc_log debug "ERROR: Fail to load http resource due to network error or other: $RESOURCE_NAME "
            return 1
        fi

        echo "$TGT"
        return 0
    }

    # Section: export-all
    ___xcmd_xrc_export_all(){
        export -f xrc
        export -f x
        export -f ___xcmd_curl_gitx
        export -f ___xcmd_http_get
        export -f ___xcmd_logger
        export -f ___xcmd_xrc_source_file_list_code
        export -f ___xcmd_curl

        # Using command line.
        export X_CMD_SRC_SHELL
        export ___X_CMD_XRC_PATH
        export XRC_LOG_COLOR
        export XRC_LOG_TIMESTAMP
        export TMPDIR
    }

    # Section: advise and help
    if [ -z "$XRC_NO_ADVISE" ] && [ -n "${BASH_VERSION}${ZSH_VERSION}" ] && [ "${-#*i}" != "$-" ]; then
        xrc_log debug "Using module advise for completion."
        xrc advise/v0

        # shellcheck disable=SC3010,SC2154
        _xrc_log_completer(){
            if [ "$cur" = "" ]; then
                echo "+"
                echo "-"
                ls $___X_CMD_XRC_PATH | grep -v BASE64  | awk '{ print $0 "/"; }'
                # echo "$___X_CMD_XRC_MODULE_IMPORTED"  | awk '{ print $0 "/"; }'
            elif [[ "$cur" = */* ]]; then
                echo "${cur%/*}/debug"
                echo "${cur%/*}/verbose"
                echo "${cur%/*}/warn"
                echo "${cur%/*}/error"
            elif [[ "$cur" =~ ^\+ ]]; then
                ls $___X_CMD_XRC_PATH | grep -v BASE64 | awk '{ print "+" $0; }'
            elif [[ "$cur" =~ ^\- ]]; then
                ls $___X_CMD_XRC_PATH | grep -v BASE64 | awk '{ print "-" $0; }'
            else
                ls $___X_CMD_XRC_PATH | grep -v BASE64 | awk -v cur="$cur" '
                    BEGIN { arr_len=0; }
                    $0~"^"cur{
                        arr_len += 1
                        arr[arr_len] = $0;
                        if ( $0 !~ /\/$/ ) arr[arr_len] = arr[arr_len] "/"
                    }
                    END {
                        if (arr_len != 1) {
                            for (i=1; i<=arr_len; ++i) print arr[i]
                        } else {
                            # It is useful! The completion seemed to pause before "/"
                            print arr[1] "verbose"
                            print arr[1] "debug"
                            print arr[1] "warn"
                            print arr[1] "error"
                        }
                    }
                '
            fi

        }

        advise init xrc - <<A
{
    "cat|c": {
        "#n": "ls $___X_CMD_XRC_PATH | grep -v BASE64"
    },
    "which|w": {
        "#n": "ls $___X_CMD_XRC_PATH | grep -v BASE64"
    },
    "update|u": {},
    "upgrade": {},
    "reinstall": {},
    "cache": {},
    "clear": {},
    "log": {
        "init": {},
        "timestamp": {
            "on": {},
            "off": {}
        },
        "#n": "_xrc_log_completer"
    },
    "initrc": {
        "cat": null
        "which|w": null,
        "mod": {
            "add|+": null,
            "del|-": "x initrc mod ls",
            "ls": null
        }
    },
    "mirror": {
        "#n": [
            "https://x-bash.gitee.io",
            "https://x-bash.github.io",
            "https://x-bash.gitlab.io",
            "https://bitbucket.com/x-bash/x-bash"
        ]
    }
}
A
        :
    fi

    _xrc_help(){
        printf "xrc     x-bash core function.
        Uasge:  xrc <lib> [<lib>...]
        Notice, builtin command 'source' format is 'source <lib> [argument...]'
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
" >&2
    }
    # EndSection

    # Section: initrc, consider external module.
    _xrc_initrc(){
        case "$1" in
            trust)
                    # trust gitee, github, gitlab, or any other url
                    ;;
            mod)    shift
                    case "$1" in
                        add|+)      shift
                                    (
                                        for i in "$@"; do
                                            s="$(printf "xrc %s # auto generated" "$i")"
                                            if ! grep "$s" "$X_CMD_SRC_PATH/.init.rc" 1>/dev/null 2>&1; then
                                                printf "%s\n" "$s" >> "$X_CMD_SRC_PATH/.init.rc"
                                            fi
                                        done
                                    ) ;;
                        del|-)      shift
                                    (
                                        s="$(cat "$X_CMD_SRC_PATH/.init.rc")"
                                        for i in "$@"; do
                                            s="$(printf "%s" "$s" | grep -v "xrc $i # auto generated")"
                                        done
                                        printf "%s" "$s" > "$X_CMD_SRC_PATH/.init.rc"
                                    )
                                    ;;
                        ls|*)         awk '$0~"auto generated"{ print $2; }' "$X_CMD_SRC_PATH/.init.rc" ;;
                    esac
                    ;;
            which)  printf "%s\n" "$X_CMD_SRC_PATH/.init.rc"    ;;
            cat|*)  cat "$X_CMD_SRC_PATH/.init.rc" 2>/dev/null  ;;
        esac
    }

    [ -f "$(xrc initrc which)" ] && . "$(xrc initrc which)"
    # EndSection

    x(){
        xrc reload xcmd/v0 && x ${1:+"$@"}
    }
fi