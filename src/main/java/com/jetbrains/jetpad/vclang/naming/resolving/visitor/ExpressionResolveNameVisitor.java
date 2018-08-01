package com.jetbrains.jetpad.vclang.naming.resolving.visitor;

import com.jetbrains.jetpad.vclang.core.context.Utils;
import com.jetbrains.jetpad.vclang.error.Error;
import com.jetbrains.jetpad.vclang.frontend.reference.TypeClassReferenceExtractVisitor;
import com.jetbrains.jetpad.vclang.naming.BinOpParser;
import com.jetbrains.jetpad.vclang.naming.error.DuplicateNameError;
import com.jetbrains.jetpad.vclang.naming.error.NamingError;
import com.jetbrains.jetpad.vclang.naming.reference.*;
import com.jetbrains.jetpad.vclang.naming.scope.ClassFieldImplScope;
import com.jetbrains.jetpad.vclang.naming.scope.ListScope;
import com.jetbrains.jetpad.vclang.naming.scope.MergeScope;
import com.jetbrains.jetpad.vclang.naming.scope.Scope;
import com.jetbrains.jetpad.vclang.term.concrete.BaseConcreteExpressionVisitor;
import com.jetbrains.jetpad.vclang.term.concrete.Concrete;
import com.jetbrains.jetpad.vclang.typechecking.error.LocalErrorReporter;
import com.jetbrains.jetpad.vclang.typechecking.typecheckable.provider.ConcreteProvider;

import java.util.*;

public class ExpressionResolveNameVisitor extends BaseConcreteExpressionVisitor<Void> {
  private final TypeClassReferenceExtractVisitor myTypeClassReferenceExtractVisitor;
  private final Scope myParentScope;
  private final Scope myScope;
  private final List<Referable> myContext;
  private final LocalErrorReporter myErrorReporter;

  public ExpressionResolveNameVisitor(ConcreteProvider concreteProvider, Scope parentScope, List<Referable> context, LocalErrorReporter errorReporter) {
    myTypeClassReferenceExtractVisitor = new TypeClassReferenceExtractVisitor(concreteProvider);
    myParentScope = parentScope;
    myScope = context == null ? parentScope : new MergeScope(new ListScope(context), parentScope);
    myContext = context;
    myErrorReporter = errorReporter;
  }

  Scope getScope() {
    return myScope;
  }

  public static Referable resolve(Referable referable, Scope scope, boolean withArg) {
    while (referable instanceof RedirectingReferable) {
      referable = ((RedirectingReferable) referable).getOriginalReferable();
    }
    if (referable instanceof UnresolvedReference) {
      if (withArg) {
        ((UnresolvedReference) referable).resolveArgument(scope);
      }
      referable = ((UnresolvedReference) referable).resolve(scope);
      while (referable instanceof RedirectingReferable) {
        referable = ((RedirectingReferable) referable).getOriginalReferable();
      }
    }
    return referable;
  }

  public static Referable resolve(Referable referable, Scope scope) {
    return resolve(referable, scope, false);
  }

  public static Concrete.Expression resolve(Concrete.ReferenceExpression refExpr, Scope scope) {
    Referable referable = refExpr.getReferent();
    while (referable instanceof RedirectingReferable) {
      referable = ((RedirectingReferable) referable).getOriginalReferable();
    }

    Concrete.Expression arg = null;
    if (referable instanceof UnresolvedReference) {
      arg = ((UnresolvedReference) referable).resolveArgument(scope);
      referable = ((UnresolvedReference) referable).resolve(scope);
      while (referable instanceof RedirectingReferable) {
        referable = ((RedirectingReferable) referable).getOriginalReferable();
      }
    }

    refExpr.setReferent(referable);
    return arg;
  }

  @Override
  public Concrete.Expression visitReference(Concrete.ReferenceExpression expr, Void params) {
    Concrete.Expression argument = resolve(expr, myScope);
    if (expr.getReferent() instanceof ErrorReference) {
      myErrorReporter.report(((ErrorReference) expr.getReferent()).getError());
    }
    return argument == null ? expr : Concrete.AppExpression.make(expr.getData(), expr, argument, false);
  }

  void updateScope(Collection<? extends Concrete.Parameter> parameters) {
    for (Concrete.Parameter parameter : parameters) {
      if (parameter instanceof Concrete.TelescopeParameter) {
        for (Referable referable : ((Concrete.TelescopeParameter) parameter).getReferableList()) {
          if (referable != null && !referable.textRepresentation().equals("_")) {
            myContext.add(referable);
          }
        }
      } else
      if (parameter instanceof Concrete.NameParameter) {
        Referable referable = ((Concrete.NameParameter) parameter).getReferable();
        if (referable != null && !referable.textRepresentation().equals("_")) {
          myContext.add(referable);
        }
      }
    }
  }

