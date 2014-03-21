package com.lunatech.labs.experiments

import javax.sql.DataSource
import org.activiti.engine.{ ProcessEngine, ProcessEngineConfiguration }
import org.activiti.engine.impl.cfg.ProcessEngineConfigurationImpl
import org.activiti.engine.impl.history.HistoryLevel
import org.apache.ibatis.transaction.TransactionFactory

trait ActivitiTools {

  /**
   * Build an Activiti process engine with the `SquerylJoinedTransactionFactory` as transaction factory
   */
  protected def buildEngine(customTransactionFactory: Option[TransactionFactory] = None, jobExecutor: Boolean = false)(implicit ds: DataSource): ProcessEngine = {
    val basicConf = ProcessEngineConfiguration.
      createStandaloneProcessEngineConfiguration.asInstanceOf[ProcessEngineConfigurationImpl].
      setDataSource(ds).asInstanceOf[ProcessEngineConfigurationImpl].
      setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE).
      setJobExecutorActivate(jobExecutor).
      setHistory(HistoryLevel.ACTIVITY.getKey).asInstanceOf[ProcessEngineConfigurationImpl]

    val fullConf = customTransactionFactory.map(factory => basicConf.setTransactionFactory(factory)).getOrElse(basicConf)
    fullConf.buildProcessEngine()
  }

  /**
   * The Activiti Job executor threads die with failing tasks. This is very noisy in the log, so we suppress the default
   * uncaught exception handler
   */
  def silenceThreadDeaths() = Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler {
    override def uncaughtException(thread: Thread, ex: Throwable) = ()
  })

}