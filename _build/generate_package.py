# -*- coding: utf-8 -*-
"""按 Java 命名规范重建工程：
   - 包名 com.heima.chNN / com.heima.practice.xxx（全小写、点分隔、域名反写）
   - 类名英文大驼峰（首字母大写，无下划线、无数字前缀）
   - 辅助类 = 入口类名 + 原类名，保证同包内唯一
"""
import re, os, glob, shutil

ROOT = r"D:\Javalianxi"
SRC = os.path.join(ROOT, "黑马Java课程笔记")
OUT = os.path.join(ROOT, "JavaProject")
JAVA = os.path.join(OUT, "src", "main", "java")
PKG_ROOT = os.path.join(JAVA, "com", "heima")

# ---------------------------------------------------------------- 课名 -> 类名
NAMES = {
    # 第 02 章
    ("02", "01"): "Literal",
    ("02", "02"): "Variable",
    ("02", "03"): "StreetFighter",
    ("02", "04"): "VariableNotes",
    ("02", "05"): "MemoryStorageRule",
    ("02", "06"): "DataType",
    ("02", "07"): "BmiCalculator",
    ("02", "08"): "Identifier",
    ("02", "09"): "KeyboardInput",
    ("02", "10"): "TwoNumberSum",
    ("02", "11"): "BmiInput",
    ("02", "12"): "ArithmeticOperator",
    ("02", "13"): "NumberSplit",
    ("02", "14"): "TimeConverter",
    ("02", "15"): "TypeConversion",
    ("02", "16"): "CharOperation",
    ("02", "17"): "StringConcat",
    ("02", "18"): "IncrementDecrement",
    ("02", "19"): "AssignmentOperator",
    ("02", "20"): "RelationalOperator",
    ("02", "21"): "LogicalOperator",
    ("02", "22"): "ShortCircuitOperator",
    ("02", "23"): "TernaryOperator",
    ("02", "24"): "OperatorPrecedence",
    # 第 03 章
    ("03", "01"): "IfBasic",
    ("03", "02"): "HealthBarCalc",
    ("03", "03"): "IfDetails",
    ("03", "04"): "IfElse",
    ("03", "05"): "IfElseIf",
    ("03", "06"): "SwitchStatement",
    ("03", "07"): "SwitchDetails",
    ("03", "08"): "ForLoop",
    ("03", "09"): "ForBasicPractice",
    ("03", "10"): "ForAlgorithmPractice",
    ("03", "11"): "WhileLoop",
    ("03", "12"): "WhileAlgorithmPractice",
    ("03", "13"): "DoWhileLoop",
    ("03", "14"): "InfiniteLoop",
    ("03", "15"): "BreakKeyword",
    ("03", "16"): "ContinueKeyword",
    ("03", "17"): "GuessNumberGame",
    ("03", "18"): "NestedLoop",
    ("03", "19"): "PrintShapes",
    ("03", "20"): "MultiplicationTable",
    # 第 04 章
    ("04", "01"): "WhatIsArray",
    ("04", "02"): "ArrayLearningContent",
    ("04", "03"): "ArrayStaticInit",
    ("04", "04"): "ArrayElementAccess",
    ("04", "05"): "ArrayTraversal",
    ("04", "06"): "ArrayDynamicInit",
    ("04", "07"): "ArrayCommonProblems",
    ("04", "08"): "SearchData",
    ("04", "09"): "ArrayMax",
    ("04", "10"): "ShuffleArray",
    ("04", "11"): "RemoveDuplicates",
    ("04", "12"): "FastSlowPointer",
    ("04", "13"): "ArrayHomework",
    # 第 05 章
    ("05", "01"): "WhatIsMethod",
    ("05", "02"): "MethodPractice",
    ("05", "03"): "MethodDesignTips",
    ("05", "04"): "MethodOverload",
    ("05", "05"): "MethodHomework",
    # 第 06 章
    ("06", "01"): "JavaRuntimeMechanism",
    ("06", "02"): "MemoryAndAddress",
    ("06", "03"): "VariableMethodMemory",
    ("06", "04"): "ArrayMemory",
    ("06", "05"): "ArrayPassInMethod",
    # 第 07 章
    ("07", "01"): "DeleteData",
    ("07", "02"): "RedPacket",
    ("07", "03"): "MergeMedian",
    ("07", "04"): "MultiDataStatistics",
    ("07", "05"): "TrappingRainWater",
    ("07", "06"): "Lottery",
    # 第 08 章
    ("08", "01"): "WhatIsOop",
    ("08", "02"): "ClassAndObject",
    ("08", "03"): "OopDetails",
    ("08", "04"): "DataSecurity",
    ("08", "05"): "StudentSecurity",
    ("08", "06"): "ThisKeyword",
    ("08", "07"): "Constructor",
    ("08", "08"): "ConstructorNotes",
    ("08", "09"): "OopSummary",
    # 第 09 章
    ("09", "01"): "ObjectMemory",
    ("09", "02"): "ObjectPassInMethod",
    ("09", "03"): "ThisEssence",
    # 第 10 章
    ("10", "01"): "StaticVariable",
    ("10", "02"): "StaticMemory",
    ("10", "03"): "StaticMethod",
    ("10", "04"): "StaticNotes",
    ("10", "05"): "FinalKeyword",
    ("10", "06"): "EnumDemo",
    ("10", "07"): "EnumNotes",
    # 第 11 章
    ("11", "01"): "InheritanceIntro",
    ("11", "02"): "InheritanceStructure",
    ("11", "03"): "InheritanceFeatures",
    ("11", "04"): "InheritedVariable",
    ("11", "05"): "InheritedMethod",
    ("11", "06"): "InheritedConstructor",
    ("11", "07"): "JavaBeanInheritance",
    ("11", "08"): "InheritanceHomework",
    ("11", "09"): "InheritedContent",
    ("11", "10"): "AccessModifier",
    ("11", "11"): "PolymorphismIntro",
    ("11", "12"): "PolymorphismMembers",
    ("11", "13"): "PolymorphismPractice",
    ("11", "14"): "AbstractClass",
    ("11", "15"): "InterfaceMembers",
    ("11", "16"): "InterfaceAbstractCase",
    ("11", "17"): "InterfaceNewFeatures",
    ("11", "18"): "InnerClassIntro",
    ("11", "19"): "MemberInnerClass",
    ("11", "20"): "StaticAnonymousInnerClass",
    ("11", "21"): "AnonymousInnerClass",
    # 第 12 章
    ("12", "01"): "ApiDoc",
    ("12", "02"): "StringOverview",
    ("12", "03"): "StringCompare",
    ("12", "04"): "StringSubstring",
    ("12", "05"): "StringBuilderDemo",
    ("12", "06"): "StringAlgorithmPractice",
    ("12", "07"): "StringCalculation",
    ("12", "08"): "StringHomework",
    ("12", "09"): "ArrayListDemo",
    ("12", "10"): "ArrayListPractice",
    # 第 13 章
    ("13", "01"): "ProjectMainPage",
    ("13", "02"): "UserRegister",
    ("13", "03"): "LoginAndCaptcha",
    ("13", "04"): "GameRoleStructure",
    ("13", "05"): "CreateCharacters",
    ("13", "06"): "BeforeBattle",
    ("13", "07"): "HeroTurn",
    ("13", "08"): "EnemyTurn",
    ("13", "09"): "BattleSettlement",
    ("13", "10"): "FinalSettlement",
    ("13", "11"): "ProjectHomework",
    # 第 14 章
    ("14", "01"): "MathClass",
    ("14", "02"): "SystemObjectClass",
    ("14", "03"): "WrapperClass",
    ("14", "04"): "BigIntegerDecimal",
    # 第 15 章（课程扩展）
    ("15", "01"): "WhyCollection",
    ("15", "02"): "CollectionMethods",
    ("15", "03"): "ListArrayList",
    ("15", "04"): "ListTraversal",
    ("15", "05"): "LinkedListDemo",
    ("15", "06"): "GenericClassMethod",
    ("15", "07"): "GenericWildcard",
    ("15", "08"): "HashSetDedup",
    ("15", "09"): "LinkedHashSetTreeSet",
    ("15", "10"): "MapHashMap",
    ("15", "11"): "MapTraversal",
    ("15", "12"): "TreeMapComparator",
    ("15", "13"): "CollectionsUtil",
    ("15", "14"): "StreamIntro",
    # 第 16 章（课程扩展）
    ("16", "01"): "FileCreateJudge",
    ("16", "02"): "FileTraversalDelete",
    ("16", "03"): "ByteStream",
    ("16", "04"): "CharStream",
    ("16", "05"): "BufferedStream",
    ("16", "06"): "ConversionStream",
    ("16", "07"): "ObjectStream",
    ("16", "08"): "PropertiesConfig",
    ("16", "09"): "NioFiles",
    ("16", "10"): "IoCase",
    # 第 17 章（课程扩展）
    ("17", "01"): "ThreadConcept",
    ("17", "02"): "ExtendThread",
    ("17", "03"): "RunnableDemo",
    ("17", "04"): "ThreadMethods",
    ("17", "05"): "ThreadSafety",
    ("17", "06"): "SyncMethodLock",
    ("17", "07"): "DeadlockWaitNotify",
    ("17", "08"): "ThreadPoolDemo",
    ("17", "09"): "ConcurrentUtil",
    # 第 18 章（课程扩展）
    ("18", "01"): "NetworkElements",
    ("18", "02"): "UdpDemo",
    ("18", "03"): "TcpDemo",
    ("18", "04"): "ChatRoom",
    ("18", "05"): "FileUpload",
    ("18", "06"): "HttpUrlDemo",
    # 第 19 章（课程扩展）
    ("19", "01"): "ReflectBasicDemo",
    ("19", "02"): "ReflectConstructor",
    ("19", "03"): "ReflectField",
    ("19", "04"): "ReflectMethod",
    ("19", "05"): "ReflectCase",
    ("19", "06"): "AnnotationDefine",
    ("19", "07"): "AnnotationValidate",
    # 第 20 章（课程扩展）
    ("20", "01"): "MySqlBasics",
    ("20", "02"): "TypeMapping",
    ("20", "03"): "CrudSqlTemplate",
    ("20", "04"): "ConditionQueryBuilder",
    ("20", "05"): "PageCalculator",
    ("20", "06"): "JoinResultModel",
    ("20", "07"): "TransactionSimulator",
    ("20", "08"): "IndexRuleChecker",
    # 第 21 章（课程扩展）
    ("21", "01"): "JdbcQuickStart",
    ("21", "02"): "JdbcStatementDml",
    ("21", "03"): "JdbcResultSetMap",
    ("21", "04"): "JdbcSqlInjection",
    ("21", "05"): "JdbcPreparedBatch",
    ("21", "06"): "JdbcTransaction",
    ("21", "07"): "JdbcUtilDemo",
    ("21", "08"): "JdbcDaoCase",
    # 第 22 章（课程扩展）
    ("22", "01"): "MavenCoordinate",
    ("22", "02"): "SettingsReader",
    ("22", "03"): "StandardLayout",
    ("22", "04"): "DependencyScope",
    ("22", "05"): "DependencyMediation",
    ("22", "06"): "LifecycleRunner",
    ("22", "07"): "MultiModule",
    ("22", "08"): "ExecutableJar",
    # 第 23 章（课程扩展）
    ("23", "01"): "WhyMyBatis",
    ("23", "02"): "MyBatisQuickStart",
    ("23", "03"): "MapperProxyDemo",
    ("23", "04"): "ParameterBinding",
    ("23", "05"): "ResultMapDemo",
    ("23", "06"): "DynamicSqlBuilder",
    ("23", "07"): "AnnotationMapperDemo",
    ("23", "08"): "AssociationDemo",
    ("23", "09"): "CacheDemo",
    ("23", "10"): "StudentMapperCase",
    # 第 24 章（课程扩展）
    ("24", "01"): "WhySpringBoot",
    ("24", "02"): "FirstBootApp",
    ("24", "03"): "AutoConfigPrinciple",
    ("24", "04"): "ConfigAndProfile",
    ("24", "05"): "IocContainer",
    ("24", "06"): "RestApiDemo",
    ("24", "07"): "ValidationDemo",
    ("24", "08"): "ExceptionAndInterceptor",
    ("24", "09"): "StudentServiceCase",
    # 第 25 章（课程扩展）
    ("25", "01"): "WhyRedis",
    ("25", "02"): "FiveDataTypes",
    ("25", "03"): "KeyDesignAndExpire",
    ("25", "04"): "PersistenceDemo",
    ("25", "05"): "TransactionAndLua",
    ("25", "06"): "ClusterTopology",
    ("25", "07"): "SpringDataRedisDemo",
    ("25", "08"): "CachePatterns",
    ("25", "09"): "CacheProblems",
    ("25", "10"): "DistributedLock",
    ("25", "11"): "ItemCacheCase",
    # 第 26 章（课程扩展）
    ("26", "01"): "AopIntro",
    ("26", "02"): "AdviceTypes",
    ("26", "03"): "LogAspectDemo",
    ("26", "04"): "PermissionAspect",
    ("26", "05"): "MyBatisPlusIntro",
    ("26", "06"): "WrapperAndPage",
    ("26", "07"): "FileUploadDemo",
    ("26", "08"): "JwtDemo",
    ("26", "09"): "LoginFlow",
    ("26", "10"): "AuthInterceptor",
    ("26", "11"): "UserModuleCase",
    # 第 27 章（课程扩展）
    ("27", "01"): "RestApiDesign",
    ("27", "02"): "LayeredArchitecture",
    ("27", "03"): "DtoValidation",
    ("27", "04"): "GlobalExceptionHandler",
    ("27", "05"): "PageQuery",
    ("27", "06"): "StockConcurrency",
    ("27", "07"): "IdempotentSubmit",
    ("27", "08"): "Observability",
    ("27", "09"): "Deployment",
    ("27", "10"): "ApiContract",
    ("27", "11"): "OrderFlowCase",
    # 第 28 章（课程扩展）
    ("28", "01"): "QueueIntro",
    ("28", "02"): "MessageModel",
    ("28", "03"): "ReliableSend",
    ("28", "04"): "AckAndRetry",
    ("28", "05"): "ConsumeIdempotent",
    ("28", "06"): "OrderAndDelayMessage",
    ("28", "07"): "BacklogDiagnosis",
    ("28", "08"): "DelayQueueDesign",
    ("28", "09"): "TransactionMessage",
    ("28", "10"): "MqSelection",
    ("28", "11"): "OrderAsyncCase",
    # 第 29 章（课程扩展）
    ("29", "01"): "MonolithToMicro",
    ("29", "02"): "ServiceSplit",
    ("29", "03"): "RegistryDiscovery",
    ("29", "04"): "RemoteCall",
    ("29", "05"): "LoadBalanceRetry",
    ("29", "06"): "ApiGateway",
    ("29", "07"): "ConfigCenter",
    ("29", "08"): "CircuitBreaker",
    ("29", "09"): "DistributedTx",
    ("29", "10"): "TraceLogging",
    ("29", "11"): "MicroserviceOrderCase",
    # 第 30 章（课程扩展）
    ("30", "01"): "JvmMemoryLayout",
    ("30", "02"): "ObjectLayout",
    ("30", "03"): "GcBasics",
    ("30", "04"): "GcCollectors",
    ("30", "05"): "ClassLoading",
    ("30", "06"): "DiagnosticTools",
    ("30", "07"): "MemoryLeakHunt",
    ("30", "08"): "CpuHotspot",
    ("30", "09"): "GcLogAnalysis",
    ("30", "10"): "PerfTuning",
    ("30", "11"): "OnlineTroubleshootCase",
    ("31", "01"): "DesignPatternIntro",
    ("31", "02"): "SingletonPattern",
    ("31", "03"): "FactoryPattern",
    ("31", "04"): "BuilderPattern",
    ("31", "05"): "ProxyDecorator",
    ("31", "06"): "AdapterFacade",
    ("31", "07"): "StrategyState",
    ("31", "08"): "ObserverChain",
    ("31", "09"): "TemplateCallback",
    ("31", "10"): "RefactorSmells",
    ("31", "11"): "PromotionEngineCase",
    ("32", "01"): "ThreadPoolBasics",
    ("32", "02"): "RejectPolicy",
    ("32", "03"): "CompletableFutureDemo",
    ("32", "04"): "AqsReentrantLock",
    ("32", "05"): "ReadWriteLockDemo",
    ("32", "06"): "ConcurrentContainers",
    ("32", "07"): "SyncTools",
    ("32", "08"): "AtomicCas",
    ("32", "09"): "ThreadCooperation",
    ("32", "10"): "ConcurrencyDiagnose",
    ("32", "11"): "SeckillCase",
    ("33", "01"): "UnitTestBasics",
    ("33", "02"): "TestDoubles",
    ("33", "03"): "TestDesignCases",
    ("33", "04"): "CoverageMetrics",
    ("33", "05"): "MockStubbing",
    ("33", "06"): "TestPyramid",
    ("33", "07"): "TestDataBuilder",
    ("33", "08"): "ExceptionContract",
    ("33", "09"): "StaticAnalysis",
    ("33", "10"): "CiPipeline",
    ("33", "11"): "QualitySystemCase",

    ("34", "01"): "ComplexityAnalysis",
    ("34", "02"): "DynamicArray",
    ("34", "03"): "LinkedListDemo",
    ("34", "04"): "StackQueue",
    ("34", "05"): "HashTableDemo",
    ("34", "06"): "BinarySearchTree",
    ("34", "07"): "HeapDemo",
    ("34", "08"): "GraphDemo",
    ("34", "09"): "SortingCompare",
    ("34", "10"): "BinarySearchVariants",
    ("34", "11"): "LruRankingCase",

    ("35", "01"): "NetworkLayers",
    ("35", "02"): "TcpHandshake",
    ("35", "03"): "TcpReliable",
    ("35", "04"): "UdpQuic",
    ("35", "05"): "HttpMessage",
    ("35", "06"): "HttpCache",
    ("35", "07"): "TlsHandshake",
    ("35", "08"): "Http2Mux",
    ("35", "09"): "DnsCdn",
    ("35", "10"): "WebSocketDemo",
    ("35", "11"): "RequestJourney",

    ("36", "01"): "ThreatModel",
    ("36", "02"): "SqlInjectionDemo",
    ("36", "03"): "XssEncoding",
    ("36", "04"): "CsrfDefense",
    ("36", "05"): "IdorDemo",
    ("36", "06"): "PasswordStorage",
    ("36", "07"): "JwtSessionDemo",
    ("36", "08"): "DeserializeSsrf",
    ("36", "09"): "SensitiveDataMasking",
    ("36", "10"): "SupplyChainHeaders",
    ("36", "11"): "SecurityReviewCase",
    # 第 37 章（课程扩展：Docker 与容器化）
    ("37", "01"): "DockerVsVm",
    ("37", "02"): "ImageLayers",
    ("37", "03"): "DockerfileDemo",
    ("37", "04"): "ContainerLifecycle",
    ("37", "05"): "DockerNetwork",
    ("37", "06"): "DockerVolume",
    ("37", "07"): "ComposeDemo",
    ("37", "08"): "JavaContainerize",
    ("37", "09"): "ImageSlim",
    ("37", "10"): "K8sIntro",
    ("37", "11"): "ContainerizeCase",
    # 第 38 章（课程扩展：Elasticsearch 与全文搜索）
    ("38", "01"): "WhyNotLike",
    ("38", "02"): "InvertedIndex",
    ("38", "03"): "ChineseTokenizer",
    ("38", "04"): "IndexMapping",
    ("38", "05"): "DslQuery",
    ("38", "06"): "RelevanceScore",
    ("38", "07"): "AggregationDemo",
    ("38", "08"): "DeepPagination",
    ("38", "09"): "DataSync",
    ("38", "10"): "EsCluster",
    ("38", "11"): "ProductSearchCase",
    # 第 39 章（课程扩展：海量数据与分库分表）
    ("39", "01"): "BigTableProblems",
    ("39", "02"): "SlowQueryOptimize",
    ("39", "03"): "DeepPaginationFix",
    ("39", "04"): "ReadWriteSplit",
    ("39", "05"): "ShardingStrategy",
    ("39", "06"): "ShardRouting",
    ("39", "07"): "DistributedId",
    ("39", "08"): "CrossShardQuery",
    ("39", "09"): "CrossDbConsistency",
    ("39", "10"): "HotColdArchive",
    ("39", "11"): "OrderShardingCase",
    # 第 40 章（课程扩展：Java AI 应用开发）
    ("40", "01"): "LlmBasics",
    ("40", "02"): "LlmApiCall",
    ("40", "03"): "PromptEngineering",
    ("40", "04"): "SseStreaming",
    ("40", "05"): "ChatMemory",
    ("40", "06"): "FunctionCalling",
    ("40", "07"): "EmbeddingSearch",
    ("40", "08"): "RagPipeline",
    ("40", "09"): "AiFrameworkDemo",
    ("40", "10"): "AiEngineering",
    ("40", "11"): "SmartSupportCase",
    # 第 41 章（课程扩展：CI/CD 与发布工程）
    ("41", "01"): "GitBranchModel",
    ("41", "02"): "CommitConvention",
    ("41", "03"): "PipelineStages",
    ("41", "04"): "ArtifactRegistry",
    ("41", "05"): "EnvironmentPromotion",
    ("41", "06"): "RollingBlueGreen",
    ("41", "07"): "CanaryRelease",
    ("41", "08"): "FeatureFlags",
    ("41", "09"): "DbMigration",
    ("41", "10"): "ReleaseAudit",
    ("41", "11"): "PipelineCase",
    # 第 42 章（课程扩展：可观测性平台）
    ("42", "01"): "WhyObservability",
    ("42", "02"): "StructuredLogging",
    ("42", "03"): "LogStoreQuery",
    ("42", "04"): "MetricsModel",
    ("42", "05"): "SloGoldenSignals",
    ("42", "06"): "AlertDesign",
    ("42", "07"): "TraceSpans",
    ("42", "08"): "TraceSampling",
    ("42", "09"): "ContinuousProfiling",
    ("42", "10"): "DashboardIncident",
    ("42", "11"): "IncidentCase",
    # 第 43 章（课程扩展：实时计算与流处理）
    ("43", "01"): "StreamVsBatch",
    ("43", "02"): "TimeSemantics",
    ("43", "03"): "WindowsDemo",
    ("43", "04"): "WatermarkDemo",
    ("43", "05"): "StateCheckpoint",
    ("43", "06"): "ExactlyOnce",
    ("43", "07"): "StreamJoin",
    ("43", "08"): "Backpressure",
    ("43", "09"): "CdcWarehouse",
    ("43", "10"): "FlinkVsSpark",
    ("43", "11"): "RealtimeDashboard",
    # 第 44 章（课程扩展：AI 进阶 Agent 与本地模型）
    ("44", "01"): "AgentIntro",
    ("44", "02"): "ReActLoop",
    ("44", "03"): "AgentMemory",
    ("44", "04"): "MultiAgent",
    ("44", "05"): "LocalLlm",
    ("44", "06"): "FineTuning",
    ("44", "07"): "McpEcosystem",
    ("44", "08"): "AiGateway",
    ("44", "09"): "AgentSecurity",
    ("44", "10"): "EvalDriven",
    ("44", "11"): "OpsAgentCase",
    # 第 45 章（课程扩展：分布式一致性与共识协议）
    ("45", "01"): "WhyConsensus",
    ("45", "02"): "CapSpectrum",
    ("45", "03"): "RaftElection",
    ("45", "04"): "RaftLogReplication",
    ("45", "05"): "ZookeeperConcepts",
    ("45", "06"): "EtcdLinearRead",
    ("45", "07"): "DistributedClocks",
    ("45", "08"): "LeaseFencing",
    ("45", "09"): "QuorumReplication",
    ("45", "10"): "ConsensusCost",
    ("45", "11"): "ConfigCenterCase",
    # 第 46 章（课程扩展：湖仓一体与数据平台）
    ("46", "01"): "WarehouseToLake",
    ("46", "02"): "ParquetColumnar",
    ("46", "03"): "ObjectStorage",
    ("46", "04"): "HiveMetastore",
    ("46", "05"): "IcebergTableFormat",
    ("46", "06"): "LakeStreaming",
    ("46", "07"): "MppAdhoc",
    ("46", "08"): "DataGovernance",
    ("46", "09"): "DataIngestion",
    ("46", "10"): "DataPlatformService",
    ("46", "11"): "LakehouseCase",
    # 第 47 章（课程扩展：云原生进阶 Mesh 与 Serverless）
    ("47", "01"): "WhyServiceMesh",
    ("47", "02"): "SidecarDataplane",
    ("47", "03"): "MeshTrafficPolicy",
    ("47", "04"): "MeshMtls",
    ("47", "05"): "MeshObservability",
    ("47", "06"): "MeshCost",
    ("47", "07"): "ServerlessFn",
    ("47", "08"): "ElasticCost",
    ("47", "09"): "MultiCluster",
    ("47", "10"): "ChaosEngineering",
    ("47", "11"): "MeshCase",
    # 第 48 章（课程扩展：推理工程与模型服务）
    ("48", "01"): "InferenceBasics",
    ("48", "02"): "KvCache",
    ("48", "03"): "ContinuousBatching",
    ("48", "04"): "PagedAttention",
    ("48", "05"): "InferenceAcceleration",
    ("48", "06"): "ServingCapacity",
    ("48", "07"): "ServingStreaming",
    ("48", "08"): "ServingHA",
    ("48", "09"): "InferenceCost",
    ("48", "10"): "InferenceObservability",
    ("48", "11"): "InferenceCase",
    # 第 49 章（课程扩展：领域驱动设计与架构模式）
    ("49", "01"): "DddOverview",
    ("49", "02"): "DddTactical",
    ("49", "03"): "DomainEventFlow",
    ("49", "04"): "HexagonalArch",
    ("49", "05"): "CqrsPattern",
    ("49", "06"): "EventSourcing",
    ("49", "07"): "AntiCorruptionLayer",
    ("49", "08"): "DddMicroservices",
    ("49", "09"): "MarketingModeling",
    ("49", "10"): "LegacyRefactor",
    ("49", "11"): "OrderDomainCase",
    # 第 50 章（课程扩展：响应式编程与虚拟线程）
    ("50", "01"): "BlockingVsNonBlocking",
    ("50", "02"): "ReactorCore",
    ("50", "03"): "WebFluxPatterns",
    ("50", "04"): "VirtualThreadInternals",
    ("50", "05"): "VirtualThreadPractice",
    ("50", "06"): "VtPitfalls",
    ("50", "07"): "ReactiveVsVt",
    ("50", "08"): "CfIntegration",
    ("50", "09"): "IoBenchmark",
    ("50", "10"): "MigrationPath",
    ("50", "11"): "AggregationGatewayCase",
    # 第 51 章（课程扩展：支付与资金系统设计）
    ("51", "01"): "PaymentOverview",
    ("51", "02"): "ChannelIntegration",
    ("51", "03"): "PaymentStateMachine",
    ("51", "04"): "CallbackReliability",
    ("51", "05"): "ReconciliationSystem",
    ("51", "06"): "RefundComplexity",
    ("51", "07"): "FundSafety",
    ("51", "08"): "ClearingLedger",
    ("51", "09"): "PaymentTransactions",
    ("51", "10"): "PaymentHa",
    ("51", "11"): "PaymentCase",
    # 第 52 章（课程扩展：毕业总纲）
    ("52", "01"): "RequirementCapacity",
    ("52", "02"): "ArchitectureDecision",
    ("52", "03"): "TechSelectionTree",
    ("52", "04"): "SystemDesignMethod",
    ("52", "05"): "NonFunctionalTradeoff",
    ("52", "06"): "KnowledgeMap",
    ("52", "07"): "GraduationChecklist",
    ("52", "08"): "PlatformArchitecture",
    ("52", "09"): "EvolutionRoadmap",
    ("52", "10"): "GradDefense",
    ("52", "11"): "ClosingWords",
    # 第 53 章（课程扩展：Spring Cloud Alibaba 生态）
    ("53", "01"): "AlibabaLandscape",
    ("53", "02"): "NacosRegistry",
    ("53", "03"): "NacosConfig",
    ("53", "04"): "DubboRpc",
    ("53", "05"): "SentinelGuard",
    ("53", "06"): "SeataAtDemo",
    ("53", "07"): "GatewayChainDemo",
    ("53", "08"): "AgentInstrumentDemo",
    ("53", "09"): "AliStackIntegration",
    ("53", "10"): "ScVsSca",
    ("53", "11"): "AliLaunchCase",
    # 第 54 章（课程扩展：推荐系统入门）
    ("54", "01"): "RecsysOverview",
    ("54", "02"): "RecallFusion",
    ("54", "03"): "ItemCFDemo",
    ("54", "04"): "FeatureDemo",
    ("54", "05"): "TwoTowerDemo",
    ("54", "06"): "RerankDemo",
    ("54", "07"): "BloomFilterDemo",
    ("54", "08"): "AbSplitDemo",
    ("54", "09"): "RealtimeFeatureDemo",
    ("54", "10"): "ColdStartDemo",
    ("54", "11"): "ProductRecCase",
    # 第 55 章（课程扩展：多租户 SaaS 架构）
    ("55", "01"): "SaaSOverview",
    ("55", "02"): "TenantIsolation",
    ("55", "03"): "TenantContext",
    ("55", "04"): "TenantDataSafety",
    ("55", "05"): "TenantQuotaBilling",
    ("55", "06"): "TenantConfig",
    ("55", "07"): "TenantLifecycle",
    ("55", "08"): "NoisyNeighborDemo",
    ("55", "09"): "TenantObservability",
    ("55", "10"): "SaaSCompliance",
    ("55", "11"): "TenantCmsCase",
    # 第 56 章（课程扩展：稳定性工程与容量管理）
    ("56", "01"): "StabilityOverview",
    ("56", "02"): "ShadowRoutingDemo",
    ("56", "03"): "WatermarkDemo2",
    ("56", "04"): "ContingencyPlatform",
    ("56", "05"): "PromotionWarRoom",
    ("56", "06"): "DrillRoutines",
    ("56", "07"): "RiskRegisterDemo",
    ("56", "08"): "DependencyGovernance",
    ("56", "09"): "AnnualCapacityPlan",
    ("56", "10"): "StabilityMetrics",
    ("56", "11"): "StabilityCase",
}

