package Compilador;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * MIPSCodeGenerator - Generador de código MIPS a partir de cuádruplos (IntermediateCode).
 *
 * Correcciones principales:
 * - loadToT1 corregido (usaba $t0 por error).
 * - emitPrint: diferencia correctamente char vs string vs entero.
 * - emitAssign: guarda char como valor ASCII (li) y string como dirección (la).
 * - emitRelational: maneja correctamente literales char y variables char.
 * - Safe naming y recolección de literales/identificadores.
 */
public class MIPSCodeGenerator {
    private IntermediateCode icode;
    private SymbolTable symtab;
    private PrintWriter out;
    private List<Quadruple> quads = new ArrayList<>();

    // Literales string detectadas y su etiqueta _strN
    private Map<String,String> literalStrings = new LinkedHashMap<>();
    private int literalCounter = 0;

    // Tipos del lenguaje: no deben acabar en .data
    private static final Set<String> TYPE_NAMES = new HashSet<>(Arrays.asList(
            "int","float","char","string","bool"
    ));

    // Reservadas MIPS (si coincide exactamente con el id -> renombrar)
    private static final Set<String> MIPS_RESERVED = new HashSet<>(Arrays.asList(
            "beq","bne","blt","ble","bgt","bge","j","jr","jal",
            "lw","sw","la","li","add","addi","sub","mul","div","mfhi","mflo","syscall"
    ));

    public MIPSCodeGenerator(IntermediateCode icode, SymbolTable symtab) {
        this.icode = icode;
        this.symtab = symtab;
        this.quads = obtainQuads(icode);
    }

    /** Public: genera el archivo .asm */
    public void generate(String outPath) throws IOException {
        out = new PrintWriter(new FileWriter(outPath));
        emitHeader();
        preScanForLiterals();
        emitDataSegment();
        emitTextSegment();
        out.close();
    }

    private void emitHeader() {
        out.println("# CODIGO MIPS generado por MIPSCodeGenerator");
        out.println();
    }

