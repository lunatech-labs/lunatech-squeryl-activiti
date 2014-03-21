package com.lunatech.labs.experiments

import java.sql.Connection
import java.util.Properties
import javax.naming.OperationNotSupportedException
import javax.sql.DataSource
import org.apache.ibatis.session.TransactionIsolationLevel
import org.apache.ibatis.transaction.{ Transaction, TransactionFactory }
import org.apache.ibatis.transaction.managed.ManagedTransaction
import org.squeryl.{ Session, SessionFactory }

/**
 * Transaction factory that joins a Squeryl transaction if called from inside a Squeryl transaction block.
 *
 * Limitation: Will ignore the passed in datasource, and always use the connection factory registered with Squeryl
 */
class SquerylJoinedTransactionFactory extends TransactionFactory {
  override def setProperties(props: Properties) {}

  override def newTransaction(connection: Connection) = throw new OperationNotSupportedException("Only the `newTransaction` method that takes a DataSource is supported on the SquerylJoinedTransactionFactory")

  def newTransaction(dataSource: DataSource, level: TransactionIsolationLevel, autoCommit: Boolean) = {
    Session.currentSessionOption map { session =>
      new ManagedTransaction(session.connection, false)
    } getOrElse new SquerylTransaction()
  }
}

/**
 * Transaction that populates Squeryls session thread local, so that
 * any Squeryl `inTransaction` block that's executed on the same thread as this
 * transaction between it's created and closed, will join this transaction
 */
class SquerylTransaction() extends Transaction {

  private val session = SessionFactory.newSession
  private val connection = session.connection

  private val originalAutoCommit = connection.getAutoCommit
  if(originalAutoCommit) connection.setAutoCommit(false)

  session.bindToCurrentThread

  override def close() = {
    // Rollback whatever is not committed
    connection.rollback()

    if(originalAutoCommit != connection.getAutoCommit)
      connection.setAutoCommit(originalAutoCommit)

    session.unbindFromCurrentThread
    session.close
  }

  override def commit() = connection.commit()
  override def getConnection() = connection
  override def rollback() = connection.rollback()

}
