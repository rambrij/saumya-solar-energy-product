package com.saumyasolar.api;
import io.swagger.v3.oas.annotations.Operation;

import com.saumyasolar.domain.CustomerAddress;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.CustomerAccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customer")
public class CustomerAccountController {
 private final CustomerAccountService service;
 public CustomerAccountController(CustomerAccountService service){this.service=service;}
 private Principal actor(Authentication authentication){return (Principal)authentication.getPrincipal();}
 public record ProfileRequest(@NotBlank @Size(max=150) String name,@Size(max=30) String phone){}
 public record AddressRequest(@NotBlank @Size(max=80) String label,@NotBlank @Size(max=180) String line1,
  @Size(max=180) String line2,@NotBlank @Size(max=100) String city,@NotBlank @Size(max=100) String state,
  @NotBlank @Pattern(regexp="[0-9A-Za-z -]{4,12}") String pincode,@Size(max=80) String country,@Size(max=120) String landmark,boolean isDefault){}
 @Operation(summary="Get my customer profile",description="Returns the profile associated with the authenticated customer identity.")
 @GetMapping("/profile") public CustomerAccountService.ProfileView profile(Authentication a){return service.profile(actor(a));}
 @Operation(summary="Update my customer profile",description="Updates editable customer name and phone only. Email and role are immutable.")
 @PatchMapping("/profile") public CustomerAccountService.ProfileView updateProfile(@Valid @RequestBody ProfileRequest x,Authentication a){
  return service.updateProfile(new CustomerAccountService.ProfileInput(x.name(),x.phone()),actor(a));
 }
 @Operation(summary="List my saved addresses",description="Lists addresses owned by the authenticated customer.")
 @GetMapping("/addresses") public List<CustomerAddress> addresses(Authentication a){return service.addresses(actor(a));}
 @Operation(summary="Create a saved address",description="Creates an address for the authenticated customer. The first address is default.")
 @PostMapping("/addresses") @ResponseStatus(HttpStatus.CREATED) public CustomerAddress createAddress(@Valid @RequestBody AddressRequest x,Authentication a){
  return service.createAddress(input(x),actor(a));
 }
 @Operation(summary="Update a saved address",description="Updates an address owned by the authenticated customer.")
 @PutMapping("/addresses/{id}") public CustomerAddress updateAddress(@PathVariable UUID id,@Valid @RequestBody AddressRequest x,Authentication a){
  return service.updateAddress(id,input(x),actor(a));
 }
 @Operation(summary="Delete a saved address",description="Deletes an address owned by the authenticated customer.")
 @DeleteMapping("/addresses/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteAddress(@PathVariable UUID id,Authentication a){service.deleteAddress(id,actor(a));}
 private CustomerAccountService.AddressInput input(AddressRequest x){
  return new CustomerAccountService.AddressInput(x.label(),x.line1(),x.line2(),x.city(),x.state(),x.pincode(),x.country(),x.landmark(),x.isDefault());
 }
}
