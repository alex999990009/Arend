package com.jetbrains.jetpad.vclang.frontend;

import com.jetbrains.jetpad.vclang.error.ErrorReporter;
import com.jetbrains.jetpad.vclang.module.ModulePath;
import com.jetbrains.jetpad.vclang.module.source.ModuleLoader;
import com.jetbrains.jetpad.vclang.module.source.SourceId;
import com.jetbrains.jetpad.vclang.module.source.Storage;
import com.jetbrains.jetpad.vclang.naming.ModuleResolver;
import com.jetbrains.jetpad.vclang.term.Abstract;

public class BaseModuleLoader<SourceIdT extends SourceId> implements ModuleLoader<SourceIdT>, ModuleResolver {
  protected final Storage<SourceIdT> myStorage;
  private final ErrorReporter myErrorReporter;

  public BaseModuleLoader(Storage<SourceIdT> storage, ErrorReporter errorReporter) {
    myStorage = storage;
    myErrorReporter = errorReporter;
  }


  protected void loadingSucceeded(SourceIdT module, Abstract.ClassDefinition abstractDefinition) {}

  protected void loadingFailed(SourceIdT module) {}

  @Override
  public Abstract.ClassDefinition load(SourceIdT sourceId) {
    final Abstract.ClassDefinition result;
    result = myStorage.loadSource(sourceId, myErrorReporter);

    if (result != null) {
      loadingSucceeded(sourceId, result);
    } else {
      loadingFailed(sourceId);
    }

    return result;
  }

  @Override
  public Abstract.ClassDefinition load(ModulePath modulePath) {
    return load(locateModule(modulePath));
  }

  public SourceIdT locateModule(ModulePath modulePath) {
    SourceIdT sourceId = myStorage.locateModule(modulePath);
    if (sourceId == null) throw new IllegalStateException();
    return sourceId;
  }
}
