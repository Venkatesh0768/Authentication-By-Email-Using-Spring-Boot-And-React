package org.auth.fullauthenticationotp.exception;

public class EmailSendingException extends RuntimeException {
    public EmailSendingException(String message, Exception e) {
        super(message);
    }
}
