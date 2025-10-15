# ADR-004: Dual-Mode Execution Architecture

**Date**: 2025-10-15
**Status**: Accepted
**Deciders**: Architecture Team
**Context**: Sprint 1-2 - Streaming Implementation

---

## Context and Problem Statement

The system needs to support both batch and streaming execution modes. Should we create separate pipeline implementations or a unified dual-mode architecture?

---

## Decision

**Chosen**: **Unified dual-mode architecture** with mode flag propagation.

### Implementation

```scala
case class PipelineContext(
  // ...
  isStreamingMode: Boolean = false
)

// Extract methods check mode
def fromKafka(config: Map[String, Any], spark: SparkSession, isStreaming: Boolean): DataFrame = {
  if (isStreaming) {
    spark.readStream.format("kafka")...
  } else {
    spark.read.format("kafka")...
  }
}

// Load methods return streaming queries
def toDeltaLake(..., isStreaming: Boolean): Option[StreamingQuery] = {
  if (isStreaming) {
    Some(df.writeStream...)
  } else {
    df.write...
    None
  }
}
```

---

## Rationale

1. **Code Reuse**: 90% of code is shared between modes
2. **Single Configuration**: One JSON schema for both modes
3. **Mode Propagation**: `isStreamingMode` flag flows through chain
4. **API Clarity**: Mode explicitly declared in pipeline config
5. **Testing**: Test both modes with same infrastructure

---

## Consequences

✅ **Unified Codebase**: Less duplication
✅ **Consistent API**: Same JSON schema
✅ **Easy Mode Switching**: Change one field in config
✅ **Shared Logic**: Validation, error handling work for both

⚠️ **Conditional Logic**: `if (isStreaming)` branches in code
⚠️ **API Complexity**: Some methods need mode parameter

---

## Alternatives Rejected

- **Separate Implementations**: Too much duplication
- **Only Streaming**: Batch is simpler and still needed
- **Only Batch**: Streaming is critical requirement

---

## Related

- [ADR-001: Chain of Responsibility](001-chain-of-responsibility-pattern.md)
- [Streaming Documentation](../features/STREAMING_INFRASTRUCTURE_COMPLETE.md)

---

**Last Updated**: 2025-10-15
