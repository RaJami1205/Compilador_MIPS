# =========================================
# Ejemplo MIPS 
# -----------------------------------------
 
.data
msg_input1: .asciiz "Ingrese el primer numero: "
msg_input2: .asciiz "Ingrese el segundo numero: "
msg_sum:    .asciiz "La suma es: "
msg_loop:   .asciiz "Iteracion: "
msg_end:    .asciiz "Fin del programa.\n"

.text
.globl main

# ===============================
# FUNCION: suma(a, b)
# int suma(int a, int b)
# return a + b;
# ===============================

suma:
    addi $sp, $sp, -12        # espacio para RA, FP y variables locales
    sw   $ra, 8($sp)
    sw   $fp, 4($sp)
    move $fp, $sp

    # Parámetros: a=$a0, b=$a1
    # Cuerpo de la función: return a+b
    add  $v0, $a0, $a1        # resultado en $v0

    move $sp, $fp
    lw   $ra, 8($sp)
    lw   $fp, 4($sp)
    addi $sp, $sp, 12
    jr   $ra


# ===============================
# PROCEDIMIENTO PRINCIPAL
# procedure principal()
# ===============================

main:
    addi $sp, $sp, -16
    sw   $ra, 12($sp)
    sw   $fp, 8($sp)
    move $fp, $sp

    # Variables locales (a, b, resultado, i)
    # Offsets: a=-4, b=-8, res=-12, i=-16

    # --- INPUT ---
    li   $v0, 4
    la   $a0, msg_input1
    syscall

    li   $v0, 5          # leer entero
    syscall
    sw   $v0, -4($fp)    # guardar en a

    li   $v0, 4
    la   $a0, msg_input2
    syscall

    li   $v0, 5
    syscall
    sw   $v0, -8($fp)    # guardar en b

    # --- LLAMADA A FUNCION suma(a,b) ---
    lw   $a0, -4($fp)    # param 1
    lw   $a1, -8($fp)    # param 2
    jal  suma
    sw   $v0, -12($fp)   # resultado

    # --- OUTPUT ---
    li   $v0, 4
    la   $a0, msg_sum
    syscall

    lw   $a0, -12($fp)
    li   $v0, 1
    syscall

    # salto de línea
    li   $a0, 10
    li   $v0, 11
    syscall


    # ============================
    # FOR i = 1 TO 5 STEP 1 DO
    #   output(i);
    #   if (i == 3) then break;
    # END LOOP;
    # ============================
    
    li   $t0, 1         # i = 1
    sw   $t0, -16($fp)

for_loop_start:
    lw   $t0, -16($fp)
    ble  $t0, 5, for_body
    j    for_loop_end

for_body:
    # imprimir "Iteracion: "
    li   $v0, 4
    la   $a0, msg_loop
    syscall

    # imprimir valor de i
    lw   $a0, -16($fp)
    li   $v0, 1
    syscall

    # salto de línea
    li   $a0, 10
    li   $v0, 11
    syscall

    # if (i == 3) break;
    lw   $t0, -16($fp)
    li   $t1, 3
    beq  $t0, $t1, for_loop_end   # break

    # i = i + 1
    lw   $t0, -16($fp)
    addi $t0, $t0, 1
    sw   $t0, -16($fp)

    j    for_loop_start

for_loop_end:

    # Mensaje final
    li   $v0, 4
    la   $a0, msg_end
    syscall

    # Epílogo
    move $sp, $fp
    lw   $ra, 12($sp)
    lw   $fp, 8($sp)
    addi $sp, $sp, 16

    li $v0, 10   # exit
    syscall