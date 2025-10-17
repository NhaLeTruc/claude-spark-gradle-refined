# List of Issues

## Testing on MS Windows

### Useful commands

```bash
# list all dependencies in a tree
./gradlew dependencies --configuration runtimeClasspath

# list all tasks available in build.gradle.
./gradlew tasks --all
```

### I.a Local test run failure

+ STATUS: **<span style="color: yellow;">SOLVED</span>**
+ SOLUTION: modify `com.pipeline.cli.PipelineRunner` by adding `case _` catches all cases.
+ NOTE: Caused by `match` missing catch all cases in `com.pipeline.cli.PipelineRunner.main`.

```bash
java -jar build/libs/pipeline-app-1.0-SNAPSHOT.jar config/examples/simple-etl.json
Exception in thread "main" java.lang.NoClassDefFoundError: scala/MatchError
        at com.pipeline.cli.PipelineRunner.main(PipelineRunner.scala)      
Caused by: java.lang.ClassNotFoundException: scala.MatchError
        at java.base/jdk.internal.loader.BuiltinClassLoader.loadClass(BuiltinClassLoader.java:580)
        at java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:490)
```

### I.b Local test run failure

```bash
java -jar build/libs/pipeline-app-1.0-SNAPSHOT.jar config/examples/simple-etl.json
Exception in thread "main" java.lang.NoClassDefFoundError: org/slf4j/LoggerFactory
        at com.pipeline.cli.PipelineRunner$.<init>(PipelineRunner.scala:17)       
        at com.pipeline.cli.PipelineRunner$.<clinit>(PipelineRunner.scala)
        at com.pipeline.cli.PipelineRunner.main(PipelineRunner.scala)
Caused by: java.lang.ClassNotFoundException: org.slf4j.LoggerFactory
        at java.base/jdk.internal.loader.BuiltinClassLoader.loadClass(BuiltinClassLoader.java:580)
        at java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:490)
```

### II.a JUnit Tests failed to lunch

+ STATUS: **<span style="color: yellow;">SOLVED</span>**
+ SOLUTION: modify `build.gradle`

```conf
        dependencies {
                testImplementation "org.junit.platform:junit-platform-launcher:1.13.4"
                testRuntimeOnly "org.junit.vintage:junit-vintage-engine:5.10.0"
        }
```

+ NOTE: <span style="color: red;">discovered `gradle test` run nothing</span>

```bash
./gradlew unitTest && ./gradlew integrationTest
...
* What went wrong:
...
> Test process encountered an unexpected problem.
   > Could not start Gradle Test Executor 2: Failed to load JUnit Platform.  Please ensure that all JUnit Platform dependencies are available on the test's runtime classpath, including the JUnit Platform launcher.
```

### II.b Integration; Streaming; and Performance Tests Failures

`gradle clean build` complie, test, and build artifact from a clean state. 

> Claude deliberately excluded failed tests in complie and build steps.

