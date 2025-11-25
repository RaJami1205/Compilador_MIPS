package Compilador;

import java.util.*;

public class SymbolTable {
    public List<Map<String, MySymbol>> scopes;
    public int currentScope;

    public SymbolTable() {
        scopes = new ArrayList<>();
        enterScope(); 
    }

    public void enterScope() {
        scopes.add(new HashMap<>());
        currentScope = scopes.size() - 1;
        System.out.println("[ST] enterScope -> nivel " + currentScope);
    }

    public void exitScope() {
       /*  if (!scopes.isEmpty()) {
            System.out.println("[ST] exitScope -> saliendo nivel " + currentScope);
            scopes.remove(scopes.size() - 1);
            currentScope = scopes.size() - 1;
            System.out.println("[ST] now currentScope = " + currentScope);
        } else {
            currentScope = -1;
        }*/
    }

    public boolean addSymbol(String name, String type, String category, Object value) {
        Map<String, MySymbol> scope = scopes.get(currentScope);
        if (scope.containsKey(name)) {
            return false;
        }
        MySymbol sym = new MySymbol(name, type, category, value, currentScope);
        sym.setAddress(name);
        if (value != null) sym.setValue(value);
        scope.put(name, sym);
        return true;
    }

    public MySymbol getSymbol(String name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            Map<String, MySymbol> scope = scopes.get(i);
            if (scope.containsKey(name)) {
                MySymbol found = scope.get(name);
                return found;
            }
        }
        return null;
    }

    public boolean updateValue(String name, Object value) {
        MySymbol s = getSymbol(name);
        if (s != null) {
            s.setValue(value);
            return true;
        }
        return false;
    }

    public int tempCount = 0;

    public MySymbol newTemp(String type) {
        String tempName = "t" + tempCount++;
        MySymbol temp = new MySymbol(tempName, type, "temporal", null, currentScope);
        temp.setTemporary(true);
        addSymbol(tempName, type, "temporal", null);
        return temp;
    }

    public void setFunctionInfo(String name, List<String> paramTypes, String returnType) {
        MySymbol func = getSymbol(name);
        if (func != null) {
            func.setParamTypes(paramTypes);
            func.setReturnType(returnType);
        }
    }

    public void printTable() {
        System.out.println("========= TABLA DE SÍMBOLOS =========");
        for (int i = 0; i < scopes.size(); i++) {
            System.out.println("Ámbito nivel " + i + ":");
            for (MySymbol s : scopes.get(i).values()) {
                System.out.println("  " + s);
            }
        }
        System.out.println("=====================================");
    }
}