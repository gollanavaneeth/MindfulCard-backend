package com.mindfulcart.assistant.controller;

import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> validation(MethodArgumentNotValidException e) {
    String detail =
        e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(x -> x.getField() + " " + x.getDefaultMessage())
            .orElse("Invalid request");
    return response(HttpStatus.BAD_REQUEST, detail);
  }

  @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
  ResponseEntity<?> bad(RuntimeException e) {
    return response(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  @ExceptionHandler(BadCredentialsException.class)
  ResponseEntity<?> credentials() {
    return response(HttpStatus.UNAUTHORIZED, "Email or password is incorrect");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception e) {
    return response(
        HttpStatus.INTERNAL_SERVER_ERROR, "The request could not be completed. Please try again.");
  }

  private ResponseEntity<?> response(HttpStatus status, String message) {
    return ResponseEntity.status(status)
        .body(
            Map.of(
                "status",
                status.value(),
                "message",
                message == null ? status.getReasonPhrase() : message,
                "timestamp",
                LocalDateTime.now()));
  }
}
