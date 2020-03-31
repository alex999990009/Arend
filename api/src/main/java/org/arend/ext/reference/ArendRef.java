package org.arend.ext.reference;

import org.arend.ext.module.LongName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ArendRef extends RawRef {
  @Nullable
  default LongName getRefLongName() {
    return null;
  }

  default boolean isClassField() {
    return false;
  }
}
