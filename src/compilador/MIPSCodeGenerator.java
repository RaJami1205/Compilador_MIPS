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
        // Quitar comillas simples
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

        // ======== 1. Declarar TODAS las variables de la tabla de símbolos ========
        Set<String> emitted = new LinkedHashSet<>();

        for (Map<String, MySymbol> scope : symtab.scopes) {
            for (MySymbol s : scope.values()) {

                // Ignorar funciones y procedimientos
                if ("function".equalsIgnoreCase(s.getCategory()) ||
                    "procedure".equalsIgnoreCase(s.getCategory())) {
                    continue;
                }

                String name = safeName(s.getAddress());
                if (emitted.contains(name)) continue;
                emitted.add(name);

                if (s.isArray()) {
                    int size = Math.max(1, s.getArraySize());
                    out.printf("%s: .space %d\n", name, size * 4);
                } else {
                    out.printf("%s: .word 0\n", name);
                }
            }
        }

        // ======== 2. Declarar STRINGS literales ========
        for (Map.Entry<String, String> e : literalStrings.entrySet()) {
            out.printf("%s: .asciiz \"%s\"\n", e.getValue(), e.getKey());
        }

        out.println();
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

            case "+": case "-": case "*": case "/": case "%":
                emitArithmetic(op, a1, a2, res);
                break;

            case "PRINT":
                emitPrint(a1);
                break;

            case "RETURN":
                emitReturn(a1);
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
            out.printf("    la $t0, %s\n", label);
        } else {
            out.printf("    lw $t0, %s\n", safeName(src));
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
            out.printf("    lw $a0, %s\n", safeName(what));
            out.println("    li $v0, 1");
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
        if (isNumber(src)) {
            out.printf("    li $t0, %s\n", src);
        } else if (isStringLiteral(src)) {
            out.printf("    la $t0, %s\n", getLiteralLabel(src));
        } else {
            out.printf("    lw $t0, %s\n", safeName(src));
        }
    }

    private void loadToT1(String src) {
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
        return s != null && s.startsWith("'") && s.endsWith("'");
    }

    private String getLiteralLabel(String literal) {
        String clean = literal.substring(1, literal.length() - 1);

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
        return lbl.replaceAll("[^a-zA-Z0-9_]", "_");
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
