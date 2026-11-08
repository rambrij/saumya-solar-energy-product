package com.saumyasolar.api;

import com.saumyasolar.api.SurveyModels.DecisionRequest;
import com.saumyasolar.api.SurveyModels.UpdateRequest;
import com.saumyasolar.domain.SiteSurvey;
import com.saumyasolar.domain.SiteSurveyHistory;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.SurveyLifecycleService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/surveys")
public class SurveyLifecycleController {
    private final SurveyLifecycleService surveys;

    public SurveyLifecycleController(SurveyLifecycleService surveys) {
        this.surveys = surveys;
    }

    private Principal actor(Authentication authentication) {
        return (Principal) authentication.getPrincipal();
    }

    @GetMapping("/{id}")
    public SiteSurvey get(@PathVariable UUID id, Authentication authentication) {
        return surveys.get(id, actor(authentication));
    }

    @GetMapping("/{id}/history")
    public List<SiteSurveyHistory> history(@PathVariable UUID id, Authentication authentication) {
        return surveys.history(id, actor(authentication));
    }

    @PatchMapping("/{id}")
    public SiteSurvey update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest input,
                             Authentication authentication) {
        return surveys.update(id, input, actor(authentication));
    }

    @PostMapping("/{id}/start")
    public SiteSurvey start(@PathVariable UUID id, Authentication authentication) {
        return surveys.start(id, actor(authentication));
    }

    @PostMapping("/{id}/submit")
    public SiteSurvey submit(@PathVariable UUID id, Authentication authentication) {
        return surveys.submit(id, actor(authentication));
    }

    @PostMapping("/{id}/approve")
    public SiteSurvey approve(@PathVariable UUID id, Authentication authentication) {
        return surveys.approve(id, actor(authentication));
    }

    @PostMapping("/{id}/reject")
    public SiteSurvey reject(@PathVariable UUID id, @Valid @RequestBody DecisionRequest input,
                             Authentication authentication) {
        return surveys.reject(id, input.reason(), actor(authentication));
    }

    @PostMapping("/{id}/cancel")
    public SiteSurvey cancel(@PathVariable UUID id, @Valid @RequestBody DecisionRequest input,
                             Authentication authentication) {
        return surveys.cancel(id, input.reason(), actor(authentication));
    }
}
