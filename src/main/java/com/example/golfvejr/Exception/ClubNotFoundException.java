package com.example.golfvejr.Exception;

public class ClubNotFoundException extends RuntimeException {
    public ClubNotFoundException(Long id) {
        super("Golfklub med id " + id + " blev ikke fundet");
    }
}