# 实操题 tag -> 类名
PRACTICE_NAMES = {
    "0201": "SmartOrderBill",
    "0202": "PaceTimeConverter",
    "0203": "MemberPointsLevel",
    "0301": "ThreeRoundBattle",
    "0302": "ScoreGradeCounter",
    "0303": "NumberPyramid",
    "0304": "MenuGuessNumber",
    "0401": "StoreSalesReport",
    "0402": "MemberCardShuffle",
    "0501": "PersonalFinanceUtil",
    "0502": "ArrayUtilKit",
    "0601": "ArrayReferenceReport",
    "0602": "MethodArrayAnalysis",
    "0701": "LuckyRedPacket",
    "0702": "TrappingRainWaterTwoPointer",
    "0703": "LotteryNumberGenerator",
    "0801": "StudentProfile",
    "0802": "BankAccount",
    "0901": "ObjectMemoryThisLab",
    "1001": "StaticArrayUtil",
    "1002": "OrderStatusFlow",
    "1101": "EmployeeSalarySystem",
    "1102": "PaymentInterfaceSystem",
    "1103": "AnonymousCallback",
    "1201": "UserRegisterValidator",
    "1202": "SensitiveWordFilter",
    "1203": "ContactManager",
    "1301": "LibraryManagementSystem",
    "1401": "InstallmentCalculator",
    "1": "CampusSecondhandPlatform",
    "2": "GymBookingSystem",
    "3": "CardBattleGame",
    # 平台篇（第37~41章）
    "3701": "ImageCacheTask",
    "3702": "ExitCodeDoctor",
    "3801": "InvertedIndexTask",
    "3802": "Bm25Ranker",
    "3901": "GeneShardingTask",
    "3902": "SnowflakeTask",
    "4001": "ExtractPipelineTask",
    "4002": "SemanticCacheTask",
    "4101": "PipelineGateTask",
    "4102": "CanaryJudgeTask",
    # 观测篇（第42~45章）
    "4201": "HistogramTask",
    "4202": "AlertAggregator",
    "4301": "WindowAggregator",
    "4302": "WatermarkTask",
    "4401": "ReActExecutor",
    "4402": "AgentGates",
    "4501": "RaftElectionTask",
    "4502": "FencingStore",
    # 架构篇（第46~49章）
    "4601": "SnapshotTable",
    "4602": "RowGroupPruning",
    "4701": "CanaryRouter",
    "4702": "ChaosExperiment",
    "4801": "KvBudgetPlanner",
    "4802": "BatchScheduler",
    "4901": "OrderAggregateTask",
    "4902": "OutboxTask",
    # 收官篇（第50~52章）
    "5001": "VtFanoutTask",
    "5002": "PinningTask",
    "5101": "IdempotentPayment",
    "5102": "DoubleEntryTask",
    "5201": "CapacityEstimator",
    "5202": "AdrGenerator",
    # 毕业进阶综合卷
    "A1": "PlatformExam",
    "A2": "ObservationExam",
    "A3": "ArchitectureExam",
    # 核心篇（第15~19章）
    "1501": "StreamPipelineTask",
    "1502": "MapInvertTask",
    "1601": "FileWalkTask",
    "1602": "SerializationTask",
    "1701": "BankTransferTask",
    "1702": "ThreadPoolTuner",
    "1801": "TcpEchoTask",
    "1802": "HttpClientTask",
    "1901": "RouteScannerTask",
    "1902": "DiContainerTask",
    # 框架篇（第20~24章）
    "2001": "MiniSqlTask",
    "2002": "IsolationTask",
    "2101": "JdbcDaoTask",
    "2102": "BatchTxTask",
    "2201": "DependencyMediator",
    "2202": "LifecycleSimTask",
    "2301": "MapperProxyTask",
    "2302": "DynamicSqlTask",
    "2401": "AutoConfigTask",
    "2402": "ValidationAggregator",
    # 中间件篇（第25~29章）
    "2501": "CacheGuardTask",
    "2502": "SetnxLockTask",
    "2601": "AopChainTask",
    "2602": "JwtMiniTask",
    "2701": "OrchestrateTask",
    "2702": "ConditionalDeductTask",
    "2801": "RetryDlqTask",
    "2802": "DelayCloseTask",
    "2901": "HeartbeatRegistry",
    "2902": "CircuitBreakerTask2",
    "2903": "ConfigClientTask",
    "2904": "LeastConnTask",
    # 深度篇（第30~36章）
    "3001": "LeakCurveTask",
    "3002": "GcLogParser",
    "3101": "BranchDictTask",
    "3102": "AuditProxyTask",
    "3201": "WordMergeTask",
    "3202": "DeadlockDetector",
    "3301": "MiniTestRunner",
    "3302": "MiniMockTask",
    "3401": "LruTask",
    "3402": "TopKTask",
    "3501": "HttpParserTask2",
    "3502": "TcpStateMachine2",
    "3601": "InjectCompareTask",
    "3602": "SaltHashTask",
    # 第15~36章综合卷
    "M1": "MidBusinessExam",
    "M2": "MidStackExam",
    "M3": "MidQualityExam",
    "A1": "PlatformExam",
    "A2": "ObservationExam",
    "A3": "ArchitectureExam",
}

