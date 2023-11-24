import cats.effect._
import skunk.{~, _}
import skunk.codec.all._
import skunk.data.Completion
import skunk.implicits._

import java.time.OffsetDateTime

object Category {

  val getCategory: Long => IO[CategoryDB] = (id) =>
    ScarabDatabase.session
      .flatMap(session => session.prepareR(getCategoryQuery))
      .use(preparedQuery => preparedQuery.unique(id))

  val updateCategory: (CategoryReq, Long) => IO[CategoryDB] = (req, id) =>
    ScarabDatabase.session
      .flatMap(session => session.prepareR(updateCategoryQuery))
      .use(preparedQuery => preparedQuery.unique(req.name, id))

  val createCategory: CategoryReq => IO[CategoryDB] = (req) =>
    ScarabDatabase.session
      .flatMap(session => session.prepareR(createCategoryQuery))
      .use(preparedQuery => preparedQuery.unique(Util.offsetDateTimeNow, req.name))

  val getCategories: IO[List[CategoryDB]] =
    ScarabDatabase.session
      .use(session => session.execute(getCategoriesQuery))

  val deleteCategory: Long => IO[Completion] = (id) =>
    ScarabDatabase.session
      .flatMap(session => session.prepareR(deleteCategoryQuery))
      .use(preparedCommand => preparedCommand.execute(id))

  val categoryDBToCategoryResponse: List[CategoryDB] => List[CategoryResp] = (list) =>
    list.map(catDB => CategoryResp(catDB.id, catDB.name)).toList

  val createCategoryQuery: Query[OffsetDateTime *: String *: EmptyTuple, CategoryDB] =
    sql"INSERT INTO categories(created, name) VALUES($timestamptz, $varchar) RETURNING *"
      .query(int8 ~ timestamptz ~ varchar).gmap[CategoryDB]

  val getCategoryQuery: Query[Long, CategoryDB] =
    sql"SELECT * FROM categories WHERE id = $int8".query(int8 ~ timestamptz ~ varchar).gmap[CategoryDB]

  val updateCategoryQuery: Query[String *: Long *: EmptyTuple, CategoryDB] =
    sql"UPDATE categories SET name = $varchar  WHERE id = $int8 RETURNING *".query(int8 ~ timestamptz ~ varchar).gmap[CategoryDB]

  val getCategoriesQuery: Query[Void, CategoryDB] =
    sql"SELECT * FROM categories".query(int8 ~ timestamptz ~ varchar).gmap[CategoryDB]

  val deleteCategoryQuery: Command[Long] =
    sql"DELETE FROM categories WHERE id = $int8"
      .command
}
