package org.hyperscala.examples.basic

import org.hyperscala.examples.Example
import org.hyperscala.html._
import org.hyperscala.html.tag.P
import org.hyperscala.realtime.{Realtime, RealtimeEvent}
import org.hyperscala.web.Webpage

import scala.language.reflectiveCalls

/**
 * @author Matt Hicks <mhicks@outr.com>
 */
class RealtimeExample extends Webpage with Example {
  require(Realtime)

  val message = new P
  body.contents += message
  message.contents += new tag.Span(content = "Click the button below.")

  body.contents += new tag.Button(id = "button", content = "Click Me!") {
    clickEvent := RealtimeEvent()

    clickEvent.on {
      case evt => {
        message.contents.replaceWith(new tag.Span(content = f"Button last clicked: ${System.currentTimeMillis()}%tr"))
      }
    }
  }
}
