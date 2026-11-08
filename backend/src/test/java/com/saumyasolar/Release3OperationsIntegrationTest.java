package com.saumyasolar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumyasolar.domain.Product;
import com.saumyasolar.domain.CustomerOrder;
import com.saumyasolar.domain.OrderItem;
import com.saumyasolar.domain.InventoryBalance;
import com.saumyasolar.domain.Warehouse;
import com.saumyasolar.security.Principal;
import com.saumyasolar.api.OperationsModels.ReservationInput;
import com.saumyasolar.service.OperationsService;
import com.saumyasolar.repo.CustomerOrderRepository;
import com.saumyasolar.repo.OrderItemRepository;
import com.saumyasolar.repo.CustomerProfileRepository;
import com.saumyasolar.repo.InventoryBalanceRepository;
import com.saumyasolar.repo.SubscriptionPlanRepository;
import com.saumyasolar.repo.TenantSubscriptionRepository;
import com.saumyasolar.domain.ProductCategory;
import com.saumyasolar.domain.Tenant;
import com.saumyasolar.repo.AppUserRepository;
import com.saumyasolar.repo.ProductCategoryRepository;
import com.saumyasolar.repo.ProductRepository;
import com.saumyasolar.repo.TenantRepository;
import com.saumyasolar.repo.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Release3OperationsIntegrationTest {
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired AppUserRepository users;
 @Autowired ProductRepository products;@Autowired ProductCategoryRepository categories;@Autowired TenantRepository tenants;@Autowired WarehouseRepository warehouses;
 @Autowired CustomerOrderRepository orders;@Autowired OrderItemRepository orderItems;@Autowired CustomerProfileRepository customerProfiles;@Autowired InventoryBalanceRepository balances;@Autowired OperationsService operations;@Autowired SubscriptionPlanRepository subscriptionPlans;@Autowired TenantSubscriptionRepository tenantSubscriptions;
 UUID tenantId;
 @BeforeEach void setup(){tenantId=users.findByEmailIgnoreCase("admin@saumya.local").orElseThrow().getTenantId();}
 private String token() throws Exception {String body=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"admin@saumya.local\",\"password\":\"Admin@123\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();return json.readTree(body).get("accessToken").asText();}
 private Tenant otherTenant(){Tenant t=new Tenant();t.setId(UUID.randomUUID());t.setName("Other vendor");t.setSlug("other-"+UUID.randomUUID());t.setStatus("ACTIVE");return tenants.save(t);}
 @Test void warehouseReceiptCreatesBalanceAndImmutableLedger() throws Exception {
  var assigned=tenantSubscriptions.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().findFirst().orElseThrow();var starter=subscriptionPlans.findById(assigned.getPlanId()).orElseThrow();assertEquals(3,starter.getMaxWarehouses(),starter.getEntitlementsJson());assertTrue(starter.getEntitlementsJson().contains("\"multiWarehouse\":true"),starter.getEntitlementsJson());
  String auth=token();String warehouseBody="{\"code\":\"WH-"+UUID.randomUUID()+"\",\"name\":\"Test warehouse\",\"address\":\"Mumbai\"}";
  var warehouseResult=mvc.perform(post("/api/v1/warehouses").header("Authorization","Bearer "+auth).contentType(MediaType.APPLICATION_JSON).content(warehouseBody)).andReturn();
  assertEquals(200,warehouseResult.getResponse().getStatus(),warehouseResult.getResponse().getContentAsString());
  String warehouseJson=warehouseResult.getResponse().getContentAsString();
  UUID warehouseId=UUID.fromString(json.readTree(warehouseJson).get("id").asText());
  Product product=products.findByTenantIdOrderByIdDesc(tenantId).stream().filter(p->"DEMO-PV-550".equals(p.getSku())).findFirst().orElseThrow();
  mvc.perform(post("/api/v1/inventory/receipts").header("Authorization","Bearer "+auth).contentType(MediaType.APPLICATION_JSON).content("{\"warehouseId\":\""+warehouseId+"\",\"productId\":\""+product.getId()+"\",\"quantity\":25,\"reason\":\"PO-TEST-001\"}")).andExpect(status().isOk());
  String balances=mvc.perform(get("/api/v1/inventory/balances").header("Authorization","Bearer "+auth)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  JsonNode rows=json.readTree(balances);assertTrue(java.util.stream.StreamSupport.stream(rows.spliterator(),false).anyMatch(x->x.get("productId").asText().equals(product.getId().toString())&&new BigDecimal(x.get("onHand").asText()).compareTo(new BigDecimal("25"))==0));
  String ledger=mvc.perform(get("/api/v1/inventory/transactions").header("Authorization","Bearer "+auth)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertTrue(ledger.contains("RECEIPT"));assertTrue(ledger.contains("PO-TEST-001"));
 }
 @Test void stockIssueDamageAndReturnPreserveSeparateLedgerDimensions() throws Exception {
  String auth=token();Warehouse warehouse=new Warehouse();warehouse.setId(UUID.randomUUID());warehouse.setTenantId(tenantId);warehouse.setCode("DIM-"+UUID.randomUUID());warehouse.setName("Ledger dimensions test warehouse");warehouse.setActive(true);warehouse.setCreatedAt(java.time.Instant.now());warehouse=warehouses.save(warehouse);
  Product product=products.findByTenantIdOrderByIdDesc(tenantId).stream().filter(p->"DEMO-PV-550".equals(p.getSku())).findFirst().orElseThrow();
  String wh=warehouse.getId().toString(),pr=product.getId().toString();
  mvc.perform(post("/api/v1/inventory/receipts").header("Authorization","Bearer "+auth).contentType(MediaType.APPLICATION_JSON).content("{\"warehouseId\":\""+wh+"\",\"productId\":\""+pr+"\",\"quantity\":25,\"reason\":\"PO-25\"}")).andExpect(status().isOk());
  mvc.perform(post("/api/v1/inventory/damages").header("Authorization","Bearer "+auth).contentType(MediaType.APPLICATION_JSON).content("{\"warehouseId\":\""+wh+"\",\"productId\":\""+pr+"\",\"quantity\":2,\"reason\":\"Damaged during unloading\"}")).andExpect(status().isOk());
  mvc.perform(post("/api/v1/inventory/issues").header("Authorization","Bearer "+auth).contentType(MediaType.APPLICATION_JSON).content("{\"warehouseId\":\""+wh+"\",\"productId\":\""+pr+"\",\"quantity\":3,\"reason\":\"Internal use\"}")).andExpect(status().isOk());
  mvc.perform(post("/api/v1/inventory/returns").header("Authorization","Bearer "+auth).contentType(MediaType.APPLICATION_JSON).content("{\"warehouseId\":\""+wh+"\",\"productId\":\""+pr+"\",\"quantity\":1,\"reason\":\"Customer return\"}")).andExpect(status().isOk());
  String response=mvc.perform(get("/api/v1/inventory/balances").header("Authorization","Bearer "+auth)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  JsonNode row=java.util.stream.StreamSupport.stream(json.readTree(response).spliterator(),false).filter(x->x.get("warehouseId").asText().equals(wh)&&x.get("productId").asText().equals(pr)).findFirst().orElseThrow();
  assertEquals(0,new BigDecimal("23").compareTo(new BigDecimal(row.get("onHand").asText())));
  assertEquals(0,new BigDecimal("2").compareTo(new BigDecimal(row.get("damaged").asText())));
  assertEquals(0,new BigDecimal("21").compareTo(new BigDecimal(row.get("onHand").asText()).subtract(new BigDecimal(row.get("reserved").asText())).subtract(new BigDecimal(row.get("damaged").asText()))));
  String ledger=mvc.perform(get("/api/v1/inventory/transactions").header("Authorization","Bearer "+auth)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertTrue(ledger.contains("DAMAGE"));assertTrue(ledger.contains("ISSUE"));assertTrue(ledger.contains("RETURN"));
 }
 @Test void concurrentReservationsCannotOversellWarehouseStock() throws Exception {
  var admin=users.findByEmailIgnoreCase("admin@saumya.local").orElseThrow();
  Product product=products.findByTenantIdOrderByIdDesc(tenantId).stream().filter(p->"DEMO-PV-550".equals(p.getSku())).findFirst().orElseThrow();
  var customer=customerProfiles.findByTenantIdOrderByIdDesc(tenantId).get(0);
  Warehouse warehouse=new Warehouse();warehouse.setId(UUID.randomUUID());warehouse.setTenantId(tenantId);warehouse.setCode("RACE-"+UUID.randomUUID());warehouse.setName("Concurrency test warehouse");warehouse.setActive(true);warehouse.setCreatedAt(java.time.Instant.now());warehouses.save(warehouse);
  InventoryBalance balance=new InventoryBalance();balance.setId(UUID.randomUUID());balance.setTenantId(tenantId);balance.setWarehouseId(warehouse.getId());balance.setProductId(product.getId());balance.setOnHand(new BigDecimal("5"));balance.setReserved(BigDecimal.ZERO);balance.setDamaged(BigDecimal.ZERO);balances.save(balance);
  UUID[] orderIds={UUID.randomUUID(),UUID.randomUUID()};
  for(int n=0;n<2;n++){CustomerOrder order=new CustomerOrder();order.setId(orderIds[n]);order.setTenantId(tenantId);order.setCustomerId(customer.getId());order.setOrderNumber("RACE-"+UUID.randomUUID());order.setStatus("CONFIRMED");order.setTotal(new BigDecimal("1000"));order.setCreatedAt(java.time.Instant.now());orders.save(order);OrderItem line=new OrderItem();line.setId(UUID.randomUUID());line.setTenantId(tenantId);line.setOrderId(orderIds[n]);line.setProductId(product.getId());line.setDescription("Concurrency test product");line.setQuantity(4);line.setUnitPrice(new BigDecimal("250"));line.setLineTotal(new BigDecimal("1000"));orderItems.save(line);}
  Principal principal=new Principal(admin.getId(),tenantId,admin.getEmail(),"VENDOR",java.util.Set.of("INVENTORY_MANAGER"),java.util.Set.of("INVENTORY_MANAGE"));
  java.util.concurrent.CountDownLatch start=new java.util.concurrent.CountDownLatch(1);java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2);
  try{java.util.List<java.util.concurrent.Future<Boolean>> results=java.util.Arrays.stream(orderIds).map(id->pool.submit(()->{start.await();try{operations.reserve(id,new ReservationInput(warehouse.getId(),"concurrency test"),principal);return true;}catch(org.springframework.web.server.ResponseStatusException ex){return false;}})).toList();start.countDown();long success=0;for(var f:results)if(f.get())success++;assertEquals(1,success);InventoryBalance after=balances.findByTenantIdAndWarehouseIdAndProductId(tenantId,warehouse.getId(),product.getId()).orElseThrow();assertEquals(0,new BigDecimal("4").compareTo(after.getReserved()));assertEquals(0,new BigDecimal("1").compareTo(after.getAvailable()));}finally{pool.shutdownNow();}
 }
 @Test void vendorCannotReceiveStockForAnotherTenantsProduct() throws Exception {
  String auth=token();var foreign=otherTenant();ProductCategory category=new ProductCategory();category.setId(UUID.randomUUID());category.setTenantId(foreign.getId());category.setName("Foreign category");category.setActive(true);categories.save(category);
  Product product=new Product();product.setId(UUID.randomUUID());product.setTenantId(foreign.getId());product.setCategoryId(category.getId());product.setSku("FOREIGN-"+UUID.randomUUID());product.setName("Foreign product");product.setPrice(BigDecimal.TEN);product.setTaxPercent(BigDecimal.ZERO);product.setActive(true);products.save(product);
  var warehouse=warehouses.findByTenantIdOrderByNameAsc(tenantId).get(0);
  mvc.perform(post("/api/v1/inventory/receipts").header("Authorization","Bearer "+auth).contentType(MediaType.APPLICATION_JSON).content("{\"warehouseId\":\""+warehouse.getId()+"\",\"productId\":\""+product.getId()+"\",\"quantity\":1,\"reason\":\"should fail\"}")).andExpect(status().isNotFound());
 }
 @Test void productCrudIsTenantScopedAndDeleteArchivesInsteadOfRemovingHistory() throws Exception {
  String auth=token();ProductCategory category=categories.findByNameAndTenantId("Solar Panels",tenantId).orElseThrow();
  String sku="CRUD-"+UUID.randomUUID();
  String body="{\"categoryId\":\""+category.getId()+"\",\"sku\":\""+sku+"\",\"name\":\"CRUD module\",\"manufacturer\":\"Demo\",\"model\":\"M-1\",\"technology\":\"TOPCon\",\"description\":\"Test only\",\"price\":10000,\"purchasePrice\":7000,\"discountPercent\":5,\"taxPercent\":18,\"warrantyYears\":25,\"imageUrl\":\"https://images.unsplash.com/photo-1509391366360-2e959784a276?w=800\",\"reorderLevel\":3,\"unitOfMeasure\":\"PCS\",\"hsnCode\":\"85414300\",\"serialized\":true,\"attributesJson\":\"{\\\"powerWp\\\":550}\",\"active\":true}";
  String response=mvc.perform(post("/api/v1/catalog/products").header("Authorization","Bearer "+auth).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  UUID id=UUID.fromString(json.readTree(response).get("id").asText());
  String update=body.replace("CRUD module","Updated CRUD module").replace(sku,sku+"-UPDATED");
  mvc.perform(patch("/api/v1/products/"+id).header("Authorization","Bearer "+auth).contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isOk());
  mvc.perform(delete("/api/v1/products/"+id).header("Authorization","Bearer "+auth)).andExpect(status().isOk());
  assertFalse(products.findByIdAndTenantId(id,tenantId).orElseThrow().isActive());
 }

 @Test void inventoryLedgerCompensationRestoresBalanceAndCannotBeRepeated() throws Exception {
  String auth=token();
  Warehouse warehouse=new Warehouse();warehouse.setId(UUID.randomUUID());warehouse.setTenantId(tenantId);warehouse.setCode("REV-"+UUID.randomUUID());warehouse.setName("Compensation test warehouse");warehouse.setActive(true);warehouse.setCreatedAt(java.time.Instant.now());warehouse=warehouses.save(warehouse);
  Product product=products.findByTenantIdOrderByIdDesc(tenantId).stream().filter(p->"DEMO-PV-550".equals(p.getSku())).findFirst().orElseThrow();
  String wh=warehouse.getId().toString(),pr=product.getId().toString();
  mvc.perform(post("/api/v1/inventory/receipts").header("Authorization","Bearer "+auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("warehouseId",wh,"productId",pr,"quantity",7,"reason","Compensation test receipt")))).andExpect(status().isOk());
  String ledger=mvc.perform(get("/api/v1/inventory/transactions").header("Authorization","Bearer "+auth)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  JsonNode original=java.util.stream.StreamSupport.stream(json.readTree(ledger).spliterator(),false).filter(row->"RECEIPT".equals(row.get("type").asText())&&row.get("warehouseId").asText().equals(wh)&&row.get("reason").asText().equals("Compensation test receipt")).findFirst().orElseThrow();
  String originalId=original.get("id").asText();
  mvc.perform(post("/api/v1/inventory/transactions/"+originalId+"/reverse").header("Authorization","Bearer "+auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("reason","Duplicate goods receipt")))).andExpect(status().isOk()).andExpect(jsonPath("$.type").value("COMPENSATION")).andExpect(jsonPath("$.reversalOfTransactionId").value(originalId));
  mvc.perform(post("/api/v1/inventory/transactions/"+originalId+"/reverse").header("Authorization","Bearer "+auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("reason","Attempt duplicate reversal")))).andExpect(status().isConflict());
  InventoryBalance after=balances.findByTenantIdAndWarehouseIdAndProductId(tenantId,warehouse.getId(),product.getId()).orElseThrow();
  assertEquals(0,BigDecimal.ZERO.compareTo(after.getOnHand()));
  assertEquals(0,BigDecimal.ZERO.compareTo(after.getReserved()));
  assertEquals(0,BigDecimal.ZERO.compareTo(after.getDamaged()));
 }
}
