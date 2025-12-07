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

        boolean huboErrores = false;

        try {
            /* ============================================================
             * 1. ANÁLISIS LÉXICO 
             * ============================================================ */
            FileReader fr = new FileReader(archivo);
            Lexer lexer = new Lexer(fr);

            System.out.println("\n[LEXER] Tokens detectados:");
            Symbol token;
            while ((token = lexer.next_token()).sym != sym.EOF) {
                System.out.printf("  %-15s -> '%s'%n",
                        symToString(token.sym), lexer.yytext());
            }

            // si el lexer registró errores, no continuar
            if (!Parser.errores.isEmpty()) {
                huboErrores = true;
                System.out.println("\nSe detectaron errores léxicos.");
            }

            /* ============================================================
             * 2. ANÁLISIS SINTÁCTICO Y SEMÁNTICO
             * ============================================================ */
            if (!huboErrores) {
                fr = new FileReader(archivo);  // reiniciar entrada
                lexer = new Lexer(fr);
                Parser parser = new Parser(lexer);

                System.out.println("\n[PARSER] Iniciando análisis sintáctico y semántico...");
                parser.parse();
                System.out.println("-------------------------------------");

                if (!Parser.errores.isEmpty()) {
                    huboErrores = true;
                    System.out.println("\nSe detectaron errores sintácticos o semánticos.");
                }
            }

            /* ============================================================
             * 3. REPORTE DE ERRORES
             * ============================================================ */
            System.out.println("\n--- RESUMEN DE ERRORES ---");
            if (Parser.errores.isEmpty()) {
                System.out.println("No se encontraron errores.");
            } else {
                for (String err : Parser.errores) {
                    System.out.println("  - " + err);
                }
            }

            /* ============================================================
             * 4. TABLA DE SÍMBOLOS Y CÓDIGO INTERMEDIO
             * ============================================================ */
            System.out.println("\n--- TABLA DE SÍMBOLOS ---");
            Parser.tablaSimbolos.printTable();

            if (!huboErrores) {
                Parser.codigoIntermedio.printCode();
                String ruta = "src/Codigo_Intermedio/codigo_intermedio.txt"; 
                Parser.codigoIntermedio.exportToFile(ruta);
            }


            System.out.println("-------------------------------------");

            /* ============================================================
             * 5. GENERACIÓN DE CÓDIGO MIPS 
             * ============================================================ */
            System.out.println("\n--- GENERACIÓN DE CÓDIGO MIPS ---");

            if (!huboErrores) {
                MIPSCodeGenerator gen = new MIPSCodeGenerator(Parser.codigoIntermedio, Parser.tablaSimbolos);
                gen.generate("src/MIPS/objectCode.asm");
                System.out.println("Código MIPS generado correctamente.");
            } else {
                System.out.println("No se generó código Intermedio y MIPS debido a errores.");
            }

            System.out.println("-------------------------------------");

        } catch (Exception e) {
            System.out.println("\nError fatal durante el análisis:");
            e.printStackTrace();
            System.out.println("No se generó código Intermedio y MIPS.");
        }
    }

    /** Convierte un valor numérico de token a texto */
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
