package com.saumyasolar.service;

import com.saumyasolar.domain.*;
import com.saumyasolar.repo.*;
import com.saumyasolar.security.Principal;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@Service
public class CustomerAccountService {
 private final CustomerProfileRepository profiles;
 private final CustomerAddressRepository addresses;
 private final AppUserRepository users;
 public CustomerAccountService(CustomerProfileRepository profiles,CustomerAddressRepository addresses,AppUserRepository users){
  this.profiles=profiles;this.addresses=addresses;this.users=users;
 }
 private CustomerProfile customer(Principal actor){
  if(actor==null||!"CUSTOMER".equals(actor.accountType())||actor.tenantId()==null)
   throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  return profiles.findByTenantIdAndUserId(actor.tenantId(),actor.userId())
   .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Customer profile not found"));
 }
 public ProfileView profile(Principal actor){
  CustomerProfile c=customer(actor);
  return new ProfileView(c.getId(),c.getName(),c.getEmail(),c.getPhone(),c.getAddress(),c.getGstin());
 }
 @Transactional public ProfileView updateProfile(ProfileInput input,Principal actor){
  CustomerProfile c=customer(actor);
  if(input.name()==null||input.name().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Name is required");
  c.setName(input.name().trim());c.setPhone(trim(input.phone()));profiles.save(c);
  AppUser user=users.findById(actor.userId()).filter(u->actor.tenantId().equals(u.getTenantId())&&"CUSTOMER".equals(u.getAccountType()))
   .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Customer account not found"));
  user.setName(c.getName());users.save(user);
  return profile(actor);
 }
 public List<CustomerAddress> addresses(Principal actor){
  CustomerProfile c=customer(actor);
  return addresses.findByTenantIdAndCustomerIdOrderByIsDefaultDescCreatedAtDesc(actor.tenantId(),c.getId());
 }
 @Transactional public CustomerAddress createAddress(AddressInput input,Principal actor){
  CustomerProfile c=customer(actor);validate(input);
  CustomerAddress a=new CustomerAddress();a.setId(UUID.randomUUID());a.setTenantId(actor.tenantId());a.setCustomerId(c.getId());
  Instant now=Instant.now();a.setCreatedAt(now);a.setUpdatedAt(now);
  boolean makeDefault=input.isDefault()||addresses.countByTenantIdAndCustomerId(actor.tenantId(),c.getId())==0;
  if(makeDefault)clearDefault(actor.tenantId(),c.getId());apply(a,input);a.setDefault(makeDefault);
  return addresses.save(a);
 }
 @Transactional public CustomerAddress updateAddress(UUID id,AddressInput input,Principal actor){
  CustomerProfile c=customer(actor);validate(input);
  CustomerAddress a=addresses.findByIdAndTenantIdAndCustomerId(id,actor.tenantId(),c.getId())
   .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Address not found"));
  if(input.isDefault())clearDefault(actor.tenantId(),c.getId());
  apply(a,input);a.setUpdatedAt(Instant.now());return addresses.save(a);
 }
 @Transactional public void deleteAddress(UUID id,Principal actor){
  CustomerProfile c=customer(actor);
  CustomerAddress a=addresses.findByIdAndTenantIdAndCustomerId(id,actor.tenantId(),c.getId())
   .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Address not found"));
  boolean wasDefault=a.isDefault();addresses.delete(a);
  if(wasDefault)addresses.findByTenantIdAndCustomerIdOrderByIsDefaultDescCreatedAtDesc(actor.tenantId(),c.getId()).stream().findFirst().ifPresent(next->{next.setDefault(true);next.setUpdatedAt(Instant.now());addresses.save(next);});
 }
 private void clearDefault(UUID tenant,UUID customer){
  addresses.findByTenantIdAndCustomerIdOrderByIsDefaultDescCreatedAtDesc(tenant,customer).stream().filter(CustomerAddress::isDefault).forEach(a->{a.setDefault(false);a.setUpdatedAt(Instant.now());addresses.save(a);});
 }
 private void apply(CustomerAddress a,AddressInput x){
  a.setLabel(x.label().trim());a.setLine1(x.line1().trim());a.setLine2(trim(x.line2()));a.setCity(x.city().trim());
  a.setState(x.state().trim());a.setPincode(x.pincode().trim());a.setCountry(x.country()==null||x.country().isBlank()?"India":x.country().trim());
  a.setLandmark(trim(x.landmark()));a.setDefault(x.isDefault());
 }
 private void validate(AddressInput x){
  if(x.label()==null||x.label().isBlank()||x.line1()==null||x.line1().isBlank()||x.city()==null||x.city().isBlank()
   ||x.state()==null||x.state().isBlank()||x.pincode()==null||!x.pincode().trim().matches("[0-9A-Za-z -]{4,12}"))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Address label, line 1, city, state and valid PIN code are required");
 }
 private String trim(String x){return x==null||x.isBlank()?null:x.trim();}
 public record ProfileInput(String name,String phone){}
 public record ProfileView(UUID id,String name,String email,String phone,String address,String gstin){}
 public record AddressInput(String label,String line1,String line2,String city,String state,String pincode,String country,String landmark,boolean isDefault){}
}
