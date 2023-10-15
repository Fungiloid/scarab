import Permission.PermissionResp

import java.time.{Instant, OffsetDateTime, ZonedDateTime}


// Exceptions
final case class UnauthorizedException(private val message: String = "", private val cause: Throwable = None.orNull ) extends Exception(message, cause)
final case class NoJWTSecretSet() extends Exception("no secret for JWT found. set SCARAB_SECRET asn environment variable")

// Meta Models
case class ErrorJson(code:Int, message:String)
case class ResponseList[A](page:Long, perPage:Long, totalResults:Long, results:List[A])
case class Claims(userId: Long, permissions: List[String])

case class Login(username: String, password: String)

// Models how they exists in the DB
case class ImageDB(id:Long, created:OffsetDateTime, location:String, name:String, description:String)
case class CategoryDB(id:Long, created:OffsetDateTime, name:String)
case class TagDB(id:Long, created:OffsetDateTime, name:String)
case class CommentDB(id:Long, created:OffsetDateTime, text:String)

// Models how they get sent to the FE
case class ImageResp(id:Long, created:OffsetDateTime, location:String, name:String, description:String)
case class CategoryResp(id:Long, name:String)
case class TagResp(id:Long, created:OffsetDateTime, name:String)

case class CommentResp(id:Long, created:OffsetDateTime, text:String)

// Models how they are created from the FE
case class ImageReq(name:String, description:String)
case class CategoryReq(name:String)
case class TagReq(name:String)

case class CommentReq(text:String)