CHAPTER_DIRS = {
    "02": "语法篇", "03": "判断与循环", "04": "数组", "05": "方法",
    "06": "原理篇", "07": "算法训练", "08": "面向对象",
    "09": "面向对象原理篇", "10": "面向对象进阶", "11": "面向对象高级",
    "12": "常见API", "13": "阶段项目实战", "14": "常用类API",
    "15": "集合框架", "16": "IO流与File", "17": "多线程",
    "18": "网络编程", "19": "反射与注解", "20": "MySQL基础", "21": "JDBC",
    "22": "Maven与项目构建", "23": "MyBatis", "24": "SpringBoot入门",
    "25": "Redis与缓存", "26": "SpringBoot进阶", "27": "项目实战",
    "28": "消息队列与异步", "29": "微服务入门", "30": "JVM与性能调优",
    "31": "设计模式与重构", "32": "并发编程进阶", "33": "测试与质量保障",
    "34": "数据结构与算法进阶", "35": "计算机网络与HTTP协议",
    "36": "安全编码与漏洞防护", "37": "Docker与容器化部署",
    "38": "Elasticsearch与全文搜索", "39": "海量数据与分库分表",
    "40": "JavaAI应用开发入门", "41": "CICD与发布工程",
    "42": "可观测性平台", "43": "实时计算与流处理",
    "44": "AI进阶Agent与本地模型", "45": "分布式一致性与共识协议",
    "46": "湖仓一体与数据平台", "47": "云原生进阶Mesh与Serverless",
    "48": "推理工程与模型服务", "49": "领域驱动设计与架构模式",
    "50": "响应式编程与虚拟线程", "51": "支付与资金系统设计",
    "52": "毕业总纲系统设计与技术决策",
    "53": "SpringCloudAlibaba生态实战", "54": "推荐系统入门",
    "55": "多租户SaaS架构", "56": "稳定性工程与容量管理",
}

