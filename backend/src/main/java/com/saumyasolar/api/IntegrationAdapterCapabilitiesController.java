package com.saumyasolar.api;

import com.saumyasolar.security.Principal;
import com.saumyasolar.service.AccountingAdapterRegistry;
import com.saumyasolar.service.PaymentGatewayRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/integration-adapters")
public class IntegrationAdapterCapabilitiesController {
    private final PaymentGatewayRegistry payments;
    private final AccountingAdapterRegistry accounting;
    public IntegrationAdapterCapabilitiesController(PaymentGatewayRegistry payments,AccountingAdapterRegistry accounting){
        this.payments=payments;this.accounting=accounting;
    }
    @GetMapping("/capabilities")
    public Map<String,Object> capabilities(Authentication authentication){
        Principal actor=(Principal)authentication.getPrincipal();
        if(!"VENDOR".equals(actor.accountType())||actor.tenantId()==null||actor.permissions()==null
                ||!actor.permissions().contains("INTEGRATION_MANAGE"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return Map.of("paymentGatewayProviders",payments.providers(),
                "accountingProviders",accounting.providers(),
                "providerNeutralOnly",true);
    }
}
