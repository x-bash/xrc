{
    text = $0
    while (1) {
        match( text, "xrc[ \t]+(w|which|c|cat|log)[ \t]+[A-Za-z/0-9_-]+" )
        if (RLENGTH <= 0) {
            match( text, "xrc[ \t]+[A-Za-z/0-9_-]+" )
        }
       
        if (RLENGTH <= 0) break
        code = substr(text, RSTART, RLENGTH)
        gsub( "xrc[ \t]+((w|which|c|cat|log)[ \t]+)?", "", code )
        # if ( (code != "cat") && (code != "log") && (code != "which") && (code != "update") && (code != "w")) {
        if (code !~ /^(cat|c|which|w|update|log)$/) {
            print code
        }

        text = substr(text, RSTART + RLENGTH)
    }
}
