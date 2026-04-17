package com.bohannjein.zack.utils

/**
 * Validation utilities for user input
 */

/**
 * Validates if the string is a valid IPv4 address
 * @return true if valid, false otherwise
 */
fun String.isValidIp(): Boolean {
    if (this.isBlank()) return false
    
    val parts = split(".")
    if (parts.size != 4) return false
    
    return parts.all { part ->
        part.toIntOrNull()?.let { num -> num in 0..255 } ?: false
    }
}

/**
 * Checks if string contains only valid IP characters (digits and dots)
 */
fun String.isIpChar(): Boolean = this.all { it.isDigit() || it == '.' }

/**
 * Validates if the string is a valid port number
 * @return true if valid port (1-65535), false otherwise
 */
fun String.isValidPort(): Boolean {
    if (this.isBlank()) return false
    
    val port = this.toIntOrNull() ?: return false
    return port in 1..65535
}

/**
 * Checks if string contains only digits (for port input filtering)
 */
fun String.isDigitsOnly(): Boolean = this.all { it.isDigit() }
