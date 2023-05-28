import cats.effect.{IO, Resource}
import skunk.Session
import natchez.Trace.Implicits.noop

object Database {
  val session: Resource[IO, Session[IO]] =
    Session.single(
      host = "192.168.178.29",
      port = 5432,
      user = "admin",
      database = "postgres",
      password = Some("admin")
    )
}
