package com.backtoback.reseat.domain.game.exception;

public class InvalidBookingWindowException extends RuntimeException {
  public InvalidBookingWindowException(String message) {
    super(message);
  }
}
