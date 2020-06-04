package handlingdep;

import dataflow.*;
import soot.SootMethod;
import soot.Unit;
import soot.ValueBox;
import soot.jimple.IfStmt;
import soot.jimple.Stmt;
import soot.jimple.SwitchStmt;
import soot.toolkits.graph.ExceptionalUnitGraph;
import utility.OutputFormat;

import java.util.*;

public class ValueDep {
    private HashSet<Unit>occur;
    public ValueDep(){
        occur = new HashSet<>();
    }
    public static boolean isCompare(String s){
        return s.contains("<") || s.contains(">") || s.contains("<=") || s.contains(">=") || s.contains("cmp");
    }

    public void dfs(Unit current, ExceptionalUnitGraph graph, LinkedList<String>names, Recorder recorder, SootMethod method){
        if(occur.contains(current))return;
        occur.add(current);
        if(current.branches()){
            List<ValueBox> values = null;
            if(current instanceof  IfStmt){
                IfStmt ifStmt = (IfStmt) current;
                values = ifStmt.getUseBoxes();
            }
            if(current instanceof SwitchStmt){
                SwitchStmt switchStmt = (SwitchStmt) current;
                values = switchStmt.getUseBoxes();
            }
            if(values != null) {
                for (int i = 0; i < values.size(); ++i) {
                    if (recorder.containValue(values.get(i).getValue(), method, method.getDeclaringClass())) {
                        LinkedList<String> strVals = recorder.getByValue(values.get(i).getValue(), method, method.getDeclaringClass());
                        if (names.size() > 0) {
                            for (int j = 0; j < strVals.size(); ++j) {
                                for (int k = 0; k < names.size(); ++k) {
                                    OutputFormat.output(method.getDeclaringClass(), method, DepNames.constraint, strVals.get(j), names.get(k), (Stmt) current);
                                }
                            }
                        }
                        names.addAll(strVals);
                    }
                }
            }
        }
        List<Unit> preds = graph.getUnexceptionalPredsOf(current);
        for(int i=0;i<preds.size();++i)dfs(preds.get(i),graph,(LinkedList<String>) names.clone(),recorder,method);
    }
}
