package com.darshan.ecommerce.payment;

// What a payment provider answered to a charge or a refund.
public sealed interface GatewayResult {

    // reference is the provider's id for the transaction; null when no provider was involved (COD).
    record Approved(String reference) implements GatewayResult {
    }

    record Declined(String reason) implements GatewayResult {
    }

    // No answer within the provider's time limit. The outcome is unknown: the provider may still
    // have processed the request.
    record TimedOut() implements GatewayResult {
    }
}
