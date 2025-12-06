package Compilador;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class IntermediateCode {

    private final List<Quadruple> code = new ArrayList<>();

    // -------------------------
    //   TEMPORALES Y ETIQUETAS
    // -------------------------
    private int tempCounter = 0;
    private int labelCounter = 0;

    public String newTemp() {
        tempCounter++;
        return "t" + tempCounter;
    }

    public String newLabel() {
        labelCounter++;
        return "L" + labelCounter;
    }

    // -------------------------
    //   AGREGAR CUADRUPLOS
    // -------------------------
    public void add(String op, String arg1, String arg2, String result) {
        code.add(new Quadruple(op, arg1, arg2, result));
    }

    // -------------------------
    //   ACCESOR
    // -------------------------
    public List<Quadruple> getCode() {
        return code;
    }

    // -------------------------
    //   IMPRESIÓN
    // -------------------------
    public void printCode() {
        System.out.println("===== CÓDIGO INTERMEDIO =====");
        for (int i = 0; i < code.size(); i++) {
            System.out.println(i + ": " + code.get(i));
        }
    }

    // -------------------------
    //   EXPORTACIÓN
    // -------------------------
    public void exportToFile(String ruta) {
        try (FileWriter fw = new FileWriter(ruta)) {
            for (Quadruple q : code) {
                fw.write(q.toString() + "\n");
            }
        } catch (IOException e) {
            System.err.println("Error al guardar archivo: " + e.getMessage());
        }
    }
}
