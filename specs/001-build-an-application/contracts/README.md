# Pipeline Configuration Contracts

This directory contains JSON schemas for validating pipeline configurations.

## Multi-DataFrame Support

### New Configuration Fields

#### registerAs (Extract and Transform Steps)
Registers the output DataFrame with a name for later reference in multi-DataFrame operations.

```json
{
  "stepType": "extract",
  "method": "fromPostgres",
  "config": {
    "table": "users",
    "registerAs": "users",
    "credentialPath": "secret/data/postgres/dev",
    "credentialType": "jdbc"
  }
}
```

**Validation**:
- Type: string
- Pattern: `^[a-zA-Z_][a-zA-Z0-9_]*$` (valid identifier)
- Optional field
- Must be unique within pipeline

#### inputDataFrames (Transform Steps)
Lists the names of registered DataFrames required for multi-DataFrame transformations.

```json
{
  "stepType": "transform",
  "method": "joinDataFrames",
  "config": {
    "inputDataFrames": ["orders", "customers"],
    "joinKey": "customer_id",
    "joinType": "inner"
  }
}
```

**Validation**:
- Type: array of strings
- Items Pattern: `^[a-zA-Z_][a-zA-Z0-9_]*$`
- Optional field
- Referenced DataFrames must be registered in previous steps

## Schema Files

### pipeline-schema.json
Defines the top-level pipeline configuration structure:
- Pipeline name and execution mode
- Steps array with step type validation
- Metadata fields

### step-config-schema.json
Defines configuration schemas for each method:
- Extract methods: fromPostgres, fromMySQL, fromKafka, fromS3, fromDeltaLake
- Transform methods: filterRows, aggregateData, joinDataFrames, enrichData, reshapeData, unionDataFrames
- Validate methods: validateSchema, validateNulls, validateRanges, validateReferentialIntegrity, validateBusinessRules
- Load methods: toPostgres, toMySQL, toKafka, toS3, toDeltaLake

**All extract step schemas now support**:
- `registerAs` (optional): Register extracted DataFrame

**All transform step schemas now support**:
- `registerAs` (optional): Register transformed DataFrame
- `inputDataFrames` (optional): List of input DataFrame names

### credential-schema.json
Documents the expected structure of credentials stored in Vault:
- JdbcCredentials (PostgreSQL, MySQL)
- IAMCredentials (S3)
- KafkaCredentials
- DeltaLakeCredentials

## Usage

### Validation in Code

```scala
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.{JsonSchemaFactory, SpecVersion}

val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
val pipelineSchema = factory.getSchema(new File("contracts/pipeline-schema.json"))

val config = mapper.readTree(new File("config/pipeline.json"))
val errors = pipelineSchema.validate(config)

if (!errors.isEmpty) {
  errors.forEach(error => println(s"Validation error: ${error.getMessage}"))
}
```

### Validation with CLI Tools

```bash
# Using ajv (install: npm install -g ajv-cli)
ajv validate \
  -s contracts/pipeline-schema.json \
  -d config/examples/simple-etl.json

# Using jsonschema (Python)
pip install jsonschema
jsonschema -i config/examples/simple-etl.json contracts/pipeline-schema.json
```

## Schema Extensions

When adding new methods or configuration options:

1. Update the appropriate schema file
2. Add validation rules (type, pattern, required fields)
3. Document in this README
4. Add examples to quickstart.md
5. Update data-model.md with implementation details

## Multi-DataFrame Examples

See [quickstart.md](../quickstart.md#multi-dataframe-pipelines) for complete examples:
- Two-table join
- Star schema joins (fact + multiple dimensions)
- Union multiple sources
- Complex multi-step transformations
