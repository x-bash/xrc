___x_cmd_trimutil(){
    eval "
[ -z \"\$${O}\" ] && return 1
IT=\"\${${O}%%${SEP:-
}*}\"
if [ \"\${IT}\" = \"\${$O}\" ]; then
    $O=\"\"
else
    $O=\"\${${O}#*${SEP:-
}}\"
fi
return 0
"
}

f(){
    local IT
    local SEP=,
    local a
    a="abc,cde,eft"
    # O=a ___x_cmd_trimutil
    while O=a ___x_cmd_trimutil; do
        printf "%s\n" "$IT"
    done
}

f
