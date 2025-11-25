# CODIGO MIPS generado por MIPSCodeGenerator

.data
x: .word 0
y: .word 0
a: .word 0
_v_b: .word 0
resultado: .word 0
t1: .word 0
_str1: .asciiz "hola"

.text
.globl main

main:
# DECLARE x
# ASSIGN x = 'hola'
    la $t0, _str1
    sw $t0, x
# DECLARE y
# ASSIGN y = 10
    li $t0, 10
    sw $t0, y
# PRINT x
    lw $a0, x
    li $v0, 1
    syscall
# PRINT y
    lw $a0, y
    li $v0, 1
    syscall
# DECLARE a
# ASSIGN a = 5
    li $t0, 5
    sw $t0, a
# DECLARE b
# ASSIGN b = 10
    li $t0, 10
    sw $t0, _v_b
# ARITH t1 = a + b
    lw $t0, a
    lw $t1, _v_b
    add $t2, $t0, $t1
    sw $t2, t1
# DECLARE resultado
# ASSIGN resultado = t1
    lw $t0, t1
    sw $t0, resultado
# RETURN resultado
    lw $v0, resultado
    jr $ra

# terminar programa
li $v0, 10
syscall
