package org.arend.extImpl;

import org.arend.core.expr.Expression;
import org.arend.ext.concrete.ConcreteClause;
import org.arend.ext.concrete.expr.ConcreteArgument;
import org.arend.ext.concrete.expr.ConcreteCoclause;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.typechecking.ContextData;
import org.arend.term.concrete.Concrete;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ContextDataImpl implements ContextData {
  private final Concrete.Expression myExpression;
  private List<? extends ConcreteArgument> myArguments;
  private List<? extends ConcreteCoclause> myCoclauses;
  private List<? extends ConcreteClause> myClauses;
  private Expression myExpectedType;
  private Object myUserData;

  public ContextDataImpl(Concrete.Expression expression, List<? extends ConcreteArgument> arguments, List<? extends ConcreteCoclause> coclauses, List<? extends ConcreteClause> clauses, Expression expectedType, Object userData) {
    myExpression = expression;
    myArguments = arguments;
    myCoclauses = coclauses;
    myClauses = clauses;
    myExpectedType = expectedType;
    myUserData = userData;
  }

  @Override
  public Concrete.ReferenceExpression getReferenceExpression() {
    return myExpression instanceof Concrete.ReferenceExpression ? (Concrete.ReferenceExpression) myExpression : null;
  }

  @NotNull
  @Override
  public Concrete.Expression getMarker() {
    return myExpression;
  }

  @NotNull
  @Override
  public List<? extends ConcreteArgument> getArguments() {
    return myArguments;
  }

  @Override
  public void setArguments(@NotNull List<? extends ConcreteArgument> arguments) {
    myArguments = arguments;
  }

  @Override
  public @Nullable List<? extends ConcreteCoclause> getCoclauses() {
    return myCoclauses;
  }

  @Override
  public void setCoclauses(List<? extends ConcreteCoclause> coclauses) {
    myCoclauses = coclauses;
  }

  @Override
  public @Nullable List<? extends ConcreteClause> getClauses() {
    return myClauses;
  }

  @Override
  public void setClauses(@Nullable List<? extends ConcreteClause> clauses) {
    myClauses = clauses;
  }

  @Override
  public Expression getExpectedType() {
    return myExpectedType;
  }

  @Override
  public void setExpectedType(@Nullable CoreExpression expectedType) {
    if (!(expectedType instanceof Expression)) {
      throw new IllegalArgumentException();
    }
    myExpectedType = (Expression) expectedType;
  }

  @Override
  public Object getUserData() {
    return myUserData;
  }

  @Override
  public void setUserData(Object userData) {
    myUserData = userData;
  }
}
