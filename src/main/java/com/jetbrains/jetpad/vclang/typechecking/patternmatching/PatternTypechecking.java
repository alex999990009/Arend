package com.jetbrains.jetpad.vclang.typechecking.patternmatching;

import com.jetbrains.jetpad.vclang.core.context.Utils;
import com.jetbrains.jetpad.vclang.core.context.binding.Binding;
import com.jetbrains.jetpad.vclang.core.context.param.DependentLink;
import com.jetbrains.jetpad.vclang.core.context.param.EmptyDependentLink;
import com.jetbrains.jetpad.vclang.core.context.param.TypedDependentLink;
import com.jetbrains.jetpad.vclang.core.definition.Constructor;
import com.jetbrains.jetpad.vclang.core.expr.*;
import com.jetbrains.jetpad.vclang.core.expr.visitor.NormalizeVisitor;
import com.jetbrains.jetpad.vclang.core.pattern.*;
import com.jetbrains.jetpad.vclang.core.subst.ExprSubstitution;
import com.jetbrains.jetpad.vclang.core.subst.LevelSubstitution;
import com.jetbrains.jetpad.vclang.core.subst.StdLevelSubstitution;
import com.jetbrains.jetpad.vclang.error.Error;
import com.jetbrains.jetpad.vclang.error.doc.DocFactory;
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable;
import com.jetbrains.jetpad.vclang.naming.reference.Referable;
import com.jetbrains.jetpad.vclang.naming.reference.UnresolvedReference;
import com.jetbrains.jetpad.vclang.prelude.Prelude;
import com.jetbrains.jetpad.vclang.term.concrete.Concrete;
import com.jetbrains.jetpad.vclang.typechecking.error.LocalErrorReporter;
import com.jetbrains.jetpad.vclang.typechecking.error.local.DataTypeNotEmptyError;
import com.jetbrains.jetpad.vclang.typechecking.error.local.ExpectedConstructor;
import com.jetbrains.jetpad.vclang.typechecking.error.local.TypeMismatchError;
import com.jetbrains.jetpad.vclang.typechecking.error.local.TypecheckingError;
import com.jetbrains.jetpad.vclang.typechecking.visitor.CheckTypeVisitor;
import com.jetbrains.jetpad.vclang.util.Pair;

import java.util.*;

public class PatternTypechecking {
  private final LocalErrorReporter myErrorReporter;
  private final EnumSet<Flag> myFlags;
  private Map<Referable, Binding> myContext;

  public enum Flag { ALLOW_INTERVAL, ALLOW_CONDITIONS, CHECK_COVERAGE, CONTEXT_FREE }

  public PatternTypechecking(LocalErrorReporter errorReporter, EnumSet<Flag> flags) {
    myErrorReporter = errorReporter;
    myFlags = flags;
  }

  private void collectBindings(List<Pattern> patterns, Collection<? super DependentLink> bindings) {
    for (Pattern pattern : patterns) {
      if (pattern instanceof BindingPattern) {
        bindings.add(((BindingPattern) pattern).getBinding());
      } else if (pattern instanceof ConstructorPattern) {
        collectBindings(((ConstructorPattern) pattern).getArguments(), bindings);
      }
    }
  }

