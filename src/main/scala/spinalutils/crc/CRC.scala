package spinalutils.crc

import spinal.core._
import spinal.lib._

import spinal.sim._
import spinal.core.sim._
import scala.collection.mutable.ArrayBuffer

/**
 * CRC configuration class for setting parameters of the CRC (Cyclic Redundancy Check) algorithm.
 *
 * @param CrcWidth  The width of the CRC output value, i.e., the number of bits in the checksum.
 * @param DataWidth The width of the input data, i.e., the number of bits considered per input for the calculation.
 * @param RefIN     Whether to reverse the input value. If true, the input bits are reversed before CRC calculation.
 * @param RefOut    Whether to reverse the output value. If true, the output CRC value is reversed before final output.
 * @param InitValue The initial value for the CRC calculation, often used to set the starting state.
 * @param XorValue  The final XOR value for the CRC calculation. The output value is XORed with this value after computation.
 * @param Poly      The polynomial used as the generator polynomial for the CRC calculation.
 */
case class CRCConfig(
    CrcWidth: Int,
    DataWidth: Int,
    RefIN: Boolean = false,
    RefOut: Boolean = false,
    InitValue: BigInt,
    XorValue: BigInt = 0,
    Poly: BigInt
)

/**
 * Enumeration representing the command mode for CRC operations.
 * It defines two modes: INIT and UPDATE.
 */
object CRCCmdMode extends SpinalEnum {
  // INIT mode is used to initialize or reset the CRC calculation.
  // UPDATE mode is used to continue the CRC calculation with new data.
  val INIT, UPDATE = newElement()
}

/**
 * Command bundle for CRC operations, containing the mode and data.
 *
 * @param config The CRC configuration object providing details such as data width.
 */
case class CRCCmd(config: CRCConfig) extends Bundle {
  val mode = CRCCmdMode()
  val data = Bits(config.DataWidth bits)
}

class CRC(config: CRCConfig) extends Component {
  val io = new Bundle {
    val cmd = slave Flow (CRCCmd(config))
    val crcOut = out(Bits(config.CrcWidth bits))
  }

  var lfsrMatrix: Array[Array[Int]] =
    Array.ofDim(config.CrcWidth, config.CrcWidth + config.DataWidth)
  var polyArray: Array[Int] = Array.ofDim(config.CrcWidth + 1)

  private[this] def lfsrSerialShiftCrc(
      lfsrCur: Array[Int],
      lfsrNext: Array[Int],
      dataCur: Array[Int]
  ): Unit = {
    for (i <- 0 until config.CrcWidth)(lfsrNext(i) = lfsrCur(i))
    for (i <- 0 until config.DataWidth) {
      var lfsrUpperBit: Int = lfsrNext(config.CrcWidth - 1)
      for (j <- (1 until config.CrcWidth).reverse) {
        if (this.polyArray(j) == 1)(lfsrNext(j) = lfsrNext(j - 1) ^ lfsrUpperBit ^ dataCur(i))
        else (lfsrNext(j) = lfsrNext(j - 1))
      }
      lfsrNext(0) = lfsrUpperBit ^ dataCur(i)
    }
  }

  private[this] def buildCrcMatrix(): Unit = {
    var lfsrCur: Array[Int] = Array.ofDim(config.CrcWidth)
    var lfsrNext: Array[Int] = Array.ofDim(config.CrcWidth)
    var dataCur: Array[Int] = Array.ofDim(config.DataWidth)
    for (i <- 0 until lfsrCur.length)(lfsrCur(i) = 0)
    for (i <- 0 until dataCur.length)(dataCur(i) = 0)

    for (i <- 0 until config.CrcWidth) {
      lfsrCur(i) = 1
      if (i > 0)(lfsrCur(i - 1) = 0)
      this.lfsrSerialShiftCrc(lfsrCur, lfsrNext, dataCur)
      for (j <- 0 until config.CrcWidth) {
        if (lfsrNext(j) == 1) {
          lfsrMatrix(j)(i) = 1
        }
      }
    }

    for (i <- 0 until lfsrCur.length)(lfsrCur(i) = 0)
    for (i <- 0 until dataCur.length)(dataCur(i) = 0)

    for (i <- 0 until config.DataWidth) {
      dataCur(i) = 1
      if (i > 0)(dataCur(i - 1) = 0)
      this.lfsrSerialShiftCrc(lfsrCur, lfsrNext, dataCur)
      for (j <- 0 until config.CrcWidth) {
        if (lfsrNext(j) == 1) {
          lfsrMatrix(j)(config.DataWidth - i - 1 + config.CrcWidth) = 1
        }
      }
    }
  }

  private[this] def getMatrix(): Unit = {
    this.polyArray = this.getPolyArray().clone()
    this.buildCrcMatrix()
  }

  private[this] def getPolyArray(): Array[Int] = {
    var ret: ArrayBuffer[Int] = new ArrayBuffer()
    for (i <- 0 until config.CrcWidth) {
      ret.append((config.Poly >> i & 0x1).toInt)
    }
    ret.toArray
  }

  this.getMatrix()

  val dat = Bits(config.DataWidth bits)
  dat.assignFromBits(
    if (config.RefIN) EndiannessSwap(io.cmd.data, BitCount(1)) else io.cmd.data
  )
  val lfsr_q = Reg(Bits(config.CrcWidth bits)).init(config.InitValue)
  val lfsr_c = Bits(config.CrcWidth bits)
  io.crcOut := (if (config.RefOut) EndiannessSwap(lfsr_q, BitCount(1))
                else (lfsr_q)) ^ config.XorValue
  lfsrMatrix.zipWithIndex.foreach { case (x, index) =>
    lfsr_c(index) := x.zipWithIndex
      .filter(_._1 == 1)
      .map { case (item, idx) =>
        if (idx < config.CrcWidth) lfsr_q(idx)
        else dat(idx - config.CrcWidth)
      }
      .reduce(_ ^ _)
  }
  when(io.cmd.mode === CRCCmdMode.UPDATE && io.cmd.valid)(
    lfsr_q := lfsr_c
  ) elsewhen (io.cmd.mode === CRCCmdMode.INIT && io.cmd.valid) (
    lfsr_q := B(config.InitValue, config.CrcWidth bits)
  )
}

object CRC {

  /**
   * Factory method to create an instance of CRC with the provided configuration.
   *
   * @param config The CRC configuration object that specifies parameters such as data width,
   *               polynomial, initial value, and any other necessary settings for the CRC calculation.
   * @return       A new instance of the CRC class configured according to the provided settings.
   */
  def apply(config: CRCConfig): CRC =
    new CRC(config)
}
