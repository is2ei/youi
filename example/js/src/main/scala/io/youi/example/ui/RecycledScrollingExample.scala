package io.youi.example.ui

import io.youi.{Color, Key}
import io.youi.component.{Component, Container, HTMLTextInput, HTMLTextView}
import io.youi.example.screen.UIExampleScreen
import io.youi.layout.VerticalLayout
import io.youi.net._
import io.youi.style.{Display, HTMLBorder, HTMLBorderStyle, Overflow, Position}
import reactify._

import scala.concurrent.Future
import scribe.Execution.global

class RecycledScrollingExample extends UIExampleScreen {
  override def title: String = "Recycled Scrolling Example"
  override def path: Path = path"/examples/recycled-scrolling.html"

  override def createUI(): Future[Unit] = {
    val scroller = new RecycledScroller[Int, NumberComponent](8, NumberComponentRenderer)
    scroller.position.center := container.size.center
    scroller.position.middle := container.size.middle
    scroller.size.width := 1000.0
    scroller.size.height := 500.0
    scroller.background := Color.LightGray
    scroller.batch.data := BatchedData((0 to 300).toList)
    container.children += scroller

    val textTotal = new HTMLTextView {
      position.right := scroller.position.right
      position.bottom := scroller.position.top
      font.size := 24.0
      value := s"of ${scroller.batch.total()}"
    }
    val inputPosition = new HTMLTextInput {
      position.right := textTotal.position.left - 10.0
      position.bottom := scroller.position.top - 5.0
      size.width := 50.0
      size.height := 25.0
      scroller.batch.position.attachAndFire { p =>
        value := p.toString
      }

      event.key.up.attach { evt =>
        if (evt.key == Key.Enter) {
          val text = value().filter(_.isDigit)
          if (text.nonEmpty) {
            val n = text.toInt
            scroller.batch.position := n
          }
        }
      }
    }
    container.children += inputPosition
    container.children ++= List(scroller, textTotal, inputPosition)

    Future.successful(())
  }

  class NumberComponent extends Container {
    val value: Var[Int] = Var(0)

    position.`type` := Position.Absolute
    size.width := 1000.0
    size.height := 50.0
    htmlBorder.radius := 5.0
    htmlBorder := HTMLBorder(1.0, HTMLBorderStyle.Dashed, Color.DarkRed)

    val label = new HTMLTextView
    label.position.left := 20.0
    label.position.middle := size.middle
    label.value := s"Number: ${value()} with lots of extra text"
    label.font.size := 42.0
    children += label
  }

  object NumberComponentRenderer extends RecycledRenderer[Int, NumberComponent] {
    override def createComponent: NumberComponent = new NumberComponent

    override def setData(data: Int, component: NumberComponent): Unit = component.value := data

    override def getData(component: NumberComponent): Int = component.value()

    override def loading(component: NumberComponent): Unit = component.value := -1
  }
}

class RecycledScroller[T, C <: Component](perPage: Int, renderer: RecycledRenderer[T, C]) extends Container { scroller =>
  object batch {
    val data: Var[BatchedData[T]] = Var(BatchedData.empty[T])
    val position: Var[Int] = Var(0)
    val value: Var[Option[T]] = Var(None)
    val total: Val[Int] = Val(data().total)
  }

  overflow := Overflow.Hidden

  private val pane1 = new RecycledPane
  private val pane2 = new RecycledPane
  private val pane3 = new RecycledPane

  private var middle = pane1
  private var top = pane2
  private var bottom = pane3

