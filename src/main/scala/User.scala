import cats.effect._
import org.http4s.{AuthedRoutes, HttpRoutes}
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

object User {

  case class UserPost(username:String, password:String)
  case class UserPut(username:Option[String] = Option.empty, password:Option[String] = Option.empty, groups:Option[List[String]] = Option.empty)
  case class UserResp(id:Long, created:OffsetDateTime, username:String)
  case class UserDB(id:Long, created:OffsetDateTime, username:String, password:String, salt:String)

  val routes: () => AuthedRoutes[Claims, IO] = () => {
    AuthedRoutes.of[Claims, IO] {
      case GET -> Root / id as claims =>
        User.getUser(id.toLong).flatMap(userDB => Ok(UserResp(userDB.id, userDB.created, userDB.username).asJson)).handleErrorWith(t => InternalServerError(t.toString))
      case DELETE -> Root / id as claims =>
        User.deleteUser(id.toLong).flatMap(complete => NoContent()).handleErrorWith(t => InternalServerError(t.toString))
      /*
      case req@PUT -> Root / id as claims =>
        for {
          requestBody <- req.req.as[UserPut]
          resp <- User.updateUser(requestBody, id.toLong).flatMap(updated => Ok(UserResp(updated.id, updated.created, updated.username).asJson)).handleErrorWith(t => InternalServerError(t.toString))
        } yield (resp)
      */
      case req@POST -> Root as claims =>
        for {
          requestBody <- req.req.as[UserPost]
          resp <- User.createUser(requestBody).flatMap(userDB => Created(UserResp(userDB.id, userDB.created, userDB.username).asJson)).handleErrorWith(t => InternalServerError(t.toString))
        } yield (resp)
      case GET -> Root as claims =>
        User.getUsers.map(users => User.UserDBToUserResponse(users)).map(list => list.asJson).flatMap(json => Ok(json)).handleErrorWith(t => InternalServerError(t.toString))
    }
  }

  private val getUser: Long => IO[UserDB] = (id) =>
    ScarabDatabase.session
      .flatMap(session => session.prepareR(getUserQuery))
      .use(preparedQuery => preparedQuery.unique(id))

  val createUser: UserPost => IO[UserDB] = (req) => {
    val salt = Util.randomString(20)
    Util.hashStrong(req.password + salt).map(hashed =>
      ScarabDatabase.session
        .flatMap(session => session.prepareR(createUserQuery))
        .use(preparedQuery => preparedQuery.unique(Util.offsetDateTimeNow, req.username, hashed, salt))
    ).get
  }

  val getUsers: IO[List[UserDB]] =
    ScarabDatabase.session
      .use(session => session.execute(getUsersQuery))

  val deleteUser: Long => IO[Completion] = (id) =>
    ScarabDatabase.session
      .flatMap(session => session.prepareR(deleteUserQuery))
      .use(preparedCommand => preparedCommand.execute(id))

  val UserDBToUserResponse: List[UserDB] => List[UserResp] = (list) =>
    list.map(userDB => UserResp(userDB.id, userDB.created, userDB.username)).toList

  val createUserQuery: Query[OffsetDateTime *: String *: String *: String *: EmptyTuple, UserDB] =
    sql"INSERT INTO users(created, username, password, salt) VALUES($timestamptz, $varchar, $varchar, $varchar) RETURNING *"
      .query(int8 ~ timestamptz ~ varchar ~ varchar ~ varchar).gmap[UserDB]

  val getUserQuery: Query[Long, UserDB] =
    sql"SELECT * FROM users WHERE id = $int8".query(int8 ~ timestamptz ~ varchar ~ varchar ~ varchar).gmap[UserDB]

  val updateUserQuery: Query[Option[String] *: Option[String] *: Long *: EmptyTuple, UserDB] =
    sql"UPDATE users SET username = COALESCE(${varchar.opt}, username), password = COALESCE(${varchar.opt}, password)  WHERE id = $int8 RETURNING *".query(int8 ~ timestamptz ~ varchar ~ varchar ~ varchar).gmap[UserDB]

  val getUsersQuery: Query[Void, UserDB] =
    sql"SELECT * FROM users".query(int8 ~ timestamptz ~ varchar ~ varchar ~ varchar).gmap[UserDB]

  val deleteUserQuery: Command[Long] =
    sql"DELETE FROM users WHERE id = $int8"
      .command

  val dropPermissionsQuery: Command[Long] =
    sql"DELETE FROM role_permission WHERE role_id = $int8"
      .command
}
