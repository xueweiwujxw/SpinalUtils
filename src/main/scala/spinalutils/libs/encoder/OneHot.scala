package spinalutils.libs.encoder

import spinal.core._
import spinal.lib._

object OneHot {
  def oneHot2Bin[T <: Data](onehot: T): UInt = {
    val ohWidth = onehot.getBitsWidth
    val binWidth = log2Up(ohWidth)
    val oh = onehot.asBits
    val bin = UInt(binWidth bits)

    switch(oh) {
      for (i <- 0 until ohWidth)
        is(B(i, ohWidth bits)) { bin := i }
      default { bin := 0 }

    }

    bin
  }

  def bin2OneHot[T <: Data](binary: T): Bits = {
    val binWidth = binary.getBitsWidth
    val ohWidth = 1 << binWidth
    val bin = binary.asBits.asUInt

    val oh = B(1, ohWidth bits) |<< bin

    oh
  }
}

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

  SpinalVerilog(Bin2OneHotExample())

  SpinalVerilog(OneHot2BinExample())
}

