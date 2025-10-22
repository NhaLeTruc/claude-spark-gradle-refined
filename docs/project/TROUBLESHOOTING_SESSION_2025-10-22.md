# Troubleshooting Session - JAR Packaging & Execution Issues

**Date**: October 22, 2025
**Status**: ✅ Resolved

## Summary

Fixed critical JAR packaging and runtime issues preventing the application from running locally via `java -jar`. The application now executes correctly with proper logging and error reporting.

## Issues Identified and Fixed

### 1. NoClassDefFoundError - Missing Dependencies ✅

**Error Message**:
```
Exception in thread "main" java.lang.NoClassDefFoundError: org/slf4j/LoggerFactory
```

**Root Cause**:
- The `jar` task in `build.gradle` was only packaging compiled classes
- Runtime dependencies (SLF4J, Logback, Spark, etc.) were not included
- JAR size was minimal (~few KB) instead of expected ~500MB

**Fix Applied**:
- Modified `build.gradle` lines 114-134 to create a fat JAR
- Added `from` block to include all runtime dependencies
- Added proper duplicate handling and zip64 support for large archives
- Excluded signature files (META-INF/*.SF, *.DSA, *.RSA) to prevent conflicts

**Files Modified**:
- `build.gradle` - Updated `jar` task configuration

**Verification**:
```bash
ls -lh build/libs/pipeline-app-1.0-SNAPSHOT.jar
# Output: -rw-rw-r-- 1 bob bob 507M Oct 22 17:10 pipeline-app-1.0-SNAPSHOT.jar
```

---

### 2. IllegalAccessError - Java 17 Module System ✅

**Error Message**:
```
IllegalAccessError: class org.apache.spark.storage.StorageUtils$ cannot access class sun.nio.ch.DirectBuffer
because module java.base does not export sun.nio.ch to unnamed module
```

**Root Cause**:
- Java 17 introduced the module system which restricts access to internal APIs
- Spark 3.5.6 requires access to internal Java packages (sun.nio.ch, sun.util.calendar, etc.)
- Running `java -jar` without JVM arguments triggers access violations

**Fix Applied**:
- Created `run-pipeline.sh` helper script with all required `--add-opens` JVM arguments
- Updated `README.md` with complete documentation of required JVM flags
- Made script executable and user-friendly with error checking

**Files Created**:
- `run-pipeline.sh` - Helper script with proper JVM configuration

**JVM Arguments Required**:
```bash
--add-opens=java.base/java.lang=ALL-UNNAMED
--add-opens=java.base/java.lang.invoke=ALL-UNNAMED
--add-opens=java.base/java.lang.reflect=ALL-UNNAMED
--add-opens=java.base/java.io=ALL-UNNAMED
--add-opens=java.base/java.net=ALL-UNNAMED
--add-opens=java.base/java.nio=ALL-UNNAMED
--add-opens=java.base/java.util=ALL-UNNAMED
--add-opens=java.base/java.util.concurrent=ALL-UNNAMED
--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED
--add-opens=java.base/sun.nio.ch=ALL-UNNAMED
--add-opens=java.base/sun.nio.cs=ALL-UNNAMED
--add-opens=java.base/sun.security.action=ALL-UNNAMED
--add-opens=java.base/sun.util.calendar=ALL-UNNAMED
--add-opens=java.security.jgss/sun.security.krb5=ALL-UNNAMED
```

**Verification**:
```bash
timeout 30 ./run-pipeline.sh config/examples/simple-etl.json 2>&1 | grep -i "IllegalAccessError"
# Output: No IllegalAccessError found - the issue is fixed!
```

---

### 3. Silent Logging Failure - Missing JsonLayout Dependency ✅

**Symptoms**:
- No log output to console or file
- Application appeared to hang or fail silently
- Log file `logs/pipeline.log` remained at 0 bytes
- No error messages visible

**Root Cause**:
- `logback.xml` configuration used `ch.qos.logback.contrib.json.classic.JsonLayout`
- This layout requires `logback-json-classic` and `logback-jackson` dependencies
- Dependencies were not included in `build.gradle`
- When logback couldn't load JsonLayout, it failed silently causing NO logging at all
- Application messages like "Pipeline Orchestration Application Starting" never appeared

**Fix Applied**:
- Replaced JSON layout with standard `PatternLayout` in `src/main/resources/logback.xml`
- Updated both CONSOLE and FILE appenders
- New pattern: `%d{yyyy-MM-dd'T'HH:mm:ss.SSSX,UTC} [%thread] %-5level %logger{36} - %msg%n`
- Provides timestamp, thread, log level, logger name, and message

**Files Modified**:
- `src/main/resources/logback.xml` - Lines 3-38

**Before**:
```xml
<layout class="ch.qos.logback.contrib.json.classic.JsonLayout">
    <jsonFormatter class="ch.qos.logback.contrib.jackson.JacksonJsonFormatter">
        <prettyPrint>false</prettyPrint>
    </jsonFormatter>
    ...
</layout>
```

**After**:
```xml
<encoder>
    <pattern>%d{yyyy-MM-dd'T'HH:mm:ss.SSSX,UTC} [%thread] %-5level %logger{36} - %msg%n</pattern>
</encoder>
```

**Verification**:
```bash
./run-pipeline.sh config/examples/simple-etl.json 2>&1 | head -10
# Expected output:
# 2025-10-22T10:54:54.117Z [main] INFO  com.pipeline.cli.PipelineRunner$ - === Pipeline Orchestration Application Starting ===
# 2025-10-22T10:54:54.289Z [main] INFO  com.pipeline.cli.PipelineRunner$ - Arguments: config/examples/simple-etl.json
# 2025-10-22T10:54:54.289Z [main] INFO  com.pipeline.cli.PipelineRunner$ - Loading pipeline configuration from: config/examples/simple-etl.json
```

---

## Expected Runtime Errors (Not Bugs!)

After fixing the above issues, the application now runs correctly and shows proper error messages for missing infrastructure:

### VAULT_ADDR Not Set

```
❌ Pipeline failed after 10577ms
com.pipeline.exceptions.PipelineExecutionException: VAULT_ADDR environment variable is not set
```

**This is expected behavior** - the example pipeline requires HashiCorp Vault for credential management.

**Solution**: Either set up Vault or use a simpler test configuration.

---

## Files Modified Summary

| File | Change | Reason |
|------|--------|--------|
| `build.gradle` | Updated `jar` task to create fat JAR | Fix NoClassDefFoundError |
| `run-pipeline.sh` | Created new helper script | Fix IllegalAccessError with JVM args |
| `src/main/resources/logback.xml` | Replace JsonLayout with PatternLayout | Fix silent logging failure |
| `README.md` | Added troubleshooting section | Document all fixes and usage |

---

## Testing Performed

### 1. Dependency Verification
```bash
# Verify JAR size
ls -lh build/libs/pipeline-app-1.0-SNAPSHOT.jar
# ✅ Result: 507M (previously ~few KB)

# Verify dependencies included
jar tf build/libs/pipeline-app-1.0-SNAPSHOT.jar | grep -E "slf4j|logback|spark" | wc -l
# ✅ Result: Thousands of dependency classes found
```

### 2. Module Access Verification
```bash
# Test for IllegalAccessError
timeout 30 ./run-pipeline.sh config/examples/simple-etl.json 2>&1 | grep -i "IllegalAccessError"
# ✅ Result: No IllegalAccessError found
```

### 3. Logging Verification
```bash
# Test logging output
./run-pipeline.sh config/examples/simple-etl.json 2>&1 | head -5
# ✅ Result: Proper log messages appear with timestamps
```

### 4. End-to-End Execution
```bash
# Run complete pipeline
./run-pipeline.sh config/examples/simple-etl.json
# ✅ Result: Application starts, parses config, creates Spark session, fails with expected Vault error
```

---

## Build Artifacts

After fixes, the build produces:

- **Standard JAR** (`pipeline-app-1.0-SNAPSHOT.jar`): ~507MB
  - Includes ALL dependencies (Spark, SLF4J, Logback, etc.)
  - For local CLI execution with `run-pipeline.sh`

- **Shadow JAR** (`pipeline-app-1.0-SNAPSHOT-all.jar`): ~457MB
  - Excludes Spark and Scala (provided by cluster)
  - For cluster deployment via `spark-submit`

---

## How to Use

### Quick Start
```bash
# 1. Build the project
./gradlew clean build

# 2. Run with helper script (recommended)
./run-pipeline.sh config/examples/simple-etl.json

# 3. Or run manually with full JVM args
java --add-opens=java.base/java.lang=ALL-UNNAMED \
     --add-opens=java.base/java.lang.invoke=ALL-UNNAMED \
     ... (see run-pipeline.sh for complete list) \
     -jar build/libs/pipeline-app-1.0-SNAPSHOT.jar config.json
```

### Verifying the Fix
```bash
# Should show: "=== Pipeline Orchestration Application Starting ==="
./run-pipeline.sh config/examples/simple-etl.json 2>&1 | head -5
```

---

## Lessons Learned

1. **Fat JAR Configuration**: The standard Gradle `jar` task doesn't include dependencies by default. Must explicitly configure with `from` block.

2. **Java 17 Module System**: Spark requires extensive JVM arguments to access internal APIs. Always document these for users.

3. **Logback Silent Failures**: When logback configuration fails (missing dependencies), it fails silently with no logging at all. Use standard layouts unless JSON is truly required.

4. **Error Debugging**: When logs don't appear, first check logback configuration - silent failures are hard to debug.

5. **Documentation**: Clear error messages and troubleshooting guides are essential for JAR distribution.

---

## References

- Gradle Fat JAR: https://docs.gradle.org/current/userguide/working_with_files.html
- Java 17 Module System: https://docs.oracle.com/en/java/javase/17/migrate/migrating-jdk-8-later-jdk-releases.html
- Logback Configuration: https://logback.qos.ch/manual/configuration.html
- Spark on Java 17: https://spark.apache.org/docs/latest/

---

**Status**: ✅ All issues resolved. Application runs successfully with proper error reporting.
