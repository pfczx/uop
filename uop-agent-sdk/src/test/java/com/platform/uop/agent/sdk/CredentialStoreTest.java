package com.platform.uop.agent.sdk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class CredentialStoreTest {

    private CredentialStore store;
    private Path tempDir;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        this.tempDir = tempDir;
        Path credFile = tempDir.resolve("credentials");
        store = new CredentialStore(credFile);
    }

    @Test
    void saveAndLoad() {
        String credential = "test-credential-123";
        store.save(credential);
        assertEquals(credential, store.load());
    }

    @Test
    void loadNonExistentReturnsNull() {
        assertNull(store.load());
    }

    @Test void loadBlankFileReturnsNull() throws Exception {
        Path credFile = tempDir.resolve("credentials");
        Files.writeString(credFile, "   ");
        CredentialStore store2 = new CredentialStore(credFile);
        assertNull(store2.load());
    }

    @Test void clearRemovesCredential() throws Exception {
        store.save("test-cred");
        assertNotNull(store.load());
        store.clear();
        assertNull(store.load());
    }

    @Test void saveNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> store.save(null));
    }

    @Test void saveBlankThrows() {
        assertThrows(IllegalArgumentException.class, () -> store.save("   "));
    }

    @Test void atomicWriteDoesNotCorrupt() throws Exception {
        Path credFile = tempDir.resolve("credentials");
        CredentialStore store2 = new CredentialStore(credFile);
        
        // Write multiple times
        for (int i = 0; i < 100; i++) {
            store2.save("cred-" + i);
        }
        
        // Should be able to read final value
        String result = store2.load();
        assertEquals("cred-99", result);
    }
}