package ParserLexer;

import java_cup.runtime.*;

%%

%class Lexer
%cup
%unicode
%line
%column
%public
%ignorecase

%{
    private Symbol symbol(int type) {
        return new Symbol(type, yyline, yycolumn);
    }
    
    private Symbol symbol(int type, Object value) {
        return new Symbol(type, yyline, yycolumn, value);
    }
%}

LineTerminator = \r|\n|\r\n
WhiteSpace = {LineTerminator} | [ \t\f]

/* Comentarios */
CommentLine = "|" [^\r\n]*
CommentMulti = "¡" [^!]* "!"

/* Literales */
Digito = [0-9]
IntegerLiteral = {Digito}+
FloatLiteral = {Digito}+ "." {Digito}+
CharLiteral = "'" [^'] "'"
StringLiteral = "\"" [^\"]* "\""
Identifier = [_]*[a-zA-Z][a-zA-Z0-9_]*


%%

<YYINITIAL> {
    /* Palabras reservadas */
    "let"              { return symbol(sym.LET); }
    "procedure"        { return symbol(sym.PROCEDURE); }
    "principal"        { return symbol(sym.PRINCIPAL); }
    "int"              { return symbol(sym.INT, "int"); }
    "float"            { return symbol(sym.FLOAT, "float"); }
    "char"             { return symbol(sym.CHAR, "char"); }
    "bool"             { return symbol(sym.BOOL, "bool"); }
    "string"           { return symbol(sym.STRING, "string"); }
    "true"             { return symbol(sym.TRUE); }
    "false"            { return symbol(sym.FALSE); }
    "decide"           { return symbol(sym.DECIDE); }
    "of"               { return symbol(sym.OF); }
    "else"             { return symbol(sym.ELSE); }
    "end"              { return symbol(sym.END); }
    "loop"             { return symbol(sym.LOOP); }
    "exit"             { return symbol(sym.EXIT); }
    "when"             { return symbol(sym.WHEN); }
    "for"              { return symbol(sym.FOR); }
    "step"             { return symbol(sym.STEP); }
    "to"               { return symbol(sym.TO); }
    "downto"           { return symbol(sym.DOWNTO); }
    "do"               { return symbol(sym.DO); }
    "return"           { return symbol(sym.RETURN); }
    "break"            { return symbol(sym.BREAK); }
    "input"            { return symbol(sym.INPUT); }
    "output"           { return symbol(sym.OUTPUT); }
    
    /* Operadores */
    "="                { return symbol(sym.ASSIGN); }
    "<"                { return symbol(sym.LT); }
    "<="               { return symbol(sym.LE); }
    ">"                { return symbol(sym.GT); }
    ">="               { return symbol(sym.GE); }
    "=="               { return symbol(sym.EQ); }
    "!="               { return symbol(sym.NE); }
    "+"                { return symbol(sym.PLUS); }
    "-"                { return symbol(sym.MINUS); }
    "*"                { return symbol(sym.MULT); }
    "/"                { return symbol(sym.DIV); }
    "//"               { return symbol(sym.INT_DIV); }
    "%"                { return symbol(sym.MOD); }
    "^"                { return symbol(sym.POW); }
    "@"                { return symbol(sym.AND); }
    "~"                { return symbol(sym.OR); }
    "Σ"                { return symbol(sym.SIGMA); }
    "++"               { return symbol(sym.INCREMENT); }
    "--"               { return symbol(sym.DECREMENT); }
    
    /* Símbolos especiales */
    "є"                { return symbol(sym.OPEN_PAREN); } 
    "э"                { return symbol(sym.CLOSE_PAREN); }
    "¿"                { return symbol(sym.OPEN_BRACE); } 
    "?"                { return symbol(sym.CLOSE_BRACE); }
    "["                { return symbol(sym.OPEN_BRACKET); } 
    "]"                { return symbol(sym.CLOSE_BRACKET); }
    ":"                { return symbol(sym.COLON); }
    ","                { return symbol(sym.COMMA); }
    "->"               { return symbol(sym.ARROW); }
    "$"                { return symbol(sym.SEMICOLON); }
    
    /* Literales */
    {IntegerLiteral}   { return symbol(sym.INTEGER_LITERAL, Integer.valueOf(yytext())); }
    {FloatLiteral}     { return symbol(sym.FLOAT_LITERAL, Float.valueOf(yytext())); }
    
    {CharLiteral}      { return symbol(sym.CHAR_LITERAL, yytext().charAt(1)); }
    {StringLiteral}    { return symbol(sym.STRING_LITERAL, yytext().substring(1, yytext().length()-1)); }
    {Identifier}       { return symbol(sym.IDENTIFIER, yytext()); }
    
    /* Comentarios - se ignoran */
    {CommentLine}      {}
    {CommentMulti}     {}
    
    /* Espacios en blanco */
    {WhiteSpace}       {}
}

[^ \t\r\n] {
    System.err.println("Error léxico en línea " + (yyline+1) + ", columna " + (yycolumn+1) + ": " + yytext());
}