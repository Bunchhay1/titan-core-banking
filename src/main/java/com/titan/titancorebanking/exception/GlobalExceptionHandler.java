package com.titan.titancorebanking.exception;

import com.titan.titancorebanking.dto.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;

@RestControllerAdvice // ✅ នេះគឺជាកន្លែងដែលប្រាប់ Spring ថា Class នេះជាអ្នកចាប់ Error
public class GlobalExceptionHandler {

    // 🎯 1. ចាប់យក RuntimeException (Error ទូទៅដែលយើង Throw ពី Service)
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex, WebRequest request) {

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value()) // យើងកំណត់ថាឱ្យចេញ 400
                .error("Business Logic Error")
                .message(ex.getMessage()) // យកសារដែលយើងសរសេរក្នុង Service (ឧ. "Insufficient balance")
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    // 🎯 2. ចាប់យក Exception ផ្សេងៗទៀត (Unexpected Error)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex, WebRequest request) {

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value()) // 500
                .error("Internal Server Error")
                .message("An unexpected error occurred: " + ex.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}