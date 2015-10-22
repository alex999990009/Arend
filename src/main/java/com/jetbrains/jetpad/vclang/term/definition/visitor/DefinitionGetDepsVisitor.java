package com.jetbrains.jetpad.vclang.term.definition.visitor;

import com.jetbrains.jetpad.vclang.module.Namespace;
import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.term.definition.NamespaceMember;
import com.jetbrains.jetpad.vclang.term.definition.ResolvedName;
import com.jetbrains.jetpad.vclang.term.expr.visitor.GetDepsVisitor;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class DefinitionGetDepsVisitor implements AbstractDefinitionVisitor<Void, Set<ResolvedName>> {
  private final Namespace myNamespace;

  public DefinitionGetDepsVisitor(Namespace namespace) {
    myNamespace = namespace;
  }

  public static Set<ResolvedName> visitNamespace(Namespace ns) {
    Set<ResolvedName> result = new HashSet<>();
    for (NamespaceMember member : ns.getMembers()) {
      if (member.abstractDefinition != null) {
        if (!(member.abstractDefinition instanceof Abstract.Constructor)) {
          result.add(member.getResolvedName());
          result.addAll(member.abstractDefinition.accept(new DefinitionGetDepsVisitor(member.namespace), null));
        }
      } else {
        result.addAll(visitNamespace(member.namespace));
      }
    }
    return result;
  }

  @Override
  public Set<ResolvedName> visitFunction(Abstract.FunctionDefinition def, Void params) {
    Set<ResolvedName> result = new HashSet<>();
    result.addAll(visitStatements(def.getStatements()));

    for (Abstract.Argument arg : def.getArguments()) {
      if (arg instanceof Abstract.TypeArgument) {
        result.addAll(((Abstract.TypeArgument) arg).getType().accept(new GetDepsVisitor(), null));
      }
    }

    Abstract.Expression resultType = def.getResultType();
    if (resultType != null) {
      result.addAll(resultType.accept(new GetDepsVisitor(), null));
    }

    Abstract.Expression term = def.getTerm();
    if (term != null) {
      result.addAll(term.accept(new GetDepsVisitor(), null));
    }

    return result;
  }

  @Override
  public Set<ResolvedName> visitAbstract(Abstract.AbstractDefinition def, Void params) {
    Set<ResolvedName> result = new HashSet<>();

    for (Abstract.Argument arg : def.getArguments()) {
      if (arg instanceof Abstract.TypeArgument) {
        result.addAll(((Abstract.TypeArgument) arg).getType().accept(new GetDepsVisitor(), null));
      }
    }

    Abstract.Expression resultType = def.getResultType();
    if (resultType != null) {
      result.addAll(resultType.accept(new GetDepsVisitor(), null));
    }

    return result;
  }

  @Override
  public Set<ResolvedName> visitData(Abstract.DataDefinition def, Void isSiblings) {
    Set<ResolvedName> result = new HashSet<>();

    for (Abstract.TypeArgument param : def.getParameters()) {
      result.addAll(param.getType().accept(new GetDepsVisitor(), null));
    }

    for (Abstract.Constructor constructor : def.getConstructors()) {
      for (Abstract.TypeArgument arg : constructor.getArguments()) {
        result.addAll(arg.getType().accept(new GetDepsVisitor(), null));
      }
    }

    return result;
  }

  @Override
  public Set<ResolvedName> visitConstructor(Abstract.Constructor def, Void isSiblings) {
    throw new IllegalStateException();
  }

  public Set<ResolvedName> visitStatements(Collection<? extends Abstract.Statement> statements) {
    Set<ResolvedName> result = new HashSet<>();
    for (Abstract.Statement statement : statements) {
      if (statement instanceof Abstract.DefineStatement) {
        Abstract.DefineStatement defineStatement = (Abstract.DefineStatement) statement;
        result.add(new ResolvedName(myNamespace, defineStatement.getDefinition().getName()));
        result.addAll(defineStatement.getDefinition().accept(new DefinitionGetDepsVisitor(
            myNamespace.getChild(defineStatement.getDefinition().getName())
        ), null));
      }
    }
    return result;
  }

  @Override
  public Set<ResolvedName> visitClass(Abstract.ClassDefinition def, Void isSiblings) {
    return visitStatements(def.getStatements());
  }
}
