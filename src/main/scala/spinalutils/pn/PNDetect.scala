package spinalutils.pn

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._

/**
 * Detects a PN (Pseudo-Noise) sequence within incoming data using a specified polynomial.
 *
 * @param order   The order of the PN sequence, indicating the number of shift registers utilized.
 * @param poly    The generator polynomial used to detect the PN sequence.
 * @param inWidth The width of the input data in bits, specifying how many bits are processed at a time.
 */
class PNDetect(
    order: Int,
    poly: BigInt,
    inWidth: Int
) extends Component {
  val io = new Bundle {
    val flush = in(Bool())
    val s_stream = slave(Stream(Bits(inWidth bits)))
    val errcnt = out(UInt(64 bits))
  }

  require(order <= 31 && order > 0, "The supported range of order for pn is (0, 31].")
  require(poly.bitLength == order + 1)

  def calculateNeedCycles: Int = {
    return Math.ceil((order + inWidth).toDouble / inWidth.toDouble).toInt
  }

  def revertBit(data: BigInt, width: Int): BigInt = {
    val tmp = Array.fill(width)(false)
    for (i <- 0 until width)
      tmp(i) = ((data >> i) & 1) == 1

    val reversedData = tmp.zipWithIndex
      .map { case (bit, index) =>
        if (bit) BigInt(1) << (width - 1 - index) else BigInt(0)
      }
      .reduce(_ | _)

    reversedData
  }

  val checkMap = Vec(Bits(order + inWidth bits), inWidth)
  checkMap.zipWithIndex.map({ case (check, index) =>
    check := revertBit(poly << index, order + inWidth)
  })

  val shiftData = Reg(Bits(order + inWidth bits)).init(0)
  val errcnt = Reg(UInt(64 bits)).init(0)
  io.errcnt := errcnt

  val fsm = new StateMachine {
    val initCnt = Counter(start = 0, end = calculateNeedCycles)
    val stateInit: State = new State with EntryPoint {
      whenIsActive {
        when(io.s_stream.valid) {
          shiftData := shiftData |<< inWidth
          shiftData(inWidth - 1 downto 0) := io.s_stream.payload
          initCnt.increment()
          when(initCnt.willOverflowIfInc) {
            goto(stateCheck)
          }
        }
      }
    }
    val stateCheck: State = new State {
      whenIsActive {
        when(io.s_stream.valid) {
          shiftData := shiftData |<< inWidth
          shiftData(inWidth - 1 downto 0) := io.s_stream.payload

          val cnt = checkMap.zipWithIndex
            .map({
              case (polyBits, index) => {
                val cb = (polyBits & shiftData)
                cb.xorR.asUInt.resize(1 + log2Up(order + inWidth))
              }
            })
            .reduceBalancedTree(_ + _)
          errcnt := errcnt + cnt
        }

        when(io.flush) {
          errcnt := 0
          shiftData := 0
          goto(stateInit)
        }
      }
    }
  }
  io.s_stream.ready := True
}

object PNDetect {

  /**
   * Factory method to create an instance of PNDetect with specified parameters.
   *
   * @param order   The order of the PN sequence to be detected.
   * @param poly    The generator polynomial for PN sequence detection.
   * @param inWidth The input data width in bits to be processed.
   * @return        A new instance of PNDetect configured with the provided parameters.
   */
  def apply(order: Int, poly: BigInt, inWidth: Int): PNDetect =
    new PNDetect(order = order, poly = poly, inWidth = inWidth)
}
