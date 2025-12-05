package Compilador;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.*;

public class MIPSCodeGenerator {
    private IntermediateCode icode;
    private SymbolTable symtab;
    private PrintWriter out;
    private List<Quadruple> quads;

    // Para manejar los strings literales
    private Map<String, String> literalStrings = new LinkedHashMap<>();
    private int literalCounter = 0;

    private void preScanForLiterals() {
        for (Quadruple q : quads) {
            checkLiteral(q.getArg1());
            checkLiteral(q.getArg2());
            checkLiteral(q.getResult());
        }
    }

    private void checkLiteral(String s) {
        if (s != null && isStringLiteral(s)) {
            getLiteralLabel(s); // asegura que se agregue a literalStrings
        }
    }

    private static final Set<String> MIPS_RESERVED = new HashSet<>(Arrays.asList(
        // Branch and jump
        "b", "beq", "bne", "blt", "ble", "bgt", "bge",
        "j", "jr", "jal",

        // Loads and stores
        "lw", "sw", "la", "li",

        // Arithmetic instructions
        "add", "addi", "sub", "mul", "div", "mfhi", "mflo",

        // Syscall
        "syscall"
    ));

    public MIPSCodeGenerator(IntermediateCode icode, SymbolTable symtab) {
        this.icode = icode;
        this.symtab = symtab;
        this.quads = extractQuadsViaReflection(icode);
    }

    /** Genera el archivo .asm MIPS */
    public void generate(String outPath) throws IOException {
        out = new PrintWriter(new FileWriter(outPath));

        emitHeader();
        preScanForLiterals();  // guardar strings literales
        emitDataSegment();      // variables + strings literales
        emitTextSegment();      // código
        out.close();
    }

    private void emitHeader() {
        out.println("# CODIGO MIPS generado por MIPSCodeGenerator");
        out.println();
    }

    private String normalizeStringLiteralForData(String s) {
        // Quitar comillas simples si vienen: 'hola' -> hola
        if (s.startsWith("'") && s.endsWith("'")) {
            s = s.substring(1, s.length() - 1);
        }
        // Escapar comillas dobles
        s = s.replace("\"", "\\\"");
        return s;
    }

    /* ============================================================
       ===============        DATA SEGMENT        ==================
       ============================================================ */
    private void emitDataSegment() {
        out.println(".data");

        // 1) Recolectar nombres desde la symbol table (si existen)
        Set<String> emitted = new LinkedHashSet<>();
        if (symtab != null && symtab.scopes != null) {
            for (Map<String, MySymbol> scope : symtab.scopes) {
                for (MySymbol s : scope.values()) {
                    // ignorar funciones/procedimientos como variables de datos
                    if ("function".equalsIgnoreCase(s.getCategory()) ||
                        "procedure".equalsIgnoreCase(s.getCategory())) continue;

                    String name = safeName(s.getAddress());
                    emitted.add(name);
                }
            }
        }

        // 2) Recolectar TODOS los identificadores usados en los cuádruplos
        //    (esto asegura que temporales, locales o variables que no estén en symtab
        //     también sean reservadas en .data)
        Set<String> neededFromQuads = collectNamesFromQuads();
        // unir sets (emitted contiene names from symtab, we'll ensure emitted contains quad names too)
        emitted.addAll(neededFromQuads);

        // 3) Emitir declaraciones para cada nombre recolectado
        //    (si el nombre es una etiqueta de string literal tipo _strX la saltamos)
        for (String name : new LinkedHashSet<>(emitted)) {
            if (name == null || name.isEmpty()) continue;
            // Saltar nombres que corresponden a literales (ej: _str1) o constantes que manejamos aparte
            if (name.startsWith("_str")) continue;
            if (name.equals("true") || name.equals("false") || name.equals("_null")) continue;
            // Emitir .word para variables / temporales / parámetros
            out.printf("%s: .word 0\n", name);
        }

        out.println();

        // 4) Declarar STRINGS literales
        for (Map.Entry<String, String> e : literalStrings.entrySet()) {
            // la clave es el texto limpio (sin comillas)
            String lit = normalizeStringLiteralForData(e.getKey());
            out.printf("%s: .asciiz \"%s\"\n", e.getValue(), lit);
        }

        out.println();

        // 5) Constantes auxiliares si no se agregaron ya
        //    (null, true, false pueden ser referenciadas en icode)
        //    Sólo añadir si no existen ya en emitted
        if (!emitted.contains("_null")) {
            out.println("_null: .word 0");
        }
        if (!emitted.contains("true")) {
            out.println("true: .word 1");
        }
        if (!emitted.contains("false")) {
            out.println("false: .word 0");
        }

        out.println();
    }

    /**
     * Recolecta identificadores (nombres) encontrados en los cuádruplos.
     * Filtra números, literales de string y etiquetas de salto (L...).
     * Devuelve nombres ya "safeName"d (sanitizados).
     */
    private Set<String> collectNamesFromQuads() {
        Set<String> names = new LinkedHashSet<>();
        if (quads == null) return names;

        for (Quadruple q : quads) {
            addIfIdentifier(names, q.getArg1());
            addIfIdentifier(names, q.getArg2());
            addIfIdentifier(names, q.getResult());
            // algunas instrucciones usan a1 como etiqueta (LABEL) -> no queremos declarar esas L1.. como variables
        }
        return names;
    }

