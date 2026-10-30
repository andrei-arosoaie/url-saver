package com.know.categories.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.know.accounts.application.CurrentAccount;
import com.know.shared.application.RequestFailure;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CategoryManagementTest {
  private final UUID owner = UUID.randomUUID();
  private final CategoryCatalog catalog = mock(CategoryCatalog.class);
  private final CurrentAccount current = () -> owner;
  private final CategoryManagement service =
      new CategoryManagement(current, catalog, new CategoryRules(100));

  @Test
  void blocksReferencedCategoryChangesWithoutCallingPersistenceWrites() {
    when(catalog.lock(owner, "custom")).thenReturn(category("custom", false, 2, 3));
    assertReason(() -> service.edit("custom", 2L, null, null, false), "CATEGORY_IN_USE");
    assertReason(() -> service.remove("custom", 2L), "CATEGORY_IN_USE");
    verify(catalog, never()).edit(any(), any(), anyLong(), any(), any(), any());
    verify(catalog, never()).remove(any(), any(), anyLong());
  }

  @Test
  void protectsFallbackDefaultsAndStaleVersionsBeforeAnyWrite() {
    when(catalog.lock(owner, "OTHER")).thenReturn(category("OTHER", true, 0, 0));
    assertReason(() -> service.edit("OTHER", 0L, null, null, false), "CATEGORY_REQUIRED");
    assertReason(() -> service.remove("OTHER", 0L), "DEFAULT_CATEGORY_PROTECTED");
    assertReason(() -> service.edit("OTHER", 1L, "Name", null, null), "VERSION_CONFLICT");
    verify(catalog, never()).edit(any(), any(), anyLong(), any(), any(), any());
    verify(catalog, never()).remove(any(), any(), anyLong());
  }

  @Test
  void allowsRenamingReferencedDefaultsWithoutChangingTheirStableKey() {
    var original = category("BOOKS", true, 3, 10);
    when(catalog.lock(owner, "BOOKS")).thenReturn(original);
    service.edit("BOOKS", 3L, " Reading ", "#112233", null);
    verify(catalog).edit(owner, "BOOKS", 3, "Reading", "#112233", null);
  }

  private Category category(String key, boolean defaultCategory, long version, long count) {
    return new Category(
        key, "Name", "#112233", defaultCategory, true, Instant.EPOCH, version, count);
  }

  private void assertReason(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String reason) {
    assertThatThrownBy(action)
        .isInstanceOfSatisfying(
            RequestFailure.class, e -> assertThat(e.getReason()).isEqualTo(reason));
  }
}
