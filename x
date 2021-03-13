# shellcheck shell=sh

x(){
    case "${1:?Provide Sub Command}" in
        rc|src) SRC_LOADER=bash eval "$(_xrc_print_code "$@")" ;;
        # java | jar);;
        python|py) 
            shift;
            # Install python using pyenv
            xrc pyenv
            python "$(xrc_curl which "$2")" "$@" ;;
        javascript|js)
            shift;
            # Install node using nvm
            node "$(xrcwhich "$2")" "$@" ;;
        typescript|ts)
            shift;
            # Install node using nvm
            ts-node "$(xrc which "$2")" "$@" ;;
        ruby|rb)
            shift;
            python "$(xrc which "$2")" "$@" ;;
        lua) ;;
        *)  if [ -z "$X_CMD_PATH" ] && ! X_CMD_PATH="$(command -v x)"; then
                if ! eval "$(curl https://get.x-cmd.com/x-cmd-binary)" || ! X_CMD_PATH="$(command -v x)"; then
                    echo "Installation of x-cmd binary failed. Please retry again." >&2
                    return 1
                fi
            fi
            "$X_CMD_PATH" "$@"  ;;
    esac
}
