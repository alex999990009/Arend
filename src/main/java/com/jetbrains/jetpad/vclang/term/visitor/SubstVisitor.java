package com.jetbrains.jetpad.vclang.term.visitor;

import com.jetbrains.jetpad.vclang.term.expr.*;

import static com.jetbrains.jetpad.vclang.term.expr.Expression.*;

public class SubstVisitor implements ExpressionVisitor<Expression> {
  private final Expression substExpr;
  private final int from;

  public SubstVisitor(Expression substExpr, int from) {
    this.substExpr = substExpr;
    this.from = from;
  }

  @Override
  public Expression visitApp(AppExpression expr) {
    return Apps(expr.getFunction().accept(this), expr.getArgument().accept(this));
  }

  @Override
  public Expression visitDefCall(DefCallExpression expr) {
    return expr;
  }

  @Override
  public Expression visitIndex(IndexExpression expr) {
    if (expr.getIndex() < from) return Index(expr.getIndex());
    if (expr.getIndex() == from) return substExpr;
    return Index(expr.getIndex() - 1);
  }

  @Override
  public Expression visitLam(LamExpression expr) {
    return Lam(expr.getVariable(), expr.getBody().subst(substExpr.liftIndex(0, 1), from + 1));
  }

  @Override
  public Expression visitNat(NatExpression expr) {
    return expr;
  }

  @Override
  public Expression visitNelim(NelimExpression expr) {
    return expr;
  }

  @Override
  public Expression visitPi(PiExpression expr) {
    return Pi(expr.isExplicit(), expr.getVariable(), expr.getDomain().accept(this), expr.getCodomain().subst(substExpr, from + 1));
  }

  @Override
  public Expression visitSuc(SucExpression expr) {
    return expr;
  }

  @Override
  public Expression visitUniverse(UniverseExpression expr) {
    return expr;
  }

  @Override
  public Expression visitVar(VarExpression expr) {
    return expr;
  }

  @Override
  public Expression visitZero(ZeroExpression expr) {
    return expr;
  }

  @Override
  public Expression visitHole(HoleExpression expr) {
    return expr.getInstance(expr.expression() == null ? null : expr.expression().accept(this));
  }
}