    private void addIfIdentifier(Set<String> names, String token) {
        if (token == null) return;
        token = token.trim();
        // si es número o literal string, ignorar
        if (isNumber(token) || isStringLiteral(token)) return;
        // si parece una etiqueta de salto Lxx (generada por GenerateLabel), ignorar
        if (token.matches("^L\\d+$")) return;
        // si ya es un label de literal (ej _strN) ignorar como variable
        if (token.startsWith("_str")) return;
        // si es operador o palabra reservada improbable, ignorar
        if (MIPS_RESERVED.contains(token)) return;

        // si viene con índice como a[3] -> extraer nombre base
        String base = stripIndexIfAny(token);
        // sanitizar y agregar
        String safe = safeName(base);
        names.add(safe);
    }

    /* ============================================================
       =================        TEXT SEGMENT       =================
       ============================================================ */
    private void emitTextSegment() {
        out.println(".text");
        out.println(".globl main");
        out.println();

        // Etiqueta principal
        out.println("main:");

        for (Quadruple q : quads) {
            emitQuad(q);
        }

        out.println();
        out.println("# terminar programa");
        out.println("li $v0, 10");
        out.println("syscall");
    }

    /* ============================================================
       =================      EMIT QUADS          ==================
       ============================================================ */
    private void emitQuad(Quadruple q) {
        String op = q.getOperator();
        String a1 = q.getArg1();
        String a2 = q.getArg2();
        String res = q.getResult();

        if (op == null) return;

        switch (op) {
            case "LABEL":
                out.println(safeLabel(res != null ? res : a1) + ":");
                break;

            case "DECLARE":
            case "DECLARE_ARRAY":
                out.println("# DECLARE " + res);
                break;

            case "=":
                emitAssign(a1, res);
                break;

            case "<": case "<=": case ">": case ">=": case "==": case "!=":
                emitRelational(op, a1, a2, res);
                break;

            case "+": case "-": case "*": case "/": case "%":
                emitArithmetic(op, a1, a2, res);
                break;

            case "PRINT":
                emitPrint(a1);
                break;

            case "RETURN":
                emitReturn(a1);
                break;

            case "GOTO":
                if (res != null) {
                    out.println("    j " + safeLabel(res));
                }
                break;

            case "IF_FALSE":
                // IF_FALSE cond -> label
                if (a1 != null && res != null) {
                    loadToT0(a1);
                    out.println("    beq $t0, $zero, " + safeLabel(res));
                }
                break;

            case "IF_TRUE":
                if (a1 != null && res != null) {
                    loadToT0(a1);
                    out.println("    bne $t0, $zero, " + safeLabel(res));
                }
                break;

            case "READ":
                // Leer entero desde stdin y guardar en res
                if (res != null) {
                    out.println("    li $v0, 5");
                    out.println("    syscall");
                    out.printf("    sw $v0, %s\n", safeName(res));
                }
                break;

            default:
                out.println("# OP no soportada: " + q);
        }
    }

    /* =======================  ASIGNACIÓN ======================== */
    private void emitAssign(String src, String dest) {
        out.println("# ASSIGN " + dest + " = " + src);

        if (isNumber(src)) {
            out.printf("    li $t0, %s\n", src);
        } else if (isStringLiteral(src)) {
            String label = getLiteralLabel(src);
            out.printf("    la $t0, %s\n", label);     // cargar address del literal
        } else {
            out.printf("    lw $t0, %s\n", safeName(src)); // cargar valor/puntero si es variable
        }

        out.printf("    sw $t0, %s\n", safeName(dest));
    }

    /* =======================  ARITMÉTICA ======================== */
    private void emitArithmetic(String op, String a1, String a2, String res) {
        out.println("# ARITH " + res + " = " + a1 + " " + op + " " + a2);

        loadToT0(a1);
        loadToT1(a2);

        switch (op) {
            case "+": out.println("    add $t2, $t0, $t1"); break;
            case "-": out.println("    sub $t2, $t0, $t1"); break;
            case "*": out.println("    mul $t2, $t0, $t1"); break;
            case "/":
                out.println("    div $t0, $t1");
                out.println("    mflo $t2");
                break;
            case "%":
                out.println("    div $t0, $t1");
                out.println("    mfhi $t2");
                break;
        }

        out.printf("    sw $t2, %s\n", safeName(res));
    }

