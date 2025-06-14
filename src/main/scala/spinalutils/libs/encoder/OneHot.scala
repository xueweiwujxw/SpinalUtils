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
        is(B(1 << i, ohWidth bits)) { bin := i }
      default { bin := 0 }
    }

    bin
  }

  def oneHot2BinSecure[T <: Data](onehot: T): (UInt, Bool) = {
    val ohWidth = onehot.getBitsWidth
    val binWidth = log2Up(ohWidth)
    val oh = onehot.asBits
    val bin = UInt(binWidth bits)
    val valid = Bool()

    switch(oh) {
      for (i <- 0 until ohWidth)
        is(B(1 << i, ohWidth bits)) {
          bin := i
          valid := True
        }
      default {
        bin := 0
        valid := False
      }
    }

    (bin, valid)
  }

  def bin2OneHot[T <: Data](binary: T): Bits = {
    val binWidth = binary.getBitsWidth
    val ohWidth = 1 << binWidth
    val bin = binary.asBits.asUInt

    val oh = B(1, ohWidth bits) |<< bin

    oh
  }
}
