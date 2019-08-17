package org.arend.typechecking;

import org.arend.error.ErrorReporter;
import org.arend.naming.reference.GlobalReferable;
import org.arend.term.Precedence;
import org.arend.typechecking.error.local.LocalErrorReporter;

import javax.annotation.Nonnull;

public class TestLocalErrorReporter extends LocalErrorReporter {
  public TestLocalErrorReporter(ErrorReporter errorReporter) {
    super(new GlobalReferable() {
        @Nonnull
        @Override
        public Precedence getPrecedence() {
                                              return Precedence.DEFAULT;
                                                                        }

        @Nonnull
        @Override
        public String textRepresentation() {
                                               return "testDefinition";
                                                                       }
      }, errorReporter);
  }
}
