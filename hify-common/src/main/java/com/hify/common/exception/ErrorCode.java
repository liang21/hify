package com.hify.common.exception;

/**
 * Unified error code enumeration
 */
public enum ErrorCode {

    // Common errors (1xxx)
    SUCCESS(200, "success"),
    PARAM_ERROR(400, "Parameter error"),
    UNAUTHORIZED(401, "Unauthorized"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Resource not found"),
    INTERNAL_ERROR(500, "Internal server error"),

    // Model errors (10xx)
    MODEL_NOT_FOUND(1001, "Model not found"),
    MODEL_CONFIG_INVALID(1002, "Model configuration invalid"),
    LLM_CALL_TIMEOUT(1003, "LLM call timeout"),
    LLM_CALL_FAILED(1004, "LLM call failed"),
    LLM_PROVIDER_UNAVAILABLE(1005, "LLM provider unavailable"),

    // Agent errors (11xx)
    AGENT_NOT_FOUND(1101, "Agent not found"),
    AGENT_PROMPT_INVALID(1102, "Agent prompt invalid"),

    // Conversation errors (12xx)
    CONVERSATION_NOT_FOUND(1201, "Conversation not found"),
    MESSAGE_NOT_FOUND(1202, "Message not found"),

    // Knowledge errors (13xx)
    KNOWLEDGE_NOT_FOUND(1301, "Knowledge base not found"),
    DOCUMENT_UPLOAD_FAILED(1302, "Document upload failed"),
    VECTOR_SEARCH_FAILED(1303, "Vector search failed"),

    // Workflow errors (14xx)
    WORKFLOW_NOT_FOUND(1401, "Workflow not found"),
    WORKFLOW_EXECUTION_FAILED(1402, "Workflow execution failed"),

    // MCP errors (15xx)
    MCP_SERVER_NOT_FOUND(1501, "MCP server not found"),
    MCP_TOOL_CALL_FAILED(1502, "MCP tool call failed"),
    ;

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
