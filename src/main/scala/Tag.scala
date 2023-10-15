import cats.effect._
import skunk.{~, _}
import skunk.codec.all._
import skunk.data.Completion
import skunk.implicits._

import java.time.OffsetDateTime

object Tag {

  val getTag: Long => IO[TagDB] = (id) =>
    Database.session
      .flatMap(session => session.prepareR(getTagQuery))
      .use(preparedQuery => preparedQuery.unique(id))

  val updateTag: (TagReq, Long) => IO[TagDB] = (req, id) =>
    Database.session
      .flatMap(session => session.prepareR(updateTagQuery))
      .use(preparedQuery => preparedQuery.unique(req.name, id))

  val createTag: TagReq => IO[TagDB] = (req) =>
    Database.session
      .flatMap(session => session.prepareR(createTagQuery))
      .use(preparedQuery => preparedQuery.unique(Util.offsetDateTimeNow, req.name))

  val getTags: IO[List[TagDB]] =
    Database.session
      .use(session => session.execute(getTagsQuery))

  val deleteTag: Long => IO[Completion] = (id) =>
    Database.session
      .flatMap(session => session.prepareR(deleteTagQuery))
      .use(preparedCommand => preparedCommand.execute(id))

  val TagDBToTagResponse: List[TagDB] => List[TagResp] = (list) =>
    list.map(tagDB => TagResp(tagDB.id, tagDB.created, tagDB.name)).toList

  val createTagQuery: Query[OffsetDateTime *: String *: EmptyTuple, TagDB] =
    sql"INSERT INTO tags(created, name) VALUES($timestamptz, $varchar) RETURNING *"
      .query(int8 ~ timestamptz ~ varchar).gmap[TagDB]

  val getTagQuery: Query[Long, TagDB] =
    sql"SELECT * FROM tags WHERE id = $int8".query(int8 ~ timestamptz ~ varchar).gmap[TagDB]

  val updateTagQuery: Query[String *: Long *: EmptyTuple, TagDB] =
    sql"UPDATE tags SET name = $varchar  WHERE id = $int8 RETURNING *".query(int8 ~ timestamptz ~ varchar).gmap[TagDB]

  val getTagsQuery: Query[Void, TagDB] =
    sql"SELECT * FROM tags".query(int8 ~ timestamptz ~ varchar).gmap[TagDB]

  val deleteTagQuery: Command[Long] =
    sql"DELETE FROM tags WHERE id = $int8"
      .command
}
