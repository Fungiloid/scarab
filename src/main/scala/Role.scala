import cats.effect._
import cats.implicits._
import cats.data.EitherT
import org.http4s.HttpRoutes
import org.http4s.dsl.io._
import org.http4s.circe._
import org.http4s.circe.CirceEntityCodec._
import io.circe.generic.auto._
import io.circe.syntax._
import skunk.{~, _}
import skunk.codec.all._
import skunk.data.Completion
import skunk.implicits._
import Permission.{PermissionDB, permissionDBListToPermissionResponse, PermissionResp}

import java.time.OffsetDateTime
import java.util.UUID

object Role {

  case class RoleResp(id:UUID, created:OffsetDateTime, name:String, permissions: Seq[PermissionResp] = List.empty)
  case class RoleDB(id:UUID, created:OffsetDateTime, name:String)
  case class RolePost(name: String)
  case class RolePut(name: Option[String] = Option.empty)

  case class RolePostPermissions(permissions: Seq[String] = List.empty)

  def routes : () => HttpRoutes[IO] = () => {
    HttpRoutes.of[IO] {

      case GET -> Root / id =>
        Role.getRole(UUID.fromString(id))
          .map(roleDB => RoleResp(roleDB.id, roleDB.created, roleDB.name))
          .flatMap(roleResp => Role.getRolePermissions(UUID.fromString(id))
            .map(permissionsDB => Permission.permissionDBListToPermissionResponse(permissionsDB))
            .map(permResp => RoleResp(roleResp.id, roleResp.created, roleResp.name, permResp)))
          .flatMap(roleResp => Ok(roleResp.asJson))
          .handleErrorWith(t => InternalServerError(t.toString))
      case DELETE -> Root / id =>
        Role.deleteRole(UUID.fromString(id)).flatMap(complete => NoContent()).handleErrorWith(t => InternalServerError(t.toString))
      case req@PUT -> Root / id =>
        for {
          requestBody <- req.as[RolePut]
          resp <- Role.updateRole(requestBody, UUID.fromString(id))
            .semiflatMap(roleDB => getRolePermissions(roleDB.id).map(permissionsDB => permissionDBListToPermissionResponse(permissionsDB)).map(permissionsResp => RoleResp(roleDB.id, roleDB.created, roleDB.name, permissionsResp)))
            .foldF(errorJson => InternalServerError(errorJson).map(resp => resp.withStatus(org.http4s.Status(errorJson.code))), roleResp => Ok(roleResp.asJson)).handleErrorWith(t => InternalServerError(t.toString))
        } yield (resp)
      case req@POST -> Root =>
        for {
          requestBody <- req.as[RolePost]
          resp <- Role.createRole(requestBody)
              .semiflatMap(roleDB => getRolePermissions(roleDB.id).map(permissionsDB => permissionDBListToPermissionResponse(permissionsDB)).map(permissionsResp => RoleResp(roleDB.id, roleDB.created, roleDB.name, permissionsResp)))
            .foldF(
              errorJson => InternalServerError(errorJson).map(resp => resp.withStatus(org.http4s.Status(errorJson.code))),
              roleResp => Created(roleResp.asJson)
            )
        } yield (resp)
      case GET -> Root =>
        Role.getRoles
          .map(roles => Role.RoleDBToRoleResponse(roles))
          .map(list => list.map(roleResp =>
            Role.getRolePermissions(roleResp.id)
              .map(permissionsDB => Permission.permissionDBListToPermissionResponse(permissionsDB))
              .map(permResp => RoleResp(roleResp.id, roleResp.created, roleResp.name, permResp))
          )
          )
          .flatMap(_.sequence)
          .map(list => list.asJson)
          .flatMap(json => Ok(json))
          .handleErrorWith(t => InternalServerError(t.toString))
      case req@POST -> Root / id / "permissions" =>
          for {
            requestBody <- req.as[RolePostPermissions]
            response <- ScarabDatabase.session.use(session => session.transaction.use(transaction =>
                dropPermissions(UUID.fromString(id), session)
                  .flatMap(_ => assignPermissions(UUID.fromString(id), requestBody.permissions.toList.map(id => UUID.fromString(id)), session))
              )
                .flatMap(_ => getRolePermissions(UUID.fromString(id))
                  .map(permissionsDB => permissionDBListToPermissionResponse(permissionsDB))
                  .flatMap(permissions => getRole(UUID.fromString(id)).map(roleDB => RoleResp(roleDB.id, roleDB.created, roleDB.name, permissions))))
              ).attempt.flatMap(either => either.fold(error => InternalServerError(error.toString), roleResponse => Ok(roleResponse.asJson)))
          } yield(response)
    }
  }

