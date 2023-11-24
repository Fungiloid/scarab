import Role.RoleDB
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

import java.time.OffsetDateTime

object Group {

  case class GroupDB(id:Long, created:OffsetDateTime, name:String)
  case class GroupResp(id:Long, created:OffsetDateTime, name:String)

  case class GroupPost(name: String, roles: Option[List[Long]] = Option.empty)
  case class GroupPut(name: Option[String] = Option.empty, roles: Option[List[Long]] = Option.empty)

  def routes: () => HttpRoutes[IO] = () => {
    HttpRoutes.of[IO] {
      case GET -> Root / id =>
        Group.getGroup(id.toLong)
          .flatMap(groupDB => Ok(GroupResp(groupDB.id, groupDB.created, groupDB.name).asJson))
          .handleErrorWith(t => InternalServerError(t.toString))
      case DELETE -> Root / id =>
        Group.deleteGroup(id.toLong).flatMap(complete => NoContent()).handleErrorWith(t => InternalServerError(t.toString))
      case req@PUT -> Root / id =>
        for {
          requestBody <- req.as[GroupPut]
          resp <- Group.updateGroup(requestBody, id.toLong)
            .foldF(errorJson => InternalServerError(errorJson).map(resp => resp.withStatus(org.http4s.Status(errorJson.code))), groupDB => Ok(GroupResp(groupDB.id, groupDB.created, groupDB.name).asJson)).handleErrorWith(t => InternalServerError(t.toString))
        } yield (resp)
      case req@POST -> Root =>
        for {
          requestBody <- req.as[GroupPost]
          resp <- Group.createGroup(requestBody).flatMap(groupDB => Created(GroupResp(groupDB.id, groupDB.created, groupDB.name).asJson)).handleErrorWith(t => InternalServerError(t.toString))
        } yield (resp)
      case GET -> Root =>
        Group.getGroups.map(groups => Group.GroupDBToGroupResponse(groups)).map(list => list.asJson).flatMap(json => Ok(json)).handleErrorWith(t => InternalServerError(t.toString))

    }
  }

  private val getGroup: Long => IO[GroupDB] = (id) =>
    ScarabDatabase.session
      .flatMap(session => session.prepareR(getGroupQuery))
      .use(preparedQuery => preparedQuery.unique(id))

  private val getGroupRoles: Long => IO[List[RoleDB]] = (id) =>
    ScarabDatabase.session
      .flatMap(session => session.prepareR(getGroupRolesQuery))
      .use(preparedQuery => preparedQuery.stream(id, 1000).compile.toList)

  private val updateGroup: (GroupPut, Long) => EitherT[IO, ErrorJson, GroupDB] = (req, id) => {
    val res: IO[Either[ErrorJson, GroupDB]] = ScarabDatabase.session.use { session =>
      session.transaction.use { _ =>
        List(
          req.name.traverse_(name => session.prepareR(updateGroupQuery).use(pq => pq.unique(name, id))),
          req.roles.traverse_(roles => dropRoles(id).flatMap(_ => assignRoles(id, roles, session)))
        ).parSequence_
      }.flatMap(_ => getGroup(id).attempt.map(either => either.left.map(t => ErrorJson(500, "unable to update group"))))
    }
    EitherT(res)
  }


  private val createGroup: GroupPost => IO[GroupDB] = (req) =>
    ScarabDatabase.session.use { session =>
      session.transaction.use { tx =>
        session
          .prepareR(createGroupQuery)
          .use(pq => pq.unique(Util.offsetDateTimeNow, req.name))
          .flatMap(group => assignRoles(group.id, req.roles.getOrElse(List.empty), session))
      }
    }

  private val getGroups: IO[List[GroupDB]] =
    ScarabDatabase.session
      .use(session => session.execute(getGroupsQuery))

  private val deleteGroup: Long => IO[Completion] = (id) =>
    ScarabDatabase.session
      .flatMap(session => session.prepareR(deleteGroupQuery))
      .use(preparedCommand => preparedCommand.execute(id))

  private val GroupDBToGroupResponse: List[GroupDB] => List[GroupResp] = (list) =>
    list.map(groupDB => GroupResp(groupDB.id, groupDB.created, groupDB.name)).toList

  private def dropRoles: Long => IO[Completion] = (id) =>
    ScarabDatabase.session
      .flatMap(session => session.prepareR(dropRolesQuery))
      .use(preparedCommand => preparedCommand.execute(id))

  private val assignRoles: (Long, List[Long], Session[IO]) => IO[GroupDB] = (groupId, roles, session) => {
    session.prepareR(assignRolesCommand).use { cmd =>
      roles.traverse_(id => cmd.execute(groupId, id))
    }.flatMap(_ => getGroup(groupId))
  }

  private val createGroupQuery: Query[OffsetDateTime *: String *: EmptyTuple, GroupDB] =
    sql"INSERT INTO groups(created, name) VALUES($timestamptz, $varchar) RETURNING *"
      .query(int8 ~ timestamptz ~ varchar).gmap[GroupDB]

  private val getGroupQuery: Query[Long, GroupDB] =
    sql"SELECT * FROM groups WHERE id = $int8".query(int8 ~ timestamptz ~ varchar).gmap[GroupDB]

  private val updateGroupQuery: Query[String *: Long *: EmptyTuple, GroupDB] =
    sql"UPDATE groups SET name = $varchar  WHERE id = $int8 RETURNING *".query(int8 ~ timestamptz ~ varchar).gmap[GroupDB]

  private val getGroupsQuery: Query[Void, GroupDB] =
    sql"SELECT * FROM groups".query(int8 ~ timestamptz ~ varchar).gmap[GroupDB]

  private val deleteGroupQuery: Command[Long] =
    sql"DELETE FROM groups WHERE id = $int8"
      .command

  private val assignRolesCommand: Command[Long *: Long *: EmptyTuple] =
    sql"INSERT INTO group_role(group_id, role_id) VALUES ($int8, $int8)"
      .command

  private val getGroupRolesQuery: Query[Long, Role.RoleDB] =
    sql"select roles.sql.id, roles.sql.created, roles.sql.name from roles.sql join group_role ON role.id = group_role.role_id where group_id = $int8".query(uuid ~ timestamptz ~ varchar).gmap[RoleDB]

  private val dropRolesQuery: Command[Long] =
    sql"DELETE FROM group_role WHERE group_id = $int8"
      .command
}
