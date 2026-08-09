package com.example.dawanow.event;

public record SubOrderStatusChangedEvent(   Long orderId,
                                            Long masterOrderId) {
}
