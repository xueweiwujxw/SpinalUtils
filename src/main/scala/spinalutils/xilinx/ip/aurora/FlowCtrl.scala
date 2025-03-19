package spinalutils.xilinx.ip.aurora

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba4.axis._
import spinal.lib.fsm._

object GTLoopBack extends Enumeration {
  val NORMAL = Value(0x0)
  val NearEnd_PCS = Value(0x1)
  val NearEnd_PMA = Value(0x2)
  val FarEnd_PMA = Value(0x4)
  val FarEnd_PCS = Value(0x6)
}

object Consts {
  val AURORA_NFC_MAX_DELAY = 64
}

object AuroraFlowCtrlImmediateNFC {
  def apply(
      axisAuroraRx: Flow[Axi4Stream.Axi4StreamBundle],
      axisRX: Stream[Axi4Stream.Axi4StreamBundle],
      axisNFCTx: Stream[Bits],
      fifoDepth: Int
  ): (UInt, UInt, StateMachine) = {
    require(
      fifoDepth >= Consts.AURORA_NFC_MAX_DELAY * 2,
      f"buffer fifo depth ${fifoDepth} need to be greater than ${Consts.AURORA_NFC_MAX_DELAY * 2}"
    )
    val fcArea = new Area {
      val rxFifo = StreamFifo(Axi4Stream.Axi4StreamBundle(axisRX.config), fifoDepth)
      rxFifo.io.push.valid := axisAuroraRx.valid
      rxFifo.io.push.payload := axisAuroraRx.payload
      axisRX << rxFifo.io.pop

      axisNFCTx << axisNFCTx.getZero

      val fcFsm = new StateMachine {
        val Idle: State = new State with EntryPoint {
          whenIsActive {
            when(!axisRX.ready) {
              axisNFCTx.valid := True
              axisNFCTx.payload := 0xf
              goto(XferPause)
            }
          }
        }
        val XferPause: State = new State {
          whenIsActive {
            axisNFCTx.valid := True
            axisNFCTx.payload := 0xf
            when(axisNFCTx.ready) {
              goto(WaitReady)
            }
          }
        }
        val WaitReady: State = new State {
          whenIsActive {
            when(axisRX.ready && rxFifo.io.availability >= Consts.AURORA_NFC_MAX_DELAY) {
              axisNFCTx.valid := True
              axisNFCTx.payload := 0
              goto(XferContinue)
            }
          }
        }
        val XferContinue: State = new State {
          whenIsActive {
            axisNFCTx.valid := True
            axisNFCTx.payload := 0
            when(axisNFCTx.ready) {
              goto(Idle)
            }
          }
        }
      }
    }
    fcArea.setName("fcArea")
    (fcArea.rxFifo.io.availability, fcArea.rxFifo.io.occupancy, fcArea.fcFsm)
  }
}
