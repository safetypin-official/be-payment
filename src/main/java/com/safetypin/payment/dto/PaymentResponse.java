package com.safetypin.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
@AllArgsConstructor
public class PaymentResponse {
    private boolean success;
    private String message;
    private Object data;
}