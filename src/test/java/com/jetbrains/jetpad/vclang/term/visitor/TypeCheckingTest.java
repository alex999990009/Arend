package com.jetbrains.jetpad.vclang.term.visitor;

import com.jetbrains.jetpad.vclang.term.definition.Binding;
import com.jetbrains.jetpad.vclang.term.definition.Definition;
import com.jetbrains.jetpad.vclang.term.definition.FunctionDefinition;
import com.jetbrains.jetpad.vclang.term.definition.Signature;
import com.jetbrains.jetpad.vclang.term.error.TypeCheckingError;
import com.jetbrains.jetpad.vclang.term.error.TypeMismatchError;
import com.jetbrains.jetpad.vclang.term.expr.Expression;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.jetbrains.jetpad.vclang.term.expr.ExpressionFactory.*;
import static org.junit.Assert.*;

public class TypeCheckingTest {
  @Test
  public void typeCheckingLam() {
    // \x. x : N -> N
    Expression expr = Lam("x", Var("x"));
    List<TypeCheckingError> errors = new ArrayList<>();
    expr.checkType(new HashMap<String, Definition>(), new ArrayList<Binding>(), Pi(Nat(), Nat()), errors);
    assertEquals(0, errors.size());
  }

  @Test
  public void typeCheckingLamIndex() {
    // \x. x : N -> N
    Expression expr = Lam("x", Index(0));
    List<TypeCheckingError> errors = new ArrayList<>();
    expr.checkType(new HashMap<String, Definition>(), new ArrayList<Binding>(), Pi(Nat(), Nat()), errors);
    assertEquals(0, errors.size());
  }

  @Test
  public void typeCheckingLamError() {
    // \x. x : N -> N -> N
    Expression expr = Lam("x", Index(0));
    List<TypeCheckingError> errors = new ArrayList<>();
    assertEquals(null, expr.checkType(new HashMap<String, Definition>(), new ArrayList<Binding>(), Pi(Nat(), Pi(Nat(), Nat())), errors));
    assertEquals(1, errors.size());
    assertTrue(errors.get(0) instanceof TypeMismatchError);
  }

  @Test
  public void typeCheckingId() {
    // \X x. x : (X : Type0) -> X -> X
    Expression expr = Lam("X", Lam("x", Var("x")));
    Expression type = Pi("X", Universe(0), Pi(Index(0), Index(0)));
    List<TypeCheckingError> errors = new ArrayList<>();
    expr.checkType(new HashMap<String, Definition>(), new ArrayList<Binding>(), type, errors);
    assertEquals(0, errors.size());
  }

  @Test
  public void typeCheckingIdIndex() {
    // \X x. x : (X : Type0) -> X -> X
    Expression expr = Lam("X", Lam("x", Index(0)));
    Expression type = Pi("X", Universe(0), Pi(Index(0), Index(0)));
    List<TypeCheckingError> errors = new ArrayList<>();
    expr.checkType(new HashMap<String, Definition>(), new ArrayList<Binding>(), type, errors);
    assertEquals(0, errors.size());
  }

  @Test
  public void typeCheckingIdError() {
    // \X x. X : (X : Type0) -> X -> X
    Expression expr = Lam("X", Lam("x", Var("X")));
    Expression type = Pi("X", Universe(0), Pi(Index(0), Index(0)));
    List<TypeCheckingError> errors = new ArrayList<>();
    assertEquals(null, expr.checkType(new HashMap<String, Definition>(), new ArrayList<Binding>(), type, errors));
    assertEquals(1, errors.size());
    assertTrue(errors.get(0) instanceof TypeMismatchError);
  }

  @Test
  public void typeCheckingApp() {
    // \x y. y (y x) : N -> (N -> N) -> N
    Expression expr = Lam("x", Lam("y", Apps(Var("y"), Apps(Var("y"), Var("x")))));
    List<TypeCheckingError> errors = new ArrayList<>();
    expr.checkType(new HashMap<String, Definition>(), new ArrayList<Binding>(), Pi(Nat(), Pi(Pi(Nat(), Nat()), Nat())), errors);
    assertEquals(0, errors.size());
  }

  @Test
  public void typeCheckingAppIndex() {
    // \x y. y (y x) : N -> (N -> N) -> N
    Expression expr = Lam("x", Lam("y", Apps(Index(0), Apps(Index(0), Index(1)))));
    List<TypeCheckingError> errors = new ArrayList<>();
    expr.checkType(new HashMap<String, Definition>(), new ArrayList<Binding>(), Pi(Nat(), Pi(Pi(Nat(), Nat()), Nat())), errors);
    assertEquals(0, errors.size());
  }

  @Test
  public void typeCheckingAppPi() {
    // \f g. g zero (f zero) : (f : (x : N) -> N x) -> ((x : N) -> N x -> N (f x)) -> N (f zero)
    Expression expr = Lam("f", Lam("g", Apps(Var("g"), Zero(), Apps(Var("f"), Zero()))));
    Expression type = Pi("f", Pi("x", Nat(), Apps(Nat(), Index(0))), Pi(Pi("x", Nat(), Pi(Apps(Nat(), Index(0)), Apps(Nat(), Apps(Index(1), Index(0))))), Apps(Nat(), Apps(Index(0), Zero()))));
    List<TypeCheckingError> errors = new ArrayList<>();
    expr.checkType(new HashMap<String, Definition>(), new ArrayList<Binding>(), type, errors);
    assertEquals(0, errors.size());
  }

