import cats.effect.{IO, Resource}
import com.typesafe.config.ConfigFactory
import skunk.Session
import natchez.Trace.Implicits.noop
import slick.jdbc.JdbcBackend.Database

object ScarabDatabase {
  val session: Resource[IO, Session[IO]] =
    Session.single(
      host = "localhost",
      port = 5432,
      user = "admin",
      database = "postgres",
      password = Some("admin")
    )
}

object DatabaseConnector {
  val config = ConfigFactory.load()
  val db: Database = Database.forConfig("postgres", config)
}