import cats.effect._
import org.http4s.HttpRoutes
import org.http4s.dsl.io._
import org.http4s.circe._
import org.http4s.circe.CirceEntityCodec._
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe._
import io.circe.{Encoder, Json}
import cats.effect._
import cats.implicits._
import skunk.{~, _}
import skunk.codec.all._
import skunk.data.Completion
import skunk.implicits._

import java.time.OffsetDateTime
import java.util.UUID

object Permission {
  case class PermissionDB(id:UUID, created:OffsetDateTime, name:String)
  case class PermissionResp(id:UUID, created:OffsetDateTime, name:String)
  case class PermissionPost(name:String)

  def routes : () => HttpRoutes[IO] = () => {
    HttpRoutes.of[IO] {
      case GET -> Root / id =>
        Permission.getPermission(UUID.fromString(id)).flatMap(permissionDB => Ok(PermissionResp(permissionDB.id, permissionDB.created, permissionDB.name).asJson)).handleErrorWith(t => InternalServerError(t.toString))
      case DELETE -> Root / id =>
        Permission.deletePermission(UUID.fromString(id)).flatMap(complete => NoContent()).handleErrorWith(t => InternalServerError(t.toString))
      case req@PUT -> Root / id =>
        for {
          requestBody <- req.as[PermissionPost]
          resp <- Permission.updatePermission(requestBody, UUID.fromString(id)).flatMap(updated => Ok(PermissionResp(updated.id, updated.created, updated.name).asJson)).handleErrorWith(t => InternalServerError(t.toString))
        } yield (resp)
      case req@POST -> Root =>
        for {
          requestBody <- req.as[PermissionPost]
          resp <- Permission.createPermission(requestBody).flatMap(permissionDB => Created(PermissionResp(permissionDB.id, permissionDB.created, permissionDB.name).asJson)).handleErrorWith(t => InternalServerError(t.toString))
        } yield (resp)
      case GET -> Root =>
        Permission.getPermissions.map(permissions => Permission.permissionDBToPermissionResponse(permissions)).map(list => list.asJson).flatMap(json => Ok(json)).handleErrorWith(t => InternalServerError(t.toString))
    }
  }

  def getPermission: UUID => IO[PermissionDB] = (id) =>
    Database.session
      .flatMap(session => session.prepareR(getPermissionQuery))
      .use(preparedQuery => preparedQuery.unique(id))

  def updatePermission: (PermissionPost, UUID) => IO[PermissionDB] = (req, id) =>
    Database.session
      .flatMap(session => session.prepareR(updatePermissionQuery))
      .use(preparedQuery => preparedQuery.unique(req.name, id))

  def createPermission: PermissionPost => IO[PermissionDB] = (req) =>
    Database.session
      .flatMap(session => session.prepareR(createPermissionQuery))
      .use(preparedQuery => preparedQuery.unique(Util.offsetDateTimeNow, req.name))

  def getPermissions: IO[List[PermissionDB]] =
    Database.session
      .use(session => session.execute(getPermissionsQuery))

  def deletePermission: UUID => IO[Completion] = (id) =>
    Database.session
      .flatMap(session => session.prepareR(deletePermissionQuery))
      .use(preparedCommand => preparedCommand.execute(id))

  def permissionDBToPermissionResponse: List[PermissionDB] => List[PermissionResp] = (list) =>
    list.map(permissionDB => PermissionResp(permissionDB.id, permissionDB.created, permissionDB.name)).toList

  def createPermissionQuery: Query[OffsetDateTime *: String *: EmptyTuple, PermissionDB] =
    sql"INSERT INTO permissions(created, name) VALUES($timestamptz, $varchar) RETURNING *"
      .query(uuid ~ timestamptz ~ varchar).gmap[PermissionDB]

  def getPermissionQuery: Query[UUID, PermissionDB] =
    sql"SELECT * FROM permissions WHERE id = $uuid".query(uuid ~ timestamptz ~ varchar).gmap[PermissionDB]

  def updatePermissionQuery: Query[String *: UUID *: EmptyTuple, PermissionDB] =
    sql"UPDATE permissions SET name = $varchar  WHERE id = $uuid RETURNING *".query(uuid ~ timestamptz ~ varchar).gmap[PermissionDB]

  def getPermissionsQuery: Query[Void, PermissionDB] =
    sql"SELECT * FROM permissions".query(uuid ~ timestamptz ~ varchar).gmap[PermissionDB]

  def deletePermissionQuery: Command[UUID] =
    sql"DELETE FROM permissions WHERE id = $uuid"
      .command
}
