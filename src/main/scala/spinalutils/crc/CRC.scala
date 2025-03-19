package spinalutils.crc

import spinal.core._
import spinal.lib._

import spinal.sim._
import spinal.core.sim._
import scala.collection.mutable.ArrayBuffer

/**
 * CRC configuration class for setting parameters of the CRC (Cyclic Redundancy Check) algorithm.
 *
 * @param crcWidth  The width of the CRC output value, i.e., the number of bits in the checksum.
 * @param dataWidth The width of the input data, i.e., the number of bits considered per input for the calculation.
 * @param refIn     Whether to reverse the input value. If true, the input bits are reversed before CRC calculation.
 * @param refOut    Whether to reverse the output value. If true, the output CRC value is reversed before final output.
 * @param initValue The initial value for the CRC calculation, often used to set the starting state.
 * @param xorValue  The final XOR value for the CRC calculation. The output value is XORed with this value after computation.
 * @param poly      The polynomial used as the generator polynomial for the CRC calculation.
 */
case class CRCConfig(
    crcWidth: Int,
    dataWidth: Int,
    refIn: Boolean = false,
    refOut: Boolean = false,
    initValue: BigInt,
    xorValue: BigInt = 0,
    poly: BigInt
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
  val data = Bits(config.dataWidth bits)
}

class CRC(config: CRCConfig) extends Component {
  val io = new Bundle {
    val cmd = slave Flow (CRCCmd(config))
    val crcOut = out(Bits(config.crcWidth bits))
  }

  var lfsrMatrix: Array[Array[Int]] =
    Array.ofDim(config.crcWidth, config.crcWidth + config.dataWidth)
  var polyArray: Array[Int] = Array.ofDim(config.crcWidth + 1)

  private[this] def lfsrSerialShiftCrc(
      lfsrCur: Array[Int],
      lfsrNext: Array[Int],
      dataCur: Array[Int]
  ): Unit = {
    for (i <- 0 until config.crcWidth)(lfsrNext(i) = lfsrCur(i))
    for (i <- 0 until config.dataWidth) {
      var lfsrUpperBit: Int = lfsrNext(config.crcWidth - 1)
      for (j <- (1 until config.crcWidth).reverse) {
        if (this.polyArray(j) == 1)(lfsrNext(j) = lfsrNext(j - 1) ^ lfsrUpperBit ^ dataCur(i))
        else (lfsrNext(j) = lfsrNext(j - 1))
      }
      lfsrNext(0) = lfsrUpperBit ^ dataCur(i)
    }
  }

  private[this] def buildCrcMatrix(): Unit = {
    var lfsrCur: Array[Int] = Array.ofDim(config.crcWidth)
    var lfsrNext: Array[Int] = Array.ofDim(config.crcWidth)
    var dataCur: Array[Int] = Array.ofDim(config.dataWidth)
    for (i <- 0 until lfsrCur.length)(lfsrCur(i) = 0)
    for (i <- 0 until dataCur.length)(dataCur(i) = 0)

    for (i <- 0 until config.crcWidth) {
      lfsrCur(i) = 1
      if (i > 0)(lfsrCur(i - 1) = 0)
      this.lfsrSerialShiftCrc(lfsrCur, lfsrNext, dataCur)
      for (j <- 0 until config.crcWidth) {
        if (lfsrNext(j) == 1) {
          lfsrMatrix(j)(i) = 1
        }
      }
    }

    for (i <- 0 until lfsrCur.length)(lfsrCur(i) = 0)
    for (i <- 0 until dataCur.length)(dataCur(i) = 0)

    for (i <- 0 until config.dataWidth) {
      dataCur(i) = 1
      if (i > 0)(dataCur(i - 1) = 0)
      this.lfsrSerialShiftCrc(lfsrCur, lfsrNext, dataCur)
      for (j <- 0 until config.crcWidth) {
        if (lfsrNext(j) == 1) {
          lfsrMatrix(j)(config.dataWidth - i - 1 + config.crcWidth) = 1
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
    for (i <- 0 until config.crcWidth) {
      ret.append((config.poly >> i & 0x1).toInt)
    }
    ret.toArray
  }

  this.getMatrix()

  val dat = Bits(config.dataWidth bits)
  dat.assignFromBits(
    if (config.refIn) EndiannessSwap(io.cmd.data, BitCount(1)) else io.cmd.data
  )
  val lfsr_q = Reg(Bits(config.crcWidth bits)).init(config.initValue)
  val lfsr_c = Bits(config.crcWidth bits)
  io.crcOut := (if (config.refOut) EndiannessSwap(lfsr_q, BitCount(1))
                else (lfsr_q)) ^ config.xorValue
  lfsrMatrix.zipWithIndex.foreach { case (x, index) =>
    lfsr_c(index) := x.zipWithIndex
      .filter(_._1 == 1)
      .map { case (item, idx) =>
        if (idx < config.crcWidth) lfsr_q(idx)
        else dat(idx - config.crcWidth)
      }
      .reduce(_ ^ _)
  }
  when(io.cmd.mode === CRCCmdMode.UPDATE && io.cmd.valid)(
    lfsr_q := lfsr_c
  ) elsewhen (io.cmd.mode === CRCCmdMode.INIT && io.cmd.valid) (
    lfsr_q := B(config.initValue, config.crcWidth bits)
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
  def apply(config: CRCConfig): CRC = {
    val crc = new CRC(config)
    crc.io.cmd.valid := False
    crc.io.cmd.mode := CRCCmdMode.INIT
    crc.io.cmd.data := 0

    crc
  }

  implicit class CRCRich(crc: CRC) {
    def init() {
      crc.io.cmd.valid := True
      crc.io.cmd.mode := CRCCmdMode.INIT
    }

    def calculate[T <: Data](d: T) {
      require(d.getBitsWidth == crc.io.cmd.config.dataWidth)
      crc.io.cmd.valid := True
      crc.io.cmd.mode := CRCCmdMode.UPDATE
      crc.io.cmd.data := d.asBits
    }
  }
}
