package spinalutils.libs.axispi

import spinal.core._
import spinal.lib._
import spinal.lib.com.spi._
import spinal.lib.bus.misc.BusSlaveFactory

case class SpiMasterCtrlContinuous(generics: SpiMasterCtrlGenerics, continuous: Boolean = false) extends Component {

  import generics._

  val io = new Bundle {
    val config = in(SpiMasterCtrlConfig(generics))
    val cmd = slave Stream (SpiMasterCmd(generics))
    val rsp = master Flow (Bits(dataWidth bits))
    val spi = master(SpiMaster(ssWidth))

    /*
     * In short, it has one command fifo (write/read/chip-select commands) and one read fifo.
     * data -> 0x00 :
     *   rxData -> R[7:0]
     *   rxOccupancy -> R[30:16] rx fifo occupancy (include the rxData in the amount)
     *   rxValid -> R[31] Inform that the readed rxData is valid
     *   When you read this register it pop an byte of the rx fifo and provide its value (via rxData)
     *   When you write this register, it push a command into the fifo. There is the commands that you can use :
     *     0x000000xx =>  Send byte xx
     *     0x010000xx =>  Send byte xx and also push the read data into the FIFO
     *     0x1100000X =>  Enable the SS line X
     *     0x1000000X =>  Disable the SS line X
     *
     * status -> 0x04
     *   cmdIntEnable -> RW[0] Command fifo empty interrupt enable
     *   rspIntEnable -> RW[1] Read fifo not empty interrupt enable
     *   cmdInt -> RW[8] Command fifo empty interrupt pending
     *   rspInt -> RW[9] Read fifo not empty interrupt pending
     *   txAvailability -> R[31:16] Command fifo space availability (SS commands + send byte commands)
     *
     * config -> 0x08
     *   cpol -> W[0]
     *   cpha -> W[1]
     *   ssActiveHigh -> W[31..4] For each ss, the corresponding bit specify if that's a active high one.
     *
     * sclkToggle -> W 0x0C SPI frequency = FCLK / (2 * (sclkToggle + 1))
     * ssSetup -> W 0x10 time between chip select enable and the next byte
     * ssHold -> W 0x14 time between the last byte transmission and the chip select disable
     * ssDisable -> W 0x18 time between chip select disable and chip select enable
     * cmdPopCond -> W 0x1c cmd pop enable when withEn is set to true
     * ssSignal -> W 0x20 cmd pop enable when withEn is set to true
     */

    def driveFrom(bus: BusSlaveFactory, baseAddress: Int = 0)(
        generics: SpiMasterCtrlMemoryMappedConfig
    ) = new Area {
      import generics._
      require(cmdFifoDepth >= 1)
      require(rspFifoDepth >= 1)

      require(cmdFifoDepth < 32.KiB)
      require(rspFifoDepth < 32.KiB)

      val cmdPopCond = RegInit(False)

      // CMD
      val cmdLogic = new Area {
        val streamUnbuffered = Stream(SpiMasterCmd(ctrlGenerics))
        streamUnbuffered.valid := bus.isWriting(address = baseAddress + 0)
        val dataCmd = SpiMasterCtrlCmdData(ctrlGenerics)
        bus.nonStopWrite(dataCmd.data, bitOffset = 0)
        bus.nonStopWrite(dataCmd.read, bitOffset = 24)
        if (ctrlGenerics.ssGen) {
          val ssCmd = SpiMasterCtrlCmdSs(ctrlGenerics)
          bus.nonStopWrite(ssCmd.index, bitOffset = 0)
          bus.nonStopWrite(ssCmd.enable, bitOffset = 24)
          bus.nonStopWrite(streamUnbuffered.mode, bitOffset = 28)
          switch(streamUnbuffered.mode) {
            is(SpiMasterCtrlCmdMode.DATA) {
              streamUnbuffered.args.assignFromBits(dataCmd.asBits)
            }
            is(SpiMasterCtrlCmdMode.SS) {
              streamUnbuffered.args.assignFromBits(ssCmd.asBits.resized)
            }
          }
        } else {
          streamUnbuffered.args.assignFromBits(dataCmd.asBits)
        }

        bus.createAndDriveFlow(SpiMasterCmd(ctrlGenerics), address = baseAddress + 0).toStream
        val (stream, fifoAvailability) = streamUnbuffered.queueWithAvailability(cmdFifoDepth)
        if (!continuous)
          cmd << stream
        else
          (stream & cmdPopCond) >> cmd
        bus.read(fifoAvailability, address = baseAddress + 4, 16)
      }

      // RSP
      val rspLogic = new Area {
        val (stream, fifoOccupancy) = rsp.queueWithOccupancy(rspFifoDepth)
        bus.readStreamNonBlocking(
          stream,
          address = baseAddress + 0,
          validBitOffset = 31,
          payloadBitOffset = 0
        )
        bus.read(fifoOccupancy, address = baseAddress + 0, 16)
      }

      // Status
      val interruptCtrl = new Area {
        val cmdIntEnable = bus.createReadAndWrite(Bool(), address = baseAddress + 4, 0) init (False)
        val rspIntEnable = bus.createReadAndWrite(Bool(), address = baseAddress + 4, 1) init (False)
        val cmdInt = bus.read(cmdIntEnable & !cmdLogic.stream.valid, address = baseAddress + 4, 8)
        val rspInt = bus.read(rspIntEnable & rspLogic.stream.valid, address = baseAddress + 4, 9)
        val interrupt = rspInt || cmdInt
      }
      bus.read(spi.ss, address = baseAddress + 32)

      // Configs
      bus.drive(config.kind, baseAddress + 8)
      bus.drive(config.sclkToggle, baseAddress + 12)
      if (ssGen) new Bundle {
        bus.drive(config.ss.activeHigh, baseAddress + 8, bitOffset = 4) init (0)
        bus.drive(config.ss.setup, address = baseAddress + 16)
        bus.drive(config.ss.hold, address = baseAddress + 20)
        bus.drive(config.ss.disable, address = baseAddress + 24)
      }
      else null
      bus.drive(cmdPopCond, address = baseAddress + 28)
    }
  }

  val timer = new Area {
    val counter = Reg(UInt(timerWidth bits))
    val reset = False
    val ss = if (ssGen) new Area {
      val setupHit = counter === io.config.ss.setup
      val holdHit = counter === io.config.ss.hold
      val disableHit = counter === io.config.ss.disable
    }
    else null
    val sclkToggleHit = counter === io.config.sclkToggle

    counter := counter + 1
    when(reset) {
      counter := 0
    }
  }

  val fsm = new Area {
    val counter = Counter(dataWidth * 2)
    val buffer = Reg(Bits(dataWidth bits))
    val ss = RegInit(B((1 << ssWidth) - 1, ssWidth bits))

    io.cmd.ready := False
    when(io.cmd.valid) {
      when(io.cmd.isData) {
        when(timer.sclkToggleHit) {
          counter.increment()
          timer.reset := True
          io.cmd.ready := counter.willOverflowIfInc
          when(counter.lsb) {
            buffer := (buffer ## io.spi.miso).resized
          }
        }
      } otherwise {
        if (ssGen) {
          when(io.cmd.argsSs.enable) {
            ss(io.cmd.argsSs.index) := False
            when(timer.ss.setupHit) {
              io.cmd.ready := True
            }
          } otherwise {
            when(!counter.lsb) {
              when(timer.ss.holdHit) {
                counter.increment()
                timer.reset := True
              }
            } otherwise {
              ss(io.cmd.argsSs.index) := True
              when(timer.ss.disableHit) {
                io.cmd.ready := True
              }
            }
          }
        }
      }
    }

    // CMD responses
    io.rsp.valid := RegNext(io.cmd.fire && io.cmd.isData && io.cmd.argsData.read) init (False)
    io.rsp.payload := buffer

    // Idle states
    when(!io.cmd.valid || io.cmd.ready) {
      counter := 0
      timer.reset := True
    }

    // SPI connections
    if (ssGen) io.spi.ss := ss ^ io.config.ss.activeHigh
    io.spi.sclk := RegNext(
      ((io.cmd.valid && io.cmd.isData) && (counter.lsb ^ io.config.kind.cpha)) ^ io.config.kind.cpol
    )
    io.spi.mosi := RegNext(io.cmd.argsData.data(dataWidth - 1 - (counter >> 1)))
  }

}