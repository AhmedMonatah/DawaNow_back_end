package com.example.dawanow.dtos.response;

import com.example.dawanow.entity.OrderStatus;
import com.example.dawanow.entity.PaymentMethod;
import com.example.dawanow.entity.PaymentStatus;

public record FulfillmentConfirmationResponse(Long masterOrderId,
                                              OrderStatus orderStatus,
                                              PaymentMethod paymentMethod,
                                              PaymentStatus paymentStatus) {
}
