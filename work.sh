f(){

    exec 2>/dev/null
    exec 3>/dev/stderr

    stty -echo
    (
        curl in header>&4 2>&3
    ) 3>&2 &
    stty +echo

    handler="$(cat <&2)"

}


(echo hi>&2)&
cat <&2