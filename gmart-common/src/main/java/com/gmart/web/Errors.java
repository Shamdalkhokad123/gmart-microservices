package com.gmart.web;

import com.gmart.client.InternalClient.RemoteServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Turns any exception into a message that is safe to send to the browser. */
public final class Errors {

    private static final Logger log = LoggerFactory.getLogger(Errors.class);
    public static final String GENERIC = "Something went wrong. Please try again.";

    private Errors() {
    }

    /**
     * The exception's own message when it was written for customers (BusinessException,
     * or a message relayed from another service); otherwise logs it and returns a generic text.
     */
    public static String userMessage(Throwable e) {
        if (e instanceof BusinessException || e instanceof RemoteServiceException) {
            return e.getMessage();
        }
        log.error("Unexpected error", e);
        return GENERIC;
    }
}
