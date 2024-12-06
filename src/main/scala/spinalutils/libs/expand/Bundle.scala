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

case class DemodRFConfData(pad: Boolean = false) extends IPadSingleDir[DemodRFConfData] {

  /** 射频接收载波频率 */
  val freq_rf_rx = Bits(16 bits)

  /** 使能，判断上升沿 */
  val en = Bits(1 bits)

  /** 补位 */
  val __pad_0__ = pad generate B(0, 15 bits)
}

object IPadSingleDirDataTest extends App {
  case class IPadSingleDirData(pad: Boolean = false) extends IPadSingleDir[IPadSingleDirData] {
    val data = Bits(32 bits)
    val __pad_0__ = pad generate B(0, 1 bits)
  }
  case class IPadSingleDirComponent() extends Component {
    val io = new Bundle {
      val input = slave(IPadSingleDirData())
      val output = master(IPadSingleDirData())
    }

    val reg = RegInit(IPadSingleDirData(true).getZero)
    io.input >> reg
    io.output << reg
  }

  SpinalConfig().generateVerilog(IPadSingleDirComponent())
}
