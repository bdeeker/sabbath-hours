package io.github.bdeeker.sabbathhours.web;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import io.github.bdeeker.sabbathhours.location.LocationNotFoundException;

/**
 * Turns every error into an RFC 9457 problem detail. Validation failures list each bad parameter
 * under {@code errors}; unexpected failures return a generic 500 and are logged, never echoed.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** One invalid request parameter. */
    public record FieldProblem(String field, String message) {
    }

    /** Validators run in no fixed order; sorting keeps the response stable for clients and tests. */
    private static final Comparator<FieldProblem> BY_FIELD_THEN_MESSAGE =
            Comparator.comparing(FieldProblem::field).thenComparing(FieldProblem::message);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldProblem> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(ApiExceptionHandler::describe)
                .sorted(BY_FIELD_THEN_MESSAGE)
                .toList();
        return ResponseEntity.badRequest().body(invalid(errors));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldProblem> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldProblem(result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .sorted(BY_FIELD_THEN_MESSAGE)
                .toList();
        return ResponseEntity.badRequest().body(invalid(errors));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String field = ex.getPropertyName() != null ? ex.getPropertyName() : "parameter";
        return ResponseEntity.badRequest().body(invalid(List.of(new FieldProblem(field, formatHint(ex.getRequiredType())))));
    }

    @ExceptionHandler(LocationNotFoundException.class)
    public ProblemDetail handleNotFound(LocationNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Not found");
        return problem;
    }

    /** Two writes to the same location raced; the loser must re-read and retry. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleConflict(OptimisticLockingFailureException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The location was changed by another request. Fetch it again and retry.");
        problem.setTitle("Conflict");
        return problem;
    }

    /** Domain-level rejections (e.g. coordinates that pass bean validation but not {@code GeoPoint}, such as NaN). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid request");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled error", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred.");
        problem.setTitle("Internal server error");
        return problem;
    }

    private static ProblemDetail invalid(List<FieldProblem> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "One or more request parameters are invalid.");
        problem.setTitle("Invalid request");
        problem.setProperty("errors", errors);
        return problem;
    }

    private static FieldProblem describe(FieldError error) {
        if (error.isBindingFailure()) {
            return new FieldProblem(error.getField(), "has an invalid format");
        }
        if ("NotNull".equals(error.getCode()) || "NotBlank".equals(error.getCode())) {
            return new FieldProblem(error.getField(), "is required");
        }
        return new FieldProblem(error.getField(), error.getDefaultMessage());
    }

    private static final Map<String, String> FORMAT_HINTS = Map.of(
            "LocalDate", "must be a date in the form YYYY-MM-DD",
            "Instant", "must be an ISO-8601 instant such as 2026-10-02T22:00:00Z",
            "UUID", "must be a UUID",
            "int", "must be a whole number",
            "Integer", "must be a whole number");

    private static String formatHint(Class<?> type) {
        return type == null ? "has an invalid format" : FORMAT_HINTS.getOrDefault(type.getSimpleName(), "has an invalid format");
    }
}
