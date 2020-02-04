package org.arend.term.expr.visitor;

import org.arend.core.definition.FunctionDefinition;
import org.arend.core.expr.*;
import org.arend.frontend.reference.ConcreteLocatedReferable;
import org.arend.term.concrete.Concrete;
import org.arend.typechecking.TypeCheckingTestCase;
import org.arend.typechecking.visitor.CorrespondedSubExprVisitor;
import org.arend.util.Pair;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class CorrespondedSubExprTest extends TypeCheckingTestCase {
  @Test
  public void multiParamLam() {
    Concrete.LamExpression xyx = (Concrete.LamExpression) resolveNamesExpr("\\lam x y => x");
    Expression pi = typeCheckExpr(resolveNamesExpr("\\Pi (x y : \\Type) -> \\Type"), null).expression;
    Pair<Expression, Concrete.Expression> accept = xyx.accept(new CorrespondedSubExprVisitor(xyx.getBody()), typeCheckExpr(xyx, pi).expression);
    assertEquals("x", accept.proj1.cast(ReferenceExpression.class).getBinding().getName());
  }

  @Test
  public void simpleLam() {
    Concrete.LamExpression xx = (Concrete.LamExpression) resolveNamesExpr("\\lam x => x");
    Expression pi = typeCheckExpr(resolveNamesExpr("\\Pi (x : \\Type) -> \\Type"), null).expression;
    Pair<Expression, Concrete.Expression> accept = xx.accept(new CorrespondedSubExprVisitor(xx.getBody()), typeCheckExpr(xx, pi).expression);
    assertEquals("x", accept.proj1.cast(ReferenceExpression.class).getBinding().getName());
  }

  @Test
  public void simplePi() {
    Concrete.PiExpression xyx = (Concrete.PiExpression) resolveNamesExpr("\\Pi (A : \\Type) (x y : A) -> A");
    Expression pi = typeCheckExpr(xyx, null).expression;
    Pair<Expression, Concrete.Expression> accept = xyx.accept(new CorrespondedSubExprVisitor(xyx.getParameters().get(1).getType()), pi);
    assertEquals("A", accept.proj1.cast(ReferenceExpression.class).getBinding().getName());
  }

  @Test
  public void complexPi() {
    Concrete.PiExpression xyx = (Concrete.PiExpression) resolveNamesExpr("\\Pi (A B : \\Type) (x y : A) -> B");
    Expression pi = typeCheckExpr(xyx, null).expression;
    Pair<Expression, Concrete.Expression> accept = xyx.accept(new CorrespondedSubExprVisitor(xyx.getParameters().get(1).getType()), pi);
    assertEquals("A", accept.proj1.cast(ReferenceExpression.class).getBinding().getName());
  }

  @Test
  public void complexSigma() {
    Concrete.SigmaExpression xyx = (Concrete.SigmaExpression) resolveNamesExpr("\\Sigma (A B : \\Type) (x y : A) A");
    Expression sig = typeCheckExpr(xyx, null).expression;
    {
      Pair<Expression, Concrete.Expression> accept = xyx.accept(new CorrespondedSubExprVisitor(xyx.getParameters().get(1).getType()), sig);
      assertEquals("A", accept.proj1.cast(ReferenceExpression.class).getBinding().getName());
    }
    {
      Pair<Expression, Concrete.Expression> accept = xyx.accept(new CorrespondedSubExprVisitor(xyx.getParameters().get(2).getType()), sig);
      assertEquals("A", accept.proj1.cast(ReferenceExpression.class).getBinding().getName());
    }
  }

  @Test
  public void simpleAppExpr() {
    Concrete.LamExpression expr = (Concrete.LamExpression) resolveNamesExpr("\\lam {A : \\Type} (f : A -> A -> A) (a b : A) => f b a");
    Expression core = typeCheckExpr(expr, null).expression;
    Concrete.AppExpression body = (Concrete.AppExpression) expr.getBody();
    {
      Pair<Expression, Concrete.Expression> accept = expr.accept(new CorrespondedSubExprVisitor(body.getArguments().get(0).getExpression()), core);
      assertEquals("b", accept.proj1.cast(ReferenceExpression.class).getBinding().getName());
      assertEquals("b", accept.proj2.toString());
    }
    {
      Pair<Expression, Concrete.Expression> accept = expr.accept(new CorrespondedSubExprVisitor(body.getArguments().get(1).getExpression()), core);
      assertEquals("a", accept.proj1.cast(ReferenceExpression.class).getBinding().getName());
      assertEquals("a", accept.proj2.toString());
    }
    {
      Concrete.Expression make = Concrete.AppExpression.make(body.getData(), body.getFunction(), body.getArguments().get(0).getExpression(), true);
      Pair<Expression, Concrete.Expression> accept = expr.accept(new CorrespondedSubExprVisitor(make), core);
      // Selects the whole expression
      assertEquals("f b a", accept.proj1.toString());
      assertEquals("f b a", accept.proj2.toString());
    }
  }

  @Test
  public void infixDefCall() {
    // (1+3*2)-4
    Concrete.AppExpression expr = (Concrete.AppExpression) resolveNamesExpr("1 Nat.+ 3 Nat.* 2 Nat.- 4");
    Expression core = typeCheckExpr(expr, null).expression;
    Concrete.Expression sub = expr.getFunction();
    Concrete.Argument four = expr.getArguments().get(1);
    // 1+3*2
    Concrete.Argument arg1 = expr.getArguments().get(0);
    // 1+3*2
    Concrete.AppExpression expr2 = (Concrete.AppExpression) arg1.getExpression();
    Concrete.Expression add = expr2.getFunction();
    Concrete.Argument one = expr2.getArguments().get(0);
    // 3*2
    Concrete.Argument arg2 = expr2.getArguments().get(1);
    Concrete.AppExpression expr3 = (Concrete.AppExpression) arg2.getExpression();
    Concrete.Expression mul = expr3.getFunction();
    Concrete.Argument three = expr3.getArguments().get(0);
    Concrete.Argument two = expr3.getArguments().get(1);
    {
      // 3* -> 3*2
      Concrete.Expression make = Concrete.AppExpression.make(expr3.getData(), mul, three.getExpression(), true);
      Pair<Expression, Concrete.Expression> accept = expr.accept(new CorrespondedSubExprVisitor(make), core);
      assertEquals("3 * 2", accept.proj1.toString());
      assertEquals("3 * 2", accept.proj2.toString());
    }
    {
      // *2 -> 3*2
      Concrete.Expression make = Concrete.AppExpression.make(expr3.getData(), mul, two.getExpression(), true);
      Pair<Expression, Concrete.Expression> accept = expr.accept(new CorrespondedSubExprVisitor(make), core);
      assertEquals("3 * 2", accept.proj1.toString());
      assertEquals("3 * 2", accept.proj2.toString());
    }
    {
      // 2 -> 2
      Pair<Expression, Concrete.Expression> accept = expr.accept(new CorrespondedSubExprVisitor(two.getExpression()), core);
      assertEquals("2", accept.proj1.toString());
      assertEquals("2", accept.proj2.toString());
    }
    {
      // 3*2 -> 3*2
      Pair<Expression, Concrete.Expression> accept = expr.accept(new CorrespondedSubExprVisitor(expr3), core);
      assertEquals("3 * 2", accept.proj1.toString());
      assertEquals("3 * 2", accept.proj2.toString());
    }
    {
      // 1+ -> 1+3*2
      Concrete.Expression make = Concrete.AppExpression.make(expr3.getData(), add, one.getExpression(), true);
      Pair<Expression, Concrete.Expression> accept = expr.accept(new CorrespondedSubExprVisitor(make), core);
      assertEquals("1 + 3 * 2", accept.proj1.toString());
      assertEquals("1 + 3 * 2", accept.proj2.toString());
    }
  }

  // When an AppExpr applied to more arguments, then corresponding DefCall has.
  @Test
  public void defCallExtraArgs() {
    ConcreteLocatedReferable resolved = resolveNamesDef(
        "\\func test => f 11 45 14 \\where {\n" +
            "  \\open Nat\n" +
            "  \\func f (a b : Nat) => \\lam c => a + b + c\n" +
            "}");
    Concrete.FunctionDefinition concreteDef = (Concrete.FunctionDefinition) resolved.getDefinition();
    FunctionDefinition coreDef = (FunctionDefinition) typeCheckDef(resolved);
    Concrete.AppExpression concrete = (Concrete.AppExpression) concreteDef.getBody().getTerm();
    AppExpression body = (AppExpression) coreDef.getBody();
    assertNotNull(concrete);
    {
      Pair<Expression, Concrete.Expression> accept = concrete.accept(new CorrespondedSubExprVisitor(
          concrete.getArguments().get(2).getExpression()
      ), body);
      assertEquals("14", accept.proj1.toString());
    }
    {
      Pair<Expression, Concrete.Expression> accept = concrete.accept(new CorrespondedSubExprVisitor(
          concrete.getArguments().get(1).getExpression()
      ), body);
      assertEquals("45", accept.proj1.toString());
    }
  }

  // When it is applied to less arguments.
  @Test
  public void defCallLessArgs() {
    ConcreteLocatedReferable resolved = resolveNamesDef(
        "\\func test => f 114514 \\where {\n" +
            "  \\open Nat\n" +
            "  \\func f (a b : Nat) => a + b\n" +
            "}");
    Concrete.FunctionDefinition concreteDef = (Concrete.FunctionDefinition) resolved.getDefinition();
    FunctionDefinition coreDef = (FunctionDefinition) typeCheckDef(resolved);
    Concrete.AppExpression concrete = (Concrete.AppExpression) concreteDef.getBody().getTerm();
    LamExpression body = (LamExpression) coreDef.getBody();
    assertNotNull(concrete);
    Pair<Expression, Concrete.Expression> accept = concrete.accept(new CorrespondedSubExprVisitor(
        concrete.getArguments().get(0).getExpression()
    ), body);
    assertEquals("114514", accept.proj1.toString());
  }

  // Implicit arguments in core DefCall
  @Test
  public void defCallImplicitArgs() {
    ConcreteLocatedReferable resolved = resolveNamesDef(
        "\\func test => const 114 514 \\where {\n" +
            "  \\func const {A : \\Type} (a b : A) => a\n" +
            "}");
    Concrete.FunctionDefinition concreteDef = (Concrete.FunctionDefinition) resolved.getDefinition();
    FunctionDefinition coreDef = (FunctionDefinition) typeCheckDef(resolved);
    Concrete.AppExpression concrete = (Concrete.AppExpression) concreteDef.getBody().getTerm();
    DefCallExpression body = (DefCallExpression) coreDef.getBody();
    assertNotNull(concrete);
    {
      Pair<Expression, Concrete.Expression> accept = concrete.accept(new CorrespondedSubExprVisitor(
          concrete.getArguments().get(0).getExpression()
      ), body);
      assertEquals("114", accept.proj1.toString());
    }
    {
      Pair<Expression, Concrete.Expression> accept = concrete.accept(new CorrespondedSubExprVisitor(
          concrete.getArguments().get(1).getExpression()
      ), body);
      assertEquals("514", accept.proj1.toString());
    }
  }

  // Implicit arguments in core AppExpr
  @Test
  public void appExprImplicitArgs() {
    ConcreteLocatedReferable resolved = resolveNamesDef(
        "\\func test => const 114 514 \\where {\n" +
            "  \\func const => \\lam {A : \\Type} (a b : A) => a\n" +
            "}");
    Concrete.FunctionDefinition concreteDef = (Concrete.FunctionDefinition) resolved.getDefinition();
    FunctionDefinition coreDef = (FunctionDefinition) typeCheckDef(resolved);
    Concrete.AppExpression concrete = (Concrete.AppExpression) concreteDef.getBody().getTerm();
    AppExpression body = (AppExpression) coreDef.getBody();
    assertNotNull(concrete);
    {
      Pair<Expression, Concrete.Expression> accept = concrete.accept(new CorrespondedSubExprVisitor(
          concrete.getArguments().get(0).getExpression()
      ), body);
      assertEquals("114", accept.proj1.toString());
    }
    {
      Pair<Expression, Concrete.Expression> accept = concrete.accept(new CorrespondedSubExprVisitor(
          concrete.getArguments().get(1).getExpression()
      ), body);
      assertEquals("514", accept.proj1.toString());
    }
  }

  // \let expressions
  @Test
  public void letExpr() {
    Concrete.LetExpression concrete = (Concrete.LetExpression) resolveNamesExpr("\\let | x => 1 \\in x");
    LetExpression let = (LetExpression) typeCheckExpr(concrete, null).expression;
    Pair<Expression, Concrete.Expression> accept = concrete.accept(new CorrespondedSubExprVisitor(
        concrete.getClauses().get(0).getTerm()
    ), let);
    assertEquals("1", accept.proj1.toString());
  }

  // (Data type) implicit arguments in core ConCall
  @Test
  public void conCallImplicits() {
    ConcreteLocatedReferable resolved = resolveNamesDef(
        "\\func test : Fin 114 => con 514 \\where {\n" +
            "  \\data Fin (limit : Nat)\n" +
            "    | con Nat\n" +
            "}");
    Concrete.FunctionDefinition concreteDef = (Concrete.FunctionDefinition) resolved.getDefinition();
    FunctionDefinition coreDef = (FunctionDefinition) typeCheckDef(resolved);
    Concrete.AppExpression concrete = (Concrete.AppExpression) concreteDef.getBody().getTerm();
    ConCallExpression body = (ConCallExpression) coreDef.getBody();
    assertNotNull(concrete);
    {
      Pair<Expression, Concrete.Expression> accept = concrete.accept(new CorrespondedSubExprVisitor(
          concrete.getArguments().get(0).getExpression()
      ), body);
      assertEquals("514", accept.proj1.toString());
    }
  }

  @Test
  public void simpleClassCall() {
    ConcreteLocatedReferable resolved = resolveNamesDef(
        "\\func test => X { | A => Nat } \\where {\n" +
            "  \\class X (A : \\Type0) { }\n" +
            "}");
    Concrete.FunctionDefinition concreteDef = (Concrete.FunctionDefinition) resolved.getDefinition();
    FunctionDefinition coreDef = (FunctionDefinition) typeCheckDef(resolved);
    Concrete.ClassExtExpression concrete = (Concrete.ClassExtExpression) concreteDef.getBody().getTerm();
    ClassCallExpression core = (ClassCallExpression) coreDef.getBody();
    assertNotNull(concrete);
    assertNotNull(core);
    Pair<Expression, Concrete.Expression> accept = concrete.accept(new CorrespondedSubExprVisitor(
        concrete.getStatements().get(0).implementation
    ), core);
    assertEquals("Nat", accept.proj1.toString());
  }

  @Test
  public void nestedClassCall() {
    ConcreteLocatedReferable resolved = resolveNamesDef(
        "\\func test => Y { | A => Nat | C { | B => 114514 } } \\where {\n" +
            "  \\class X (A : \\Type0) {\n" +
            "    | B : Nat\n" +
            "  }\n" +
            "  \\class Y (A : \\Type0) {\n" +
            "    | C : X A\n" +
            "  }\n" +
            "}");
    Concrete.FunctionDefinition concreteDef = (Concrete.FunctionDefinition) resolved.getDefinition();
    FunctionDefinition coreDef = (FunctionDefinition) typeCheckDef(resolved);
    Concrete.ClassExtExpression concrete = (Concrete.ClassExtExpression) concreteDef.getBody().getTerm();
    ClassCallExpression core = (ClassCallExpression) coreDef.getBody();
    assertNotNull(concrete);
    assertNotNull(core);
    Concrete.NewExpression c = (Concrete.NewExpression) concrete.getStatements().get(1).implementation;
    Concrete.ClassExtExpression classExt = (Concrete.ClassExtExpression) c.getExpression();
    Concrete.Expression subExpr = classExt.getStatements().get(0).implementation;
    Pair<Expression, Concrete.Expression> accept = concrete.accept(new CorrespondedSubExprVisitor(subExpr), core);
    assertEquals("114514", accept.proj1.toString());
  }
}
