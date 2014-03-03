package com.lunatech.labs.experiments

import java.sql.DriverManager

import org.activiti.engine.{ ProcessEngine, ProcessEngineConfiguration }
import org.activiti.engine.delegate.{ BpmnError, DelegateExecution, JavaDelegate }
import org.activiti.engine.impl.cfg.ProcessEngineConfigurationImpl
import org.activiti.engine.impl.history.HistoryLevel
import org.h2.jdbcx.JdbcDataSource
import org.h2.tools.Server
import org.specs2.mutable.{ After, Specification }
import org.specs2.specification.Scope
import org.squeryl.{ Schema, Session, SessionFactory }
import org.squeryl.KeyedEntity
import org.squeryl.PrimitiveTypeMode.{ __thisDsl, inTransaction, transaction }
import org.squeryl.adapters.H2Adapter

import javax.sql.DataSource

/**
 * Tests for Squeryl and the combination of Squeryl and Activiti
 *
 * All tests use a database, on which we apply transactions. The databaes has a single record, which - at the beginning of each test -
 * is always 'A'. Then, we perform various transactions that we commit or roll back, before we inspect the final state.
 */
class SquerylJoinedTransactionFactoryTests extends Specification {
  sequential

  val processKey = "transaction-test"
  Class.forName("org.h2.Driver")
  val jdbcUrl = "jdbc:h2:test"
  implicit val ds = new JdbcDataSource()
  ds.setURL(jdbcUrl)

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
  br
  br
  "The next couple of examples test the combination of Squeryl and Activiti".txt
  br
  //
  // Most of the Activiti examples contain a simple business process with two delegates. We generally let the
  // first delegate succeed, and throw an exception in the second one, to test which changes get committed and which gets rolled back.
  // We individually test whether the Activiti process is still to be found, and which of the delegates got committed.
  //
  "When configuring Activiti to use the SquerylJoinedTransactionFactory".txt
  br
  "an activiti process that's started outside an `inTransaction` block" should {
    "be managed by Activiti's own transaction mechanism" in {
      val engine = buildEngine

      // This is a simple process with two delegates that set the value to 'B' and 'C' respectively.
      engine deploy {
        <process id="transaction-test" name="Transaction Test" isExecutable="true">
          <startEvent id="startevent1" name="Start"></startEvent>
          <sequenceFlow id="flow1" sourceRef="startevent1" targetRef="servicetask1"></sequenceFlow>
          <serviceTask id="servicetask1" name="Service Task" activiti:class={ classOf[SetB].getName }></serviceTask>
          <sequenceFlow id="flow2" sourceRef="servicetask1" targetRef="servicetask2"></sequenceFlow>
          <serviceTask id="servicetask2" name="Service Task" activiti:class={ classOf[SetC].getName }></serviceTask>
          <sequenceFlow id="flow3" sourceRef="servicetask2" targetRef="endevent1"></sequenceFlow>
          <endEvent id="endevent1" name="End"></endEvent>
        </process>
      }

      val runtimeService = engine.getRuntimeService()
      runtimeService.startProcessInstanceByKey(processKey)

      // We expect that Activiti knows about the started process. If the activiti process would be rolled back, it wouldn't be found here...
      engine.getHistoryService().createHistoricProcessInstanceQuery().processDefinitionKey(processKey).count() must_== 1

      // We expect the value that the second delegate set to be committed.
      TestSchema.getTestValue must_== "C"
    }
  }

