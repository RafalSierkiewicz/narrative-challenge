package narrative.analytics.models

enum Event(val value: String) {
  case CLICK extends Event("click")
  case IMPRESSION extends Event("impression")
}

object Event {
  def fromString(str: String): Either[String, Event] = str.toLowerCase match
    case CLICK.value      => Right(CLICK)
    case IMPRESSION.value => Right(IMPRESSION)
    case other            => Left(s"Wrong value for Event. Got $other available ${Event.values.mkString(",")}")
}
