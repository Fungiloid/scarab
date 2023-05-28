import java.time.{Instant, OffsetDateTime, ZonedDateTime}


// Meta Models
case class ResponseList[A](page:Long, perPage:Long, totalResults:Long, results:List[A])

// Models how they exists in the DB
case class ImageDB(id:Long, created:OffsetDateTime, location:String, name:String, description:String)
case class CategoryDB(id:Long, created:OffsetDateTime, name:String)
case class Tag(id:Long, created:OffsetDateTime, name:String)
case class Comment(id:Long, created:OffsetDateTime, text:String)
case class User(id:Long, created:OffsetDateTime, username:String, password:String, salt:String)
case class Group(id:Long, created:OffsetDateTime, name:String)
case class Role(id:Long, created:OffsetDateTime, name:String)
case class Permission(id:Long, created:OffsetDateTime, name:String)

// Models how they get sent to the FE
case class ImageResp(id:Long, created:OffsetDateTime, location:String, name:String, description:String)
case class CategoryResp(id:Long, name:String)


// Models how they are created from the FE
case class ImageReq(name:String, description:String)
case class CategoryReq(name:String)