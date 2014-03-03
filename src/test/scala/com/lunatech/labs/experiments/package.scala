package com.lunatech.labs

import scala.xml.Elem

import org.activiti.engine.ProcessEngine

package object experiments {
  implicit class RichProcessEngine(engine: ProcessEngine) {
    def deploy(processXml: Elem): Unit = {

      //
      //
      // Note: trying to build a BPMNmodel from an XML containing a BoundaryEvent with compensateEventDefinition
      // and deploying that gives an error: ActivitiException: : Errors while parsing: Unsupported boundary event type
      // That's why we deploy the XML directly
      //
      //

      val definitions = <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:activiti="http://activiti.org/bpmn" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:omgdc="http://www.omg.org/spec/DD/20100524/DC" xmlns:omgdi="http://www.omg.org/spec/DD/20100524/DI" typeLanguage="http://www.w3.org/2001/XMLSchema" expressionLanguage="http://www.w3.org/1999/XPath" targetNamespace="http://www.activiti.org/test">
                          { processXml }
                        </definitions>

      val repository = engine.getRepositoryService()
      val deployment = repository.createDeployment()
      deployment.addString("test.bpmn", definitions.toString).name("Test deployment")
      deployment.deploy()

    }
  }
}