package org.hyperscala.examples.ui

import org.hyperscala.html._
import org.hyperscala.event.{ChangeEvent, ClickEvent, JavaScriptEvent}
import org.hyperscala.web.site.{Website, Webpage}
import org.powerscala.property.StandardProperty
import annotation.tailrec
import org.hyperscala.jquery.jQuery
import org.hyperscala.realtime.Realtime
import org.hyperscala.ui.dynamic.{DynamicContent, DynamicString}

/**
 * @author Matt Hicks <mhicks@outr.com>
 */
class ChatExample extends Webpage {
  Webpage().require(Realtime)

  val nickname = new StandardProperty[String]

  body.style.fontFamily = "Helvetica, sans-serif"

  val chatMain = DynamicContent.url(ChatExample.Main, null)

  val chatName = chatMain.load[tag.Input]("chatName")
  val message = chatMain.load[tag.TextArea]("chatMessage")
  val submit = chatMain.load[tag.Button]("submit")
  val messages = chatMain.load[tag.Div]("messages")

  chatName.event.change := JavaScriptEvent()
  message.event.change := JavaScriptEvent()
  submit.event.click := JavaScriptEvent()

  chatName.listeners.synchronous {
    case evt: ChangeEvent => updateNickname()
  }
  submit.listeners.synchronous {
    case evt: ClickEvent => sendMessage()
  }

  body.contents += chatMain

  ChatExample.chatHistory.foreach {   // Load history
    case (nick, text) => messages.contents += new ChatEntry(nick, text)
  }
  updateNickname()

  def sendMessage() = {
    ChatExample.sendMessage(nickname(), message.value())
    message.value := ""
    jQuery.focus(message)
  }

  def updateNickname() = {
    val current = chatName.value() match {
      case "" => "guest"
      case v => v
    }
    if (current != nickname()) {
      nickname := ChatExample.generateNick(current)
      chatName.value := nickname()
    }
  }
}

object ChatExample {
  val Main = getClass.getClassLoader.getResource("chat.html")
  val Entry = getClass.getClassLoader.getResource("chat_entry.html")

  private var history = List.empty[(String, String)]

  def instances = Website().sessions.valuesByType[ChatExample].toList
  @tailrec
  def generateNick(nickname: String, increment: Int = 0): String = {
    val nick = increment match {
      case 0 => nickname
      case _ => "%s%s".format(nickname, increment)
    }
    if (instances.find(c => c.nickname() == nick).isEmpty) {
      nick
    } else {
      generateNick(nickname, increment + 1)
    }
  }
  def sendMessage(nickname: String, message: String) = synchronized {
    instances.foreach {
      case chat => chat.context {
        chat.messages.contents += new ChatEntry(nickname, message)
      }
    }
    history = (nickname, message) :: history
  }
  def chatHistory = history.reverse
}

class ChatEntry(name: String, message: String) extends DynamicContent(null) {
  def dynamicString = DynamicString.url("chat_entry.html", ChatExample.Entry)

  val chatName = load[tag.Div]("chatName", reId = true)
  val chatBody = load[tag.Div]("chatBody", reId = true)

  chatName.contents.replaceWith(name)
  chatBody.contents.replaceWith(message)
}