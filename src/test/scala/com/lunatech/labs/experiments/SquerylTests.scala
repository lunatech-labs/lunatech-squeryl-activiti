package com.lunatech.labs.experiments

import org.specs2.mutable.Specification
import org.squeryl.PrimitiveTypeMode.{ inTransaction, transaction }

class SquerylTests extends Specification {
  sequential

  "Transactions managed by Squeryl".title

  "The next few examples test regular Squeryl behaviour".txt

  "A regular Squeryl transaction" should {
    "commit on success" in new DBScope {
      transaction {
        TestSchema.setTestValue("B")
      }

      TestSchema.getTestValue must_== "B"
    }

    "rollback on failure" in new DBScope {
      transaction {
        TestSchema.setTestValue("B")
        throw new RuntimeException("Foo!")
        ()
      } should throwA[Exception]

      // Remember, the initial DB value is "A", so with this failed transaction, we expect "A" to still be the value
      TestSchema.getTestValue must_== "A"
    }
  }

  "A 'transaction' block inside a transaction" should {
    "be committed even if the outer one is rolled back" in new DBScope {
      transaction {
        transaction {
          TestSchema.setTestValue("C")
        }
        throw new Exception("Boom!")
        ()
      } must throwA[Exception]

      // We expect the inner transaction to be committed, even if the outer one later fails.
      TestSchema.getTestValue must_== "C"
    }
  }

  "An 'inTransaction' block inside a transaction" should {
    "be rolled back if the outer one is rolled back" in new DBScope {
      transaction {
        inTransaction {
          TestSchema.setTestValue("C")
        }
        throw new Exception("Boom!")
        ()
      } must throwA[Exception]

      // We expect that the inner `inTransaction` block has no effect; it's contents are added to the outer `transaction` block,
      // which is rolled back after the exception. So we expect to end up with the initial DB value of 'A'.
      TestSchema.getTestValue must_== "A"
    }
  }

}