  Pair<List<Pattern>, CheckTypeVisitor.Result> typecheckClause(Concrete.FunctionClause clause, List<? extends Concrete.Parameter> abstractParameters, DependentLink parameters, List<DependentLink> elimParams, Expression expectedType, CheckTypeVisitor visitor) {
    try (Utils.SetContextSaver<Referable> ignored = new Utils.SetContextSaver<>(visitor.getContext())) {
      // Typecheck patterns
      Pair<List<Pattern>, List<Expression>> result = typecheckPatterns(clause.getPatterns(), abstractParameters, parameters, elimParams, clause, visitor);
      if (result == null) {
        return null;
      }

      // If we have the absurd pattern, then RHS is ignored
      if (result.proj2 == null) {
        if (clause.getExpression() != null) {
          myErrorReporter.report(new TypecheckingError(Error.Level.WARNING, "The RHS is ignored", clause.getExpression()));
        }
        return new Pair<>(result.proj1, null);
      } else {
        if (clause.getExpression() == null) {
          myErrorReporter.report(new TypecheckingError("Required a RHS", clause));
          return null;
        }
      }

      ExprSubstitution substitution = new ExprSubstitution();
      for (Expression expr : result.proj2) {
        substitution.add(parameters, expr);
        parameters = parameters.getNext();
      }
      for (Map.Entry<Referable, Binding> entry : myContext.entrySet()) {
        Expression expr = substitution.get(entry.getValue());
        if (expr != null) {
          entry.setValue(expr.cast(ReferenceExpression.class).getBinding());
        }
      }
      expectedType = expectedType.subst(substitution);

      // Typecheck the RHS
      CheckTypeVisitor.Result tcResult;
      if (abstractParameters != null) {
        tcResult = visitor.finalCheckExpr(clause.getExpression(), expectedType, false);
      } else {
        tcResult = visitor.checkExpr(clause.getExpression(), expectedType);
      }
      return tcResult == null ? null : new Pair<>(result.proj1, tcResult);
    }
  }

  public Pair<List<Pattern>, List<Expression>> typecheckPatterns(List<? extends Concrete.Pattern> patterns, List<? extends Concrete.Parameter> abstractParameters, DependentLink parameters, List<DependentLink> elimParams, Concrete.SourceNode sourceNode, CheckTypeVisitor visitor) {
    myContext = visitor.getContext();
    if (myFlags.contains(Flag.CONTEXT_FREE)) {
      myContext.clear();
    }

    // Typecheck patterns
    Pair<List<Pattern>, List<Expression>> result;
    if (!elimParams.isEmpty()) {
      // Put patterns in the correct order
      // If some parameters are not eliminated (i.e. absent in elimParams), then we put null in corresponding patterns
      List<Concrete.Pattern> patterns1 = new ArrayList<>();
      for (DependentLink link = parameters; link.hasNext(); link = link.getNext()) {
        int index = elimParams.indexOf(link);
        patterns1.add(index < 0 ? null : patterns.get(index));
      }
      result = doTypechecking(patterns1, DependentLink.Helper.subst(parameters, new ExprSubstitution()), sourceNode, true);
    } else {
      result = doTypechecking(patterns, DependentLink.Helper.subst(parameters, new ExprSubstitution()), sourceNode, false);
    }

    // Compute the context and the set of free bindings for CheckTypeVisitor
    if (result != null && result.proj2 != null && abstractParameters != null) {
      int i = 0;
      DependentLink link = parameters;
      if (!elimParams.isEmpty()) {
        for (Concrete.Parameter parameter : abstractParameters) {
          if (parameter instanceof Concrete.TelescopeParameter) {
            for (Referable referable : ((Concrete.TelescopeParameter) parameter).getReferableList()) {
              if (!elimParams.contains(link)) {
                myContext.put(referable, ((BindingPattern) result.proj1.get(i)).getBinding());
              }
              link = link.getNext();
              i++;
            }
          } else if (parameter instanceof Concrete.NameParameter) {
            if (!elimParams.contains(link)) {
              myContext.put(((Concrete.NameParameter) parameter).getReferable(), ((BindingPattern) result.proj1.get(i)).getBinding());
            }
            link = link.getNext();
            i++;
          }
        }
      }

      if (myFlags.contains(Flag.CONTEXT_FREE)) {
        visitor.getFreeBindings().clear();
      }
      collectBindings(result.proj1, visitor.getFreeBindings());
    }

    return result;
  }

