x(){
    case "$1" in
        rc|src) SRC_LOADER=bash eval "$(_xrc_print_code "$@")" ;;
        # java | jar);;
        # python | py);;
        # javascript | js);;
        # typescript | ts);;
        # ruby | rb);;
        # lua);;
        *)  if [ -z "$X_CMD_PATH" ] && ! X_CMD_PATH="$(command -v x)"; then
                if ! eval "$(curl https://get.x-cmd.com/x-cmd-binary)" || ! X_CMD_PATH="$(command -v x)"; then
                    echo "Installation of x-cmd binary failed. Please retry again." >&2
                    return 1
                fi
            fi
            "$X_CMD_PATH" "$@"  ;;
    esac
}
