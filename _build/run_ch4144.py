# -*- coding: utf-8 -*-
"""跑 ch41~ch44 的 main，检查是否有异常退出。"""
import subprocess

JAVA = r"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe"
CP = r"D:\Javalianxi\_build\out_pkg"

CHAPTERS = {
    "ch41": ["GitBranchModel", "CommitConvention", "PipelineStages", "ArtifactRegistry",
             "EnvironmentPromotion", "RollingBlueGreen", "CanaryRelease", "FeatureFlags",
             "DbMigration", "ReleaseAudit", "PipelineCase"],
    "ch42": ["WhyObservability", "StructuredLogging", "LogStoreQuery", "MetricsModel",
             "SloGoldenSignals", "AlertDesign", "TraceSpans", "TraceSampling",
             "ContinuousProfiling", "DashboardIncident", "IncidentCase"],
    "ch43": ["StreamVsBatch", "TimeSemantics", "WindowsDemo", "WatermarkDemo",
             "StateCheckpoint", "ExactlyOnce", "StreamJoin", "Backpressure",
             "CdcWarehouse", "FlinkVsSpark", "RealtimeDashboard"],
    "ch44": ["AgentIntro", "ReActLoop", "AgentMemory", "MultiAgent", "LocalLlm",
             "FineTuning", "McpEcosystem", "AiGateway", "AgentSecurity",
             "EvalDriven", "OpsAgentCase"],
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
