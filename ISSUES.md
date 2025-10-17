# List of Issues

## Testing on MS Windows

### I. Local test run failure

```bash
java -jar build/libs/pipeline-app-1.0-SNAPSHOT.jar config/examples/simple-etl.json

Exception in thread "main" java.lang.NoClassDefFoundError: scala/MatchError
        at com.pipeline.cli.PipelineRunner.main(PipelineRunner.scala)      
Caused by: java.lang.ClassNotFoundException: scala.MatchError
        at java.base/jdk.internal.loader.BuiltinClassLoader.loadClass(BuiltinClassLoader.java:580)
        at java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:490)
```

### II. Unittest and Integration Tests failed but test reported success

```bash
./gradlew unitTest && ./gradlew integrationTest
> Task :unitTest FAILED

[Incubating] Problems report is available at: file:///E:/_MyFile/_WORK/claude-spark-gradle-refined/build/reports/problems/problems-report.html

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':unitTest'.
> Test process encountered an unexpected problem.
   > Could not start Gradle Test Executor 2: Failed to load JUnit Platform.  Please ensure that all JUnit Platform dependencies are available on the test's runtime classpath, including the JUnit Platform launcher.     

* Try:
> Check common problems https://docs.gradle.org/9.1.0/userguide/java_testing.html#sec:java_testing_troubleshooting.
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to generate a Build Scan (Powered by Develocity).
> Get more help at https://help.gradle.org.

Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from 
your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/9.1.0/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD FAILED in 4s
4 actionable tasks: 1 executed, 3 up-to-date
```

### III. Format Check CMDs failed

```bash
./gradlew scalafmtAll && ./gradlew scalafmtCheck

[Incubating] Problems report is available at: file:///E:/_MyFile/_WORK/claude-spark-gradle-refined/build/reports/problems/problems-report.html

FAILURE: Build failed with an exception.

* What went wrong:
Task 'scalafmtAll' not found in root project 'pipeline-app'.

* Try:
> Run gradlew tasks to get a list of available tasks.
> For more on name expansion, please refer to https://docs.gradle.org/9.1.0/userguide/command_line_interface.html#sec:name_abbreviation in the Gradle documentation.
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to generate a Build Scan (Powered by Develocity).
> Get more help at https://help.gradle.org.

Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from 
your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/9.1.0/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD FAILED in 1s
```

### IV. Test on Spark Cluster

Need to deploy `build\libs\pipeline-app-1.0-SNAPSHOT-all.jar` on Spark Clusters.
