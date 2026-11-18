# -*- coding: utf-8 -*-
"""跑 ch45~ch48 的 main，检查是否有异常退出。"""
import subprocess

JAVA = r"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe"
CP = r"D:\Javalianxi\_build\out_pkg"

CHAPTERS = {
    "ch45": ["WhyConsensus", "CapSpectrum", "RaftElection", "RaftLogReplication",
             "ZookeeperConcepts", "EtcdLinearRead", "DistributedClocks",
             "LeaseFencing", "QuorumReplication", "ConsensusCost", "ConfigCenterCase"],
    "ch46": ["WarehouseToLake", "ParquetColumnar", "ObjectStorage", "HiveMetastore",
             "IcebergTableFormat", "LakeStreaming", "MppAdhoc", "DataGovernance",
             "DataIngestion", "DataPlatformService", "LakehouseCase"],
    "ch47": ["WhyServiceMesh", "SidecarDataplane", "MeshTrafficPolicy", "MeshMtls",
             "MeshObservability", "MeshCost", "ServerlessFn", "ElasticCost",
             "MultiCluster", "ChaosEngineering", "MeshCase"],
    "ch48": ["InferenceBasics", "KvCache", "ContinuousBatching", "PagedAttention",
             "InferenceAcceleration", "ServingCapacity", "ServingStreaming",
             "ServingHA", "InferenceCost", "InferenceObservability", "InferenceCase"],
}

fails = []
ok = 0
for pkg, names in CHAPTERS.items():
    for name in names:
        cls = "com.heima.%s.%s" % (pkg, name)
        try:
            r = subprocess.run([JAVA, "-Dstdout.encoding=UTF-8", "-cp", CP, cls],
                               capture_output=True, timeout=120)
        except subprocess.TimeoutExpired:
            fails.append((cls, "TIMEOUT"))
            print("TIMEOUT", cls)
            continue
        out = r.stdout.decode("utf-8", errors="replace")
        err = r.stderr.decode("utf-8", errors="replace")
        if r.returncode != 0:
            fails.append((cls, err[:1500]))
            print("FAIL(%d) %s" % (r.returncode, cls))
        else:
            ok += 1
            print("ok  %-46s lines=%d" % (cls, len(out.splitlines())))

print()
print("pass=%d fail=%d" % (ok, len(fails)))
for cls, msg in fails:
    print("=" * 70)
    print(cls)
    print(msg)
