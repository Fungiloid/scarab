
import cats.effect._
import cats.data._
import io.circe.Json
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server._
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityCodec._
import io.circe.parser._
import io.circe.syntax.EncoderOps
import org.http4s.headers.{Accept, Authorization, Location, `Content-Type`, `Set-Cookie`}
import org.http4s.implicits.http4sLiteralsSyntax
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import skunk.implicits._
import org.http4s.blaze.client._
import org.http4s.client.dsl.io._
import org.http4s.blaze._
import shapeless.ops.zipper

import scala.concurrent.ExecutionContext.Implicits.global
import java.time.Instant

object Auth {
  val invalidKey = "invalidKey"
  val jwtAlgorithm = JwtAlgorithm.HS256
  case class TokenResp(token:String)
  case class KeyCloakTokenRequest(grant_type:String, code:String, redirect_uri:String)
  case class KeyCloakTokenResponse(access_token:String, expires_in:Long, refresh_expires_in:Long, refresh_token:String, token_type:String, id_token:String, session_state:String, scope:String )

  def routes: () => HttpRoutes[IO] = () => {
    HttpRoutes.of[IO] {
      case req @ GET -> Root / "callback" =>

        BlazeClientBuilder[IO](global).resource.use { client =>

          val formData = UrlForm(
            "grant_type" -> "authorization_code",
            "code" -> req.params.get("code").getOrElse(""),
            "redirect_uri" -> "http://localhost:8642/auth/callback"
          )

          val request = POST(formData, uri"http://localhost:5052/realms/scarab/protocol/openid-connect/token")
            .withHeaders(
              `Content-Type`(MediaType.application.`x-www-form-urlencoded`),
              Accept(MediaType.application.json),
              Authorization(BasicCredentials("scarab", "")))

          client.fetch(request) {
            case Status.Successful(res) => res.as[KeyCloakTokenResponse].flatMap {
              responseBody =>
                Found(s"").map(_.withHeaders(
                  Location(Uri.unsafeFromString(req.params.get("state").getOrElse(""))),
                  `Set-Cookie`(ResponseCookie(name = "scarab-token", content = responseBody.access_token, path = Some("/")))))
            }
            case res =>
              // Handle unexpected status
              BadRequest(s"Unexpected status from proxy: ${res.status}")
          }
        }
    }
  }

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

  val generateValidToken: (String, List[String]) => IO[String] = (userId, permissions) => {
    getSecretKey.map(secret => JwtCirce.encode(JwtClaim(content = Claims(userId, permissions).asJson.noSpaces, issuedAt = Some(Instant.now.getEpochSecond), expiration = Some(Instant.now.plusSeconds(157784760).getEpochSecond)), secret, jwtAlgorithm)
    )
  }

  val generateExpiredToken: (String, List[String]) => IO[String] = (userId, permissions) => {
    getSecretKey.map(secret => JwtCirce.encode(JwtClaim(content = Claims(userId, permissions).asJson.noSpaces, issuedAt = Some(Instant.EPOCH.getEpochSecond), expiration = Some(Instant.EPOCH.plusSeconds(157784760).getEpochSecond)), secret, jwtAlgorithm)
    )
  }

  val generateInvalidToken: (String, List[String]) => String = (userId, permissions) => {
    JwtCirce.encode(JwtClaim(content = Claims(userId, permissions).asJson.noSpaces, issuedAt = Some(Instant.now.getEpochSecond), expiration = Some(Instant.now.plusSeconds(157784760).getEpochSecond)), invalidKey, jwtAlgorithm)
  }

  val authUserEither: Kleisli[IO, Request[IO], Either[ErrorJson, Claims]] =
    Kleisli(req => {
      val either: Either[ErrorJson, String] = req.cookies.find(_.name == "scarab-token") match {
        case Some(x) => Right(new String(java.util.Base64.getDecoder.decode(x.content.split("\\.").apply(1))))
        case None => Left(ErrorJson(401, "no auth token found"))
      }
      IO(
        either.map(token => {
          val json = parse(token).getOrElse(Json.Null)
          val id = json.hcursor.downField("sub").as[String]
          val roles = json.hcursor.downField("resource_access").downField("scarab").downField("roles").as[Vector[String]]
          Claims(id.fold(df => "", s => s), roles.fold(df => List.empty, v => v.toList))
        }
        )
      )
    })

  
  
  val onFailure: AuthedRoutes[ErrorJson, IO] =
    Kleisli(req => OptionT.liftF(Forbidden(req.context).map(_.withStatus(Status.Found)).map(_.withHeaders(Location(Uri.unsafeFromString("http://localhost:5052/realms/scarab/protocol/openid-connect/auth?response_type=code&client_id=scarab&redirect_uri=http://localhost:8642/auth/callback&scope=openid&state=" + req.req.uri.path.toString() ))))))

  val middleware: AuthMiddleware[IO, Claims] =
    AuthMiddleware(authUserEither, onFailure)
}