    /* ======================= RELACIONES ======================== */
    private void emitRelational(String op, String a1, String a2, String res) {
        out.println("# RELOP " + res + " = " + a1 + " " + op + " " + a2);

        loadToT0(a1);
        loadToT1(a2);

        switch (op) {
            case "<":
                out.println("    slt $t2, $t0, $t1");
                break;
            case ">":
                out.println("    slt $t2, $t1, $t0");
                break;
            case "<=":
                out.println("    slt $t2, $t1, $t0");
                out.println("    xori $t2, $t2, 1");
                break;
            case ">=":
                out.println("    slt $t2, $t0, $t1");
                out.println("    xori $t2, $t2, 1");
                break;
            case "==":
                out.println("    xor $t2, $t0, $t1");
                out.println("    sltiu $t2, $t2, 1");
                break;
            case "!=":
                out.println("    xor $t2, $t0, $t1");
                out.println("    sltu $t2, $zero, $t2");
                break;
            default:
                out.println("    li $t2, 0");
        }

        out.printf("    sw $t2, %s\n", safeName(res));
    }

    /* =======================   PRINT   ======================== */
    private void emitPrint(String what) {
        out.println("# PRINT " + what);

        if (isStringLiteral(what)) {
            String lbl = getLiteralLabel(what);
            out.printf("    la $a0, %s\n", lbl);
            out.println("    li $v0, 4");
        } else if (isNumber(what)) {
            out.printf("    li $a0, %s\n", what);
            out.println("    li $v0, 1");
        } else {
            // Intentar resolver el tipo desde la tabla de símbolos para
            // distinguir entre números y cadenas almacenadas en memoria.
            MySymbol s = symtab != null ? symtab.getSymbol(stripIndexIfAny(what)) : null;
            if (s != null && s.getType() != null) {
                String t = s.getType().toLowerCase();
                if (t.contains("string") || t.contains("char")) {
                    // variable que guarda puntero a string -> cargar puntero y syscall 4
                    out.printf("    lw $a0, %s\n", safeName(what));
                    out.println("    li $v0, 4");
                } else {
                    out.printf("    lw $a0, %s\n", safeName(what));
                    out.println("    li $v0, 1");
                }
            } else {
                // si no sabemos, asumimos entero
                out.printf("    lw $a0, %s\n", safeName(what));
                out.println("    li $v0, 1");
            }
        }

        out.println("    syscall");
    }

    /* =======================  RETURN  ======================== */
    private void emitReturn(String val) {
        out.println("# RETURN " + val);

        if (val != null) {
            if (isNumber(val)) out.printf("    li $v0, %s\n", val);
            else out.printf("    lw $v0, %s\n", safeName(val));
        }

        out.println("    jr $ra");
    }

    /* ============================================================
       =================        HELPERS         ====================
       ============================================================ */

    private void loadToT0(String src) {
        if (src == null) {
            out.println("    li $t0, 0");
            return;
        }
        if (isNumber(src)) {
            out.printf("    li $t0, %s\n", src);
        } else if (isStringLiteral(src)) {
            out.printf("    la $t0, %s\n", getLiteralLabel(src));
        } else {
            out.printf("    lw $t0, %s\n", safeName(src));
        }
    }

    private void loadToT1(String src) {
        if (src == null) {
            out.println("    li $t1, 0");
            return;
        }
        if (isNumber(src)) {
            out.printf("    li $t1, %s\n", src);
        } else if (isStringLiteral(src)) {
            out.printf("    la $t1, %s\n", getLiteralLabel(src));
        } else {
            out.printf("    lw $t1, %s\n", safeName(src));
        }
    }

    private boolean isNumber(String s) {
        return s != null && s.matches("-?\\d+");
    }

    private boolean isStringLiteral(String s) {
        if (s == null) return false;
        s = s.trim();
        return (s.startsWith("'") && s.endsWith("'"))
            || (s.startsWith("\"") && s.endsWith("\""));
    }

    private String getLiteralLabel(String literal) {
        String clean = literal.trim();

        if ((clean.startsWith("'") && clean.endsWith("'")) ||
            (clean.startsWith("\"") && clean.endsWith("\""))) {
            clean = clean.substring(1, clean.length() - 1); // quita comillas
        }

        if (literalStrings.containsKey(clean)) return literalStrings.get(clean);

        String label = "_str" + (++literalCounter);
        literalStrings.put(clean, label);

        return label;
    }

    private String safeName(String id) {
        if (id == null) return "_null";

        // Reemplazar caracteres invalidos
        String clean = id.replaceAll("[^a-zA-Z0-9_]", "_");

        // Si coincide con una instrucción MIPS, agregar prefijo seguro
        if (MIPS_RESERVED.contains(clean)) {
            clean = "_v_" + clean;
        }

        // Si comienza con un número, agregar prefijo también
        if (clean.matches("^[0-9].*")) {
            clean = "_v_" + clean;
        }

        return clean;
    }

    private String safeLabel(String lbl) {
        if (lbl == null) return "_L_null";
        return lbl.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private String stripIndexIfAny(String s) {
        if (s == null) return null;
        int idx = s.indexOf('[');
        if (idx >= 0) return s.substring(0, idx);
        return s;
    }

    @SuppressWarnings("unchecked")
    private List<Quadruple> extractQuadsViaReflection(IntermediateCode ic) {
        try {
            Field f = ic.getClass().getDeclaredField("code");
            f.setAccessible(true);
            Object v = f.get(ic);
            if (v instanceof List) return (List<Quadruple>) v;
        } catch (Exception ignored) {}
        return new ArrayList<>();
    }
}
