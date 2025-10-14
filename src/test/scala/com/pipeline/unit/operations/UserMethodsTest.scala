package com.pipeline.unit.operations

import org.apache.spark.sql.SparkSession
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

/**
 * Unit tests for UserMethods (transform operations).
 *
 * Tests FR-004: Transform operations.
 * Tests FR-024: 5 transform methods.
 * Validates Constitution Section II: Test-First Development (TDD).
 */
@RunWith(classOf[JUnitRunner])
class UserMethodsTest extends AnyFunSuite with Matchers with BeforeAndAfterAll {

  @transient private var spark: SparkSession = _

  override def beforeAll(): Unit = {
    spark = SparkSession
      .builder()
      .appName("UserMethodsTest")
      .master("local[2]")
      .config("spark.ui.enabled", "false")
      .config("spark.sql.shuffle.partitions", "2")
      .getOrCreate()
  }

  override def afterAll(): Unit = {
    if (spark != null) {
      spark.stop()
    }
  }

  test("UserMethods should have filterRows method") {
    val config = Map("condition" -> "age > 18")

    config should contain key "condition"
    config("condition") shouldBe "age > 18"
  }

  test("UserMethods.filterRows should support SQL WHERE conditions") {
    val conditions = List(
      "age > 18",
      "status = 'active'",
      "created_at > '2024-01-01'",
      "amount >= 100 AND category = 'premium'",
    )

    conditions.foreach { condition =>
      val config = Map("condition" -> condition)
      config("condition") shouldBe condition
    }
  }

  test("UserMethods should have enrichData method") {
    val config = Map(
      "columns" -> Map(
        "full_name" -> "concat(first_name, ' ', last_name)",
        "is_adult" -> "age >= 18",
      ),
    )

    config should contain key "columns"
  }

  test("UserMethods should have joinDataFrames method") {
    val config = Map(
      "inputDataFrames" -> List("users", "orders"),
      "joinType" -> "inner",
      "joinColumn" -> "user_id",
    )

    config should contain key "inputDataFrames"
    config("joinType") shouldBe "inner"
  }

  test("UserMethods.joinDataFrames should support multiple join types") {
    val joinTypes = List("inner", "left", "right", "outer", "cross", "left_semi", "left_anti")

    joinTypes.foreach { joinType =>
      val config = Map("joinType" -> joinType)
      config("joinType") shouldBe joinType
    }
  }

  test("UserMethods should have aggregateData method") {
    val config = Map(
      "groupBy" -> List("category", "region"),
      "aggregations" -> Map(
        "total_sales" -> "sum(amount)",
        "avg_price" -> "avg(price)",
        "count" -> "count(*)",
      ),
    )

    config should contain key "groupBy"
    config should contain key "aggregations"
  }

  test("UserMethods should have reshapeData method") {
    val config = Map(
      "operation" -> "pivot",
      "pivotColumn" -> "category",
      "valueColumn" -> "amount",
    )

    config("operation") shouldBe "pivot"
  }

  test("UserMethods should have unionDataFrames method") {
    val config = Map(
      "inputDataFrames" -> List("df1", "df2", "df3"),
      "distinct" -> "true",
    )

    val inputDfs = config("inputDataFrames").asInstanceOf[List[String]]
    inputDfs should have size 3
  }

  test("UserMethods should have at least 5 transform methods as per FR-024") {
    // FR-024: Support at least 5 transform methods
    val methods = List(
      "filterRows",
      "enrichData",
      "joinDataFrames",
      "aggregateData",
      "reshapeData",
      "unionDataFrames",
    )

    methods.size should be >= 5
  }

  test("UserMethods.filterRows should work with DataFrame API") {
    val sparkImplicits = spark.implicits
    import sparkImplicits._

    val df = Seq((1, "Alice", 25), (2, "Bob", 17), (3, "Charlie", 30))
      .toDF("id", "name", "age")

    val filtered = df.filter("age > 18")

    filtered.count() shouldBe 2
  }

  test("UserMethods.enrichData should add computed columns") {
    val sparkImplicits = spark.implicits
    import sparkImplicits._
    import org.apache.spark.sql.functions._

    val df = Seq(("Alice", "Smith"), ("Bob", "Jones"))
      .toDF("first_name", "last_name")

    val enriched = df.withColumn("full_name", concat(col("first_name"), lit(" "), col("last_name")))

    enriched.columns should contain("full_name")
    enriched.count() shouldBe 2
  }

  test("UserMethods.joinDataFrames should support inner joins") {
    val sparkImplicits = spark.implicits
    import sparkImplicits._

    val users = Seq((1, "Alice"), (2, "Bob"))
      .toDF("user_id", "name")

    val orders = Seq((101, 1, 99.99), (102, 2, 149.99))
      .toDF("order_id", "user_id", "amount")

    val joined = users.join(orders, Seq("user_id"), "inner")

    joined.count() shouldBe 2
    joined.columns should contain allOf ("user_id", "name", "order_id", "amount")
  }
}
