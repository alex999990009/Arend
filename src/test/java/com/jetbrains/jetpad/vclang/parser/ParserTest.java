package com.jetbrains.jetpad.vclang.parser;

import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.term.Concrete;
import org.junit.Test;

import static com.jetbrains.jetpad.vclang.parser.ParserTestCase.*;
import static com.jetbrains.jetpad.vclang.term.expr.ExpressionFactory.*;
import static org.junit.Assert.*;

public class ParserTest {
  @Test
  public void parserLetToTheRight() {
    Concrete.Expression expr = parseExpr("\\lam x => \\let | x => Nat \\in x x");
    Concrete.Expression expr1 = parseExpr("\\let | x => Nat \\in \\lam x => x x");
    assertTrue(compare(Lam("x", Let(lets(let("x", lamArgs(), Nat())), Apps(Var("x"), Var("x")))), expr));
    assertTrue(compare(Let(lets(let("x", lamArgs(), Nat())), Lam("x", Apps(Var("x"), Var("x")))), expr1));
  }

  @Test
  public void parseLetMultiple() {
    Concrete.Expression expr = parseExpr("\\let | x => Nat | y => x \\in y");
    assertTrue(compare(Let(lets(let("x", Nat()), let("y", Var("x"))), Var("y")), expr));
  }

  @Test
  public void parseLetTyped() {
    Concrete.Expression expr = parseExpr("\\let | x : Nat => zero \\in x");
    assertTrue(compare(Let(lets(let("x", lamArgs(), Nat(), Abstract.Definition.Arrow.RIGHT, Zero())), Var("x")), expr));
  }

  @Test
  public void parserLam() {
    Concrete.Expression expr = parseExpr("\\lam x y z => y");
    assertTrue(compare(Lam("x", Lam("y", Lam("z", Var("y")))), expr));
  }

  @Test
  public void parserLam2() {
    Concrete.Expression expr = parseExpr("\\lam x y => (\\lam z w => y z) y");
    assertTrue(compare(Lam("x'", Lam("y'", Apps(Lam("z'", Lam("w'", Apps(Var("y"), Var("z")))), Var("y")))), expr));
  }

  @Test
  public void parserLamTele() {
    Concrete.Expression expr = parseExpr("\\lam p {x t : Nat} {y} (a : Nat -> Nat) => (\\lam (z w : Nat) => y z) y");
    assertTrue(compare(Lam(lamArgs(Name("p"), Tele(false, vars("x", "t"), Nat()), Name(false, "y"), Tele(vars("a"), Pi(Nat(), Nat()))), Apps(Lam(lamArgs(Tele(vars("z", "w"), Nat())), Apps(Var("y"), Var("z"))), Var("y"))), expr));
  }

  @Test
  public void parserPi() {
    Concrete.Expression expr = parseExpr("\\Pi (x y z : Nat) (w t : Nat -> Nat) -> \\Pi (a b : \\Pi (c : Nat) -> Nat c) -> Nat b y w");
    assertTrue(compare(Pi(args(Tele(vars("x", "y", "z"), Nat()), Tele(vars("w", "t"), Pi(Nat(), Nat()))), Pi(args(Tele(vars("a", "b"), Pi("c", Nat(), Apps(Nat(), Var("c"))))), Apps(Nat(), Var("b"), Var("y"), Var("w")))), expr));
  }

  @Test
  public void parserPi2() {
    Concrete.Expression expr = parseExpr("\\Pi (x y : Nat) (z : Nat x -> Nat y) -> Nat z y x");
    assertTrue(compare(Pi(args(Tele(vars("x", "y"), Nat()), Tele(vars("z"), Pi(Apps(Nat(), Var("x")), Apps(Nat(), Var("y"))))), Apps(Nat(), Var("z"), Var("y"), Var("x"))), expr));
  }

  @Test
  public void parserLamOpenError() {
    assertNotNull(parseExpr("\\lam x => (\\Pi (y : Nat) -> (\\lam y => y)) y"));
  }

  @Test
  public void parserPiOpenError() {
    assertNotNull(parseExpr("\\Pi (a b : Nat a) -> Nat a b"));
  }

  @Test
  public void parserImplicit() {
    Concrete.AbstractDefinition def = (Concrete.AbstractDefinition) parseDef("\\abstract f (x y : Nat) {z w : Nat} (t : Nat) {r : Nat} (A : Nat -> Nat -> Nat -> Nat -> Nat -> Nat -> \\Type0) : A x y z w t r");
    assertEquals(5, def.getArguments().size());
    assertTrue(def.getArguments().get(0).getExplicit());
    assertFalse(def.getArguments().get(1).getExplicit());
    assertTrue(def.getArguments().get(2).getExplicit());
    assertFalse(def.getArguments().get(3).getExplicit());
    assertTrue(compare(Nat(), ((Concrete.TypeArgument) def.getArguments().get(0)).getType()));
    assertTrue(compare(Nat(), ((Concrete.TypeArgument) def.getArguments().get(1)).getType()));
    assertTrue(compare(Nat(), ((Concrete.TypeArgument) def.getArguments().get(2)).getType()));
    assertTrue(compare(Nat(), ((Concrete.TypeArgument) def.getArguments().get(3)).getType()));
    assertTrue(compare(Apps(Var("A"), Var("x"), Var("y"), Var("z"), Var("w"), Var("t"), Var("r")), def.getResultType()));
  }

  @Test
  public void parserImplicit2() {
    Concrete.AbstractDefinition def = (Concrete.AbstractDefinition) parseDef("\\abstract f {x : Nat} (_ : Nat) {y z : Nat} (A : Nat -> Nat -> Nat -> \\Type0) (_ : A x y z) : Nat");
    assertEquals(5, def.getArguments().size());
    assertFalse(def.getArguments().get(0).getExplicit());
    assertTrue(def.getArguments().get(1).getExplicit());
    assertFalse(def.getArguments().get(2).getExplicit());
    assertTrue(def.getArguments().get(3).getExplicit());
    assertTrue(compare(Nat(), ((Concrete.TypeArgument) def.getArguments().get(0)).getType()));
    assertTrue(compare(Nat(), ((Concrete.TypeArgument) def.getArguments().get(1)).getType()));
    assertTrue(compare(Nat(), ((Concrete.TypeArgument) def.getArguments().get(2)).getType()));
    assertTrue(compare(Apps(Var("A"), Var("x"), Var("y"), Var("z")), ((Concrete.TypeArgument) def.getArguments().get(4)).getType()));
    assertTrue(compare(Nat(), def.getResultType()));
  }

  @Test
  public void parserCase() {
    parseExpr("\\case 2 | zero => zero | suc x' => x'");
  }

  @Test
  public void elimManyMistmatch() {
    parseExpr(
        "\\static \\data D Nat | D (suc n) => dsuc\n" +
        "\\static \\function tests (n : Nat) (d : D n) : Nat <= \\elim n d\n" +
          "| suc n => 0", 1);
  }
}
