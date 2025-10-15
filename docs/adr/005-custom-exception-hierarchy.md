# ADR-005: Custom Exception Hierarchy

**Date**: 2025-10-15
**Status**: Accepted
**Deciders**: Architecture Team
**Context**: Sprint 1-2 - Error Handling Implementation

---

## Context and Problem Statement

Standard exceptions like `IllegalStateException` don't provide enough context for debugging pipeline failures. Should we use standard exceptions or create a custom hierarchy?

---

## Decision

**Chosen**: **Custom exception hierarchy** with automatic context enrichment.

### Implementation

```scala
class PipelineException(
  message: String,
  cause: Throwable = null,
  val pipelineName: Option[String] = None,
  val stepIndex: Option[Int] = None,
  val stepType: Option[String] = None,
  val method: Option[String] = None,
  val config: Option[Map[String, Any]] = None
) extends Exception

// Specialized types
class DataFrameResolutionException(
  referenceName: String,
  availableNames: Set[String],
  // ... context fields
)

class ValidationException(
  validationType: String,
  failedRecords: Long,
  sampleFailures: Option[String]
)

// 8 exception types total
```

---

## Rationale

1. **Rich Context**: Include pipeline name, step, method, config
2. **Specialized Types**: Different exceptions for different errors
3. **Better Debugging**: Errors show exactly where/why failure occurred
4. **Retry Logic**: Can classify exceptions as retryable/permanent
5. **Security**: Credential sanitization in error messages

---

## Exception Types

1. `PipelineException` - Base class
2. `PipelineExecutionException` - Execution errors
3. `DataFrameResolutionException` - Missing DataFrames
4. `ValidationException` - Data validation failures
5. `CredentialException` - Credential errors
6. `StreamingQueryException` - Streaming failures
7. `ConfigurationException` - Config errors
8. `RetryableException` - Transient failures
9. `PipelineCancelledException` - Graceful cancellation

---

## Context Enrichment

Automatic in Chain of Responsibility:

```scala
try {
  execute(context, spark)
} catch {
  case pe: PipelineException => throw pe
  case ex: Throwable =>
    throw PipelineException.wrapException(
      ex,
      pipelineName = Some("my-pipeline"),
      stepIndex = Some(2),
      stepType = Some("transform"),
      method = Some("filterRows"),
      config = Some(stepConfig)
    )
}
```

---

## Consequences

✅ **Better Debugging**: Errors include full context
✅ **Smart Retry**: Can determine if error is retryable
✅ **Security**: Credentials sanitized automatically
✅ **Type Safety**: Pattern matching on exception type

⚠️ **More Code**: Custom hierarchy requires maintenance
⚠️ **Learning Curve**: Team must learn custom types

---

## Related

- [ADR-001: Chain of Responsibility](001-chain-of-responsibility-pattern.md)
- [Error Handling Documentation](../features/ERROR_HANDLING_COMPLETE.md)

---

**Last Updated**: 2025-10-15
