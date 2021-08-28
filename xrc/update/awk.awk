{
    text = $0
    while (1) {
        match( text, "xrc[ \t]+(w|which)[ \t]+[A-Za-z/0-9_-]+" )
        if (RLENGTH <= 0) {
            match( text, "xrc[ \t]+[A-Za-z/0-9_-]+" )
        }
       
        if (RLENGTH <= 0) break
        code = substr(text, RSTART, RLENGTH)
        gsub( "xrc[ \t]+((w|which)[ \t]+)?", "", code )
        print code
        text = substr(text, RSTART + RLENGTH)
    }
}
