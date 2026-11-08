package com.saumyasolar.service;

import com.saumyasolar.api.RoleAdminModels.RoleInput;
import com.saumyasolar.config.DataSeeder;
import com.saumyasolar.domain.Permission;
import com.saumyasolar.domain.Role;
import com.saumyasolar.repo.AppUserRepository;
import com.saumyasolar.repo.PermissionRepository;
import com.saumyasolar.repo.RoleRepository;
import com.saumyasolar.security.Principal;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class VendorRoleAdminService {
    private static final Set<String> PROTECTED=Set.of("VENDOR_ADMIN","PLATFORM_SUPER_ADMIN","CUSTOMER");
    private static final Set<String> ASSIGNABLE=java.util.stream.Stream.concat(
            DataSeeder.employeeRolePermissions().values().stream().flatMap(java.util.Collection::stream),
            java.util.stream.Stream.of("USER_VIEW","CATALOG_VIEW"))
            .filter(code->!"PLATFORM_MANAGE".equals(code)&&!"USER_MANAGE".equals(code))
            .collect(Collectors.toUnmodifiableSet());
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final AppUserRepository users;

    public VendorRoleAdminService(RoleRepository roles,PermissionRepository permissions,AppUserRepository users) {
        this.roles=roles;this.permissions=permissions;this.users=users;
    }
    private UUID tenant(Principal p) {
        if(!"VENDOR".equals(p.accountType())||p.tenantId()==null||p.permissions()==null||!p.permissions().contains("USER_MANAGE"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Vendor user-management permission is required");
        return p.tenantId();
    }
    public Set<String> assignablePermissions(Principal p) { tenant(p); return ASSIGNABLE; }
    @Transactional
    public Role create(RoleInput input,Principal p) {
        UUID tenant=tenant(p);String name=normalize(input.name());validateName(name);
        if(roles.findByNameAndTenantId(name,tenant).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"Role already exists");
        Role role=new Role();role.setId(UUID.randomUUID());role.setTenantId(tenant);role.setName(name);
        role.getPermissions().addAll(resolvePermissions(input.permissionCodes()));
        return roles.save(role);
    }
    @Transactional
    public Role update(UUID id,RoleInput input,Principal p) {
        UUID tenant=tenant(p);
        Role role=roles.findById(id).filter(r->tenant.equals(r.getTenantId())&&!PROTECTED.contains(r.getName())
                &&!DataSeeder.employeeRolePermissions().containsKey(r.getName()))
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Custom role not found"));
        String name=normalize(input.name());validateName(name);
        roles.findByNameAndTenantId(name,tenant).filter(other->!other.getId().equals(id))
                .ifPresent(other->{throw new ResponseStatusException(HttpStatus.CONFLICT,"Role already exists");});
        role.setName(name);role.getPermissions().clear();role.getPermissions().addAll(resolvePermissions(input.permissionCodes()));
        return roles.save(role);
    }
    @Transactional
    public void delete(UUID id,Principal p) {
        UUID tenant=tenant(p);
        Role role=roles.findById(id).filter(r->tenant.equals(r.getTenantId())&&!PROTECTED.contains(r.getName())
                &&!DataSeeder.employeeRolePermissions().containsKey(r.getName()))
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Custom role not found"));
        boolean assigned=users.findByTenantId(tenant).stream().anyMatch(user->user.getRoles().stream().anyMatch(r->r.getId().equals(id)));
        if(assigned)throw new ResponseStatusException(HttpStatus.CONFLICT,"Remove this role from all employees before deleting it");
        roles.delete(role);
    }
    private Set<Permission> resolvePermissions(Set<String> codes) {
        if(codes==null||codes.isEmpty()||!ASSIGNABLE.containsAll(codes))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"One or more permissions are not assignable to vendor roles");
        return codes.stream().map(code->permissions.findByCode(code)
                .orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown permission: "+code)))
                .collect(Collectors.toSet());
    }
    private void validateName(String name) {
        if(PROTECTED.contains(name)||DataSeeder.employeeRolePermissions().containsKey(name)
                ||!name.matches("[A-Z][A-Z0-9_]{2,39}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a unique custom role name using 3–40 uppercase letters, numbers or underscores");
    }
    private String normalize(String name) { return name==null?"":name.trim().toUpperCase(Locale.ROOT); }
}
