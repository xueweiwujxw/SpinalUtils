package spinalutils.pn

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._

import spinal.core.sim._
import spinal.sim._
import java.nio.file.Paths
import java.io._
import scala.util.Random
import scala.collection.mutable.ArrayBuffer

/**
 * Generates a PN (Pseudo-Noise) sequence using a given polynomial.
 *
 * @param order    The order of the PN generator, indicating the number of shift registers.
 * @param init     The initial value for the PN sequence, setting the initial state of the registers.
 * @param poly     The generator polynomial used to produce the PN sequence.
 * @param outWidth The width of the output data, specifying the number of bits in each output word.
 */
class PNGen(
    order: Int,
    init: BigInt,
    poly: BigInt,
    outWidth: Int
) extends Component {
  val io = new Bundle {

    /** output data enable */
    val en = in(Bool())

    /** flush pn gen */
    val flush = in(Bool())

    /** PN Stream */
    val m_stream = master(Stream(Bits(outWidth bits)))
  }

  require(order <= 31 && order > 0, "The supported range of order for pn is (0, 31].")
  require(init.bitLength <= order)
  require(poly.bitLength == order + 1)

  def calculateXORSum(bigInt: BigInt): BigInt = {
    var xorSum: BigInt = 0
    var value = bigInt

    while (value > 0) {
      if (value.testBit(0)) {
        xorSum = xorSum ^ 1
      }
      value = value >> 1
    }

    xorSum
  }

  def getMiddleInitVal(): Array[BigInt] = {
    val retInit = Array.ofDim[Boolean](outWidth, order)
    for (i <- 0 until order)
      retInit(i % outWidth)(i / outWidth) = ((init >> i) & 1) == 1

    var initMiddle = init
    val polyFixedIn = poly & (BigInt(1 << order) - 1)

    for (i <- 0 until order) {
      for (j <- 0 until outWidth) {
        if (i * outWidth + j >= order) {
          val sumIn = calculateXORSum(initMiddle & polyFixedIn)
          initMiddle = (sumIn << (order - 1)) | (initMiddle >> 1)
          retInit(j)(i) = sumIn == 1
        }
      }
    }

    val ret = Array.ofDim[BigInt](outWidth)
    for (i <- 0 until outWidth) {
      ret(i) = 0
      for (j <- 0 until order)
        ret(i) |= (BigInt(retInit(i)(j).toInt) << j)
    }

    ret
  }

  val initMiddleVec = getMiddleInitVal()

  val pndat = Reg(Bits(outWidth bits)).init(0)
  val pnvalid = Reg(Bool()).init(False)

  io.m_stream.payload := pndat.reversed
  io.m_stream.valid := pnvalid

  val middle = Vec(Reg(Bits(order bits)).init(0), outWidth)

  val polyFixed = B(poly & (BigInt(1 << order) - 1), order bits)

  val initfsm = new StateMachine {
    val stateInit: State = new State with EntryPoint {
      whenIsActive {
        pnvalid := False
        middle.zipWithIndex.foreach({ case ((v, i)) =>
          v := initMiddleVec(i)
        })
        when(io.en && ~io.flush) {
          goto(stateFirstVal)
        }
      }
    }

    val stateFirstVal: State = new State {
      whenIsActive {
        pndat.asBools.zipWithIndex.map(x => {
          val sum = (middle(x._2) & B(polyFixed, order bits)).xorR.asBits
          middle(x._2) := sum ## (((middle(x._2) |>> 1).resize(order - 1)))
          x._1 := sum.asBool
        })
        pnvalid := True
        when(io.flush) {
          pnvalid := False
          goto(stateInit)
        } otherwise {
          goto(stateRun)
        }
      }
    }

    val stateRun: State = new State {
      whenIsActive {
        when(io.m_stream.ready) {
          pndat.asBools.zipWithIndex.map(x => {
            val sum = (middle(x._2) & B(polyFixed, order bits)).xorR.asBits
            middle(x._2) := sum ## (((middle(x._2) |>> 1).resize(order - 1)))
            x._1 := sum.asBool
          })
        }
        pnvalid := True
        when(io.flush) {
          pnvalid := False
          goto(stateInit)
        }
      }
    }
  }

}

object PNGen {

  /**
   * Factory method to create an instance of PNGen with specified parameters.
   *
   * @param order    The order of the PN generator.
   * @param init     The initial value for the PN sequence.
   * @param poly     The generator polynomial for PN sequence generation.
   * @param outWidth The output data width in bits.
   * @return         A new instance of PNGen configured with the provided parameters.
   */
  def apply(order: Int, init: BigInt, poly: BigInt, outWidth: Int): PNGen =
    new PNGen(order = order, init = init, poly = poly, outWidth = outWidth)
}

object PNGenExample extends App {
  SpinalConfig().generateVerilog(
    new PNGen(
      order = 23,
      init = 1,
      poly = 0x800021,
      outWidth = 32
    )
  )
}

object PNGenSim extends App {
  SimConfig.withIVerilog.doSim(
    new PNGen(
      order = 23,
      init = 1,
      poly = 0x800021,
      outWidth = 32
    )
  ) { dut =>
    val pnfile = new File("pntest23.dat")
    val outputStream = new DataOutputStream(new FileOutputStream(pnfile))
    var correct = new Array[Byte](dut.io.m_stream.payload.getBitsWidth / 8)
    val random = new Random
    val threshold = 0.8
    dut.clockDomain.forkStimulus(10)

    dut.io.en #= false
    dut.io.flush #= false
    dut.io.m_stream.ready #= false

    dut.clockDomain.waitSampling(20)

    dut.io.en #= true

    val ready_for = fork {
      for (i <- 0 until 5000000) {
        dut.clockDomain.waitSampling()
        val randomValue = random.nextDouble()
        if (randomValue <= threshold)
          dut.io.m_stream.ready #= true
        else
          dut.io.m_stream.ready #= false
      }
    }

    for (i <- 0 until 5000000) {
      dut.clockDomain.waitSampling()
      if (dut.io.m_stream.valid.toBoolean && dut.io.m_stream.ready.toBoolean) {
        var bytes = dut.io.m_stream.payload.toBigInt.toByteArray

        if (bytes.size > correct.size) {
          for (i <- 1 until correct.size + 1)
            correct(i - 1) = bytes(i)
        } else if (bytes.size == correct.size) {
          for (i <- 0 until correct.size)
            correct(i) = bytes(i)
        } else {
          for (i <- (correct.size - bytes.size) until correct.size)
            correct(i) = bytes(i - (correct.size - bytes.size))
          for (i <- 0 until (correct.size - bytes.size))
            correct(i) = 0
        }
        outputStream.write(correct)
      }
    }

    dut.io.flush #= true

    dut.clockDomain.waitSampling(20)

    dut.io.flush #= false
    dut.io.en #= false

    dut.clockDomain.waitSampling(20)
    ready_for.join()
    outputStream.close()
  }
}
