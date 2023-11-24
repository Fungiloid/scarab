import Permission.offsetDateTimeMapping
import cats.data.EitherT
import cats.effect._
import org.http4s.{AuthedRoutes, HttpRoutes, Response, Status}
import org.http4s.dsl.io._
import org.http4s.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.multipart.{Multipart, Part}
import slick.jdbc.PostgresProfile.api._

import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.{Locale, UUID}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object Image {

  case class ImageDB(id: UUID, created: OffsetDateTime, title: String, description:String, location:String)

  class ImageTable(tag: Tag) extends Table[ImageDB](tag, "images") {
    def id = column[UUID]("id", O.PrimaryKey)

    def created = column[OffsetDateTime]("created")(offsetDateTimeMapping)

    def title = column[String]("title")
    def description = column[String]("description")
    def location = column[String]("location")

    def * = (id, created, title, description, location) <> (ImageDB.tupled, ImageDB.unapply)
  }

  val imageTable = TableQuery[ImageTable]

  case class ImageResp(id: UUID, created: OffsetDateTime, title: String, description:String, location:String)

  case class ImagePost(title: String, description:String)

  case class ImagePut(title: String, description:String)

  def routes: () => AuthedRoutes[Claims, IO] = () => {
    AuthedRoutes.of[Claims, IO] {
      case req@POST -> Root as claims =>
        req.req.decode[Multipart[IO]] { multipart =>
          multipart.parts.find(_.name.contains("file")) match {

            case Some(filePart) =>
              val filename = filePart.filename.getOrElse("default")
              S3.uploadFile(filename, filePart)
                .flatMap { putResponse => {
                  Ok(s"File $filename uploaded successfully")
                }
                }.handleErrorWith { error => {
                System.out.println(error.getStackTrace)
                InternalServerError(s"File upload failed: ${error.getMessage}")
              }
              }
            case None =>
              BadRequest("Missing file part in the request")


          }
        }
    }
  }

  private def createImage(imagePost: ImagePost, l:String): Future[Either[ErrorJson, ImageDB]]  = {
    val imageDB = ImageDB(id = UUID.randomUUID(), created = Util.offsetDateTimeNow, title = imagePost.title, description = imagePost.description, location = l)
    //EitherT(IO.fromFuture(IO(
      DatabaseConnector.db.run(imageTable += imageDB).map { _ =>
        Right(imageDB)
      }.recover {
        case exception: Throwable => Left(ErrorJson(500, exception.getMessage))
      }
    //)))
  }
}