```bash
gradle clean build          

> Task :compileScala
[Warn] : -target is deprecated: Scala 2.12 cannot emit valid class files for targets newer than 8 (this is possible with Scala 2.13). Use -release to compile against a specific platform API version.
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/main/scala/com/pipeline/avro/AvroConverter.scala:7:25: Unused import
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/main/scala/com/pipeline/avro/AvroConverter.scala:7:34: Unused import
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/main/scala/com/pipeline/avro/AvroConverter.scala:202:39: Unused import
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/main/scala/com/pipeline/avro/AvroConverter.scala:167:11: local val readerFieldNames in method checkBasicCompatibility is never used
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/main/scala/com/pipeline/avro/AvroConverter.scala:32:93: parameter value spark in method writeAvro is never used
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/main/scala/com/pipeline/avro/AvroConverter.scala:195:66: parameter value spark in method evolveSchema is never used     
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/main/scala/com/pipeline/avro/AvroConverter.scala:272:110: parameter value spark in method writeParquetWithAvroSchema is 
never used
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/main/scala/com/pipeline/core/Pipeline.scala:68:33: private default argument in class Pipeline is never used
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/main/scala/com/pipeline/credentials/VaultClient.scala:7:29: Unused import
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/main/scala/com/pipeline/examples/SecurityPolicyExample.scala:54:11: local val credentials in method demonstratePermissivePolicy is never used
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/main/scala/com/pipeline/examples/SecurityPolicyExample.scala:119:11: local val credentials in method demonstrateStrictPolicy is never used
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/main/scala/com/pipeline/examples/SecurityPolicyExample.scala:283:9: local val manager in method demonstrateWithPipeline 
is never used
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/main/scala/com/pipeline/exceptions/PipelineExceptions.scala:3:29: Unused import
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/main/scala/com/pipeline/operations/LoadMethods.scala:5:82: Unused import
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/main/scala/com/pipeline/operations/UserMethods.scala:184:47: Unused import
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/main/scala/com/pipeline/operations/UserMethods.scala:333:11: local var condition in value $anonfun is never used        
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/main/scala/com/pipeline/security/SecureCredentialManager.scala:7:20: Unused import
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/main/scala/com/pipeline/security/SecureCredentialManager.scala:7:29: Unused import
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/main/scala/com/pipeline/operations/LoadMethods.scala:397:30: method Once in class Trigger is deprecated
20 warnings found

> Task :compileTestScala
[Warn] : -target is deprecated: Scala 2.12 cannot emit valid class files for targets newer than 8 (this is possible with Scala 2.13). Use -release to compile against a specific platform API version.
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/test/scala/com/pipeline/contract/PipelineConfigSchemaTest.scala:169:9: local val invalidConfig2 in value <local PipelineConfigSchemaTest> is never used
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/test/scala/com/pipeline/integration/EndToEndPipelineTest.scala:3:28: Unused import
[Error] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/test/scala/com/pipeline/performance/PipelinePerformanceTest.scala:17:2: not found: type RunWith
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/test/scala/com/pipeline/performance/PipelinePerformanceTest.scala:3:27: Unused import
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/test/scala/com/pipeline/performance/PipelinePerformanceTest.scala:3:40: Unused import
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/test/scala/com/pipeline/performance/PipelinePerformanceTest.scala:5:33: Unused import
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/test/scala/com/pipeline/unit/core/ExtractStepTest.scala:3:40: Unused import
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/test/scala/com/pipeline/unit/core/PipelineContextTest.scala:5:30: Unused import
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/test/scala/com/pipeline/unit/core/PipelineStepTest.scala:43:9: local val context in value <local PipelineStepTest> is never used
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/test/scala/com/pipeline/unit/core/PipelineStepTest.scala:61:9: local val context in value <local PipelineStepTest> is never used
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/test/scala/com/pipeline/unit/core/PipelineStepTest.scala:77:9: local val context in value <local PipelineStepTest> is never used
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/test/scala/com/pipeline/unit/core/PipelineTest.scala:3:60: Unused import
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/test/scala/com/pipeline/unit/core/TransformStepTest.scala:3:27: Unused import
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/test/scala/com/pipeline/unit/credentials/CredentialConfigFactoryTest.scala:3:34: Unused import
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/test/scala/com/pipeline/unit/credentials/JdbcConfigTest.scala:3:46: Unused import
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/test/scala/com/pipeline/unit/credentials/JdbcConfigTest.scala:9:19: Unused import
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/test/scala/com/pipeline/unit/credentials/OtherConfigTest.scala:63:22: a type was inferred to be `Any`; this may indicate a programming error.
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/test/scala/com/pipeline/unit/operations/ExtractMethodsTest.scala:75:18: a type was inferred to be `Any`; this may indicate a programming error.
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/test/scala/com/pipeline/unit/operations/ExtractMethodsTest.scala:3:34: Unused import
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/test/scala/com/pipeline/unit/operations/ExtractMethodsTest.scala:4:32: Unused import
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/test/scala/com/pipeline/unit/operations/ExtractMethodsTest.scala:44:9: local val config in value <local ExtractMethodsTest> is never used
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/test/scala/com/pipeline/unit/operations/LoadMethodsTest.scala:4:32: Unused import
[Error] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/test/scala/com/pipeline/unit/operations/StreamingModeTest.scala:18:2: not found: type RunWith
[Warn] E:/_MyFile/_WORK/claude-spark-gradle-refined/src/test/scala/com/pipeline/unit/retry/RetryStrategyTest.scala:9:38: Unused import
23 warnings found
two errors found

> Task :compileTestScala FAILED

[Incubating] Problems report is available at: file:///E:/_MyFile/_WORK/claude-spark-gradle-refined/build/reports/problems/problems-report.html

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':compileTestScala'.
> Compilation failed

* Try:
> Run with --scan to generate a Build Scan (Powered by Develocity).

Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/9.1.0/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD FAILED in 2m 43s
12 actionable tasks: 12 executed
```

### III. Format Check CMDs failed

+ STATUS: **<span style="color: yellow;">SOLVED</span>**
+ SOLUTION: modify `build.gradle` and update cmds `scalafmtCheck` to `checkScalafmtAll`

```conf
        plugins {
                ...
                id 'cz.augi.gradle.scalafmt' version '1.21.5'
        }

        scalafmt {
                ...
        }
```

+ NOTE: <span style="color: red;">Claude frequently uses obsolete code, and has no idea that commands need dependencies installed. It generates the most likely text with very shallow understandings.</span>

```bash
./gradlew scalafmtAll && ./gradlew scalafmtCheck

* What went wrong:
Task 'scalafmtAll' not found in root project 'pipeline-app'.
...
Task 'scalafmtCheck' not found in root project 'pipeline-app'.
```

### IV. Test on Spark Cluster

Need to deploy `build\libs\pipeline-app-1.0-SNAPSHOT-all.jar` on Spark clusters after ALL tests PASSED.
