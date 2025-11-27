package Compilador;

import java.io.FileReader;
import java_cup.runtime.Symbol;
import ParserLexer.Lexer;
import ParserLexer.Parser;
import ParserLexer.sym;

public class TestParser {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Uso: java Compilador.TestParser <archivo>");
            return;
        }

        String archivo = args[0];
        System.out.println("=== ANALIZADOR DE ARCHIVO ===");
        System.out.println("Archivo: " + archivo);
        System.out.println("-------------------------------------");

        try {
            // === ANÁLISIS LÉXICO ===
            FileReader fr = new FileReader(archivo);
            Lexer lexer = new Lexer(fr);

            System.out.println("\n[LEXER] Tokens detectados:");
            Symbol token;
            while ((token = lexer.next_token()).sym != sym.EOF) {
                System.out.printf("  %-15s -> '%s'%n", symToString(token.sym), lexer.yytext());
            }

            // === ANÁLISIS SINTÁCTICO Y SEMÁNTICO ===
            fr = new FileReader(archivo);  // reiniciar lector
            lexer = new Lexer(fr);
            Parser parser = new Parser(lexer);

            System.out.println("\n[PARSER] Iniciando análisis sintáctico y semántico...");
            parser.parse();
            System.out.println("-------------------------------------");

            // === MANEJO DE ERRORES ===
            System.out.println("\n--- RESUMEN DE ERRORES ---");
            if (Parser.errores.isEmpty()) {
                System.out.println("No se encontraron errores sintácticos ni semánticos.");
                System.out.println("El archivo es válido y puede ser generado por la gramática.\n");

                // === GENERACIÓN DEL CÓDIGO INTERMEDIO ===
                String ruta = "src/Codigo_Intermedio/codigo_intermedio.txt";
                Parser.codigoIntermedio.exportToFile(ruta);
                System.out.println(">> Código intermedio generado correctamente en: " + ruta);

            } else {
                System.out.println("Se detectaron errores. El archivo no puede ser generado:\n");
                for (String err : Parser.errores) {
                    System.out.println("  - " + err);
                }
                System.out.println("\nCódigo intermedio no generado debido a errores.");
            }

            // === TABLA DE SÍMBOLOS Y CÓDIGO INTERMEDIO ===
            System.out.println("\n--- TABLA DE SÍMBOLOS ---");
            Parser.tablaSimbolos.printTable();

            if (Parser.errores.isEmpty()) {
                Parser.codigoIntermedio.printCode();
            }

        } catch (Exception e) {
            System.out.println("\nError fatal durante el análisis. El archivo no puede ser generado:");
            e.printStackTrace();
        }
    }

    /** Convierte el número de token a su nombre legible */
    private static String symToString(int symCode) {
        try {
            for (java.lang.reflect.Field f : sym.class.getFields()) {
                if (f.getInt(null) == symCode)
                    return f.getName();
            }
        } catch (Exception ignored) {}
        return "SYM(" + symCode + ")";
    }
}
