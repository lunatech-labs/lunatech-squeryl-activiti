package com.lunatech.labs.experiments

import org.activiti.engine.delegate.{ BpmnError, DelegateExecution, JavaDelegate }
import org.squeryl.PrimitiveTypeMode.inTransaction

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