package io.youi.stream.delta

import io.youi.stream.{HTMLStream, Selector, Tag}

class InsertFirstChild(val selector: Selector, val content: () => String) extends Delta {
  override def apply(streamer: HTMLStream, tag: Tag.Open): Unit = {
    streamer.insert(tag.end, content())
  }
}