  Pair<List<Pattern>, Map<Referable, Binding>> typecheckPatterns(List<? extends Concrete.Pattern> patterns, DependentLink parameters, Concrete.SourceNode sourceNode, @SuppressWarnings("SameParameterValue") boolean fullList) {
    myContext = new HashMap<>();
    Pair<List<Pattern>, List<Expression>> result = doTypechecking(patterns, parameters, sourceNode, fullList);
    return result == null ? null : new Pair<>(result.proj1, result.proj2 == null ? null : myContext);
  }

  private static DependentLink getFirstBinding(List<Pattern> patterns) {
    for (Pattern pattern : patterns) {
      if (pattern instanceof EmptyPattern) {
        return EmptyDependentLink.getInstance();
      }
      if (pattern instanceof BindingPattern) {
        return ((BindingPattern) pattern).getBinding();
      } else if (pattern instanceof ConstructorPattern) {
        DependentLink last = getFirstBinding(((ConstructorPattern) pattern).getArguments());
        if (last != null) {
          return last;
        }
      }
    }
    return null;
  }

  private Pair<List<Pattern>, List<Expression>> doTypechecking(List<? extends Concrete.Pattern> patterns, DependentLink parameters, Concrete.SourceNode sourceNode, boolean fullList) {
    List<Pattern> result = new ArrayList<>();
    List<Expression> exprs = new ArrayList<>();

    for (Concrete.Pattern pattern : patterns) {
      if (!parameters.hasNext()) {
        myErrorReporter.report(new TypecheckingError("Too many patterns", pattern));
        return null;
      }

      if (!fullList && pattern != null) {
        if (pattern.isExplicit()) {
          while (!parameters.isExplicit()) {
            result.add(new BindingPattern(parameters));
            if (exprs != null) {
              exprs.add(new ReferenceExpression(parameters));
            }
            parameters = parameters.getNext();
            if (!parameters.hasNext()) {
              myErrorReporter.report(new TypecheckingError("Too many patterns", pattern));
              return null;
            }
          }
        } else {
          if (parameters.isExplicit()) {
            myErrorReporter.report(new TypecheckingError("Expected an explicit pattern", pattern));
            return null;
          }
        }
      }

      if (exprs == null || pattern == null || pattern instanceof Concrete.NamePattern) {
        if (!(pattern == null || pattern instanceof Concrete.NamePattern)) {
          myErrorReporter.report(new TypecheckingError(Error.Level.WARNING, "This pattern is ignored", pattern));
        }
        Referable referable = null;
        if (pattern instanceof Concrete.NamePattern) {
          referable = ((Concrete.NamePattern) pattern).getReferable();
          String name = referable == null ? null : referable.textRepresentation();
          if (name != null) {
            parameters.setName(name);
          }
        }
        result.add(new BindingPattern(parameters));
        if (exprs != null) {
          exprs.add(new ReferenceExpression(parameters));
          if (pattern != null) {
            myContext.put(referable, parameters);
          }
        }
        parameters = parameters.getNext();
        continue;
      }

      Expression expr = parameters.getTypeExpr().normalize(NormalizeVisitor.Mode.WHNF);
      if (pattern instanceof Concrete.TuplePattern) {
        List<Concrete.Pattern> patternArgs = ((Concrete.TuplePattern) pattern).getPatterns();
        // Either sigma or class patterns
        if (expr.isInstance(SigmaExpression.class) || expr.isInstance(ClassCallExpression.class)) {
          DependentLink newParameters = expr.isInstance(SigmaExpression.class) ? expr.cast(SigmaExpression.class).getParameters() : expr.cast(ClassCallExpression.class).getClassFieldParameters();
          Pair<List<Pattern>, List<Expression>> conResult = doTypechecking(((Concrete.TuplePattern) pattern).getPatterns(), newParameters, pattern, false);
          if (conResult == null) {
            return null;
          }

          ConstructorPattern newPattern = expr.isInstance(SigmaExpression.class)
            ? new ConstructorPattern(expr.cast(SigmaExpression.class), new Patterns(conResult.proj1))
            : new ConstructorPattern(expr.cast(ClassCallExpression.class), new Patterns(conResult.proj1));
          result.add(newPattern);
          if (conResult.proj2 == null) {
            exprs = null;
            parameters = parameters.getNext();
          } else {
            Expression newExpr = newPattern.toExpression(conResult.proj2);
            exprs.add(newExpr);
            parameters = DependentLink.Helper.subst(parameters.getNext(), new ExprSubstitution(parameters, newExpr));
          }

          continue;
        } else {
          if (!patternArgs.isEmpty()) {
            if (!expr.isError()) {
              myErrorReporter.report(new TypeMismatchError(DocFactory.text("a Sigma-type or a class"), expr, pattern));
            }
            return null;
          }
          if (!expr.isInstance(DataCallExpression.class)) {
            if (!expr.isError()) {
              myErrorReporter.report(new TypeMismatchError(DocFactory.text("a data type, a Sigma-type, or a class"), expr, pattern));
            }
            return null;
          }
        }
      }

      // Constructor patterns
      if (!expr.isInstance(DataCallExpression.class)) {
        if (!expr.isError()) {
          myErrorReporter.report(new TypeMismatchError(DocFactory.text("a data type"), expr, pattern));
        }
        return null;
      }
      DataCallExpression dataCall = expr.cast(DataCallExpression.class);
      if (!myFlags.contains(Flag.ALLOW_INTERVAL) && dataCall.getDefinition() == Prelude.INTERVAL) {
        myErrorReporter.report(new TypecheckingError("Pattern matching on the interval is not allowed here", pattern));
        return null;
      }

      // Empty pattern
      if (pattern instanceof Concrete.TuplePattern) {
        List<ConCallExpression> conCalls = dataCall.getMatchedConstructors();
        if (conCalls == null) {
          myErrorReporter.report(new TypecheckingError("Elimination is not possible here, cannot determine the set of eligible constructors", pattern));
          return null;
        }
        if (!conCalls.isEmpty()) {
          List<Constructor> constructors = new ArrayList<>(conCalls.size());
          for (ConCallExpression conCall : conCalls) {
            constructors.add(conCall.getDefinition());
          }
          myErrorReporter.report(new DataTypeNotEmptyError(dataCall, constructors, pattern));
          return null;
        }
        result.add(EmptyPattern.INSTANCE);
        exprs = null;
        parameters = parameters.getNext();
        continue;
      }

      if (!(pattern instanceof Concrete.ConstructorPattern)) {
        throw new IllegalStateException();
      }
      Concrete.ConstructorPattern conPattern = (Concrete.ConstructorPattern) pattern;

      Constructor constructor = conPattern.getConstructor() instanceof GlobalReferable ? dataCall.getDefinition().getConstructor((GlobalReferable) conPattern.getConstructor()) : null;
      List<ConCallExpression> conCalls = new ArrayList<>(1);
      if (constructor == null || !dataCall.getMatchedConCall(constructor, conCalls) || conCalls.isEmpty() ) {
        if (!(conPattern.getConstructor() instanceof UnresolvedReference)) {
          myErrorReporter.report(new ExpectedConstructor(conPattern.getConstructor(), dataCall, pattern));
        }
        return null;
      }
      ConCallExpression conCall = conCalls.get(0);
      if (!myFlags.contains(Flag.ALLOW_CONDITIONS) && conCall.getDefinition().getBody() != null) {
        myErrorReporter.report(new TypecheckingError("Pattern matching on a constructor with conditions is not allowed here", pattern));
        return null;
      }

      ExprSubstitution substitution = new ExprSubstitution();
      int i = 0;
      for (DependentLink link = constructor.getDataTypeParameters(); link.hasNext(); link = link.getNext(), i++) {
        substitution.add(link, conCall.getDataTypeArguments().get(i));
      }
      Pair<List<Pattern>, List<Expression>> conResult = doTypechecking(conPattern.getPatterns(), DependentLink.Helper.subst(constructor.getParameters(), substitution, new StdLevelSubstitution(conCall.getSortArgument())), conPattern, false);
      if (conResult == null) {
        return null;
      }

      result.add(new ConstructorPattern(conCall, new Patterns(conResult.proj1)));
      if (conResult.proj2 == null) {
        exprs = null;
        parameters = parameters.getNext();
      } else {
        Expression newConCall = ConCallExpression.make(conCall.getDefinition(), conCall.getSortArgument(), conCall.getDataTypeArguments(), conResult.proj2);
        exprs.add(newConCall);
        parameters = DependentLink.Helper.subst(parameters.getNext(), new ExprSubstitution(parameters, newConCall));
      }
    }

    if (!fullList) {
      while (!parameters.isExplicit()) {
        result.add(new BindingPattern(parameters));
        if (exprs != null) {
          exprs.add(new ReferenceExpression(parameters));
        }
        parameters = parameters.getNext();
      }
    }

    if (parameters.hasNext()) {
      myErrorReporter.report(new TypecheckingError("Not enough patterns, expected " + DependentLink.Helper.size(parameters) + " more", sourceNode));
      return null;
    }

    ExprSubstitution substitution = new ExprSubstitution();
    fixPatterns(result, substitution);
    if (!substitution.isEmpty()) {
      if (exprs != null) {
        for (int i = 0; i < exprs.size(); i++) {
          exprs.set(i, exprs.get(i).subst(substitution));
        }
      }

      for (Map.Entry<Referable, Binding> entry : myContext.entrySet()) {
        Expression expr = substitution.get(entry.getValue());
        if (expr != null) {
          entry.setValue(((ReferenceExpression) expr).getBinding());
        }
      }

      DependentLink link = getFirstBinding(result);
      if (link != null && link.hasNext()) {
        link = link.getNext();
        for (; link.hasNext(); link = link.getNext()) {
          link = link.getNextTyped(null);
          link.setType(link.getType().subst(substitution, LevelSubstitution.EMPTY));
        }
      }
    }

    return new Pair<>(result, exprs);
  }

