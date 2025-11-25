# CODIGO MIPS generado por MIPSCodeGenerator

.data
numero: .word 0
letra: .word 0
_str1: .asciiz "A"
_str2: .asciiz "El número es:"

.text
.globl main

main:
# DECLARE numero
# ASSIGN numero = 5
    li $t0, 5
    sw $t0, numero
# DECLARE letra
# ASSIGN letra = 'A'
    la $t0, _str1
    sw $t0, letra
# DECLARE letra
# ASSIGN letra = 'A'
    la $t0, _str1
    sw $t0, letra
# PRINT 'El número es:'
    la $a0, _str2
    li $v0, 4
    syscall
# PRINT numero
    lw $a0, numero
    li $v0, 1
    syscall

# terminar programa
li $v0, 10
syscall
