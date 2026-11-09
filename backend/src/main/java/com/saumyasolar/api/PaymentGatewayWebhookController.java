package com.saumyasolar.api;

import com.saumyasolar.service.PaymentGatewayWebhookService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
@RestController
@RequestMapping("/api/v1/payment-gateway-webhooks")
public class PaymentGatewayWebhookController {
    private final PaymentGatewayWebhookService webhooks;
    
    public PaymentGatewayWebhookController(PaymentGatewayWebhookService webhooks) {
        this.webhooks = webhooks;
    }
    
    @PostMapping("/{provider}")
    public Map<String, Object> webhook(
            @PathVariable String provider,
            @RequestHeader("X-Payment-Signature") String signature,
            @RequestBody String raw
    ) {
        return webhooks.accept(provider, raw, signature);
    }
}
