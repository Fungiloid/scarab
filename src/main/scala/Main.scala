
import cats.effect.unsafe.implicits.global
import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.IpLiteralSyntax
import io.circe.{Encoder, Json}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.{EntityDecoder, HttpRoutes, circe, multipart}
import org.http4s.dsl.io._
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityCodec._
import io.circe.syntax._
import org.http4s.multipart.Multipart

import java.io.FileOutputStream

object Main extends IOApp {

  implicit val decoderForm = circe.jsonOf[IO, FormData]
  implicit val decoderImage = circe.jsonOf[IO, ImageReq]
  implicit val decoderCat = circe.jsonOf[IO, CategoryReq]
  implicit val decoderCatList = circe.jsonOf[IO, List[CategoryResp]]

  case class FormData( file:String, metadata: ImageReq)

  val categoriesPath = "categories"

  val encoder: Encoder[CategoryResp] = new Encoder[CategoryResp] {
    final def apply(c: CategoryResp): Json = Json.obj(
      ("id", Json.fromLong(c.id)),
      ("name", Json.fromString(c.name))
    )
  }

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
    case GET -> Root / "images" / id =>
      Image.getImage(id.toLong).flatMap(imageDB => Ok(ImageResp(imageDB.id, imageDB.created, imageDB.location, imageDB.name, imageDB.description).asJson))
    case DELETE -> Root / "images" / id =>
      Image.deleteImage(id.toLong).handleErrorWith(t => InternalServerError()).flatMap(complete => NoContent())
    case GET -> Root / "images" =>
      Ok(Image.getImages.map(imgDBList => Image.imageDBToImageResponse(imgDBList)).map(imgRespList => imgRespList.asJson))
    case req@POST -> Root / "images" =>
      req.decode[Multipart[IO]] { multipart =>
        val parts = multipart.parts
        val formDataPart = parts.find(_.name.contains("metadata")).getOrElse(sys.error("Missing form-data part"))
        val filePart = parts.find(_.name.contains("file")).getOrElse(sys.error("Missing form-data part"))
        val fileStream = filePart.body
        val filename = filePart.filename.getOrElse("undefined.jpg")
        val outputStream = new FileOutputStream(s"c:\\Users\\marti\\$filename")
        fileStream.through(fs2.io.writeOutputStream[IO](IO(outputStream))).compile.drain.unsafeRunSync()
        outputStream.close()
        val imgReq = formDataPart.as[ImageReq]
        imgReq.flatMap {
          img => Image.createImage(img).flatMap(imageDB => Ok(ImageResp(imageDB.id, imageDB.created, imageDB.location, imageDB.name, imageDB.description).asJson)).handleErrorWith(t => InternalServerError(t.toString))
        }
      }
    case req@PUT -> Root / "images" / id =>
      for {
        img <- req.as[ImageReq]
        resp <- Image.updateImage(img, id.toLong).flatMap(imageDB => Ok(ImageResp(imageDB.id, imageDB.created, imageDB.location, imageDB.name, imageDB.description).asJson)).handleErrorWith(t => InternalServerError(t.toString))
      } yield (resp)
  }.orNotFound

  def run(args: List[String]): IO[ExitCode] =
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(service)
      .build
      .use(_ => IO.never)
      .as(ExitCode.Success)
}