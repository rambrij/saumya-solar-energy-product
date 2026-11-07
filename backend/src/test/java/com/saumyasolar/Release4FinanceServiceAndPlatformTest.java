package com.saumyasolar;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import java.util.UUID;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Release4FinanceServiceAndPlatformTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    private String token(String email,String password)throws Exception{
        String result=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\""+email+"\",\"password\":\""+password+"\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(result).get("accessToken").asText();
    }
    @Test void platformTenantDashboardAndExportsArePlatformOnlyAndFiltered()throws Exception{
        String platform=token("platform@saumya.local","Platform@123");
        mvc.perform(get("/api/v1/platform/tenants").header("Authorization","Bearer "+platform).param("status","ACTIVE").param("page","0").param("size","20"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").isNumber()).andExpect(jsonPath("$.active").isNumber()).andExpect(jsonPath("$.items").isArray());
        String exportSlug="export-"+UUID.randomUUID().toString().substring(0,8);
        String exportEmail="export-"+UUID.randomUUID()+"@example.test";
        mvc.perform(post("/api/v1/tenants").header("Authorization","Bearer "+platform).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Export Tenant\",\"slug\":\""+exportSlug+"\",\"adminName\":\"Export Admin\",\"adminEmail\":\""+exportEmail+"\",\"adminPassword\":\"ExportAdmin@2026\"}"))
            .andExpect(status().isOk());
        String csv=mvc.perform(get("/api/v1/platform/tenants/export").header("Authorization","Bearer "+platform).param("status","ACTIVE"))
            .andExpect(status().isOk()).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString("tenants-active-"+LocalDate.now()))).andExpect(content().contentTypeCompatibleWith("text/csv"))
            .andReturn().getResponse().getContentAsString();
        assertTrue(csv.startsWith("Tenant Name,Tenant Slug,Administrator Name"));
        assertTrue(csv.contains(exportEmail));
        
        String emptyCsv=mvc.perform(get("/api/v1/platform/tenants/export").header("Authorization","Bearer "+platform).param("status","ACTIVE").param("search","no-tenant-can-match-this"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(1,emptyCsv.lines().count(),"Empty exports retain the CSV header and no fabricated rows");
        mvc.perform(get("/api/v1/platform/tenants/export.xlsx").header("Authorization","Bearer "+platform))
            .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        mvc.perform(get("/api/v1/platform/tenants/export.pdf").header("Authorization","Bearer "+platform))
            .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF));
        String vendor=token("admin@saumya.local","Admin@123");
        mvc.perform(get("/api/v1/platform/tenants").header("Authorization","Bearer "+vendor)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/platform/tenants/export.xlsx").header("Authorization","Bearer "+vendor)).andExpect(status().isForbidden());
    }
    @Test void tenantEditAllowsOnlyDisplayNameAndTenantStatusRemainsPlatformProtected()throws Exception{
        String platform=token("platform@saumya.local","Platform@123");
        String email="tenant-edit-"+UUID.randomUUID()+"@example.test";
        String created=mvc.perform(post("/api/v1/tenants").header("Authorization","Bearer "+platform).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Tenant before\",\"slug\":\"tenant-before-"+UUID.randomUUID().toString().substring(0,8)+"\",\"adminName\":\"Tenant Admin\",\"adminEmail\":\""+email+"\",\"adminPassword\":\"TenantAdmin@2026\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id=json.readTree(created).get("id").asText();
        mvc.perform(patch("/api/v1/platform/tenants/"+id).header("Authorization","Bearer "+platform).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Tenant after\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Tenant after"));
        mvc.perform(patch("/api/v1/platform/tenants/"+id).header("Authorization","Bearer "+platform).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\" \"}")).andExpect(status().isBadRequest());
    }
    @Test void serviceSlaTicketLifecycleIsTenantScopedAndCustomerCanConfirmResolution()throws Exception{
        String vendor=token("admin@saumya.local","Admin@123");
        mvc.perform(post("/api/v1/service-sla-policies").header("Authorization","Bearer "+vendor).contentType(MediaType.APPLICATION_JSON)
            .content("{\"priority\":\"MEDIUM\",\"responseMinutes\":30,\"resolutionMinutes\":240,\"timeZone\":\"Asia/Kolkata\",\"holidaysJson\":\"[]\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.priority").value("MEDIUM"));
        String email="svc-customer-"+UUID.randomUUID()+"@example.test";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"storeSlug\":\"saumya-solar\",\"name\":\"Service Customer\",\"email\":\""+email+"\",\"password\":\"ServiceCustomer@2026\"}"))
            .andExpect(status().isOk());
        String customer=token(email,"ServiceCustomer@2026");
        String created=mvc.perform(post("/api/v1/service-requests").header("Authorization","Bearer "+customer).contentType(MediaType.APPLICATION_JSON)
            .content("{\"category\":\"INVERTER\",\"priority\":\"MEDIUM\",\"description\":\"Inverter is not starting\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN")).andReturn().getResponse().getContentAsString();
        String id=json.readTree(created).get("id").asText();
        mvc.perform(get("/api/v1/service-requests").header("Authorization","Bearer "+customer)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
        MockMultipartFile evidence=new MockMultipartFile("file","service-evidence.png","image/png",new byte[]{(byte)0x89,'P','N','G',13,10,26,10,0,0,0,0});
        String attachment=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/service-requests/"+id+"/attachments").file(evidence).header("Authorization","Bearer "+customer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.contentType").value("image/png")).andReturn().getResponse().getContentAsString();
        String attachmentId=json.readTree(attachment).get("id").asText();
        mvc.perform(get("/api/v1/service-requests/"+id+"/attachments/"+attachmentId).header("Authorization","Bearer "+customer))
            .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("image/png"));
        mvc.perform(get("/api/v1/service-requests/"+id).header("Authorization","Bearer "+vendor)).andExpect(status().isOk());
        String otherEmail="other-customer-"+UUID.randomUUID()+"@example.test";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"storeSlug\":\"saumya-solar\",\"name\":\"Other Customer\",\"email\":\""+otherEmail+"\",\"password\":\"OtherCustomer@2026\"}"))
            .andExpect(status().isOk());
        String other=token(otherEmail,"OtherCustomer@2026");
        mvc.perform(get("/api/v1/service-requests/"+id).header("Authorization","Bearer "+other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/service-requests/"+id+"/attachments/"+attachmentId).header("Authorization","Bearer "+other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/service-sla-policies").header("Authorization","Bearer "+customer)).andExpect(status().isForbidden());
    }
}
