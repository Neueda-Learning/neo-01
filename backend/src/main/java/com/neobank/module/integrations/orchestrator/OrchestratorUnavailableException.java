package com.neobank.module.integrations.orchestrator;

/**
 * Raised when the orchestrator cannot be reached for live data fetches.
 */
public class OrchestratorUnavailableException extends RuntimeException {

    public OrchestratorUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}