# 官方课程到第 14 章结束，以下章节为按同一格式补充的扩展内容
EXT_CHAPTERS = {"15", "16", "17", "18", "19", "20", "21", "22", "23", "24",
                "25", "26", "27", "28", "29", "30", "31", "32", "33",
                "34", "35", "36", "37", "38", "39", "40",
                "41", "42", "43", "44", "45", "46", "47", "48",
                "49", "50", "51", "52", "53", "54", "55", "56"}

PRACTICE_PKGS = {
    "基础篇_第02-05章": "com.heima.practice.basic",
    "进阶篇_第06-10章": "com.heima.practice.advanced",
    "综合篇_第11-14章": "com.heima.practice.comprehensive",
    "毕业综合卷": "com.heima.practice.exam",
    "平台篇_第37-41章": "com.heima.practice.platform",
    "观测篇_第42-45章": "com.heima.practice.observability",
    "架构篇_第46-49章": "com.heima.practice.architecture",
    "收官篇_第50-52章": "com.heima.practice.graduation",
    "毕业进阶综合卷": "com.heima.practice.examadvanced",
    "核心篇_第15-19章": "com.heima.practice.core",
    "框架篇_第20-24章": "com.heima.practice.framework",
    "中间件篇_第25-29章": "com.heima.practice.middleware",
    "深度篇_第30-36章": "com.heima.practice.deepdive",
    "第15-36章综合卷": "com.heima.practice.midexam",
}

