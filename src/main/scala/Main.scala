
import Auth.TokenResp
import Permission._
import Role._
import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import com.comcast.ip4s.IpLiteralSyntax
import io.circe.{Encoder, Json}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.{AuthedRoutes, EntityDecoder, Header, HttpRoutes, circe, multipart}
import org.http4s.dsl.io.{Ok, _}
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityCodec._
import io.circe.syntax._
import org.http4s.multipart.Multipart
import org.http4s.server.Router

import java.io.FileOutputStream

object Main extends IOApp {


  implicit val decoderClaims = circe.jsonOf[IO, Claims]
  implicit val decoderImage = circe.jsonOf[IO, ImageReq]
  implicit val decoderCat = circe.jsonOf[IO, CategoryReq]
  implicit val decoderCatList = circe.jsonOf[IO, List[CategoryResp]]

  val service = HttpRoutes.of[IO] {
    case GET -> Root / "categories" / id =>
      Category.getCategory(id.toLong).flatMap(categoryDB => Ok(CategoryResp(categoryDB.id, categoryDB.name).asJson))
    case DELETE -> Root / "categories" / id =>
      Category.deleteCategory(id.toLong).handleErrorWith(t => InternalServerError()).flatMap(complete => NoContent())
    case GET -> Root / "categories" =>
      Ok(Category.getCategories.map(catDBList => Category.categoryDBToCategoryResponse(catDBList)).map(catRespList => catRespList.asJson))
    case req @ POST -> Root / "categories" =>
      for {
        cat <- req.as[CategoryReq]
        resp <- Category.createCategory(cat).flatMap(categoryDB => Created(CategoryResp(categoryDB.id, categoryDB.name).asJson))
      } yield (resp)
    case req@PUT -> Root / "categories" / id =>
      for {
        cat <- req.as[CategoryReq]
        resp <- Category.updateCategory(cat, id.toLong).flatMap(updated => Ok(CategoryResp(updated.id, updated.name).asJson))
      } yield (resp)

    case GET -> Root / "tags" / id =>
      Tag.getTag(id.toLong).flatMap(tagDB => Ok(TagResp(tagDB.id, tagDB.created, tagDB.name).asJson)).handleErrorWith(t => InternalServerError(t.toString))
    case DELETE -> Root / "tags" / id =>
      Tag.deleteTag(id.toLong).flatMap(complete => NoContent()).handleErrorWith(t => InternalServerError(t.toString))
    case req@PUT -> Root / "tags" / id =>
      for {
        requestBody <- req.as[TagReq]
        resp <- Tag.updateTag(requestBody, id.toLong).flatMap(updated => Ok(TagResp(updated.id, updated.created, updated.name).asJson)).handleErrorWith(t => InternalServerError(t.toString))
      } yield (resp)
    case req@POST -> Root / "tags" =>
      for {
        requestBody <- req.as[TagReq]
        resp <- Tag.createTag(requestBody).flatMap(tagDB => Created(TagResp(tagDB.id, tagDB.created, tagDB.name).asJson)).handleErrorWith(t => InternalServerError(t.toString))
      } yield (resp)
    case GET -> Root / "tags" =>
      Tag.getTags.map(tags => Tag.TagDBToTagResponse(tags)).map(list => list.asJson).flatMap(json => Ok(json)).handleErrorWith(t => InternalServerError(t.toString))

  }.orNotFound

  val testRoutes = HttpRoutes.of[IO]  {
    case req@POST -> Root / "token-valid" =>
      for {
        requestBody <- req.as[Claims]
        resp <- Auth.generateValidToken(requestBody.userId, requestBody.permissions).map(token => TokenResp(token)).flatMap(tokenResp => Ok(tokenResp.asJson).map(_.putHeaders(Header("Content-Type", "application/json")))).handleErrorWith(t => InternalServerError(t.toString))
      } yield (resp)
    case req@POST -> Root / "token-invalid" =>
      for {
        requestBody <- req.as[Claims]
        resp <- Ok(Auth.generateInvalidToken(requestBody.userId, requestBody.permissions))
      } yield (resp)
    case req@POST -> Root / "token-expired" =>
      for {
        requestBody <- req.as[Claims]
        resp <- Ok(Auth.generateExpiredToken(requestBody.userId, requestBody.permissions))
      } yield (resp)
  }

  val authRoutes = HttpRoutes.of[IO] {
    case req@POST -> Root / "register" =>
      for {
        requestBody <- req.as[Claims]
        resp <- Ok(Auth.generateValidToken(requestBody.userId, requestBody.permissions))
      } yield (resp)
    case req@POST -> Root / "login" =>
      for {
        requestBody <- req.as[Claims]
        resp <- Ok(Auth.generateInvalidToken(requestBody.userId, requestBody.permissions))
      } yield (resp)
  }

  val serviceRouter =  {
    Router(
      "/auth" -> Auth.routes.apply(),
      "/test" -> testRoutes,
      "/users" -> Auth.middleware(User.routes.apply()),
      "/groups" -> Group.routes.apply(),
      "/roles" -> Role.routes.apply(),
      "/permissions" -> Auth.middleware(Permission.routes.apply()),
      "/images" -> Auth.middleware(Image.routes.apply()),
      "/posts" -> Auth.middleware(Post.routes.apply())
    )
  }.orNotFound

  def run(args: List[String]): IO[ExitCode] =
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8642")
      .withHttpApp(serviceRouter)
      .build
      .use(_ => IO.never)
      .as(ExitCode.Success)
}