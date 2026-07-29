package com.neobank.module.service;

/** A referral cannot be changed because another operator owns or completed it. */
public class ReferralConflictException extends RuntimeException {

    public ReferralConflictException(String message) {
        super(message);
    }
}
