package spinalutils.libs.signal

import spinal.core._

case class DiffSignal(width: Int = 1) extends Bundle {
  val n = Bits(width bits)
  val p = Bits(width bits)
}

object DiffSignalRenamer {
  def apply(that: DiffSignal): DiffSignal = {
    def replaceLast(input: String, target: String, replacement: String): String = {
      val regex = s"(.*)${target}(?!.*${target})".r
      input match {
        case regex(prefix) => s"${prefix}${replacement}"
        case _             => input
      }
    }

    def doIt = {
      that.flatten.foreach((bt) => {
        bt.setName(replaceLast(bt.getName(), "_n", "n"))
        bt.setName(replaceLast(bt.getName(), "_p", "p"))
      })
    }

    if (Component.current == that.component)
      that.component.addPrePopTask(() => { doIt })
    else
      doIt

    that
  }
}
