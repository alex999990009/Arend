package com.jetbrains.jetpad.vclang.term.definition;

import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.term.context.param.DependentLink;
import com.jetbrains.jetpad.vclang.term.context.param.EmptyDependentLink;
import com.jetbrains.jetpad.vclang.term.expr.ConCallExpression;
import com.jetbrains.jetpad.vclang.term.expr.DataCallExpression;
import com.jetbrains.jetpad.vclang.term.expr.Expression;
import com.jetbrains.jetpad.vclang.term.expr.UniverseExpression;
import com.jetbrains.jetpad.vclang.term.expr.subst.LevelSubstitution;
import com.jetbrains.jetpad.vclang.term.expr.subst.Substitution;
import com.jetbrains.jetpad.vclang.term.pattern.Pattern;

import java.util.*;

import static com.jetbrains.jetpad.vclang.term.expr.ExpressionFactory.*;

public class DataDefinition extends Definition {
  private List<Constructor> myConstructors;
  private DependentLink myParameters;
  private Map<Constructor, Condition> myConditions;

  public DataDefinition(String name, Abstract.Definition.Precedence precedence) {
    super(name, precedence);
    myConstructors = new ArrayList<>();
    myParameters = EmptyDependentLink.getInstance();
  }

  public DataDefinition(String name, Abstract.Definition.Precedence precedence, TypeUniverse universe, DependentLink parameters) {
    super(name, precedence, universe);
    hasErrors(false);
    myParameters = parameters;
    myConstructors = new ArrayList<>();
  }

  public DependentLink getParameters() {
    assert !hasErrors();
    return myParameters;
  }

  public void setParameters(DependentLink parameters) {
    assert parameters != null;
    myParameters = parameters;
  }

  public List<Constructor> getConstructors() {
    return myConstructors;
  }

  public List<ConCallExpression> getMatchedConstructors(List<? extends Expression> parameters) {
    List<ConCallExpression> result = new ArrayList<>();
    for (Constructor constructor : myConstructors) {
      if (constructor.hasErrors())
        continue;
      List<? extends Expression> matchedParameters;
      if (constructor.getPatterns() != null) {
        Pattern.MatchResult matchResult = constructor.getPatterns().match(parameters);
        if (matchResult instanceof Pattern.MatchMaybeResult) {
          return null;
        } else if (matchResult instanceof Pattern.MatchFailedResult) {
          continue;
        } else if (matchResult instanceof Pattern.MatchOKResult) {
          matchedParameters = ((Pattern.MatchOKResult) matchResult).expressions;
        } else {
          throw new IllegalStateException();
        }
      } else {
        matchedParameters = parameters;
      }

      result.add(ConCall(constructor, new ArrayList<>(matchedParameters)));
    }
    return result;
  }

  public void addCondition(Condition condition) {
    if (myConditions == null) {
      myConditions = new HashMap<>();
    }
    myConditions.put(condition.getConstructor(), condition);
  }

  public Collection<Condition> getConditions() {
    return myConditions == null ? Collections.<Condition>emptyList() : myConditions.values();
  }

  public Condition getCondition(Constructor constructor) {
    return myConditions == null ? null : myConditions.get(constructor);
  }

  public Constructor getConstructor(String name) {
    for (Constructor constructor : myConstructors) {
      if (constructor.getName().equals(name)) {
        return constructor;
      }
    }
    return null;
  }

  public void addConstructor(Constructor constructor) {
    myConstructors.add(constructor);
  }

  @Override
  public Expression getType() {
    if (hasErrors()) {
      return null;
    }

    Expression resultType = new UniverseExpression(getUniverse());
    return myParameters.hasNext() ? Pi(myParameters, resultType) : resultType;
  }

  @Override
  public DataCallExpression getDefCall() {
    return DataCall(this);
  }

  @Override
  public DataDefinition substPolyParams(LevelSubstitution substitution) {
    if (!isPolymorphic()) {
      return this;
    }
    Substitution subst = new Substitution(substitution);
    DataDefinition newDef = new DataDefinition(getName(), getPrecedence(), getUniverse().subst(subst.LevelSubst), DependentLink.Helper.subst(myParameters, subst));
    for (Constructor constructor : getConstructors()) {
      Constructor newConstructor = new Constructor(constructor.getName(), constructor.getPrecedence(),
              constructor.getUniverse().subst(subst.LevelSubst), DependentLink.Helper.subst(constructor.getParameters(), subst), newDef, constructor.getPatterns());
      newDef.addConstructor(newConstructor);

      Condition cond = newDef.getCondition(constructor);
      if (cond != null) {
        newDef.addCondition(new Condition(newConstructor, cond.getElimTree() /*.subst(subst) /**/));
      }
    }
    return newDef;
  }
}