  // Chains the bindings in the leaves of patterns
  private void fixPatterns(List<Pattern> patterns, ExprSubstitution substitution) {
    List<DependentLink> leaves = new ArrayList<>();
    getLeaves(patterns, leaves);

    for (int i = 0; i < leaves.size(); i++) {
      DependentLink next = i < leaves.size() - 1 ? leaves.get(i + 1) : EmptyDependentLink.getInstance();
      DependentLink leaf = leaves.get(i);
      if (leaf.getNext() == next) {
        continue;
      }
      if (leaf instanceof TypedDependentLink) {
        leaf.setNext(next);
      } else {
        TypedDependentLink newLeaf = new TypedDependentLink(leaf.isExplicit(), leaf.getName(), leaf.getType(), next);
        substitution.add(leaf, new ReferenceExpression(newLeaf));
        leaves.set(i, newLeaf);
        if (i > 0) {
          leaves.get(i - 1).setNext(newLeaf);
        }
      }
    }

    if (!substitution.isEmpty()) {
      setLeaves(patterns, leaves, 0);
    }
  }

  private void getLeaves(List<Pattern> patterns, List<DependentLink> leaves) {
    for (Pattern pattern : patterns) {
      if (pattern instanceof ConstructorPattern) {
        getLeaves(((ConstructorPattern) pattern).getArguments(), leaves);
      } else if (pattern instanceof BindingPattern) {
        leaves.add(((BindingPattern) pattern).getBinding());
      }
    }
  }

  private int setLeaves(List<Pattern> patterns, List<DependentLink> leaves, int j) {
    for (int i = 0; i < patterns.size(); i++) {
      Pattern pattern = patterns.get(i);
      if (pattern instanceof BindingPattern) {
        patterns.set(i, new BindingPattern(leaves.get(j++)));
      } else if (pattern instanceof ConstructorPattern) {
        j = setLeaves(((ConstructorPattern) pattern).getArguments(), leaves, j);
      }
    }
    return j;
  }
}
