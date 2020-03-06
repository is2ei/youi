package io.youi.material

import io.youi.component.{Component, Container}
import io.youi.{Unique, dom}
import io.youi.dom._
import io.youi.net._
import org.scalajs.dom.html
import reactify.Var

import scala.concurrent.Future
import scribe.Execution.global

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

object MaterialComponents {
  lazy val loaded: Future[Unit] = {
    dom.addCSS(url"https://unpkg.com/material-components-web@v4.0.0/dist/material-components-web.min.css")
    dom.addScript(url"https://unpkg.com/material-components-web@v4.0.0/dist/material-components-web.min.js").flatMap { _ =>
      Material.load().map(_ => ())
    }
  }
}

class MDCButton extends Component(dom.create.button) {
  classes := Set("mdc-button")

  val label: Var[String] = Var("")

  private object elements {
    val ripple: html.Div = {
      val div = dom.create.div
      div.addClasses("mdc-button__ripple")
      div
    }
    val label: html.Span = {
      val span = dom.create.span
      span.addClasses("mdc-button__label")
      MDCButton.this.label.attach(span.innerHTML_=)
      span
    }
  }

  element.appendChild(elements.ripple)
  element.appendChild(elements.label)

  private val adapter: Future[MDCButtonImplementation] = MaterialComponents.loaded.map(_ => MDCButton.attachTo(element))
}

class MDCTextField extends Component(dom.create.div) {
  classes := Set("mdc-text-field")

  val label: Var[String] = Var("")
  val value: Var[String] = Var("")

  private object elements {
    val input: html.Input = {
      val input = dom.create.input
      input.id = Unique()
      input.addClasses("mdc-text-field__input")
      MDCTextField.this.value.attach(input.value_=)
      // TODO: Support change events
      input
    }
    val lineRipple: html.Div = {
      val div = dom.create.div
      div.addClasses("mdc-line-ripple")
      div
    }
    val label: html.Label = {
      val label = dom.create.label
      label.addClasses("mdc-floating-label")
      label.htmlFor = input.id
      MDCTextField.this.label.attach(label.innerHTML_=)
      label
    }
  }

  element.appendChild(elements.input)
  element.appendChild(elements.lineRipple)
  element.appendChild(elements.label)

  private val adapter: Future[MDCTextFieldImplementation] = MaterialComponents.loaded.map(_ => MDCTextField.attachTo(element))

  def shakeLabel(): Unit = adapter.foreach(_.getLabelAdapterMethods_().shakeLabel(true))
}

@js.native
@JSGlobal("mdc.ripple.MDCRipple")
object MDCButton extends js.Object {
  def attachTo(element: html.Element): MDCButtonImplementation = js.native
}

@js.native
@JSGlobal("mdc.textField.MDCTextField")
object MDCTextField extends js.Object {
  def attachTo(element: html.Element): MDCTextFieldImplementation = js.native
}

@js.native
trait MDCButtonImplementation extends js.Object {
}

@js.native
trait MDCTextFieldImplementation extends js.Object {
  def getLabelAdapterMethods_(): MDCLabelAdapter
}

@js.native
trait MDCLabelAdapter extends js.Object {
  def shakeLabel(shouldShake: Boolean): Unit
}