    /* ===================== helpers literales ===================== */
    private boolean isStringLiteral(String s) {
        if (s == null) return false;
        s = s.trim();
        return (s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\""));
    }

    private boolean isNumber(String s) {
        return s != null && s.matches("-?\\d+");
    }

    /**
     * Normaliza el contenido de un literal (quita comillas externas).
     * Devuelve el contenido listo para .asciiz o para obtener ASCII del char.
     */
    private String normalizeLiteralContent(String literal) {
        String tmp = literal.trim();
        if ((tmp.startsWith("'") && tmp.endsWith("'")) || (tmp.startsWith("\"") && tmp.endsWith("\""))) {
            tmp = tmp.substring(1, tmp.length()-1);
        }
        // escapar comillas dobles para .asciiz
        tmp = tmp.replace("\"","\\\"");
        return tmp;
    }

    private String getLiteralLabel(String literal) {
        String content = normalizeLiteralContent(literal);
        if (literalStrings.containsKey(content)) return literalStrings.get(content);
        String lbl = "_str" + (++literalCounter);
        literalStrings.put(content, lbl);
        return lbl;
    }

    private void preScanForLiterals() {
        for (Quadruple q : quads) {
            checkLiteral(q.getArg1());
            checkLiteral(q.getArg2());
            checkLiteral(q.getResult());
        }
    }

    private void checkLiteral(String s) {
        if (s != null && isStringLiteral(s)) getLiteralLabel(s);
    }

    /* ===================== DATA SEGMENT ===================== */
    private void emitDataSegment() {
        out.println(".data");

        // 1) Recolectar nombres desde symbol table (si existe)
        Set<String> names = new LinkedHashSet<>();
        if (symtab != null && symtab.scopes != null) {
            for (Map<String, MySymbol> scope : symtab.scopes) {
                for (MySymbol sym : scope.values()) {
                    // ignorar funciones/procedimientos
                    if ("function".equalsIgnoreCase(sym.getCategory()) || "procedure".equalsIgnoreCase(sym.getCategory()))
                        continue;
                    // evitar declarar tipos como variables
                    if (sym.getName() != null && TYPE_NAMES.contains(sym.getName())) continue;
                    String addr = sym.getAddress();
                    String safe = safeNameWithCheck(addr);
                    if (safe != null) names.add(safe);
                }
            }
        }

        // 2) Recolectar identificadores desde cuádruplos (temporales, variables no en tabla, etc.)
        names.addAll(collectNamesFromQuads());

        // 3) Emitir .word para cada nombre recolectado (filtrando tipos/literales)
        for (String n : names) {
            if (n == null || n.isEmpty()) continue;
            if (n.startsWith("_str")) continue;
            if (TYPE_NAMES.contains(n)) continue;
            if ("true".equals(n) || "false".equals(n) || "_null".equals(n)) continue;
            out.printf("%s: .word 0\n", n);
        }

        out.println();

        // 4) Strings literales (nota: la clave del map es el contenido sin comillas)
        for (Map.Entry<String,String> e : literalStrings.entrySet()) {
            out.printf("%s: .asciiz \"%s\"\n", e.getValue(), e.getKey());
        }

        out.println();

        // 5) Constantes obligatorias
        out.println("_null: .word 0");
        out.println("true: .word 1");
        out.println("false: .word 0");
        out.println();
    }

    private Set<String> collectNamesFromQuads() {
        Set<String> res = new LinkedHashSet<>();
        for (Quadruple q : quads) {
            addIfIdentifier(res, q.getArg1());
            addIfIdentifier(res, q.getArg2());
            addIfIdentifier(res, q.getResult());
        }
        return res;
    }

    private void addIfIdentifier(Set<String> set, String token) {
        if (token == null) return;
        token = token.trim();

        // ignorar numeros y literales
        if (isNumber(token) || isStringLiteral(token)) return;
        // etiquetas Lx
        if (token.matches("^L\\d+$")) return;
        // si es literal label _strN
        if (token.startsWith("_str")) return;
        // evitar tipos
        if (TYPE_NAMES.contains(token)) return;
        // evitar true/false/null como variables
        if ("true".equals(token) || "false".equals(token) || "_null".equals(token)) return;

        // extraer base si viene con índice a[3]
        String base = stripIndexIfAny(token);
        String safe = safeNameWithCheck(base);
        if (safe != null) set.add(safe);
    }

    private String stripIndexIfAny(String s) {
        if (s == null) return null;
        int i = s.indexOf('[');
        if (i >= 0) return s.substring(0,i);
        return s;
    }

    /* ===================== TEXT SEGMENT ===================== */
    private void emitTextSegment() {
        out.println(".text");
        out.println(".globl main");
        out.println();
        out.println("main:");

        for (Quadruple q : quads) {
            emitQuad(q);
        }

        out.println();
        out.println("# terminar programa");
        out.println("li $v0, 10");
        out.println("syscall");
    }

    private void emitQuad(Quadruple q) {
        String op = q.getOperator();
        String a1 = q.getArg1();
        String a2 = q.getArg2();
        String r  = q.getResult();

        if (op == null) return;

        switch (op) {
            case "LABEL":
                out.println(safeLabel(r != null ? r : a1) + ":");
                break;

            case "DECLARE":
            case "DECLARE_ARRAY":
                out.println("# " + op + " " + r);
                break;

            case "=":
                emitAssign(a1, r);
                break;

            case "+":
            case "-":
            case "*":
            case "/":
            case "%":
                emitArithmetic(op, a1, a2, r);
                break;

            case "<": case ">": case "<=": case ">=": case "==": case "!=":
                emitRelational(op, a1, a2, r);
                break;

            case "PRINT":
                emitPrint(a1);
                break;

            case "RETURN":
                emitReturn(a1);
                break;

            case "GOTO":
                if (r != null) out.println("    j " + safeLabel(r));
                break;

            case "IF_FALSE":
                if (a1 != null && r != null) {
                    loadToT0(a1);
                    out.println("    beq $t0, $zero, " + safeLabel(r));
                }
                break;

            case "IF_TRUE":
                if (a1 != null && r != null) {
                    loadToT0(a1);
                    out.println("    bne $t0, $zero, " + safeLabel(r));
                }
                break;

            case "READ":
                if (r != null) {
                    out.println("    li $v0, 5");
                    out.println("    syscall");
                    out.printf("    sw $v0, %s\n", safeNameWithCheck(r));
                }
                break;

            default:
                out.println("# OP no soportada: " + q);
                break;
        }
    }

    /* ===================== ASIGNACIÓN ===================== */
    private void emitAssign(String src, String dest) {
        out.println("# ASSIGN " + dest + " = " + src);

        String destName = safeNameWithCheck(dest);
        if (destName == null) destName = dest; // fallback

        if (isNumber(src)) {
            out.printf("    li $t0, %s\n", src);
        } else if ("true".equals(src) || "false".equals(src)) {
            out.printf("    lw $t0, %s\n", src);
        } else if (isStringLiteral(src)) {
            String lit = normalizeLiteralContent(src);

            // Si es char literal de 1 caracter → guardar ASCII, NO string pointer
            if (lit.length() == 1) {
                out.printf("    li $t0, %d\n", (int) lit.charAt(0));
            } else {
                // string → su dirección
                out.printf("    la $t0, %s\n", getLiteralLabel(src));
            }
        } else {
            // src es una variable/temporal: cargar su contenido (puede ser puntero en caso de strings)
            out.printf("    lw $t0, %s\n", safeNameWithCheck(src));
        }

        out.printf("    sw $t0, %s\n", destName);
    }

    /* ===================== ARITMÉTICA ===================== */
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

        out.printf("    sw $t2, %s\n", safeNameWithCheck(res));
    }

    /* ===================== RELACIONAL ===================== */
    private void emitRelational(String op, String a1, String a2, String res) {
        out.println("# RELOP " + res + " = " + a1 + " " + op + " " + a2);

        // Si alguno de los operandos es literal char ('A') queremos tratarlo como ASCII
        boolean a1IsChar = isStringLiteral(a1) && normalizeLiteralContent(a1).length() == 1;
        boolean a2IsChar = isStringLiteral(a2) && normalizeLiteralContent(a2).length() == 1;

        if (a1IsChar) {
            String lit = normalizeLiteralContent(a1);
            out.printf("    li $t0, %d\n", (int) lit.charAt(0));
        } else {
            loadToT0(a1);
        }

        if (a2IsChar) {
            String lit = normalizeLiteralContent(a2);
            out.printf("    li $t1, %d\n", (int) lit.charAt(0));
        } else {
            loadToT1(a2);
        }

        switch (op) {
            case "<":
                out.println("    slt $t2, $t0, $t1"); break;
            case ">":
                out.println("    slt $t2, $t1, $t0"); break;
            case "<=":
                out.println("    slt $t2, $t1, $t0");
                out.println("    xori $t2, $t2, 1"); break;
            case ">=":
                out.println("    slt $t2, $t0, $t1");
                out.println("    xori $t2, $t2, 1"); break;
            case "==":
                out.println("    xor $t2, $t0, $t1");
                out.println("    sltiu $t2, $t2, 1"); break;
            case "!=":
                out.println("    xor $t2, $t0, $t1");
                out.println("    sltu $t2, $zero, $t2"); break;
            default:
                out.println("    li $t2, 0"); break;
        }

        out.printf("    sw $t2, %s\n", safeNameWithCheck(res));
    }

    /* ===================== PRINT ===================== */
    private void emitPrint(String what) {
        out.println("# PRINT " + what);

        // 1) literal string
        if (isStringLiteral(what)) {
            String content = normalizeLiteralContent(what);
            // Si es char literal de longitud 1 -> imprimir como caracter (syscall 11)
            if (content.length() == 1) {
                out.printf("    li $a0, %d\n", (int) content.charAt(0));
                out.println("    li $v0, 11");
                out.println("    syscall");
                return;
            } else {
                out.printf("    la $a0, %s\n", getLiteralLabel(what));
                out.println("    li $v0, 4");
                out.println("    syscall");
                return;
            }
        }

        // 2) boolean literal
        if ("true".equals(what) || "false".equals(what)) {
            out.printf("    lw $a0, %s\n", what);
            out.println("    li $v0, 1");
            out.println("    syscall");
            return;
        }

        // 3) numeric literal
        if (isNumber(what)) {
            out.printf("    li $a0, %s\n", what);
            out.println("    li $v0, 1");
            out.println("    syscall");
            return;
        }

        // 4) variable/temporal: decidir según tipo en symbol table
        MySymbol s = (symtab != null) ? symtab.getSymbol(stripIndexIfAny(what)) : null;
        if (s != null && s.getType() != null) {
            String t = s.getType().toLowerCase();
            if (t.contains("string")) {
                // variable que guarda puntero a string -> cargar puntero y syscall 4
                out.printf("    lw $a0, %s\n", safeNameWithCheck(what));
                out.println("    li $v0, 4");
                out.println("    syscall");
                return;
            } else if (t.contains("char")) {
                // char almacenado como ASCII entero -> cargar y syscall 11
                out.printf("    lw $a0, %s\n", safeNameWithCheck(what));
                out.println("    li $v0, 11");
                out.println("    syscall");
                return;
            } else {
                // entero / float -> imprimir como número (syscall 1)
                out.printf("    lw $a0, %s\n", safeNameWithCheck(what));
                out.println("    li $v0, 1");
                out.println("    syscall");
                return;
            }
        }

        // 5) fallback: asumir entero
        out.printf("    lw $a0, %s\n", safeNameWithCheck(what));
        out.println("    li $v0, 1");
        out.println("    syscall");
    }

    /* ===================== RETURN ===================== */
    private void emitReturn(String val) {
        out.println("# RETURN " + val);
        if (val != null) {
            if (isNumber(val)) out.printf("    li $v0, %s\n", val);
            else out.printf("    lw $v0, %s\n", safeNameWithCheck(val));
        }
        out.println("    jr $ra");
    }

    /* ===================== CARGAS ===================== */
    private void loadToT0(String src) {
        if (src == null) { out.println("    li $t0, 0"); return; }
        if (isNumber(src)) { out.printf("    li $t0, %s\n", src); return; }
        if ("true".equals(src) || "false".equals(src)) { out.printf("    lw $t0, %s\n", src); return; }
        if (isStringLiteral(src)) {
            String lit = normalizeLiteralContent(src);
            if (lit.length() == 1) {
                out.printf("    li $t0, %d\n", (int) lit.charAt(0));
                return;
            } else {
                out.printf("    la $t0, %s\n", getLiteralLabel(src));
                return;
            }
        }
        out.printf("    lw $t0, %s\n", safeNameWithCheck(src));
    }

    private void loadToT1(String src) {
        if (src == null) { out.println("    li $t1, 0"); return; }
        if (isNumber(src)) { out.printf("    li $t1, %s\n", src); return; }
        if ("true".equals(src) || "false".equals(src)) { out.printf("    lw $t1, %s\n", src); return; }
        if (isStringLiteral(src)) {
            String lit = normalizeLiteralContent(src);
            if (lit.length() == 1) {
                // <-- CORRECCIÓN: usar $t1 (no $t0)
                out.printf("    li $t1, %d\n", (int) lit.charAt(0));
                return;
            } else {
                out.printf("    la $t1, %s\n", getLiteralLabel(src));
                return;
            }
        }
        out.printf("    lw $t1, %s\n", safeNameWithCheck(src));
    }

    /* ===================== NOMBRES SEGUROS ===================== */
    /**
     * Devuelve un nombre "safe" para usar en .data/text.
     * Si la symbol table contiene el identificador con ese nombre EXACTO,
     * devuelve el mismo nombre (para no romper la relación).
     * Si es un tipo, devuelve null (no declarar).
     * Si coincide con instrucción MIPS, le añade prefijo.
     */
    private String safeNameWithCheck(String id) {
        if (id == null) return "_null";
        id = id.trim();
        // si coincidia exactamente con true/false/_null o literal label -> devolver tal cual
        if ("true".equals(id) || "false".equals(id) || "_null".equals(id)) return id;
        if (id.startsWith("_str")) return id;

        // evitar declarar tipos
        if (TYPE_NAMES.contains(id)) return null;

        // si la symbol table contiene exactamente el símbolo con ese nombre -> devolverlo tal cual
        if (symtab != null && symtab.getSymbol(id) != null) return id;

        // limpiar caracteres no válidos
        String clean = id.replaceAll("[^A-Za-z0-9_]", "_");

        // si el limpio coincide con instrucción MIPS -> prefix
        if (MIPS_RESERVED.contains(clean)) clean = "_v_" + clean;

        // si empieza con número -> prefix
        if (clean.matches("^[0-9].*")) clean = "_v_" + clean;

        return clean;
    }

    private String safeLabel(String lbl) {
        if (lbl == null) return "_L_null";
        return lbl.replaceAll("[^A-Za-z0-9_]", "_");
    }

    /* ===================== UTIL: obtener quads ===================== */
    // Intenta usar icode.getCode(); si no existe hace reflection al campo "code"
    @SuppressWarnings("unchecked")
    private List<Quadruple> obtainQuads(IntermediateCode ic) {
        if (ic == null) return new ArrayList<>();
        try {
            // intentar método público getCode()
            Method m = ic.getClass().getMethod("getCode");
            Object r = m.invoke(ic);
            if (r instanceof List) return (List<Quadruple>) r;
        } catch (NoSuchMethodException ignored) {
            // fallback por reflection al campo "code"
        } catch (Exception e) {
            // si falla, continuamos a reflection fallback
        }
        // reflection fallback (existía en tu versión anterior)
        try {
            Field f = ic.getClass().getDeclaredField("code");
            f.setAccessible(true);
            Object v = f.get(ic);
            if (v instanceof List) return (List<Quadruple>) v;
        } catch (Exception ignored) {}
        return new ArrayList<>();
    }
}