  "An exception in an Activiti business process that is not wrapped in a Squeryl transaction" should {
    "roll back the failing delegate, and the Activiti process state, but not the completed delegates" in new DBScope {

      val engine = buildEngine

      // In this process, the second delegate throws an exception
      engine deploy {
        <process id="transaction-test" name="Transaction Test" isExecutable="true">
          <startEvent id="startevent1" name="Start"></startEvent>
          <sequenceFlow id="flow1" sourceRef="startevent1" targetRef="servicetask1"></sequenceFlow>
          <serviceTask id="servicetask1" name="Service Task" activiti:class={ classOf[SetB].getName }></serviceTask>
          <sequenceFlow id="flow2" sourceRef="servicetask1" targetRef="servicetask2"></sequenceFlow>
          <serviceTask id="servicetask2" name="Service Task" activiti:class={ classOf[SetCAndThrow].getName }></serviceTask>
          <sequenceFlow id="flow3" sourceRef="servicetask2" targetRef="endevent1"></sequenceFlow>
          <endEvent id="endevent1" name="End"></endEvent>
        </process>
      }

      val runtimeService = engine.getRuntimeService()

      runtimeService.startProcessInstanceByKey(processKey) must throwA[Exception]

      // We expect the business process to be rolled back
      engine.getHistoryService().createHistoricProcessInstanceQuery().processDefinitionKey(processKey).count() must_== 0

      // We expect that the result of the first delegate is committed.
      TestSchema.getTestValue must_== "B"

    }

    "NOT trigger the compensating action of succesfully completed service tasks, if a regular exception is thrown " in new DBScope {

      val engine = buildEngine

      // This is a business process where the first delegate has a compensating task defined.
      engine.deploy {
        <process id="transaction-test" name="Transaction Test" isExecutable="true">
          <startEvent id="startevent1" name="Start"></startEvent>
          <sequenceFlow id="flow1" sourceRef="startevent1" targetRef="servicetask1"></sequenceFlow>
          <serviceTask id="servicetask1" name="Service Task" activiti:class={ classOf[SetB].getName }></serviceTask>
          <boundaryEvent id="servicetask1compensator" attachedToRef="servicetask1">
            <compensateEventDefinition/>
          </boundaryEvent>
          <serviceTask id="undoservicetask1" isForCompensation="true" activiti:class={ classOf[SetD].getName }></serviceTask>
          <sequenceFlow id="flow2" sourceRef="servicetask1" targetRef="servicetask2"></sequenceFlow>
          <serviceTask id="servicetask2" name="Service Task" activiti:class={ classOf[SetCAndThrow].getName }></serviceTask>
          <sequenceFlow id="flow3" sourceRef="servicetask2" targetRef="endevent1"></sequenceFlow>
          <endEvent id="endevent1" name="End"></endEvent>
          <association associationDirection="One" sourceRef="servicetask1compensator" targetRef="undoservicetask1"/>
        </process>
      }

      val runtimeService = engine.getRuntimeService()

      runtimeService.startProcessInstanceByKey(processKey) must throwA[Exception]

      // We expect the business process to be rolled back
      engine.getHistoryService().createHistoricProcessInstanceQuery().processDefinitionKey(processKey).count() must_== 0

      // We expect the transaction of first delegate to be committed, and not be compensated.
      TestSchema.getTestValue must_== "B"

    }

    "trigger the compensating action of succesfully completed service tasks, if it's a transaction subprocess that ends with a cancel event " in new DBScope {

      val engine = buildEngine

      // This is a contrived example with a BPMN transaction. After the first delegate, the business process throws a compensate event.
      // This cancels the BPMN transaction and triggers the compensate task for the (previously succesfully executed) first delegate.
      engine.deploy {
        <process id="transaction-test" name="Transaction Test" isExecutable="true">
          <startEvent id="outerstartevent" name="Start"></startEvent>
          <sequenceFlow id="outerflow1" sourceRef="outerstartevent" targetRef="transaction"></sequenceFlow>
          <transaction id="transaction">
            <startEvent id="innerstartevent" name="Start"></startEvent>
            <sequenceFlow id="innerflow1" sourceRef="innerstartevent" targetRef="servicetask1"></sequenceFlow>
            <serviceTask id="servicetask1" name="Service Task" activiti:class={ classOf[SetB].getName }></serviceTask>
            <boundaryEvent id="servicetask1compensator" attachedToRef="servicetask1">
              <compensateEventDefinition/>
            </boundaryEvent>
            <serviceTask id="undoservicetask1" isForCompensation="true" activiti:class={ classOf[SetD].getName }></serviceTask>
            <sequenceFlow id="innerflow2" sourceRef="servicetask1" targetRef="throwCompensation"></sequenceFlow>
            <intermediateThrowEvent id="throwCompensation">
              <compensateEventDefinition/>
            </intermediateThrowEvent>
            <sequenceFlow id="innerflow3" sourceRef="throwCompensation" targetRef="innerendevent1"></sequenceFlow>
            <endEvent id="innerendevent1" name="End"></endEvent>
            <association associationDirection="One" sourceRef="servicetask1compensator" targetRef="undoservicetask1"/>
          </transaction>
          <endEvent id="outerendevent1" name="End"></endEvent>
          <sequenceFlow id="outerflow2" sourceRef="transaction" targetRef="outerendevent1"></sequenceFlow>
        </process>
      }

      val runtimeService = engine.getRuntimeService()

      runtimeService.startProcessInstanceByKey(processKey)

      // We expect the business process to not be rolled back. It's cancelled, but still committed.
      engine.getHistoryService().createHistoricProcessInstanceQuery().processDefinitionKey(processKey).count() must_== 1

      // We expect the value that the compensate tasks sets to be committed.
      TestSchema.getTestValue must_== "D"

    }
  }

