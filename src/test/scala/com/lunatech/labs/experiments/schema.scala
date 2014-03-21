package com.lunatech.labs.experiments

import org.squeryl.KeyedEntity
import org.squeryl.PrimitiveTypeMode.{ __thisDsl, transaction }
import org.squeryl.Schema

/**
 * The database record that we use in our tests
 */
case class TestRecord(id: Int, testValue: String) extends KeyedEntity[Int]

/**
 * The Schema that we use in our tests
 */
object TestSchema extends Schema {
  val testRecords = table[TestRecord]

  /**
   * Read the `testValue` column of the single record that's in the database.
   */
  def getTestValue: String = transaction {
    TestSchema.testRecords.get(1).testValue
  }

  /**
   * Write the `testValue` column of the single record that's in the database.
   */
  def setTestValue(newValue: String) = TestSchema.testRecords.update(TestRecord(1, newValue))
}
