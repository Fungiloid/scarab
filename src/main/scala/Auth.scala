
import cats.effect._
import cats.data._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server._
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityCodec._
import io.circe.parser._
import io.circe.syntax.EncoderOps
import org.http4s.headers.Authorization
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}

import java.time.Instant

object Auth {
  val invalidKey = "invalidKey"
  val jwtAlgorithm = JwtAlgorithm.HS256


  val validatePermissions: (Claims, List[String]) => Either[ErrorJson, Claims] = (claims, requirements) => {
    val intersect = claims.permissions.intersect(requirements)
    if (intersect.size > 0) {
      Right(claims)
    } else {
      Left(ErrorJson(403, "insufficient permissions"))
    }
  }

  private val getSecretKey: IO[String] =
    sys.env.get("SCARAB_SECRET") match {
      case Some(value) => IO(value)
      case None => IO.raiseError(NoJWTSecretSet())
    }

  val generateValidToken: (Long, List[String]) => IO[String] = (userId, permissions) => {
    getSecretKey.map(secret => JwtCirce.encode(JwtClaim(content = Claims(userId, permissions).asJson.noSpaces, issuedAt = Some(Instant.now.getEpochSecond), expiration = Some(Instant.now.plusSeconds(157784760).getEpochSecond)), secret, jwtAlgorithm)
    )
  }

  val generateExpiredToken: (Long, List[String]) => IO[String] = (userId, permissions) => {
    getSecretKey.map(secret => JwtCirce.encode(JwtClaim(content = Claims(userId, permissions).asJson.noSpaces, issuedAt = Some(Instant.EPOCH.getEpochSecond), expiration = Some(Instant.EPOCH.plusSeconds(157784760).getEpochSecond)), secret, jwtAlgorithm)
    )
  }

  val generateInvalidToken: (Long, List[String]) => String = (userId, permissions) => {
    JwtCirce.encode(JwtClaim(content = Claims(userId, permissions).asJson.noSpaces, issuedAt = Some(Instant.now.getEpochSecond), expiration = Some(Instant.now.plusSeconds(157784760).getEpochSecond)), invalidKey, jwtAlgorithm)
  }

  val authUserEither: Kleisli[IO, Request[IO], Either[ErrorJson, Claims]] =
    Kleisli(req => {
      val either: Either[ErrorJson, String] = req.headers.get[Authorization].collect {
        case Authorization(Credentials.Token(AuthScheme.Bearer, token)) => new String(java.util.Base64.getDecoder.decode(token))
      } match {
        case Some(x) => Right(x)
        case None => Left(ErrorJson(401, "no auth token found"))
      }

      IO(either.map(token => parse(token).flatMap(_.as[Claims]).left.map(error => ErrorJson(401, "error parsing auth token")))
        .flatMap(either => either))
    })

  val onFailure: AuthedRoutes[ErrorJson, IO] =
    Kleisli(req => OptionT.liftF(Forbidden(req.context).map(_.withStatus(Status.Unauthorized))))

  val middleware: AuthMiddleware[IO, Claims] =
    AuthMiddleware(authUserEither, onFailure)
}
