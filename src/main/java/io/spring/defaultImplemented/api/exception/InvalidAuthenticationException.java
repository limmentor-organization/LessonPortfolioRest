package io.spring.defaultImplemented.api.exception;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.Errors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

public class InvalidAuthenticationException extends RuntimeException {

  public InvalidAuthenticationException() {
    super("invalid email or password");
  }

  @RestControllerAdvice
  public static class CustomizeExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler({InvalidRequestException.class})
    public ResponseEntity<Object> handleInvalidRequest(RuntimeException e, WebRequest request) {
      InvalidRequestException ire = (InvalidRequestException) e;

      List<FieldErrorResource> errorResources =
          ire.getErrors().getFieldErrors().stream()
              .map(
                  fieldError ->
                      new FieldErrorResource(
                          fieldError.getObjectName(),
                          fieldError.getField(),
                          fieldError.getCode(),
                          fieldError.getDefaultMessage()))
              .collect(Collectors.toList());

      ErrorResource error = new ErrorResource(errorResources);

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);

      return handleExceptionInternal(e, error, headers, UNPROCESSABLE_ENTITY, request);
    }

    @ExceptionHandler(InvalidAuthenticationException.class)
    public ResponseEntity<Object> handleInvalidAuthentication(
        InvalidAuthenticationException e, WebRequest request) {
      return ResponseEntity.status(UNPROCESSABLE_ENTITY)
          .body(
              new HashMap<String, Object>() {
                {
                  put("message", e.getMessage());
                }
              });
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException e,
        HttpHeaders headers,
        HttpStatus status,
        WebRequest request) {
      List<FieldErrorResource> errorResources =
          e.getBindingResult().getFieldErrors().stream()
              .map(
                  fieldError ->
                      new FieldErrorResource(
                          fieldError.getObjectName(),
                          fieldError.getField(),
                          fieldError.getCode(),
                          fieldError.getDefaultMessage()))
              .collect(Collectors.toList());

      return ResponseEntity.status(UNPROCESSABLE_ENTITY).body(new ErrorResource(errorResources));
    }

    @ExceptionHandler({ConstraintViolationException.class})
    @ResponseStatus(UNPROCESSABLE_ENTITY)
    @ResponseBody
    public ErrorResource handleConstraintViolation(
        ConstraintViolationException ex, WebRequest request) {
      List<FieldErrorResource> errors = new ArrayList<>();
      for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
        FieldErrorResource fieldErrorResource =
            new FieldErrorResource(
                violation.getRootBeanClass().getName(),
                getParam(violation.getPropertyPath().toString()),
                violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
                violation.getMessage());
        errors.add(fieldErrorResource);
      }

      return new ErrorResource(errors);
    }

    private String getParam(String s) {
      String[] splits = s.split("\\.");
      if (splits.length == 1) {
        return s;
      } else {
        return String.join(".", Arrays.copyOfRange(splits, 2, splits.length));
      }
    }
  }

  @JsonSerialize(using = ErrorResourceSerializer.class)
  @JsonIgnoreProperties(ignoreUnknown = true)
  @lombok.Getter
  @JsonRootName("errors")
  public static class ErrorResource {
    private List<FieldErrorResource> fieldErrors;

    public ErrorResource(List<FieldErrorResource> fieldErrorResources) {
      this.fieldErrors = fieldErrorResources;
    }
  }

  public static class ErrorResourceSerializer extends JsonSerializer<ErrorResource> {
    @Override
    public void serialize(ErrorResource value, JsonGenerator gen, SerializerProvider serializers)
        throws IOException, JsonProcessingException {
      Map<String, List<String>> json = new HashMap<>();
      gen.writeStartObject();
      gen.writeObjectFieldStart("errors");
      for (FieldErrorResource fieldErrorResource : value.getFieldErrors()) {
        if (!json.containsKey(fieldErrorResource.getField())) {
          json.put(fieldErrorResource.getField(), new ArrayList<String>());
        }
        json.get(fieldErrorResource.getField()).add(fieldErrorResource.getMessage());
      }
      for (Map.Entry<String, List<String>> pair : json.entrySet()) {
        gen.writeArrayFieldStart(pair.getKey());
        pair.getValue()
            .forEach(
                content -> {
                  try {
                    gen.writeString(content);
                  } catch (IOException e) {
                    e.printStackTrace();
                  }
                });
        gen.writeEndArray();
      }
      gen.writeEndObject();
      gen.writeEndObject();
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  @Getter
  @AllArgsConstructor
  public static class FieldErrorResource {
    private String resource;
    private String field;
    private String code;
    private String message;
  }

  @SuppressWarnings("serial")
  public static class InvalidRequestException extends RuntimeException {
    private final Errors errors;

    public InvalidRequestException(Errors errors) {
      super("");
      this.errors = errors;
    }

    public Errors getErrors() {
      return errors;
    }
  }
}
