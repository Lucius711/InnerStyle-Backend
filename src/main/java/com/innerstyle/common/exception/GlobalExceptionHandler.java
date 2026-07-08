package com.innerstyle.common.exception;

import com.innerstyle.common.response.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
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
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Single global exception handler that maps exceptions to the standard {@link ErrorResponse}.
 *
 * <p>This service is i18n-agnostic: it returns stable, machine-readable <b>message codes</b>
 * (e.g. {@code meshy.task.notFound}, {@code validation.image.required}) rather than localized
 * text. Translation is performed by the client (frontend i18n), so there is no server-side
 * {@code MessageSource} or {@code messages*.properties}.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Pattern PRIORITY_PREFIX = Pattern.compile("^\\d+\\.");
    private static final String VALIDATION_NS = "validation.";

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleApp(AppException ex) {
        String code = codeOf(ex.getMessage());
        log.warn("{} -> {}: {}", ex.getClass().getSimpleName(), ex.getStatus(), code);
        return ResponseEntity.status(ex.getStatus())
            .body(ErrorResponse.simple(code, ex.getStatus().getReasonPhrase()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(BindException ex) {
        Map<String, List<String>> rawByField = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            rawByField.computeIfAbsent(fe.getField(), k -> new ArrayList<>())
                .add(fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
            .body(ErrorResponse.fields(codesByPriority(rawByField),
                HttpStatus.BAD_REQUEST.getReasonPhrase()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleParamValidation(ConstraintViolationException ex) {
        Map<String, List<String>> rawByField = new LinkedHashMap<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            rawByField.computeIfAbsent(lastNode(v.getPropertyPath()), k -> new ArrayList<>())
                .add(v.getMessage());
        }
        return ResponseEntity.badRequest()
            .body(ErrorResponse.fields(codesByPriority(rawByField),
                HttpStatus.BAD_REQUEST.getReasonPhrase()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(ErrorResponse.simple("validation.image.tooLarge",
                HttpStatus.PAYLOAD_TOO_LARGE.getReasonPhrase()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
            .body(ErrorResponse.simple("validation.parameter.invalid",
                HttpStatus.BAD_REQUEST.getReasonPhrase()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
            .body(ErrorResponse.simple("validation.body.malformed",
                HttpStatus.BAD_REQUEST.getReasonPhrase()));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException ex) {
        return ResponseEntity.badRequest()
            .body(ErrorResponse.simple("validation.part.missing",
                HttpStatus.BAD_REQUEST.getReasonPhrase()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
            .body(ErrorResponse.simple("validation.parameter.missing",
                HttpStatus.BAD_REQUEST.getReasonPhrase()));
    }

    /**
     * Client disconnected mid-response (e.g. a browser aborts a large model/asset download).
     * The socket is already gone, so there is no usable stream to write an {@link ErrorResponse}
     * to — and the response Content-Type may already be committed to a non-JSON type (e.g.
     * {@code model/gltf-binary}), which would otherwise make the fallback handler fail with
     * {@code HttpMessageNotWritableException}. Swallow it quietly at DEBUG; returning {@code void}
     * tells Spring not to render a body.
     */
    @ExceptionHandler({ClientAbortException.class, AsyncRequestNotUsableException.class})
    public void handleClientDisconnect(Exception ex) {
        log.debug("Client aborted the response: {}", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.simple("common.serverError",
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase()));
    }

    /** Normalize an exception message into a stable code (never null). */
    private String codeOf(String key) {
        return key == null ? "common.serverError" : key;
    }

    /**
     * Sort each field's raw messages by their numeric "{n}." priority (ascending), then turn each
     * into a stable validation code. Bean Validation does not guarantee constraint order, so we
     * sort explicitly to make the priority scheme deterministic.
     */
    private Map<String, List<String>> codesByPriority(Map<String, List<String>> rawByField) {
        Map<String, List<String>> codes = new LinkedHashMap<>();
        rawByField.forEach((field, rawMessages) -> {
            List<String> sorted = rawMessages.stream()
                .sorted(Comparator.comparingInt(this::priorityOf))
                .map(this::validationCode)
                .distinct()
                .collect(Collectors.toList());
            codes.put(field, sorted);
        });
        return codes;
    }

    /**
     * Strip a leading "{n}." priority prefix and namespace the result under {@code validation.}
     * (unless it is already namespaced), e.g. "1.image.required" -&gt; "validation.image.required".
     */
    private String validationCode(String raw) {
        if (raw == null) {
            return "validation.invalid";
        }
        String key = PRIORITY_PREFIX.matcher(raw).replaceFirst("");
        if (key.startsWith(VALIDATION_NS) || key.startsWith("meshy.") || key.startsWith("common.")) {
            return key;
        }
        return VALIDATION_NS + key;
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
