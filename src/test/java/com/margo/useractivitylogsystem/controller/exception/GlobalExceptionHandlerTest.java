package com.margo.useractivitylogsystem.controller.exception;

import com.datastax.oss.driver.api.core.AllNodesFailedException;
import com.datastax.oss.driver.api.core.NoNodeAvailableException;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.datastax.oss.driver.api.core.DriverTimeoutException;
import com.datastax.oss.driver.api.core.servererrors.UnavailableException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.cassandra.CassandraConnectionFailureException;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ExceptionHandlerMethodResolver resolver =
            new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class);

    @ParameterizedTest
    @MethodSource("handledExceptions")
    void handler_mapsExceptionToProblemDetail(
            Exception exception, HttpStatus status, String title, String detail, List<String> errors) throws Exception {
        // given
        Method handlerMethod = resolver.resolveMethodByThrowable(exception);

        // when
        assertThat(handlerMethod).isNotNull();
        ProblemDetail problem = (ProblemDetail) handlerMethod.invoke(handler, exception);

        // then
        assertThat(problem.getStatus()).isEqualTo(status.value());
        assertThat(problem.getTitle()).isEqualTo(title);
        assertThat(problem.getDetail()).isEqualTo(detail);
        assertThat(problem.getProperties()).isEqualTo(errors == null ? null : Map.of("errors", errors));
    }

    private static Stream<Arguments> handledExceptions() throws Exception {
        String unavailable = "Activity log service is temporarily unavailable. Please try again later.";

        return Stream.of(
                Arguments.of(new RuntimeException("error"), HttpStatus.INTERNAL_SERVER_ERROR,
                        "Internal Server Error", "An unexpected error occurred: error", null),

                Arguments.of(new HttpMessageNotReadableException("bad json", mock(HttpInputMessage.class)),
                        HttpStatus.BAD_REQUEST, "Malformed JSON Request",
                        "Invalid JSON format or malformed request body.", null),

                Arguments.of(constraintViolation(),
                        HttpStatus.BAD_REQUEST, "Invalid Request Parameters",
                        "Validation failed for query or path parameters.",
                        List.of("limit: must be less than or equal to 100")),

                Arguments.of(invalidPayload(
                                new FieldError("activityRequest", "activityType", "Activity type is required")),
                        HttpStatus.BAD_REQUEST, "Validation Failed", "Input validation failed for request payload.",
                        List.of("activityType: Activity type is required")),

                Arguments.of(invalidPayload(
                                new FieldError("activityRequest", "activityType", "Activity type is required"),
                                new FieldError("activityRequest", "ttlInSeconds", "TTL must be at least 60 seconds (1 minute)")),
                        HttpStatus.BAD_REQUEST, "Validation Failed", "Input validation failed for request payload.",
                        List.of("activityType: Activity type is required",
                                "ttlInSeconds: TTL must be at least 60 seconds (1 minute)")),

                Arguments.of(new IllegalArgumentException("Parameter 'from' cannot be after 'to'."),
                        HttpStatus.BAD_REQUEST, "Invalid Argument", "Parameter 'from' cannot be after 'to'.", null),

                Arguments.of(new NoResourceFoundException(HttpMethod.GET, "uri", "/path"),
                        HttpStatus.NOT_FOUND, "Resource Not Found",
                        "The requested path '/path' was not found.", null),

                Arguments.of(new UnavailableException(mock(Node.class), DefaultConsistencyLevel.QUORUM, 2, 1),
                        HttpStatus.SERVICE_UNAVAILABLE, "Connection Failure", unavailable, null),

                Arguments.of(new NoNodeAvailableException(),
                        HttpStatus.SERVICE_UNAVAILABLE, "Connection Failure", unavailable, null),

                Arguments.of(AllNodesFailedException.fromErrors(List.of()),
                        HttpStatus.SERVICE_UNAVAILABLE, "Connection Failure", unavailable, null),

                Arguments.of(new CassandraConnectionFailureException(Map.of(), "connection failed", new RuntimeException("connection failed")),
                        HttpStatus.SERVICE_UNAVAILABLE, "Connection Failure", unavailable, null),

                Arguments.of(new DataAccessResourceFailureException("resource failure"),
                        HttpStatus.SERVICE_UNAVAILABLE, "Connection Failure", unavailable, null),

                Arguments.of(new DriverTimeoutException("timed out"),
                        HttpStatus.GATEWAY_TIMEOUT, "Timeout", "Operation timed out.", null),

                Arguments.of(new MethodArgumentTypeMismatchException(
                                "abc", Integer.class, "limit", methodParameter(), new NumberFormatException("abc")),
                        HttpStatus.BAD_REQUEST, "Invalid Value", "Invalid value for parameter 'limit'", null));
    }

    private static ConstraintViolationException constraintViolation() {
        Path propertyPath = mock(Path.class);
        when(propertyPath.toString()).thenReturn("limit");

        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        when(violation.getPropertyPath()).thenReturn(propertyPath);
        when(violation.getMessage()).thenReturn("must be less than or equal to 100");

        return new ConstraintViolationException(Set.of(violation));
    }

    private static MethodArgumentNotValidException invalidPayload(FieldError... errors) throws Exception {
        BindingResult result = new BeanPropertyBindingResult(new Object(), "activityRequest");
        Arrays.stream(errors).forEach(result::addError);
        return new MethodArgumentNotValidException(methodParameter(), result);
    }

    private static MethodParameter methodParameter() throws NoSuchMethodException {
        return new MethodParameter(GlobalExceptionHandlerTest.class.getDeclaredMethod("target", String.class), 0);
    }

    @SuppressWarnings("unused")
    private static void target(String argument) {}
}