import java.time.{Instant, OffsetDateTime}

object Util {
  def timestamp: Long = Instant.now().toEpochMilli
  def offsetDateTimeNow: OffsetDateTime = OffsetDateTime.now()
}
