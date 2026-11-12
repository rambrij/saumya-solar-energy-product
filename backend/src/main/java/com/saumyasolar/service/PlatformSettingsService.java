package com.saumyasolar.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumyasolar.domain.AuditEvent;
import com.saumyasolar.domain.PlatformSetting;
import com.saumyasolar.repo.AuditEventRepository;
import com.saumyasolar.repo.PlatformSettingRepository;
import com.saumyasolar.security.Principal;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class PlatformSettingsService {
    private record Definition(String type, String defaultValue, int maxLength) {}
    private static final Map<String, Definition> ALLOWED = Map.of(
        "PLATFORM_DEFAULT_CURRENCY", new Definition("CURRENCY", "INR", 3),
        "PLATFORM_DEFAULT_TIME_ZONE", new Definition("TIME_ZONE", "Asia/Kolkata", 80),
        "PLATFORM_DEFAULT_LANGUAGE", new Definition("LANGUAGE", "en-IN", 20),
        "PLATFORM_SUPPORT_EMAIL", new Definition("EMAIL", "", 254),
        "PLATFORM_SUPPORT_PHONE", new Definition("PHONE", "", 40),
        "PLATFORM_BANNER_MESSAGE", new Definition("STRING", "", 300)
    );
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");
    private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}(?:-[A-Z]{2})?");
    private final PlatformSettingRepository settings;
    private final AuditEventRepository audits;
    private final ObjectMapper mapper;

    public PlatformSettingsService(PlatformSettingRepository settings, AuditEventRepository audits, ObjectMapper mapper) {
        this.settings = settings; this.audits = audits; this.mapper = mapper;
    }

    public List<SettingView> list(Principal actor) {
        requirePlatformAdmin(actor);
        Map<String, PlatformSetting> stored = new HashMap<>();
        settings.findAll().forEach(row -> stored.put(row.getKey(), row));
        return ALLOWED.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> {
            PlatformSetting row = stored.get(entry.getKey());
            Definition definition = entry.getValue();
            return new SettingView(entry.getKey(), definition.type(), row == null ? definition.defaultValue() : row.getValue(),
                    row == null ? 0 : row.getVersion(), row == null ? null : row.getUpdatedAt());
        }).toList();
    }

    @Transactional
    public SettingView update(String key, String rawValue, Integer expectedVersion, Principal actor) {
        requirePlatformAdmin(actor);
        Definition definition = ALLOWED.get(key);
        if (definition == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Setting key is not configurable");
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.length() > definition.maxLength()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Setting value is too long");
        validate(definition.type(), value);
        PlatformSetting row = settings.findById(key).orElse(null);
        int currentVersion = row == null ? 0 : row.getVersion();
        if (expectedVersion == null || expectedVersion != currentVersion)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Setting changed since it was loaded; refresh and retry");
        if (row == null) {
            row = new PlatformSetting();
            row.setKey(key);
            row.setVersion(0);
        }
        row.setValueType(definition.type());
        row.setValue(value);
        row.setUpdatedBy(actor.userId());
        row.setUpdatedAt(Instant.now());
        try {
            row = settings.saveAndFlush(row);
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Setting was updated concurrently; refresh and retry");
        }
        audit(actor, key, value, row.getVersion());
        return new SettingView(key, definition.type(), value, row.getVersion(), row.getUpdatedAt());
    }

    private void validate(String type, String value) {
        if (value.isBlank() && Set.of("EMAIL", "PHONE", "STRING").contains(type)) return;
        boolean valid = switch (type) {
            case "CURRENCY" -> CURRENCY.matcher(value).matches();
            case "TIME_ZONE" -> ZoneId.getAvailableZoneIds().contains(value);
            case "LANGUAGE" -> LANGUAGE.matcher(value).matches();
            case "EMAIL" -> value.length() <= 254 && value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
            case "PHONE" -> value.matches("[+0-9() .-]{7,40}");
            case "STRING" -> !value.chars().anyMatch(Character::isISOControl);
            default -> false;
        };
        if (!valid) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid value for setting type " + type);
    }

    private void requirePlatformAdmin(Principal actor) {
        if (actor == null || !"PLATFORM".equals(actor.accountType()) || actor.userId() == null
                || actor.roles() == null || !actor.roles().contains("PLATFORM_SUPER_ADMIN")
                || actor.permissions() == null || !actor.permissions().contains("PLATFORM_MANAGE"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform Super Admin access required");
    }

    private void audit(Principal actor, String key, String value, int version) {
        try {
            AuditEvent event = new AuditEvent();
            event.setId(UUID.randomUUID());
            event.setActorId(actor.userId());
            event.setActorType("PLATFORM");
            event.setAction("PLATFORM_SETTING_UPDATED");
            event.setEntityType("PLATFORM_SETTING");
            event.setEntityId(key);
            event.setOutcome("SUCCESS");
            event.setAfterJson(mapper.writeValueAsString(Map.of("key", key, "value", value, "version", version)));
            event.setCreatedAt(Instant.now());
            audits.save(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to audit platform setting change", ex);
        }
    }

    public record SettingView(String key, String type, String value, int version, Instant updatedAt) {}
}