# 辅助类改名（原类名 -> 新类名），按课号生效
HELPER_RENAME = {
    ("11", "04"): {"Fu": "Father", "Zi": "Son"},
    ("11", "09"): {"Fu": "Father", "Zi": "Son"},
    ("11", "17"): {"Inter": "Greeting", "InterImpl": "GreetingImpl"},
    ("11", "18"): {"Outer": "OuterDemo"},
    ("11", "19"): {"Outer": "OuterDemo"},
    ("11", "20"): {"Outer": "OuterDemo"},
    ("11", "15"): {"Swim": "Swimmable"},
    ("11", "21"): {"Swim": "Swimmable"},
    ("12", "01"): {},
}

# 辅助类的"绝对改名"（不拼入口类前缀），用于类名过长、需要保留可读性的场景
# 注意：改名后仍需保证同一包内唯一
ABS_RENAME = {
    ("21", "03"): {"JdbcResultSetMapStudent": "StudentRecord"},
    ("21", "07"): {"JdbcUtilDemoUtil": "JdbcUtil"},
    ("21", "08"): {"JdbcDaoCaseStudent": "StudentEntity", "JdbcDaoCaseDao": "StudentDao"},
}

# 代码中（非注释/字符串）需要纠正的标识符
CODE_FIX = {"学生成绩": "studentScore"}