  private def getRole: (UUID) => IO[RoleDB] = (id) =>
    ScarabDatabase.session
      .flatMap(session => session.prepareR(getRoleQuery))
      .use(preparedQuery => preparedQuery.unique(id))

  private def getRolePermissions: UUID => IO[List[PermissionDB]] = (id) =>
    ScarabDatabase.session
      .flatMap(session => session.prepareR(getPermissionsQuery))
      .use(preparedQuery => preparedQuery.stream(id,1000).compile.toList)

  private def updateRole: (RolePut, UUID) => EitherT[IO, ErrorJson, RoleDB] = (req, id) => {
    EitherT(
      ScarabDatabase.session
        .flatMap(session => session.prepareR(updateRoleQuery))
        .use(pq => pq.unique(req.name, id))
        .attempt.map(either => either.left.map(_ => ErrorJson(500, "error updating role")))
    )
  }

  private def createRole: RolePost => EitherT[IO, ErrorJson, RoleDB] = (req) =>
    EitherT(
      ScarabDatabase.session
        .flatMap(session => session.prepareR(createRoleQuery))
        .use(preparedQuery => preparedQuery.unique(Util.offsetDateTimeNow, req.name))
        .attempt.map(either => either.left.map(_ => ErrorJson(500, "error creating role")))
    )

  private def assignPermissions: (UUID, List[UUID], Session[IO]) => IO[List[UUID]] = (roleId, permissions, session) => {
    session.prepareR(assignPermissionCommand).use { cmd =>
      permissions.traverse_(id => cmd.execute(roleId, id))
    }.map(_ => permissions)
  }

  private def getRoles: IO[List[RoleDB]] =
    ScarabDatabase.session
      .use(session => session.execute(getRolesQuery))

  private def deleteRole: UUID => IO[Completion] = (id) =>
    ScarabDatabase.session
      .flatMap(session => session.prepareR(deleteRoleQuery))
      .use(preparedCommand => preparedCommand.execute(id))

  private def dropPermissions : (UUID, Session[IO]) => IO[Completion] = (id, session) =>
    session.prepareR(dropPermissionsQuery)
      .use(preparedCommand => preparedCommand.execute(id))

  private def RoleDBToRoleResponse: List[RoleDB] => List[RoleResp] = (list) =>
    list.map(roleDB => RoleResp(roleDB.id, roleDB.created, roleDB.name)).toList

  private val createRoleQuery: Query[OffsetDateTime *: String *: EmptyTuple, RoleDB] =
    sql"INSERT INTO roles(created, name) VALUES($timestamptz, $varchar) RETURNING *"
      .query(uuid ~ timestamptz ~ varchar).gmap[RoleDB]

  private val getRoleQuery: Query[UUID, RoleDB] =
    sql"SELECT * FROM roles WHERE id = $uuid".query(uuid ~ timestamptz ~ varchar).gmap[RoleDB]

  private val updateRoleQuery: Query[Option[String] *: UUID *: EmptyTuple, RoleDB] =
    sql"UPDATE roles SET name = COALESCE(${varchar.opt}, name)  WHERE id = $uuid RETURNING *".query(uuid ~ timestamptz ~ varchar).gmap[RoleDB]

  private val getRolesQuery: Query[Void, RoleDB] =
    sql"SELECT * FROM roles".query(uuid ~ timestamptz ~ varchar).gmap[RoleDB]

  private val deleteRoleQuery: Command[UUID] =
    sql"DELETE FROM roles WHERE id = $uuid"
      .command

  private val assignPermissionCommand: Command[UUID *: UUID *: EmptyTuple] =
    sql"INSERT INTO role_permission(role_id, permission_id) VALUES ($uuid, $uuid)"
      .command

  private val getPermissionsQuery: Query[UUID, PermissionDB] =
    sql"select permissions.id, permissions.created, permissions.name from permissions join role_permission ON permissions.id = role_permission.permission_id where role_id = $uuid".query(uuid ~ timestamptz ~ varchar).gmap[PermissionDB]

  private val dropPermissionsQuery: Command[UUID] =
    sql"DELETE FROM role_permission WHERE role_id = $uuid"
      .command
}

