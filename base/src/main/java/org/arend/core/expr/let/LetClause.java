package org.arend.core.expr.let;

import org.arend.core.context.binding.EvaluatingBinding;
import org.arend.core.expr.Expression;
import org.arend.core.subst.SubstVisitor;

public class LetClause extends HaveClause implements EvaluatingBinding {
  private LetClause(String name, LetClausePattern pattern, Expression expression) {
    super(name, pattern, expression);
  }

  public static HaveClause make(boolean isLet, String name, LetClausePattern pattern, Expression expression) {
    return isLet ? new LetClause(name, pattern, expression) : new HaveClause(name, pattern, expression);
  }

  @Override
  public Expression subst(SubstVisitor visitor) {
    return getExpression().accept(visitor, null);
  }
}
