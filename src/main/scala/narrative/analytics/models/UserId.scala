package narrative.analytics.models

object UserId {
  opaque type UserId = String

  extension (uId: UserId) {
    def value: String = uId
  }

  def apply(str: String): UserId = str
}