  private ClassReferable getTypeClassReference(Concrete.Expression type) {
    return myTypeClassReferenceExtractVisitor.getTypeClassReference(Collections.emptyList(), type);
  }

  protected void visitParameter(Concrete.Parameter parameter) {
    if (parameter instanceof Concrete.TypeParameter) {
      ((Concrete.TypeParameter) parameter).type = ((Concrete.TypeParameter) parameter).type.accept(this, null);
    }

    if (parameter instanceof Concrete.TelescopeParameter) {
      ClassReferable classRef = getTypeClassReference(((Concrete.TelescopeParameter) parameter).getType());
      List<? extends Referable> referableList = ((Concrete.TelescopeParameter) parameter).getReferableList();
      for (int i = 0; i < referableList.size(); i++) {
        Referable referable = referableList.get(i);
        if (referable != null && !referable.textRepresentation().equals("_")) {
          for (int j = 0; j < i; j++) {
            Referable referable1 = referableList.get(j);
            if (referable1 != null && referable.textRepresentation().equals(referable1.textRepresentation())) {
              myErrorReporter.report(new DuplicateNameError(Error.Level.WARNING, referable1, referable));
            }
          }
          myContext.add(classRef == null ? referable : new TypedRedirectingReferable(referable, classRef));
        }
      }
    } else
    if (parameter instanceof Concrete.NameParameter) {
      Referable referable = ((Concrete.NameParameter) parameter).getReferable();
      if (referable != null && !referable.textRepresentation().equals("_")) {
        myContext.add(referable);
      }
    }
  }

  @Override
  public Concrete.Expression visitLam(Concrete.LamExpression expr, Void params) {
    try (Utils.ContextSaver ignored = new Utils.ContextSaver(myContext)) {
      visitParameters(expr.getParameters());
      expr.body = expr.body.accept(this, null);
      return expr;
    }
  }

  @Override
  public Concrete.Expression visitPi(Concrete.PiExpression expr, Void params) {
    try (Utils.ContextSaver ignored = new Utils.ContextSaver(myContext)) {
      visitParameters(expr.getParameters());
      expr.codomain = expr.codomain.accept(this, null);
      return expr;
    }
  }

  @Override
  public Concrete.Expression visitSigma(Concrete.SigmaExpression expr, Void params) {
    try (Utils.ContextSaver ignored = new Utils.ContextSaver(myContext)) {
      visitParameters(expr.getParameters());
      return expr;
    }
  }

  @Override
  public Concrete.Expression visitBinOpSequence(Concrete.BinOpSequenceExpression expr, Void params) {
    Concrete.Expression result = super.visitBinOpSequence(expr, null);
    return result instanceof Concrete.BinOpSequenceExpression ? new BinOpParser(myErrorReporter).parse((Concrete.BinOpSequenceExpression) result) : result;
  }

  @Override
  protected void visitClause(Concrete.Clause clause) {
    if (clause instanceof Concrete.FunctionClause) {
      Concrete.FunctionClause functionClause = (Concrete.FunctionClause) clause;
      try (Utils.ContextSaver ignored = new Utils.ContextSaver(myContext)) {
        visitPatterns(clause.getPatterns(), new HashMap<>(), true);
        if (functionClause.expression != null) {
          functionClause.expression = functionClause.expression.accept(this, null);
        }
      }
    }
  }

  private GlobalReferable visitPattern(Concrete.Pattern pattern, Map<String, Concrete.NamePattern> usedNames) {
    if (pattern instanceof Concrete.NamePattern) {
      Concrete.NamePattern namePattern = (Concrete.NamePattern) pattern;
      Referable referable = namePattern.getReferable();
      String name = referable == null ? null : referable.textRepresentation();
      if (name == null) return null;
      Referable ref = myParentScope.resolveName(name);
      if (ref instanceof GlobalReferable && ((GlobalReferable) ref).getKind() == GlobalReferable.Kind.CONSTRUCTOR) {
        return (GlobalReferable) ref;
      }
      if (!name.equals("_")) {
        Concrete.NamePattern prev = usedNames.put(name, namePattern);
        if (prev != null) {
          myErrorReporter.report(new DuplicateNameError(Error.Level.WARNING, referable, prev.getReferable()));
        }
        myContext.add(referable);
      }
      return null;
    } else if (pattern instanceof Concrete.ConstructorPattern) {
      visitPatterns(((Concrete.ConstructorPattern) pattern).getPatterns(), usedNames, false);
      return null;
    } else if (pattern instanceof Concrete.TuplePattern) {
      visitPatterns(((Concrete.TuplePattern) pattern).getPatterns(), usedNames, false);
      return null;
    } else if (pattern instanceof Concrete.NumberPattern) {
      return null;
    } else {
      throw new IllegalStateException();
    }
  }

