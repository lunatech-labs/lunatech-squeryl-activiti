package com.lunatech.labs.experiments

import org.specs2.specification.Scope
import org.specs2.mutable.After
import org.squeryl.SessionFactory
import org.squeryl.Session
import org.squeryl.PrimitiveTypeMode.transaction
import org.h2.tools.Server
import java.sql.DriverManager
import org.h2.jdbcx.JdbcDataSource
import org.squeryl.adapters.H2Adapter

/**
 * A scope in which an H2 database is available.
 */
trait DBScope extends Scope with After {
  val jdbcUrl = "jdbc:h2:test"
  val server = Server.createTcpServer().start()

  SessionFactory.concreteFactory = Some { () =>
    Session.create(DriverManager.getConnection(jdbcUrl), new H2Adapter)
  }

  setupDB()

  implicit val ds = new JdbcDataSource()
  ds.setURL(jdbcUrl)


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