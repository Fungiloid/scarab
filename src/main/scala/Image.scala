import cats.effect._
import skunk.{~, _}
import skunk.codec.all._
import skunk.data.Completion
import skunk.implicits._

import java.time.OffsetDateTime

object Image {

  val getImage: Long => IO[ImageDB] = (id) =>
    Database.session
      .flatMap(session => session.prepareR(getImageQuery))
      .use(preparedQuery => preparedQuery.unique(id))

  val updateImage: (ImageReq, Long) => IO[ImageDB] = (req, id) =>
    Database.session
      .flatMap(session => session.prepareR(updateImageQuery))
      .use(preparedQuery => preparedQuery.unique(req.name, req.description, id))

  val createImage: ImageReq => IO[ImageDB] = (req) =>
    Database.session
      .flatMap(session => session.prepareR(createImageQuery))
      .use(preparedQuery => preparedQuery.unique(Util.offsetDateTimeNow, "/dummy/path", req.name, req.description))

  val getImages: IO[List[ImageDB]] =
    Database.session
      .use(session => session.execute(getImagesQuery))

  val deleteImage: Long => IO[Completion] = (id) =>
    Database.session
      .flatMap(session => session.prepareR(deleteImageQuery))
      .use(preparedCommand => preparedCommand.execute(id))

  val imageDBToImageResponse: List[ImageDB] => List[ImageResp] = (list) =>
    list.map(imgDB => ImageResp(imgDB.id, imgDB.created, imgDB.location, imgDB.name, imgDB.description)).toList

  val createImageQuery: Query[OffsetDateTime *: String *: String *: String *:EmptyTuple, ImageDB] =
    sql"INSERT INTO images(created, location, name, description) VALUES($timestamptz, $varchar, $varchar, $varchar) RETURNING *"
      .query(int8 ~ timestamptz ~ varchar ~ varchar ~ varchar).gmap[ImageDB]

  val getImageQuery: Query[Long, ImageDB] =
    sql"SELECT * FROM images WHERE id = $int8".query(int8 ~ timestamptz ~ varchar ~ varchar ~ varchar).gmap[ImageDB]

  val updateImageQuery: Query[String *: String *: Long *: EmptyTuple, ImageDB] =
    sql"UPDATE images SET name = $varchar, description = $varchar  WHERE id = $int8 RETURNING *".query(int8 ~ timestamptz ~ varchar ~ varchar ~ varchar).gmap[ImageDB]

  val getImagesQuery: Query[Void, ImageDB] =
    sql"SELECT * FROM images".query(int8 ~ timestamptz ~ varchar ~ varchar ~ varchar).gmap[ImageDB]

  val deleteImageQuery: Command[Long] =
    sql"DELETE FROM images WHERE id = $int8"
      .command
}