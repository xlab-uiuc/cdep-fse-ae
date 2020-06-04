package utility;

import soot.RefType;
import soot.Value;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;


public class IsGetter {

    public Boolean check(Stmt stmt){
        if (!stmt.containsInvokeExpr()) return false;
        InvokeExpr expr = stmt.getInvokeExpr();
        if(isLogger(expr))return false;
        if (expr.getMethod().getDeclaringClass().getName().contains("DeprecationDelta"))return false;
        else return false;
    }

    protected boolean isLogger(InvokeExpr iexpr) {
        if (iexpr instanceof InstanceInvokeExpr) {
            Value base = ((InstanceInvokeExpr) iexpr).getBase();
            if (base.getType() instanceof RefType) {
                RefType rty = (RefType) base.getType();
                if (rty.getClassName().contains("Logger")) {
                    return true;
                }
            }
        }
        return false;
    }
}
