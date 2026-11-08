package com.saumyasolar.api;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.PaymentGatewayCheckoutService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/payment-gateways")
public class PaymentGatewayController {
 private final PaymentGatewayCheckoutService checkout;
 public PaymentGatewayController(PaymentGatewayCheckoutService checkout){this.checkout=checkout;}
 @GetMapping("/availability")
 public java.util.Map<String,Object> availability(Authentication authentication){
  return checkout.availability((Principal)authentication.getPrincipal());
 }
 @PostMapping("/invoices/{invoiceId}/checkout")
 public PaymentGatewayCheckoutService.CheckoutResult checkout(@PathVariable UUID invoiceId,
   @RequestParam(required=false) String providerCode,@RequestHeader("Idempotency-Key") String idempotencyKey,Authentication authentication){
  return checkout.create(invoiceId,providerCode,idempotencyKey,(Principal)authentication.getPrincipal());
 }
}
