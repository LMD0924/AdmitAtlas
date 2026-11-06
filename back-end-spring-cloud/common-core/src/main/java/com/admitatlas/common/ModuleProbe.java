package com.admitatlas.common;

/**
 * Module probe. Every business module contributes one Spring bean implementing this interface,
 * so the single launcher can prove that component scanning covers all flat modules.
 */
public interface ModuleProbe {

    String moduleName();

    default String status() {
        return "P0-skeleton";
    }
}