  void visitPatterns(List<Concrete.Pattern> patterns, Map<String, Concrete.NamePattern> usedNames, boolean resolvePatterns) {
    for (int i = 0; i < patterns.size(); i++) {
      Referable constructor = visitPattern(patterns.get(i), usedNames);
      if (constructor != null) {
        patterns.set(i, new Concrete.ConstructorPattern(patterns.get(i).getData(), patterns.get(i).isExplicit(), constructor, Collections.emptyList()));
      }
      if (resolvePatterns) {
        resolvePattern(patterns.get(i));
      }
    }
  }

  private void resolvePattern(Concrete.Pattern pattern) {
    if (pattern instanceof Concrete.TuplePattern) {
      for (Concrete.Pattern patternArg : ((Concrete.TuplePattern) pattern).getPatterns()) {
        resolvePattern(patternArg);
      }
      return;
    }
    if (!(pattern instanceof Concrete.ConstructorPattern)) {
      return;
    }

    Referable referable = resolve(((Concrete.ConstructorPattern) pattern).getConstructor(), myParentScope);
    if (referable instanceof ErrorReference) {
      myErrorReporter.report(((ErrorReference) referable).getError());
    } else {
      ((Concrete.ConstructorPattern) pattern).setConstructor(referable);
    }

    for (Concrete.Pattern patternArg : ((Concrete.ConstructorPattern) pattern).getPatterns()) {
      resolvePattern(patternArg);
    }
  }

  @Override
  public Concrete.Expression visitClassExt(Concrete.ClassExtExpression expr, Void params) {
    expr.baseClassExpression = expr.baseClassExpression.accept(this, null);
    ClassReferable classRef = expr.baseClassExpression.getUnderlyingClassReferable(false);
    if (classRef != null) {
      visitClassFieldImpls(expr.getStatements(), classRef);
    } else {
      myErrorReporter.report(new NamingError("Expected a class", expr.baseClassExpression.getData()));
      expr.getStatements().clear();
    }
    return expr;
  }

  void visitClassFieldImpls(List<Concrete.ClassFieldImpl> classFieldImpls, ClassReferable classDef) {
    for (Concrete.ClassFieldImpl impl : classFieldImpls) {
      Referable field = impl.getImplementedField();
      while (field instanceof RedirectingReferable) {
        field = ((RedirectingReferable) field).getOriginalReferable();
      }
      if (field instanceof UnresolvedReference) {
        field = ((UnresolvedReference) field).resolve(new ClassFieldImplScope(classDef, true));
        while (field instanceof RedirectingReferable) {
          field = ((RedirectingReferable) field).getOriginalReferable();
        }
        if (field instanceof ErrorReference) {
          myErrorReporter.report(((ErrorReference) field).getError());
        }
        impl.setImplementedField(field);
      }

      if (impl.implementation == null) {
        ClassReferable classRef = impl.getImplementedField() instanceof ClassReferable
          ? (ClassReferable) impl.getImplementedField()
          : impl.getImplementedField() instanceof TypedReferable
            ? ((TypedReferable) impl.getImplementedField()).getTypeClassReference()
            : null;
        if (classRef != null) {
          visitClassFieldImpls(impl.subClassFieldImpls, classRef);
        }
      } else {
        impl.implementation = impl.implementation.accept(this, null);
      }
    }
  }

  @Override
  public Concrete.Expression visitLet(Concrete.LetExpression expr, Void params) {
    try (Utils.ContextSaver ignored = new Utils.ContextSaver(myContext)) {
      for (Concrete.LetClause clause : expr.getClauses()) {
        try (Utils.ContextSaver ignored1 = new Utils.ContextSaver(myContext)) {
          visitParameters(clause.getParameters());
          if (clause.resultType != null) {
            clause.resultType = clause.resultType.accept(this, null);
          }
          clause.term = clause.term.accept(this, null);
        }

        ClassReferable classRef = myTypeClassReferenceExtractVisitor.getTypeClassReference(clause.getParameters(), clause.getResultType());
        myContext.add(classRef == null ? clause.getData() : new TypedRedirectingReferable(clause.getData(), classRef));
      }

      expr.expression = expr.expression.accept(this, null);
      return expr;
    }
  }
}
