package configinterface;

import soot.SootMethod;
import soot.Value;
import soot.jimple.*;
import java.util.List;

public class ZooKeeperInterface implements ConfigInterface {
    static String[] serverConfigNames = {"getClientPortAddress", "getSecureClientPortAddress", "getDataDir", "getDataLogDir", "getTickTime", "getMaxClientCnxns", "getMinSessionTimeout", "getMaxSessionTimeout"};
    static String[] quorumConfigNames = {"areLocalSessionsEnabled", "isLocalSessionsUpgradingEnabled", "getInitLimit", "getSyncLimit", "getElectionAlg", "getElectionPort", "getSnapRetainCount", "getPurgeInterval", "getSyncEnabled", "getQuorumVerifier", "getLastSeenQuorumVerifier", "getServerId", "getPeerType", "getConfigFilename", "getQuorumListenOnAllIPs", "isStandaloneEnabled", "isReconfigEnabled"};
    public static String[] configNames = {"clientPortAddress", "secureClientPortAddress", "dataDir", "dataLogDir", "tickTime", "maxClientCnxns", "minSessionTimeout", "maxSessionTimeout", "electionType","localSessionsEnabled", "localSessionsUpgradingEnabled", "initLimit", "syncLimit", "electionAlg", "electionPort", "snapRetainCount", "purgeInterval", "syncEnabled", "quorumVerifier", "lastSeenQuorumVerifier", "serverId", "peerType", "configFilename", "quorumListenOnAllIPs", "standaloneEnabled", "reconfigEnabled"};

    private static Boolean contain(String[] arrays, String name) {
        for (int i = 0; i < arrays.length; ++i) {
            if (arrays[i].equals(name)) return true;
        }
        return false;
    }

    public boolean isSetter(InvokeExpr iexpr) {
        SootMethod callee = iexpr.getMethod();
        if (callee.getName().startsWith("set")) {
            List<Value> args = iexpr.getArgs();
            if (args.size() == 2 && args.get(0) instanceof StringConstant) {
                return true;
            } else return false;
        }
        return false;
    }

    public boolean isGetter(InvokeExpr iexpr) {
        SootMethod callee = iexpr.getMethod();
        String name = callee.getName();
        if (contain(serverConfigNames, name) || contain(quorumConfigNames, name)) {
            return true;
        }
        if (callee.getName().contains("get")) {
            List<Value> args = iexpr.getArgs();
            for (int i = 0; i < args.size(); ++i) {
                if (args.get(i) instanceof StringConstant) return true;
            }
        }
        return false;
    }

    public String getConfigName(InvokeExpr iexpr) {
        try {
            String functionName = iexpr.getMethod().getName();
            if (contain(serverConfigNames, functionName) || contain(quorumConfigNames, functionName)) {
                return functionName;
            }
            Value name = iexpr.getArgs().get(0);
            StringConstant strVal = (StringConstant) name;
            return strVal.value;
        } catch (Exception e) {
            return null;
        }
    }
}