HEADER = """/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：{no}
 * 课名：{title}
 * ------------------------------------------------------------
 * 知识点：
{points}
 * ============================================================
 */
"""

# 扩展章节的文件头：注明官方课程到第 14 章结束，本章为同格式补充内容
HEADER_EXT = """/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：{no}
 * 课名：{title}
 * ------------------------------------------------------------
 * 知识点：
{points}
 * ============================================================
 */
"""


def safe_title(t):
    t = re.sub(r"[^\w\u4e00-\u9fff]+", "_", t)
    return re.sub(r"_+", "_", t).strip("_") or "Lesson"


def bullets(text):
    res = []
    for line in text.splitlines():
        s = line.strip()
        if not s:
            continue
        if s.startswith("- ") or s.startswith("* "):
            res.append(s[2:].strip())
        elif res:
            res[-1] += " " + s
    return res


def parse_md(path):
    text = open(path, encoding="utf-8").read()
    ms = list(re.finditer(r"^### (\d+)-(\d+)\s+(.+?)\s*$", text, re.M))
    out = []
    for i, m in enumerate(ms):
        cc, ll, title = m.group(1).zfill(2), m.group(2), m.group(3).strip()
        seg = text[m.end(): ms[i + 1].start() if i + 1 < len(ms) else len(text)]
        ks, es, ps = seg.find("**知识点**"), seg.find("**示例代码**"), seg.find("**课后练习**")
        if min(ks, es) < 0:
            print("SKIP(no sections):", cc, ll, title)
            continue
        out.append(dict(cc=cc, ll=ll, no=f"{int(cc)}-{ll}", title=title,
                        knowledge=seg[ks + len("**知识点**"):es],
                        examples=seg[es + len("**示例代码**"):ps if ps >= 0 else len(seg)],
                        exercises=seg[ps + len("**课后练习**"):] if ps >= 0 else ""))
    return out


