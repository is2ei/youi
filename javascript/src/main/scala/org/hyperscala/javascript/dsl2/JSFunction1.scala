package org.hyperscala.javascript.dsl2

/**
 * @author Matt Hicks <matt@outr.com>
 */
abstract class JSFunction1[P1, P2](name: String) extends JavaScriptContext {
  val p1 = ExistingStatement[P1]("p1")
  val p2 = ExistingStatement[P1]("p2")

  override protected def before(b: StringBuilder) = {
    b.append(s"function $name(p1, p2) {\r\n")
  }

  override protected def after(b: StringBuilder) = {
    b.append("}")
  }

  override def variable(v: Any) = if (v == p1) {
    Some("p1")
  } else if (v == p2) {
    Some("p2")
  } else {
    super.variable(v)
  }
}
