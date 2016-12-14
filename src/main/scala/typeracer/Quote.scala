package typeracer

import scala.io.Source
import scala.util.Random

object Quote {
  val quotes: Vector[String] = Source.fromResource("quotes.txt").getLines.toVector
}

trait Quoting {
  def getQuote(): String = {
    val randomIndex = Random.nextInt(Quote.quotes.length)
    Quote.quotes(randomIndex)
  }
}
