package org.arend.ext.core.definition;

import org.arend.ext.core.context.CoreParameter;
import org.arend.ext.core.elimtree.CoreBody;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CoreConstructor extends CoreDefinition {
  @NotNull CoreDataDefinition getDataType();
  @NotNull CoreParameter getParameters();
  @Nullable CoreBody getBody();
}
