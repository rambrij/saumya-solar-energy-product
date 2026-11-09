package com.saumyasolar.domain;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class InventoryTransactionTest {
    @Test
    void assignsReferenceBeforePersistenceWhenMissing() throws Exception {
        InventoryTransaction transaction = new InventoryTransaction();
        Method callback = InventoryTransaction.class.getDeclaredMethod("assignTransactionReference");
        callback.setAccessible(true);
        callback.invoke(transaction);
        assertNotNull(transaction.getTransactionReference());
        assertTrue(transaction.getTransactionReference().matches("INV-[A-F0-9]{32}"));
    }
    
    @Test
    void preservesAnExistingReference() throws Exception {
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setTransactionReference("INV-EXISTING");
        Method callback = InventoryTransaction.class.getDeclaredMethod("assignTransactionReference");
        callback.setAccessible(true);
        callback.invoke(transaction);
        assertEquals("INV-EXISTING", transaction.getTransactionReference());
    }
}
