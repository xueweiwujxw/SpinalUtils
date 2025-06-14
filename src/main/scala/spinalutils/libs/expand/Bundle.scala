package spinalutils.libs.expand

import spinal.core._
import spinal.lib._

trait IPadSingleDir[T <: IPadSingleDir[T]] extends Bundle with IMasterSlave with IConnectable[T] {
  val pad: Boolean

  def asMaster(): Unit = {
    require(!pad, "pad mode is invalid in master or slave interface")
    this.elements.foreach { case (name, ele) =>
      if (!name.matches("__pad_\\d+__"))
        out(ele)
    }
  }

  def connectFrom(that: T): T = {
    this.elements.foreach { case (name, ele) =>
      if (!name.matches("__pad_\\d+__"))
        that.elements.find(_._1 == name) match {
          case Some((_, that_ele)) => ele := that_ele
          case None                => println(s"Element $name not found in the source bundle")
        }
    }
    this.asInstanceOf[T]
  }

  def assignPad(): Unit = {
    this.elements.foreach { case (name, ele) =>
      if (name.matches("__pad_\\d+__"))
        ele := B(0)
    }
  }

  def bytes: Int = this.getBitsWidth / 8
  def bits: Int = this.getBitsWidth

  def busAlignedBytes(busDataWidth: Int): Int =
    scala.math.ceil(this.bits.toDouble / busDataWidth.toDouble).toInt * (busDataWidth / 8)
}
