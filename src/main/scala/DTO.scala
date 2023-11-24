import java.time.OffsetDateTime

case class ResponseMeta(created: OffsetDateTime, creator:String)
case class ResponsePagination(currentPage:Long, pageSize:Long, totalItems:Long, totalPages:Long)
case class ResponseLinks(next:String, prev:String, first:String, last:String)
case class ResponseFilters(search:String, sort:String)
case class ResponsePaginated[T](meta:ResponseMeta, pagination: ResponsePagination, items:List[T], links: ResponseLinks, filters: ResponseFilters)