  "An exception in an Activiti business process that is wrapped in a Squeryl transaction" should {
    "roll back the Activiti process state and all delegates" in new DBScope {

      val engine = buildEngine

      // In this process, the second delegate throws an exception
      engine deploy {
        <process id="transaction-test" name="Transaction Test" isExecutable="true">
          <startEvent id="startevent1" name="Start"></startEvent>
          <sequenceFlow id="flow1" sourceRef="startevent1" targetRef="servicetask1"></sequenceFlow>
          <serviceTask id="servicetask1" name="Service Task" activiti:class={ classOf[SetB].getName }></serviceTask>
          <sequenceFlow id="flow2" sourceRef="servicetask1" targetRef="servicetask2"></sequenceFlow>
          <serviceTask id="servicetask2" name="Service Task" activiti:class={ classOf[SetCAndThrow].getName }></serviceTask>
          <sequenceFlow id="flow3" sourceRef="servicetask2" targetRef="endevent1"></sequenceFlow>
          <endEvent id="endevent1" name="End"></endEvent>
        </process>
      }

      val runtimeService = engine.getRuntimeService()

      inTransaction { runtimeService.startProcessInstanceByKey(processKey) } must throwA[Exception]

      // We expect the business process to be rolled back
      engine.getHistoryService().createHistoricProcessInstanceQuery().processDefinitionKey(processKey).count() must_== 0

      // We expect that the result of all delegates is rolled back.
      TestSchema.getTestValue must_== "A"

    }
  }

  /**
   * Build an Activiti process engine with the `SquerylJoinedTransactionFactory` as transaction factory
   */
  protected def buildEngine(implicit ds: DataSource): ProcessEngine =
    ProcessEngineConfiguration.
      createStandaloneProcessEngineConfiguration.asInstanceOf[ProcessEngineConfigurationImpl].
      setDataSource(ds).asInstanceOf[ProcessEngineConfigurationImpl].
      setTransactionFactory(new SquerylJoinedTransactionFactory()).
      setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE).
      setHistory(HistoryLevel.ACTIVITY.getKey).
      buildProcessEngine()

  /**
   * A scope in which an H2 database is available.
   */
  trait DBScope extends Scope with After {
    SessionFactory.concreteFactory = Some { () =>
      Session.create(DriverManager.getConnection(jdbcUrl), new H2Adapter)
    }

    val server = Server.createTcpServer().start()

    setupDB()

    def after = {
      server.shutdown()
    }

    def setupDB() = transaction {
      dropDB()
      TestSchema.create
      TestSchema.testRecords.insert(TestRecord(1, "A"))
    }

    def dropDB() = transaction {
      val conn = DriverManager.getConnection(jdbcUrl)
      conn.createStatement().execute("DROP ALL OBJECTS")
      conn.close()
    }

  }

}

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

/**
 * Delegate that tries to commit the value 'B'
 */
class SetB extends JavaDelegate {
  override def execute(ex: DelegateExecution): Unit = inTransaction {
    TestSchema.setTestValue("B")
  }
}

/**
 * Delegate that tries to commit the value 'C'
 */
class SetC extends JavaDelegate {
  override def execute(ex: DelegateExecution): Unit = inTransaction {
    TestSchema.setTestValue("C")
  }
}

/**
 * Delegate that tries to commit the value 'D'
 */
class SetD extends JavaDelegate {
  override def execute(ex: DelegateExecution): Unit = inTransaction {
    TestSchema.setTestValue("D")
  }
}

/**
 * Delegate that tries to commit the value 'C', but then throws a `BpmnError`
 */
class SetCAndThrowBpmnError extends JavaDelegate {
  override def execute(ex: DelegateExecution): Unit = inTransaction[Unit] {
    TestSchema.setTestValue("C")
    throw new BpmnError("Boom!")
    ()
  }
}

/**
 * Delegate that tries to commit the value 'C', but then throws a regular exception
 */
class SetCAndThrow extends JavaDelegate {
  override def execute(ex: DelegateExecution): Unit = inTransaction {
    TestSchema.setTestValue("C")
    throw new RuntimeException("Boom!")
    ()
  }
}