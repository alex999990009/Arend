package org.arend.naming.reference;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class ParameterReferable implements Referable {
  private final TCDefReferable myDefinition;
  private final int myIndex;
  private final Referable myReferable;

  public ParameterReferable(TCDefReferable definition, int index, Referable referable) {
    myDefinition = definition;
    myIndex = index;
    myReferable = referable;
  }

  @Override
  public @NotNull String textRepresentation() {
    return myReferable.textRepresentation();
  }

  public TCDefReferable getDefinition() {
    return myDefinition;
  }

  public int getIndex() {
    return myIndex;
  }

  @Override
  public @NotNull Referable getUnderlyingReferable() {
    return myReferable;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ParameterReferable that = (ParameterReferable) o;
    return myIndex == that.myIndex && myDefinition.equals(that.myDefinition);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myDefinition, myIndex);
  }
}
