package com.innerstyle.common.exception;

import com.innerstyle.common.response.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Single global exception handler that maps exceptions to the standard
 * {@link ErrorResponse} and resolves i18n message keys (see rules/09-error-handling.md).
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final Pattern PRIORITY_PREFIX = Pattern.compile("^\\d+\\.");

    private final MessageSource messageSource;

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleApp(AppException ex, Locale locale) {
        String message = resolve(ex.getMessage(), ex.getArgs(), locale);
        log.warn("{} -> {}: {}", ex.getClass().getSimpleName(), ex.getStatus(), message);
        return ResponseEntity.status(ex.getStatus())
            .body(ErrorResponse.simple(message, ex.getStatus().getReasonPhrase()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(BindException ex,
                                                              Locale locale) {
        Map<String, List<String>> rawByField = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            rawByField.computeIfAbsent(fe.getField(), k -> new ArrayList<>())
                .add(fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
            .body(ErrorResponse.fields(resolveByPriority(rawByField, locale),
                HttpStatus.BAD_REQUEST.getReasonPhrase()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleParamValidation(ConstraintViolationException ex,
                                                               Locale locale) {
        Map<String, List<String>> rawByField = new LinkedHashMap<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            rawByField.computeIfAbsent(lastNode(v.getPropertyPath()), k -> new ArrayList<>())
                .add(v.getMessage());
        }
        return ResponseEntity.badRequest()
            .body(ErrorResponse.fields(resolveByPriority(rawByField, locale),
                HttpStatus.BAD_REQUEST.getReasonPhrase()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUpload(MaxUploadSizeExceededException ex, Locale locale) {
        String message = resolve("validation.image.tooLarge", null, locale);
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(ErrorResponse.simple(message, HttpStatus.PAYLOAD_TOO_LARGE.getReasonPhrase()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value for parameter '" + ex.getName() + "'";
        return ResponseEntity.badRequest()
            .body(ErrorResponse.simple(message, HttpStatus.BAD_REQUEST.getReasonPhrase()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
            .body(ErrorResponse.simple("Malformed or missing request body",
                HttpStatus.BAD_REQUEST.getReasonPhrase()));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException ex) {
        return ResponseEntity.badRequest()
            .body(ErrorResponse.simple("Required part '" + ex.getRequestPartName() + "' is missing",
                HttpStatus.BAD_REQUEST.getReasonPhrase()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
            .body(ErrorResponse.simple("Required parameter '" + ex.getParameterName() + "' is missing",
                HttpStatus.BAD_REQUEST.getReasonPhrase()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, Locale locale) {
        log.error("Unexpected error", ex);
        String message = resolve("common.serverError", null, locale);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.simple(message, HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase()));
    }

    /** Resolve a plain message key (no priority prefix). */
    private String resolve(String key, Object[] args, Locale locale) {
        if (key == null) {
            return "Error";
        }
        return messageSource.getMessage(key, args, key, locale);
    }

    /**
     * Sort each field's raw messages by their numeric "{n}." priority (ascending), then strip the
     * prefix and resolve via MessageSource. Bean Validation does not guarantee constraint order,
     * so we sort explicitly to make the priority scheme deterministic.
     */
    private Map<String, List<String>> resolveByPriority(Map<String, List<String>> rawByField,
                                                        Locale locale) {
        Map<String, List<String>> resolved = new LinkedHashMap<>();
        rawByField.forEach((field, rawMessages) -> {
            List<String> sorted = rawMessages.stream()
                .sorted(Comparator.comparingInt(this::priorityOf))
                .map(raw -> resolveValidationMessage(raw, locale))
                .collect(Collectors.toList());
            resolved.put(field, sorted);
        });
        return resolved;
    }

    /** Strip a leading "{n}." priority prefix, then resolve via MessageSource. */
    private String resolveValidationMessage(String raw, Locale locale) {
        if (raw == null) {
            return "Invalid value";
        }
        String key = PRIORITY_PREFIX.matcher(raw).replaceFirst("");
        return messageSource.getMessage(key, null, raw, locale);
    }

    /** Extract the numeric priority from a "{n}.field.type" message; defaults to lowest priority. */
    private int priorityOf(String raw) {
        if (raw == null) {
            return Integer.MAX_VALUE;
        }
        int dot = raw.indexOf('.');
        if (dot > 0) {
            try {
                return Integer.parseInt(raw.substring(0, dot));
            } catch (NumberFormatException ignored) {
                // not a priority-prefixed message
            }
        }
        return Integer.MAX_VALUE;
    }

    private String lastNode(Path path) {
        String node = "";
        for (Path.Node n : path) {
            node = n.getName();
        }
        return node;
    }
}
