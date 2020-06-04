package configinterface;

import soot.jimple.InvokeExpr;


public interface ConfigInterface {
    String getConfigName(InvokeExpr iexpr);
    boolean isGetter(InvokeExpr iexpr);
    boolean isSetter(InvokeExpr iexpr);
}
