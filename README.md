# Compilador MIPS

Proyecto de curso — un compilador educativo que transforma un lenguaje de ejemplo en código MIPS. Este repositorio contiene un pipeline sencillo pero completo: análisis léxico (JFlex), análisis sintáctico (CUP), generación de código intermedio y traducción final a ensamblador MIPS.

**Autores:** Diego Araya Ureña · Raúl Alfaro Rodríguez

**Características principales:**
- **Analizador léxico:** Implementado con `JFlex` (`src/Codigo/Lexer.flex`).
- **Parser:** Generado con `java-cup` a partir de `src/Codigo/Parser.cup`.
- **Código intermedio:** Estructuras y cuadruplos en `src/compilador`.
- **Generador MIPS:** Conversión desde código intermedio a ensamblador MIPS.

**Estructura general del proyecto:**
- `src/` : código fuente (lexer, parser, backend, tests).
- `lib/` : dependencias (`jflex`, `java-cup`, etc.).
- `bin/` : artefactos compilados.
- `src/testing/` : archivos de prueba (ej. `ejemplo4.txt`).

**Uso rápido (Windows / PowerShell)**
Puedes usar los tasks de VS Code definidas en el workspace accediendo a el mediante ctrl + shift + p y escribiendo "tasks: run task".
- `Generar Lexer` → genera `src/ParserLexer/Lexer.java`.
- `Generar Parser` → genera `src/ParserLexer/Parser.java` y `sym.java`.
- `Compilar Java` → compila las clases en `bin/`.
- `Ejecutar TestParser` → ejecuta `Compilador.TestParser` con un archivo de prueba.

O ejecuta manualmente los comandos (desde la raíz del proyecto):

```powershell
# Generar lexer
"C:/Program Files/Java/jdk-25/bin/java.exe" -jar lib/jflex-full-1.9.1.jar src/Codigo/Lexer.flex -d src/ParserLexer

# Generar parser
"C:/Program Files/Java/jdk-25/bin/java.exe" -jar lib/java-cup-11b.jar -parser Parser -symbols sym -destdir src/ParserLexer src/Codigo/Parser.cup

# Compilar
"C:/Program Files/Java/jdk-25/bin/javac.exe" -d bin -cp "lib/*;src" src/ParserLexer/*.java src/Compilador/*.java

# Ejecutar test (ajusta la ruta del JRE/JDK si es necesario)
"C:/Program Files/Java/jdk-25/bin/java.exe" -cp "bin;lib/*" Compilador.TestParser src/testing/ejemplo4.txt
```