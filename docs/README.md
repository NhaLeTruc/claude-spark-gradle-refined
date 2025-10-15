# Data Pipeline Orchestration Application - Documentation

**Version**: 1.0.0
**Date**: October 15, 2025
**Status**: Production Ready ✅

---

## About This Documentation

This documentation is organized into logical categories for easy navigation. See [Documentation Organization](DOCUMENTATION_ORGANIZATION.md) for details on the structure and how to navigate.

---

## Quick Start

- [Getting Started Guide](guides/GETTING_STARTED.md)
- [Quick Reference](guides/QUICK_REFERENCE.md)
- [Deployment Guide](guides/DEPLOYMENT_GUIDE.md)
- [Performance Guide](guides/PERFORMANCE_GUIDE.md)
- [Troubleshooting](guides/TROUBLESHOOTING.md)

---

## Feature Documentation

Comprehensive guides for all major features:

1. **[Streaming Infrastructure](features/STREAMING_INFRASTRUCTURE_COMPLETE.md)**
   - Dual-mode execution (batch + streaming)
   - Kafka streaming support
   - Delta Lake streaming
   - Query lifecycle management

2. **[Error Handling](features/ERROR_HANDLING_COMPLETE.md)**
   - Custom exception hierarchy (8 types)
   - Context enrichment
   - Smart retry logic
   - Error patterns and best practices

3. **[Performance Features](features/PERFORMANCE_FEATURES_COMPLETE.md)**
   - DataFrame caching (12 storage levels)
   - Repartitioning strategies
   - Performance optimization guide

4. **[Metrics Collection](features/METRICS_COLLECTION_COMPLETE.md)**
   - Pipeline and step-level metrics
   - 3 export formats (Prometheus, JSON, Logs)
   - Integration with monitoring systems

5. **[Security Enhancements](features/SECURITY_ENHANCEMENTS_COMPLETE.md)**
   - Vault-only enforcement mode
   - Credential auditing
   - Security policies
   - Compliance support (PCI DSS, SOC 2, HIPAA)

6. **[Integration Testing](features/INTEGRATION_TESTING_COMPLETE.md)**
   - Testcontainers setup
   - End-to-end tests
   - CI/CD integration

7. **[Sprint 1-4 Summary](features/SPRINT_1-4_FINAL_COMPLETION.md)**
   - Complete implementation overview
   - All features and improvements
   - Production readiness assessment

8. **[Technical Debt Report](features/TECHNICAL_DEBT_REPORT.md)**
   - Current technical debt status
   - Resolved issues
   - Remaining improvements

---

## Architecture Decision Records (ADRs)

Documents explaining key architectural decisions:

1. **[ADR-001: Chain of Responsibility Pattern](adr/001-chain-of-responsibility-pattern.md)**
2. **[ADR-002: Mutable State in PipelineContext](adr/002-mutable-state-in-context.md)**
3. **[ADR-003: Either Type for Primary Data](adr/003-either-type-for-primary.md)**
4. **[ADR-004: Dual-Mode Execution Architecture](adr/004-dual-mode-execution.md)**
5. **[ADR-005: Custom Exception Hierarchy](adr/005-custom-exception-hierarchy.md)**
6. **[ADR-006: Security Policy Framework](adr/006-security-policy-framework.md)**

---

## API Documentation

- [ScalaDoc API Reference](api/index.html) (Generated)
- [Core Classes](api/CORE_CLASSES.md)
- [Operations API](api/OPERATIONS_API.md)
- [Configuration API](api/CONFIGURATION_API.md)

---

## User Guides

### Getting Started
- [Installation](guides/INSTALLATION.md)
- [Quick Start](guides/GETTING_STARTED.md)
- [Basic Concepts](guides/BASIC_CONCEPTS.md)
- [Configuration](guides/CONFIGURATION.md)

### How-To Guides
- [Creating Batch Pipelines](guides/how-to/BATCH_PIPELINES.md)
- [Creating Streaming Pipelines](guides/how-to/STREAMING_PIPELINES.md)
- [Working with Multiple DataFrames](guides/how-to/MULTI_DATAFRAME.md)
- [Data Validation](guides/how-to/DATA_VALIDATION.md)
- [Performance Tuning](guides/how-to/PERFORMANCE_TUNING.md)
- [Security Best Practices](guides/how-to/SECURITY_BEST_PRACTICES.md)
- [Monitoring and Metrics](guides/how-to/MONITORING.md)

### Reference
- [Configuration Schema](guides/reference/CONFIGURATION_SCHEMA.md)
- [Extract Methods](guides/reference/EXTRACT_METHODS.md)
- [Transform Methods](guides/reference/TRANSFORM_METHODS.md)
- [Validate Methods](guides/reference/VALIDATE_METHODS.md)
- [Load Methods](guides/reference/LOAD_METHODS.md)

---

## Project Management

- [Implementation Summary](project/IMPLEMENTATION_SUMMARY.md)
- [Improvement Plan](project/IMPROVEMENT_PLAN.md)
- [Sprint 1-4 Final Summary](project/SPRINT_1-4_FINAL_SUMMARY.md)
- [Sprint 1-4 Implementation Status](project/SPRINT_1-4_IMPLEMENTATION_STATUS.md)
- [Fixes Applied](project/FIXES_APPLIED.md)

---

## Development

- [Contributing Guide](../CONTRIBUTING.md)
- [Development Setup](guides/DEVELOPMENT_SETUP.md)
- [Testing Guide](guides/TESTING_GUIDE.md)
- [Code Style Guide](guides/CODE_STYLE.md)

---

## Examples

All example configurations can be found in [`config/examples/`](../config/examples/):

- `batch-postgres-to-s3.json` - Basic batch ETL
- `streaming-kafka.json` - Kafka streaming pipeline
- `multi-source-join.json` - Multi-DataFrame joins
- `data-quality-validation.json` - Data validation pipeline
- `batch-with-metrics.json` - Pipeline with metrics collection

---

## Project Statistics

**Code Base**:
- Production Code: ~15,000 lines
- Test Code: ~5,000 lines
- Documentation: ~4,500 lines
- Total: ~24,500 lines

**Test Coverage**:
- Unit Tests: 151 (100% passing)
- Integration Tests: 5 (100% passing)
- Performance Tests: 11 (100% passing)
- **Total: 167 tests**

**Features**:
- 6 extract methods
- 11 transform methods
- 5 validate methods
- 6 load methods
- 8 exception types
- 3 metrics exporters
- 12 storage levels
- 3 security policies

---

## Support

- **Issues**: [GitHub Issues](https://github.com/your-org/pipeline/issues)
- **Discussions**: [GitHub Discussions](https://github.com/your-org/pipeline/discussions)
- **Email**: support@your-org.com

---

## License

[Your License Here]

---

**Documentation Version**: 1.0.0
**Last Updated**: October 15, 2025