  // TODO: create "scroll" in gestures
  event.pointer.wheel.attach { evt =>
    val delta = if (evt.delta.y > middle.size.height()) {     // TODO: support large jumps
      middle.size.height()
    } else if (evt.delta.y < -middle.size.height()) {         // TODO: support large jumps
      -middle.size.height()
    } else {
      evt.delta.y
    }
    if (middle.isPartial) {
      // Don't scroll at all
    } else if (middle.start == 0 && middle.position.bottom() - delta < size.height()) {
      middle.position.bottom.static(size.height())
    } else if (top.isPartial && top.position.bottom() - delta > top.renderedHeight) {
      middle.position.y.static(top.renderedHeight)
    } else {
      middle.position.y.static(middle.position.y() - delta)
      if (delta < 0) { // Scrolling up
        if (!top.isPartial && top.position.middle() >= 0.0) { // Make top the new middle
          val pt = top
          val pm = middle
          val pb = bottom
          top = pb
          middle = pt
          bottom = pm

          top.page(middle.end)

          middle.position.y.static(middle.position.y())
          top.position.bottom := middle.position.top
          bottom.position.top := middle.position.bottom
        }
      } else { // Scrolling down
        if (bottom.position.middle() <= size.height()) { // Make bottom the new middle
          val pt = top
          val pm = middle
          val pb = bottom
          top = pm
          middle = pb
          bottom = pt

          bottom.page(middle.start - perPage)

          middle.position.y.static(middle.position.y())
          top.position.bottom := middle.position.top
          bottom.position.top := middle.position.bottom
        }
      }
    }
    updatePosition()
  }

  batch.data.on {
    middle.page(0)
    top.page(perPage)
  }

  pane1.position.bottom := size.height
  pane2.position.bottom := pane1.position.top
  pane3.position.top := pane1.position.bottom

  children ++= List(pane1, pane2, pane3)

  private var updatingPosition = false

  batch.position.attach { p =>
    if (!updatingPosition) {
      scribe.info(s"Should set position to: $p")
    }
  }

  private def updatePosition(): Unit = {
    bottom.lastItemVisible.orElse(middle.lastItemVisible) match {
      case Some((item, p)) => {
        updatingPosition = true
        try {
          batch.value := Some(item)
          batch.position := p
        } finally {
          updatingPosition = false
        }
      }
      case None => scribe.warn("Neither bottom or middle has a last item visible!")
    }
  }

  class RecycledPane extends Container {
    var start: Int = 0
    var end: Int = 0
    val components: Vector[C] = (0 until perPage).toVector.map(_ => renderer.createComponent)
    components.foreach(renderer.loading)

    layout := new VerticalLayout
    size.width := scroller.size.width
    size.height := components.last.position.bottom
    children ++= components

    def isPartial: Boolean = end - start < perPage
    def renderedHeight: Double = (0 until end - start).foldLeft(0.0)((height, index) => {
      height + components(components.length - (index + 1)).size.height()
    })
    def lastItemVisible: Option[(T, Int)] = if (end - start == 0 || scroller.size.height() - position.top() < 0.0) {
      None
    } else {
      val offset = scroller.size.height() - (position.top() + 10.0)
      components.reverse.zipWithIndex.collectFirst {
        case (c, index) if c.position.top() <= offset && c.position.bottom() >= offset => (renderer.getData(c), start + index)
      }
    }

    def page(start: Int): Unit = if (batch.data().nonEmpty) {
      // Set all to loading
      components.foreach(renderer.loading)
      // Asynchronously load data
      batch.data().get(start, components.length).map { data =>
        this.start = start
        this.end = start + data.length
        update(data)
      }
    } else {
      // No data, hide it all
      components.foreach(renderer.hide)
    }

    def update(data: Vector[T]): Unit = {
      components.zipWithIndex.foreach {
        case (c, index) => data.lift(perPage - (index + 1)) match {
          case Some(t) => {
            renderer.setData(t, c)
            renderer.show(c)
          }
          case None => renderer.hide(c)
        }
      }
    }
  }
}

trait RecycledRenderer[T, C <: Component] {
  def createComponent: C
  def setData(data: T, component: C): Unit
  def getData(component: C): T
  def loading(component: C): Unit
  def show(component: C): Unit = component.display := Display.Block
  def hide(component: C): Unit = component.display := Display.None
}

trait BatchedData[T] {
  def total: Int
  def isEmpty: Boolean = total == 0
  def nonEmpty: Boolean = total > 0
  def get(start: Int, end: Int): Future[Vector[T]]
}

object BatchedData {
  def empty[T]: BatchedData[T] = new BatchedData[T] {
    override def total: Int = 0

    override def get(start: Int, end: Int): Future[Vector[T]] = throw new RuntimeException("Cannot get data from empty batch")
  }

  def apply[T](data: Seq[T]): BatchedData[T] = new BatchedData[T] {
    override def total: Int = data.length

    override def get(start: Int, end: Int): Future[Vector[T]] = Future.successful(data.slice(start, start + end).toVector)
  }
}