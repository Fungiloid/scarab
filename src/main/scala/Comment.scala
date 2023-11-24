import cats.effect._
import skunk.{~, _}
import skunk.codec.all._
import skunk.data.Completion
import skunk.implicits._

import java.time.OffsetDateTime

object Comment {

  val getComment: Long => IO[CommentDB] = (id) =>
    ScarabDatabase.session
      .flatMap(session => session.prepareR(getCommentQuery))
      .use(preparedQuery => preparedQuery.unique(id))

  val updateComment: (CommentReq, Long) => IO[CommentDB] = (req, id) =>
    ScarabDatabase.session
      .flatMap(session => session.prepareR(updateCommentQuery))
      .use(preparedQuery => preparedQuery.unique(req.text, id))

  val createComment: CommentReq => IO[CommentDB] = (req) =>
    ScarabDatabase.session
      .flatMap(session => session.prepareR(createCommentQuery))
      .use(preparedQuery => preparedQuery.unique(Util.offsetDateTimeNow, req.text))

  val getComments: IO[List[CommentDB]] =
    ScarabDatabase.session
      .use(session => session.execute(getCommentsQuery))

  val deleteComment: Long => IO[Completion] = (id) =>
    ScarabDatabase.session
      .flatMap(session => session.prepareR(deleteCommentQuery))
      .use(preparedCommand => preparedCommand.execute(id))

  val CommentDBToCommentResponse: List[CommentDB] => List[CommentResp] = (list) =>
    list.map(commentDB => CommentResp(commentDB.id, commentDB.created, commentDB.text)).toList

  val createCommentQuery: Query[OffsetDateTime *: String *: EmptyTuple, CommentDB] =
    sql"INSERT INTO comments(created, text) VALUES($timestamptz, $varchar) RETURNING *"
      .query(int8 ~ timestamptz ~ varchar).gmap[CommentDB]

  val createCommentCommentQuery: Command[Long *: Long *: Long *: EmptyTuple] =
    sql"INSERT INTO comment_comment(parent_id, child_id, creator_id) VALUES($int8, $int8, $int8)"
      .command

  val createImageCommentQuery: Command[Long *: Long *: Long *: EmptyTuple] =
    sql"INSERT INTO image_comment(image_id, comment_id, creator_id) VALUES($int8, $int8, $int8)"
      .command

  val getCommentQuery: Query[Long, CommentDB] =
    sql"SELECT * FROM comments WHERE id = $int8".query(int8 ~ timestamptz ~ varchar).gmap[CommentDB]

  val updateCommentQuery: Query[String *: Long *: EmptyTuple, CommentDB] =
    sql"UPDATE comments SET text = $varchar  WHERE id = $int8 RETURNING *".query(int8 ~ timestamptz ~ varchar).gmap[CommentDB]

  val getCommentsQuery: Query[Void, CommentDB] =
    sql"SELECT * FROM comments".query(int8 ~ timestamptz ~ varchar).gmap[CommentDB]

  val deleteCommentQuery: Command[Long] =
    sql"DELETE FROM comments WHERE id = $int8"
      .command
}
