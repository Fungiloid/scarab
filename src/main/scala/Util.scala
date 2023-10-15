import java.time.{Instant, OffsetDateTime}
import com.github.t3hnar.bcrypt._

import scala.util.{Random, Try}
object Util {
  def timestamp: Long = Instant.now().toEpochMilli
  def offsetDateTimeNow: OffsetDateTime = OffsetDateTime.now()

  def randomString: Int => String = number =>
    Random.alphanumeric.take(number).mkString
  def hashStrong: String => Try[String] = input =>
    input.bcryptSafeBounded
}
