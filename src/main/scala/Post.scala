import cats.data.EitherT
import cats.effect.IO
import org.http4s.AuthedRoutes
import org.http4s.dsl.io._
import org.http4s.{AuthedRoutes, HttpRoutes, Response, Status}
import org.http4s.multipart._
import org.http4s.circe._

import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse

import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.generic.auto._
import io.circe.parser._

import java.time.OffsetDateTime
import java.util.UUID

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import slick.jdbc.PostgresProfile.api._

object Post {

  case class PostDB(id: UUID, title: String, description: String, location: String)

  class PostTable(tag: Tag) extends Table[PostDB](tag, "posts") {
    def id = column[UUID]("id", O.PrimaryKey)

    def title = column[String]("title")

    def description = column[String]("description")

    def location = column[String]("location")

    def * = (id, title, description, location) <> (PostDB.tupled, PostDB.unapply)
  }

  val postTable = TableQuery[PostTable]

  case class PostResp(id: UUID, created: OffsetDateTime, title: String, description: String, location: String)
  case class PostPost(title: String, description: String, category:String)

  def routes: () => AuthedRoutes[Claims, IO] = () => {
    AuthedRoutes.of[Claims, IO] {
      case req@POST -> Root as claims =>
        req.req.decode[Multipart[IO]] { multipart =>
          val payload = for {
            data <- multipart.parts.find(_.name.contains("data"))
            fileUpload <- multipart.parts.find(_.name.contains("file"))
          } yield (data, fileUpload)

          payload.map { case (data, file) =>
            EitherT(combinedUpload(data, file))
          }.map(eitherT => eitherT
            .fold(errorJson => InternalServerError(errorJson.asJson), postDB => Ok(postDB.asJson)).flatMap(r => r)).getOrElse(BadRequest())
        }
    }
  }

  def combinedUpload(dataPart: Part[IO], filePart: Part[IO]): IO[Either[ErrorJson, PostDB]] = {
    val data = dataPart.body.compile.toVector.map(_.toArray).map(new String(_))
    for {
      completeMultipartUploadResponse <- S3.uploadFile(filePart.filename.getOrElse("default"), filePart)
      dbResult <- data.flatMap(
        value => decode[PostPost](value) match {
          case Right(post) => IO.pure(post)
          case Left(error) => IO.raiseError(error)
        }
      ).flatMap(post => IO.fromFuture(IO(createPost(post, completeMultipartUploadResponse.location()))))
    } yield dbResult
  }

  private def createPost(postPost: PostPost, location: String): Future[Either[ErrorJson, PostDB]] = {
    val newPostDB = PostDB(id = UUID.randomUUID(), title = postPost.title, description = postPost.description, location = location)
    DatabaseConnector.db.run(postTable += newPostDB).map { _ =>
      Right(newPostDB)
    }.recover {
      case exception: Throwable => Left(ErrorJson(500, exception.getMessage))
    }
  }
}
