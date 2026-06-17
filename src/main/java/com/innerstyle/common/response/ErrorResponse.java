package com.innerstyle.common.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Standard error envelope produced by the global exception handler
 * (see rules/09-error-handling.md).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    /** Always {@code false}. */
    private boolean success;
    /** Field name -&gt; first (highest priority) message. */
    private Map<String, String> error;
    /** Field name -&gt; all messages. */
    private Map<String, List<String>> errors;
    /** HTTP status reason phrase. */
    private String message;

    public static ErrorResponse simple(String message, String reason) {
        return new ErrorResponse(false, Map.of("_", message), Map.of("_", List.of(message)), reason);
    }

    public static ErrorResponse fields(Map<String, List<String>> fieldErrors, String reason) {
        Map<String, String> first = new LinkedHashMap<>();
        fieldErrors.forEach((key, values) -> first.put(key, values.isEmpty() ? "" : values.get(0)));
        return new ErrorResponse(false, first, fieldErrors, reason);
    }
}