def parse_practice_md(path, kind, paper_tag_prefix="", paper_label_prefix="毕业综合卷"):
    text = open(path, encoding="utf-8").read()
    pattern = r"^## 卷([一二三四五六])\s+(.+?)\s*$" if kind == "paper" else r"^### 实操 (\d+)-(\d+)\s+(.+?)\s*$"
    ms = list(re.finditer(pattern, text, re.M))
    out = []
    for i, m in enumerate(ms):
        if kind == "paper":
            cc = ll = ""
            tag = paper_tag_prefix + str(i + 1)
            no_label = paper_label_prefix + " 卷" + m.group(1)
            title = m.group(2).strip()
        else:
            cc, ll = m.group(1).zfill(2), m.group(2)
            tag = f"{cc}{ll}"
            no_label = f"实操 {int(cc)}-{ll}"
            title = m.group(3).strip()
        seg = text[m.end(): ms[i + 1].start() if i + 1 < len(ms) else len(text)]
        ks, es, ps = seg.find("**知识点注释**"), seg.find("**示例代码**"), seg.find("**课后练习题**")
        if min(ks, es, ps) < 0:
            print("SKIP(practice):", no_label, title)
            continue
        out.append(dict(cc=cc, ll=ll, tag=tag, no=no_label, title=title,
                        knowledge=seg[ks + len("**知识点注释**"):es],
                        examples=seg[es + len("**示例代码**"):ps],
                        exercises=seg[ps + len("**课后练习题**"):]))
    return out


def clean_block(b):
    imports, lines = [], []
    for line in b.splitlines():
        s = line.strip()
        if re.match(r"^//\s*文件名[:：]", s):
            continue
        if re.match(r"^import\s+", s):
            imports.append(s)
            continue
        lines.append(line.rstrip())
    return "\n".join(lines).strip("\n"), imports


def exercise_comment(text):
    lines = []
    for line in text.splitlines():
        s = line.rstrip()
        if s.strip().startswith("```") or s.strip() in ("---", "***", "___"):
            continue
        lines.append(" * " + s.replace("**", "").replace("*/", "* /"))
    while lines and lines[0].strip() == "*":
        lines.pop(0)
    while lines and lines[-1].strip() == "*":
        lines.pop()
    if not lines:
        lines = [" * （本课未布置课后练习）"]
    return ("/*\n"
            " * ============================================================\n"
            " * 课后练习题（无参考答案）\n"
            " * ------------------------------------------------------------\n"
            + "\n".join(lines) + "\n"
            " * ============================================================\n"
            " */\n")


DECL = re.compile(
    r"^(?:public\s+)?(?:abstract\s+|final\s+|static\s+)*(?:class|interface|enum)\s+"
    r"([A-Za-z_\u4e00-\u9fff][\w\u4e00-\u9fff]*)", re.M)

CH13_DEPS = open(os.path.join(ROOT, "_build", "ch13_deps.txt"), encoding="utf-8").read() \
    if os.path.exists(os.path.join(ROOT, "_build", "ch13_deps.txt")) else ""


def build(les, pkg, out_dir, entry_name, tag=None, no_label=None):
    cc, ll = les.get("cc", ""), les.get("ll", "")
    title, no = les["title"], no_label or les.get("no", "")
    pts = bullets(les["knowledge"]) or ["（见课名与示例代码）"]
    tpl = HEADER_EXT if cc in EXT_CHAPTERS else HEADER
    header = tpl.format(no=no, title=title,
                        points="\n".join(" *   - " + p.replace("*/", "* /") for p in pts))

    blocks = re.findall(r"```java\s*\n(.*?)```", les["examples"], re.S)
    if not blocks:
        print("SKIP(no java):", no, title)
        return None
    body_parts, imports = [], []
    for b in blocks:
        nb, im = clean_block(b)
        imports += im
        if nb.strip():
            body_parts.append(nb)
    if cc == "13" and CH13_DEPS and 5 <= int(ll) <= 10:
        body_parts.insert(0, CH13_DEPS)
    body = "\n\n".join(body_parts)

    for old, new in CODE_FIX.items():
        body = re.sub(r"(?<!\w)" + re.escape(old) + r"(?!\w)", new, body)

    seen, imp_lines = set(), []
    for im in imports:
        if im not in seen:
            seen.add(im)
            imp_lines.append(im)

    matches = list(DECL.finditer(body))
    if not matches:
        print("SKIP(no type):", no, title)
        return None
    type_names = [m.group(1) for m in matches]

    entry_old = None
    for i, m in enumerate(matches):
        end = matches[i + 1].start() if i + 1 < len(matches) else len(body)
        if re.search(r"\bpublic\s+static\s+void\s+main\s*\(", body[m.start():end]):
            entry_old = m.group(1)
            break

    renames = {}
    for old in set(type_names):
        if old == entry_old:
            renames[old] = entry_name
        elif old in ABS_RENAME.get((cc, ll), {}):
            renames[old] = ABS_RENAME[(cc, ll)][old]
        else:
            base = HELPER_RENAME.get((cc, ll), {}).get(old, old)
            renames[old] = entry_name + base

    for old in sorted(renames, key=len, reverse=True):
        body = re.sub(r"(?<!\w)" + re.escape(old) + r"(?!\w)", renames[old], body)

    body = re.sub(
        r"^public\s+(?=(?:abstract\s+|final\s+|static\s+)*(?:class|interface|enum)\s+)",
        "", body, flags=re.M)
    if entry_old is not None:
        body = re.sub(
            r"^((?:abstract\s+|final\s+|static\s+)*(?:class|interface|enum)\s+"
            + re.escape(entry_name) + r"\b)", r"public \1", body, count=1, flags=re.M)
        runner = ""
    else:
        runner = (f"\npublic class {entry_name} {{\n"
                  "    public static void main(String[] args) {\n"
                  '        System.out.println("本课为设计/原理示例，请阅读代码与注释，并完成文件末尾的课后练习。");\n'
                  "    }\n}\n")

    code = header + "\npackage " + pkg + ";\n"
    if imp_lines:
        code += "\n" + "\n".join(imp_lines) + "\n"
    code += "\n// ==================== 示例代码 ====================\n\n" + body.strip() + "\n" + runner
    code += "\n" + exercise_comment(les["exercises"])

    fname = entry_name + ".java"
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, fname), "w", encoding="utf-8", newline="\n") as f:
        f.write(code)
    return dict(no=no, title=title, fname=fname, entry=entry_name)


