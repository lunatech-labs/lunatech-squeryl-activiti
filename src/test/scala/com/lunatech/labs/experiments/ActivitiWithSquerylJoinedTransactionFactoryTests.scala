package com.lunatech.labs.experiments

import org.specs2.mutable.Specification
import org.squeryl.PrimitiveTypeMode._

class ActivitiWithSquerylJoinedTransactionFactoryTests extends Specification with ActivitiTools {
  sequential

  val processKey = "transaction-test"
  Class.forName("org.h2.Driver")

 "When configuring Activiti to use the SquerylJoinedTransactionFactory".txt
  br
  "an activiti process that's started outside an `inTransaction` block" should {
    "be managed by Activiti's own transaction mechanism" in new DBScope {

      val engine = buildEngine(Some(new SquerylJoinedTransactionFactory()))
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
    "roll back the Activiti process state and the delegates" in new DBScope {

      val engine = buildEngine(Some(new SquerylJoinedTransactionFactory()))

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

      // We expect that all delegates are rolled back
      TestSchema.getTestValue must_== "A"

    }
  }

  "An exception in an Activiti business process that is wrapped in a Squeryl transaction" should {
    "roll back the Activiti process state and all delegates" in new DBScope {

      val engine = buildEngine(Some(new SquerylJoinedTransactionFactory()))

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

  "An exception outside an Activiti business process but inside a Squeryl transaction that also wraps the process" should {
    "roll back the Activiti process state and all delegates" in new DBScope {
      val engine = buildEngine(Some(new SquerylJoinedTransactionFactory()))

      // This process doesn't throw exceptions, just saves 'B'
      engine deploy {
        <process id="transaction-test" name="Transaction Test" isExecutable="true">
          <startEvent id="startevent1" name="Start"></startEvent>
          <sequenceFlow id="flow1" sourceRef="startevent1" targetRef="servicetask1"></sequenceFlow>
          <serviceTask id="servicetask1" name="Service Task" activiti:class={ classOf[SetB].getName }></serviceTask>
          <sequenceFlow id="flow2" sourceRef="servicetask1" targetRef="endevent1"></sequenceFlow>
          <endEvent id="endevent1" name="End"></endEvent>
        </process>
      }

      val runtimeService = engine.getRuntimeService()

      inTransaction {
        runtimeService.startProcessInstanceByKey(processKey)
        throw new RuntimeException("Boom!")
        ()
      } must throwA[Exception]

      // We expect the business process to be rolled back
      engine.getHistoryService().createHistoricProcessInstanceQuery().processDefinitionKey(processKey).count() must_== 0

      // We expect that the result of all delegates is rolled back.
      TestSchema.getTestValue must_== "A"
    }

  }

   "An exception in an asynchronous Activiti delegate" should {
    "roll back all delegates in that BPMN transaction" in new DBScope {

      silenceThreadDeaths()

      val engine = buildEngine(Some(new SquerylJoinedTransactionFactory()), true)

      // In this process, the second delegate throws an exception, and starting from the first delegate, the chain is async.
      engine deploy {
        <process id="transaction-test" name="Transaction Test" isExecutable="true">
          <startEvent id="startevent1" name="Start"></startEvent>
          <sequenceFlow id="flow1" sourceRef="startevent1" targetRef="servicetask1"></sequenceFlow>
          <serviceTask id="servicetask1" name="Service Task" activiti:class={ classOf[SetB].getName } activiti:async="true"></serviceTask>
          <sequenceFlow id="flow2" sourceRef="servicetask1" targetRef="servicetask2"></sequenceFlow>
          <serviceTask id="servicetask2" name="Service Task" activiti:class={ classOf[SetCAndThrow].getName }></serviceTask>
          <sequenceFlow id="flow3" sourceRef="servicetask2" targetRef="endevent1"></sequenceFlow>
          <endEvent id="endevent1" name="End"></endEvent>
        </process>
      }

      val runtimeService = engine.getRuntimeService()

      // This doesn't throw, because the exception is async
      runtimeService.startProcessInstanceByKey(processKey)

      // Wait until the async job is run...
      Thread.sleep(1000)

      // We expect the business process to not be rolled back
      engine.getHistoryService().createHistoricProcessInstanceQuery().processDefinitionKey(processKey).count() must_== 1

      // We expect that all delegates are rolled back
      TestSchema.getTestValue must_== "A"

    }
  }

}