package cn.enterprise.kb.common;

import org.springframework.http.HttpStatus;

public class ApiProblem extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiProblem(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public static ApiProblem bad(String message) { return new ApiProblem(HttpStatus.BAD_REQUEST, "INVALID_INPUT", message); }
    public static ApiProblem missing(String message) { return new ApiProblem(HttpStatus.NOT_FOUND, "NOT_FOUND", message); }
    public static ApiProblem conflict(String message) { return new ApiProblem(HttpStatus.CONFLICT, "CONFLICT", message); }
    public static ApiProblem unavailable(String message) {
        return new ApiProblem(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", message);
    }
}
