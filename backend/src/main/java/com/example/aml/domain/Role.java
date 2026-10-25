package com.example.aml.domain;

/**
 * What a signed-in user may do. Each role includes everything the roles above it can do.
 * <ul>
 *   <li>ANALYST: work alerts (assign, notes, escalate, close as false positive), submit transactions,
 *       onboard customers, screen names.</li>
 *   <li>MLRO: the money laundering reporting officer. Also files SARs and freezes accounts.</li>
 *   <li>ADMIN: also manages users, detection rules and the sanctions list.</li>
 * </ul>
 */
public enum Role {
    ANALYST,
    MLRO,
    ADMIN
}
