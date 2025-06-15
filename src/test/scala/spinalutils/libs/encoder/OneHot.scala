package spinalutils.libs.encoder

import spinal.core._
import spinal.lib._

object OneHotExampleGen extends App {
  case class Bin2OneHotExample() extends Component {
    val io = new Bundle {
      val bin = in(Bits(3 bits))
      val oh = out(Bits(8 bits))
    }

    io.oh := OneHot.bin2OneHot(io.bin)
  }

  case class OneHot2BinExample() extends Component {
    val io = new Bundle {
      val oh = in(Bits(8 bits))
      val bin = out(Bits(3 bits))
    }

    io.bin := OneHot.oneHot2Bin(io.oh).asBits
  }

  case class OneHot2BinSecureExample() extends Component {
    val io = new Bundle {
      val oh = in(Bits(8 bits))
      val bin = out(Bits(3 bits))
      val valid = out(Bool())
    }

    val binSecure = OneHot.oneHot2BinSecure(io.oh)
    io.bin := binSecure._1.asBits
    io.valid := binSecure._2
  }

  SpinalVerilog(Bin2OneHotExample())

  SpinalVerilog(OneHot2BinExample())

  SpinalVerilog(OneHot2BinSecureExample())
}
