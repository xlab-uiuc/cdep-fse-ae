# A Few Configuration Dependency Examples

We show some configuration dependency cases (found by cDep) that are non-intuitive at the first glance.

## 1. Value Relationship Dependency

The first parameter is no less than the second parameter:
1. `yarn.nm.liveness-monitor.expiry-interval-ms`
2. `yarn.resourcemanager.nodemanagers.heartbeat-interval-ms`

Code snippets:
```
long expireIntvl = conf.getLong(YarnConfiguration.RM_NM_EXPIRY_INTERVAL_MS,
    YarnConfiguration.DEFAULT_RM_NM_EXPIRY_INTERVAL_MS);
long heartbeatIntvl =
    conf.getLong(YarnConfiguration.RM_NM_HEARTBEAT_INTERVAL_MS,
        YarnConfiguration.DEFAULT_RM_NM_HEARTBEAT_INTERVAL_MS);
if (expireIntvl < heartbeatIntvl) {
    throw new YarnRuntimeException("Nodemanager expiry interval should be no"
       + " less than heartbeat interval, "
       + YarnConfiguration.RM_NM_EXPIRY_INTERVAL_MS + "=" + expireIntvl
       + ", " + YarnConfiguration.RM_NM_HEARTBEAT_INTERVAL_MS + "="
       + heartbeatIntvl);
}
```

## 2. Value Relationship Dependency

If the first parameter is not `null`, then the second parameter has to be `kerberos` to enable authentication.
1. `hbase.thrift.security.qop`
2. `hadoop.security.authentication`

Code snippets:
```
if (qop != null) {
    ...
    if (!securityEnabled) {
        throw new IOException("Thrift server must run in secure mode to support authentication");
    }
}
```
(`qop` stores values of the first parameter, while `securityEnabled` takes the value from the second parameter.)


## 3. Overwrite Dependency

The second parameter overwrites the first parameter. 
1. `hadoop.security.service.user.name.key`
2. `mapreduce.jobhistory.principal`

Code snippets:
```
private Configuration addSecurityConfiguration(Configuration conf) {
    ...
    conf.set(CommonConfigurationKeys.HADOOP_SECURITY_SERVICE_USER_NAME_KEY,
             conf.get(JHAdminConfig.MR_HISTORY_PRINCIPAL, ""));
    return conf;
}
```
