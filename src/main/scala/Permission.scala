import cats.data.EitherT
import cats.effect._
import org.http4s.{AuthedRoutes, HttpRoutes, Response, Status}
import org.http4s.dsl.io._
import org.http4s.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import slick.jdbc.PostgresProfile.api._

import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.{Locale, UUID}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object Permission {
  case class PermissionDB(id:UUID, created:OffsetDateTime, name:String)

  class PermissionTable(tag: Tag) extends Table[PermissionDB](tag, "permissions") {
    def id = column[UUID]("id", O.PrimaryKey)
    def created = column[OffsetDateTime]("created")(offsetDateTimeMapping)
    def name = column[String]("name")
    def * = (id, created, name) <> (PermissionDB.tupled, PermissionDB.unapply)
  }
  val permissionTable = TableQuery[PermissionTable]

  case class PermissionResp(id:UUID, created:OffsetDateTime, name:String)
  case class PermissionPost(name:String)
  case class PermissionPut(name:String)
  
  
  def routes : () => AuthedRoutes[Claims, IO] = () => {
    AuthedRoutes.of[Claims, IO] {
      case GET -> Root / id as claims =>
        EitherT(IO.fromFuture(IO(getPermission(UUID.fromString(id))))).fold(errorJson => NotFound(errorJson.asJson), permissionDB => Ok(PermissionResp(permissionDB.id, permissionDB.created, permissionDB.name).asJson)).flatMap(r => r)
      case DELETE -> Root / id as claims =>
        EitherT(IO.pure(Auth.validatePermissions(claims, List("admin"))))
          .flatMap(_ => EitherT(IO.fromFuture(IO(deletePermission(UUID.fromString(id))))))
          .fold(errorJson => IO(Response[IO](status = Status.fromInt(errorJson.code).getOrElse(InternalServerError)).withEntity(errorJson.asJson)), _ => NoContent()).flatMap(r => r)
      case req@PUT -> Root / id as claims =>
        for {
          requestBody <- req.req.as[PermissionPut]
          response <- EitherT(IO.pure(Auth.validatePermissions(claims, List("admin"))))
            .flatMap(_ => EitherT(IO.fromFuture(IO(updatePermission(UUID.fromString(id), requestBody)))))
            .map(permissions => Permission.permissionDBToPermissionResponse(permissions))
            .fold(errorJson => IO(Response[IO] (status = Status.fromInt(errorJson.code).getOrElse(InternalServerError)).withEntity(errorJson.asJson)), permissions => Ok(permissions.asJson)).flatMap(r => r)
        } yield (response)
      case req@POST -> Root as claims =>
        for {
          requestBody <- req.req.as[PermissionPost]
          response <- EitherT(IO.pure(Auth.validatePermissions(claims, List("admin"))))
            .flatMap(_ => EitherT(IO.fromFuture(IO(createPermission(requestBody)))))
            .map(permissions => Permission.permissionDBToPermissionResponse(permissions))
            .fold(errorJson => IO(Response[IO] (status = Status.fromInt(errorJson.code).getOrElse(InternalServerError)).withEntity(errorJson.asJson)), permissions => Ok(permissions.asJson)).flatMap(r => r)
        } yield (response)
      case GET -> Root as claims =>
        EitherT(IO.pure(Auth.validatePermissions(claims, List("admin"))))
          .flatMap(_ => EitherT(IO.fromFuture(IO(getPermissions()))))
          .map(permissions => Permission.permissionDBListToPermissionResponse(permissions))
          .fold(errorJson => IO(Response[IO](status = Status.fromInt(errorJson.code).getOrElse(InternalServerError)).withEntity(errorJson.asJson)), permissions => Ok(permissions.asJson)).flatMap(r => r)
    }
  }

  implicit val offsetDateTimeMapping: BaseColumnType[OffsetDateTime] = MappedColumnType.base[OffsetDateTime, Timestamp](
    offsetDateTime => Timestamp.valueOf(offsetDateTime.atZoneSameInstant(java.time.ZoneOffset.UTC).toLocalDateTime),
    timestamp => OffsetDateTime.ofInstant(timestamp.toInstant, java.time.ZoneOffset.UTC)
  )



  private def createPermission(permissionPost: PermissionPost): Future[Either[ErrorJson, PermissionDB]] = {
    val newPermissionDB = PermissionDB(id = UUID.randomUUID(), name = permissionPost.name, created = Util.offsetDateTimeNow)
    DatabaseConnector.db.run(permissionTable += newPermissionDB).map { _ =>
      Right(newPermissionDB)
    }.recover {
      case exception: Throwable => Left(ErrorJson(500, exception.getMessage))
    }
  }

  private def updatePermission(id:UUID, permissionPut: PermissionPut): Future[Either[ErrorJson, PermissionDB]] = {
    val updateQuery = permissionTable.filter(_.id === id).map(_.name).update(permissionPut.name)

    val action = for {
      rowsAffected <- updateQuery
      permission <- if (rowsAffected > 0) permissionTable.filter(_.id === id).result.headOption else DBIO.successful(None)
    } yield permission

    DatabaseConnector.db.run(action.transactionally).map {
        case Some(permission) => Right(permission)
        case None => Left(ErrorJson(500, "updated permission not found"))
    }.recover {
      case exception: Throwable => Left(ErrorJson(500, exception.getMessage))
    }
  }
  private def getPermission(id: UUID): Future[Either[ErrorJson, PermissionDB]] =
    DatabaseConnector.db.run(permissionTable.filter(_.id === id).result.headOption).map {
      case Some(permission) => Right(permission)
      case None => Left(ErrorJson(404, "no permission for given uuid found"))
    }.recover {
      case exception: Throwable => Left(ErrorJson(500, exception.getMessage))
    }

  private def deletePermission(id: UUID): Future[Either[ErrorJson, Int]] =
    DatabaseConnector.db.run(permissionTable.filter(_.id === id).delete).map { int =>
      Right(int)
    }.recover {
      case exception: Throwable => Left(ErrorJson(500, exception.getMessage))
    }

  private def getPermissions(): Future[Either[ErrorJson, Seq[PermissionDB]]] =
    DatabaseConnector.db.run(permissionTable.result).map { permissions =>
      Right(permissions)
    }.recover {
      case exception: Throwable => Left(ErrorJson(500, exception.getMessage))
    }



  def permissionDBListToPermissionResponse: Seq[PermissionDB] => Seq[PermissionResp] = (list) =>
    list.map(permissionDB => PermissionResp(permissionDB.id, permissionDB.created, permissionDB.name)).toSeq

  def permissionDBToPermissionResponse: PermissionDB => PermissionResp = (permissionDB) =>
    PermissionResp(permissionDB.id, permissionDB.created, permissionDB.name)

}
