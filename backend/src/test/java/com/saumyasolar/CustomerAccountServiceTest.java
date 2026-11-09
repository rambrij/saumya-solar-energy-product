package com.saumyasolar;

import com.saumyasolar.domain.*;
import com.saumyasolar.repo.*;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.CustomerAccountService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerAccountServiceTest {
 @Test void profileUpdateChangesOnlyEditableNameAndPhone(){
  CustomerProfileRepository profiles=mock(CustomerProfileRepository.class);CustomerAddressRepository addresses=mock(CustomerAddressRepository.class);AppUserRepository users=mock(AppUserRepository.class);
  UUID tenant=UUID.randomUUID(),userId=UUID.randomUUID();CustomerProfile profile=new CustomerProfile();profile.setId(UUID.randomUUID());profile.setTenantId(tenant);profile.setUserId(userId);profile.setName("Old");profile.setEmail("locked@example.test");
  AppUser account=new AppUser();account.setId(userId);account.setTenantId(tenant);account.setAccountType("CUSTOMER");
  when(profiles.findByTenantIdAndUserId(tenant,userId)).thenReturn(Optional.of(profile));when(users.findById(userId)).thenReturn(Optional.of(account));when(profiles.save(any())).thenAnswer(i->i.getArgument(0));
  CustomerAccountService service=new CustomerAccountService(profiles,addresses,users);Principal actor=new Principal(userId,tenant,"locked@example.test","CUSTOMER",Set.of("CUSTOMER"),Set.of(),0);
  var result=service.updateProfile(new CustomerAccountService.ProfileInput("New Name","9999999999"),actor);
  assertEquals("New Name",result.name());assertEquals("locked@example.test",result.email());assertEquals("New Name",account.getName());
  verify(users).save(account);
 }
 @Test void addressOwnershipIsResolvedFromAuthenticatedCustomer(){
  CustomerProfileRepository profiles=mock(CustomerProfileRepository.class);CustomerAddressRepository addresses=mock(CustomerAddressRepository.class);AppUserRepository users=mock(AppUserRepository.class);
  UUID tenant=UUID.randomUUID(),userId=UUID.randomUUID();when(profiles.findByTenantIdAndUserId(tenant,userId)).thenReturn(Optional.empty());
  CustomerAccountService service=new CustomerAccountService(profiles,addresses,users);Principal actor=new Principal(userId,tenant,"customer@example.test","CUSTOMER",Set.of("CUSTOMER"),Set.of(),0);
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.addresses(actor));
  verify(addresses,never()).findOwnedAddresses(any(),any());
 }
 @Test void firstSavedAddressBecomesDefault(){
  CustomerProfileRepository profiles=mock(CustomerProfileRepository.class);CustomerAddressRepository addresses=mock(CustomerAddressRepository.class);AppUserRepository users=mock(AppUserRepository.class);
  UUID tenant=UUID.randomUUID(),userId=UUID.randomUUID();CustomerProfile profile=new CustomerProfile();profile.setId(UUID.randomUUID());profile.setTenantId(tenant);profile.setUserId(userId);
  when(profiles.findByTenantIdAndUserId(tenant,userId)).thenReturn(Optional.of(profile));when(addresses.countByTenantIdAndCustomerId(tenant,profile.getId())).thenReturn(0L);
  when(addresses.save(any(CustomerAddress.class))).thenAnswer(i->i.getArgument(0));
  CustomerAccountService service=new CustomerAccountService(profiles,addresses,users);Principal actor=new Principal(userId,tenant,"customer@example.test","CUSTOMER",Set.of("CUSTOMER"),Set.of(),0);
  var input=new CustomerAccountService.AddressInput("Home","12 Solar Road",null,"Hyderabad","Telangana","500001","India",null,false);
  CustomerAddress saved=service.createAddress(input,actor);
  assertTrue(saved.isDefault());assertEquals(tenant,saved.getTenantId());assertEquals(profile.getId(),saved.getCustomerId());
 }
 @Test void nonCustomerCannotUpdateProfile(){
  CustomerAccountService service=new CustomerAccountService(mock(CustomerProfileRepository.class),mock(CustomerAddressRepository.class),mock(AppUserRepository.class));
  Principal vendor=new Principal(UUID.randomUUID(),UUID.randomUUID(),"vendor@example.test","VENDOR",Set.of("VENDOR_ADMIN"),Set.of(),0);
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.profile(vendor));
 }
}