  @Test
  public void typeCheckingAppPiIndex() {
    // \f g. g zero (f zero) : (f : (x : N) -> N x) -> ((x : N) -> N x -> N (f x)) -> N (f zero)
    Expression expr = Lam("f", Lam("g", Apps(Index(0), Zero(), Apps(Index(1), Zero()))));
    Expression type = Pi("f", Pi("x", Nat(), Apps(Nat(), Index(0))), Pi(Pi("x", Nat(), Pi(Apps(Nat(), Index(0)), Apps(Nat(), Apps(Index(1), Index(0))))), Apps(Nat(), Apps(Index(0), Zero()))));
    List<TypeCheckingError> errors = new ArrayList<>();
    expr.checkType(new HashMap<String, Definition>(), new ArrayList<Binding>(), type, errors);
    assertEquals(0, errors.size());
  }

  @Test
  public void typeCheckingAppLamPi() {
    // \f h. h (\k -> k (suc zero)) : (f : (g : N -> N) -> N (g zero)) -> ((z : (N -> N) -> N) -> N (f (\x. z (\_. x)))) -> N (f (\x. x))
    Expression expr = Lam("f", Lam("h", Apps(Var("h"), Lam("k", Apps(Var("k"), Apps(Suc(), Zero()))))));
    Expression type = Pi("f", Pi("g", Pi(Nat(), Nat()), Apps(Nat(), Apps(Index(0), Zero()))), Pi(Pi("z", Pi(Pi(Nat(), Nat()), Nat()), Apps(Nat(), Apps(Index(1), Lam("x", Apps(Index(1), Lam("_", Index(1))))))), Apps(Nat(), Apps(Index(0), Lam("x", Index(0))))));
    List<TypeCheckingError> errors = new ArrayList<>();
    expr.checkType(new HashMap<String, Definition>(), new ArrayList<Binding>(), type, errors);
    assertEquals(0, errors.size());
  }

  @Test
  public void typeCheckingAppLamPiIndex() {
    // \f h. h (\k -> k (suc zero)) : (f : (g : N -> N) -> N (g zero)) -> ((z : (N -> N) -> N) -> N (f (\x. z (\_. x)))) -> N (f (\x. x))
    Expression expr = Lam("f", Lam("h", Apps(Index(0), Lam("k", Apps(Index(0), Apps(Suc(), Zero()))))));
    Expression type = Pi("f", Pi("g", Pi(Nat(), Nat()), Apps(Nat(), Apps(Index(0), Zero()))), Pi(Pi("z", Pi(Pi(Nat(), Nat()), Nat()), Apps(Nat(), Apps(Index(1), Lam("x", Apps(Index(1), Lam("_", Index(1))))))), Apps(Nat(), Apps(Index(0), Lam("x", Index(0))))));
    List<TypeCheckingError> errors = new ArrayList<>();
    expr.checkType(new HashMap<String, Definition>(), new ArrayList<Binding>(), type, errors);
    assertEquals(0, errors.size());
  }

  @Test
  public void typeCheckingInferPi() {
    // (X : Type1) -> X -> X : Type2
    Expression expr = Pi("X", Universe(1), Pi(Var("X"), Var("X")));
    List<TypeCheckingError> errors = new ArrayList<>();
    assertEquals(Universe(2), expr.checkType(new HashMap<String, Definition>(), new ArrayList<Binding>(), null, errors).type);
    assertEquals(0, errors.size());
  }

  @Test
  public void typeCheckingInferPiIndex() {
    // (X : Type1) -> X -> X : Type2
    Expression expr = Pi("X", Universe(1), Pi(Index(0), Index(0)));
    List<TypeCheckingError> errors = new ArrayList<>();
    assertEquals(Universe(2), expr.checkType(new HashMap<String, Definition>(), new ArrayList<Binding>(), null, errors).type);
    assertEquals(0, errors.size());
  }

  @Test
  public void typeCheckingUniverse() {
    // (f : Type1 -> Type1) -> f Type1
    Expression expr = Pi("f", Pi(Universe(1), Universe(1)), Apps(Var("f"), Universe(1)));
    List<TypeCheckingError> errors = new ArrayList<>();
    assertEquals(null, expr.checkType(new HashMap<String, Definition>(), new ArrayList<Binding>(), null, errors));
    assertEquals(1, errors.size());
    assertTrue(errors.get(0) instanceof TypeMismatchError);
  }

  @Test
  public void typeCheckingTwoErrors() {
    // f : Nat -> Nat -> Nat |- f S (f 0 S) : Nat
    Expression expr = Apps(Index(0), Suc(), Apps(Index(0), Zero(), Suc()));
    List<Binding> defs = new ArrayList<>();
    defs.add(new FunctionDefinition("f", new Signature(Pi(Nat(), Pi(Nat(), Nat()))), Var("f")));

    List<TypeCheckingError> errors = new ArrayList<>();
    CheckTypeVisitor.OKResult result = expr.checkType(new HashMap<String, Definition>(), defs, null, errors);
    assertEquals(2, errors.size());
    assertNull(result);
  }

  @Test
  public void typedLambda() {
    // \x:Nat. x : Nat -> Nat
    Expression expr = Lam("x", Index(0));
    List<TypeCheckingError> errors = new ArrayList<>();
    assertEquals(Pi(Nat(), Nat()), expr.checkType(new HashMap<String, Definition>(), new ArrayList<Binding>(), null, errors).type);
    assertEquals(0, errors.size());
  }
}