def write_readme(out_dir, pkg, rows, header_text):
    lines = [f"# {header_text}", "", f"`package {pkg}`", "",
             "| 编号 | 课名 | 文件 |", "| --- | --- | --- |"]
    for r in rows:
        lines.append(f"| {r['no']} | {r['title']} | [{r['fname']}]({r['fname']}) |")
    lines += ["", "> 练习题在每课文件末尾的注释块中，无参考答案。"]
    open(os.path.join(out_dir, "README.md"), "w", encoding="utf-8", newline="\n").write(
        "\n".join(lines) + "\n")


def main():
    # 清空旧的中文目录结构（02_语法篇/ 等），只保留 com/
    if os.path.exists(JAVA):
        for name in os.listdir(JAVA):
            if name == "com":
                continue
            p = os.path.join(JAVA, name)
            if os.path.isdir(p):
                shutil.rmtree(p, ignore_errors=True)
            else:
                try:
                    os.remove(p)
                except OSError:
                    pass
    if os.path.exists(PKG_ROOT):
        shutil.rmtree(PKG_ROOT, ignore_errors=True)

    index, counts = {}, []
    for md in sorted(glob.glob(os.path.join(SRC, "第*.md"))):
        m = re.match(r"第(\d+)章", os.path.basename(md))
        if not m:
            continue
        cc = m.group(1).zfill(2)
        if cc not in CHAPTER_DIRS:
            continue
        pkg = f"com.heima.ch{cc}"
        out_dir = os.path.join(JAVA, *pkg.split("."))
        rows = []
        for les in parse_md(md):
            name = NAMES.get((les["cc"], les["ll"]))
            if not name:
                print("NO NAME:", les["cc"], les["ll"], les["title"])
                continue
            r = build(les, pkg, out_dir, name)
            if r:
                rows.append(r)
        index[cc] = (pkg, rows)
        write_readme(out_dir, pkg, rows, f"{CHAPTER_DIRS[cc]}（{pkg}）")
        print(f"{pkg}: {len(rows)} 课")

    practice_sets = [
        ("第02-05章-基础实操.md", "基础篇_第02-05章", "practice", "", "毕业综合卷"),
        ("第06-10章-进阶实操.md", "进阶篇_第06-10章", "practice", "", "毕业综合卷"),
        ("第11-14章-综合实操.md", "综合篇_第11-14章", "practice", "", "毕业综合卷"),
        ("毕业综合实操卷.md", "毕业综合卷", "paper", "", "毕业综合卷"),
        ("第37-41章-平台实操.md", "平台篇_第37-41章", "practice", "", "毕业进阶卷"),
        ("第42-45章-观测流共识实操.md", "观测篇_第42-45章", "practice", "", "毕业进阶卷"),
        ("第46-49章-数据架构实操.md", "架构篇_第46-49章", "practice", "", "毕业进阶卷"),
        ("第50-52章-毕业收官实操.md", "收官篇_第50-52章", "practice", "", "毕业进阶卷"),
        ("毕业进阶综合卷.md", "毕业进阶综合卷", "paper", "A", "毕业进阶卷"),
        ("第15-19章-核心进阶实操.md", "核心篇_第15-19章", "practice", "", "第15-36章综合卷"),
        ("第20-24章-框架实操.md", "框架篇_第20-24章", "practice", "", "第15-36章综合卷"),
        ("第25-29章-中间件实操.md", "中间件篇_第25-29章", "practice", "", "第15-36章综合卷"),
        ("第30-36章-深度质量实操.md", "深度篇_第30-36章", "practice", "", "第15-36章综合卷"),
        ("第15-36章综合卷.md", "第15-36章综合卷", "paper", "M", "第15-36章综合卷"),
    ]
    practice_rows = []
    for fname, folder, kind, tag_prefix, label_prefix in practice_sets:
        src = os.path.join(SRC, "毕业实操题", fname)
        if not os.path.exists(src):
            continue
        pkg = PRACTICE_PKGS[folder]
        out_dir = os.path.join(JAVA, *pkg.split("."))
        rows = []
        for les in parse_practice_md(src, kind, tag_prefix, label_prefix):
            name = PRACTICE_NAMES.get(les["tag"])
            if not name:
                print("NO NAME:", les["tag"], les["title"])
                continue
            r = build(les, pkg, out_dir, name, no_label=les["no"])
            if r:
                rows.append(r)
        practice_rows.append((folder, pkg, len(rows)))
        write_readme(out_dir, pkg, rows, f"毕业实操题 / {folder}（{pkg}）")
        print(f"{pkg}: {len(rows)} 题")

    total = sum(len(v[1]) for v in index.values())
    print("总计", total, "课 +", sum(c for _, _, c in practice_rows), "题")

    # 根 README
    lines = [
        "# JavaProject（黑马 2026 版 AI+Java 课程配套工程）", "",
        "> 课程来源：B站 BV1TJxCzSEEZ《黑马程序员2026版AI+Java零基础全套视频课程》",
        f"> 共 {total} 课（一课一个 `.java`），另含 {sum(c for _, _, c in practice_rows)} 个实操题文件。",
        "> 官方视频到第 14 章结束，`ch15`~`ch56` 为按同一格式补充的扩展章节。",
        "> 全部遵循 Java 命名规范：包名全小写点分隔，类名大驼峰。", "",
        "## 目录结构", "", "```",
        "src/main/java/com/heima/",
    ]
    for cc in sorted(index):
        pkg, rows = index[cc]
        lines.append(f"├── ch{cc}/  ({len(rows)} 课)")
    lines.append("└── practice/")
    for folder, pkg, cnt in practice_rows:
        lines.append(f"    ├── {pkg.split('.')[-1]}/  ({cnt} 题)  ← {folder}")
    lines += ["```", "",
              "## 命名规范", "",
              "- 包名：`com.heima.ch02` … `com.heima.ch56`，实操题 `com.heima.practice.basic` 等，全小写、点分隔；",
              "- 类名：英文大驼峰，如 `Literal`、`BmiCalculator`、`LibraryManagementSystem`；",
              "- 辅助类：`入口类名 + 原类名`，如 `InheritanceIntroAnimal`，保证同包内不重名；",
              "- 方法/变量：小驼峰，如 `main`、`studentScore`；常量：全大写下划线。", "",
              "## 如何运行", "",
              "**IntelliJ IDEA**：`File → Open` 选择 `JavaProject/pom.xml`，SDK 选 JDK 21，",
              "打开任意文件点 `main` 左侧绿色三角。", "",
              "**命令行**：", "",
              "```bash",
              "cd JavaProject",
              "javac -encoding UTF-8 -d out $(find src/main/java -name '*.java')",
              "java -cp out com.heima.ch02.Literal",
              "```", "",
              "> 课后练习题仅出题，不提供答案；示例代码只是课堂示范，建议先独立完成练习。", ""]
    open(os.path.join(OUT, "README.md"), "w", encoding="utf-8", newline="\n").write("\n".join(lines))


if __name__ == "__main__":
    main()
