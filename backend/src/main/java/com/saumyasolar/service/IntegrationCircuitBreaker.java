package com.saumyasolar.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** Process-local breaker for provider calls; durable retries remain in integration_sync_job. */
@Component
public class IntegrationCircuitBreaker {
    private record State(int failures, Instant openUntil) {}
    private final ConcurrentHashMap<String,State> states=new ConcurrentHashMap<>();
    private final int threshold;
    private final long openSeconds;
    public IntegrationCircuitBreaker(
            @Value("${app.integrations.circuit-breaker.failure-threshold:5}") int threshold,
            @Value("${app.integrations.circuit-breaker.open-seconds:60}") long openSeconds) {
        if(threshold<1||openSeconds<1)throw new IllegalArgumentException("Circuit breaker settings must be positive");
        this.threshold=threshold;this.openSeconds=openSeconds;
    }
    public boolean allow(String key) {
        State state=states.get(key);
        if(state==null||state.openUntil()==null)return true;
        if(Instant.now().isBefore(state.openUntil()))return false;
        states.replace(key,state,new State(state.failures(),null));
        return true;
    }
    public Instant recordFailure(String key) {
        final State[] next=new State[1];
        states.compute(key,(k,old)->{
            int failures=old==null?1:old.failures()+1;
            Instant open=failures>=threshold?Instant.now().plusSeconds(openSeconds):null;
            next[0]=new State(failures,open);return next[0];
        });
        return next[0].openUntil();
    }
    public Instant openUntil(String key){State state=states.get(key);return state==null?null:state.openUntil();}
    public void recordSuccess(String key){states.remove(key);}
}
