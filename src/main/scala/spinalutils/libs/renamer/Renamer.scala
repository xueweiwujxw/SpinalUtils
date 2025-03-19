package spinalutils.libs.renamer

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba4.axis.Axi4Stream.Axi4StreamBundle
import scala.util.matching.Regex

object StreamRenamer {
  def apply[T <: BaseType](that: Stream[T]): Stream[T] = {
    def doIt = {
      that.payload.overrideLocalName("tdata")
      that.flatten.foreach((bt) => {
        bt.setName(bt.getName().replace("payload_", ""))
        bt.setName(bt.getName().replace("valid", "tvalid"))
        bt.setName(bt.getName().replace("ready", "tready"))
        if (bt.getName().startsWith("io_")) bt.setName(bt.getName().replaceFirst("io_", ""))
      })
    }
    if (Component.current == that.component)
      that.component.addPrePopTask(() => { doIt })
    else
      doIt

    that
  }
}

object Axi4FlowRenamer {
  def apply[T <: Data](that: Flow[T]): Flow[T] = {
    var doIt: Unit = {}

    that.payload match {
      case axis: Axi4StreamBundle => {
        doIt = {
          axis.data.overrideLocalName("tdata")
          (axis.id != null) generate axis.id.overrideLocalName("tid")
          (axis.strb != null) generate axis.strb.overrideLocalName("tstrb")
          (axis.keep != null) generate axis.keep.overrideLocalName("tkeep")
          (axis.last != null) generate axis.last.overrideLocalName("tlast")
          (axis.dest != null) generate axis.dest.overrideLocalName("tdest")
          (axis.user != null) generate axis.user.overrideLocalName("tuser")
          that.flatten.foreach((bt) => {
            bt.setName(bt.getName().replace("payload_", ""))
            bt.setName(bt.getName().replace("valid", "tvalid"))
            bt.setName(bt.getName().replace("ready", "tready"))
            if (bt.getName().startsWith("io_")) bt.setName(bt.getName().replaceFirst("io_", ""))
          })
        }
      }
      case _ =>
        doIt = {
          that.payload.overrideLocalName("tdata")
          that.flatten.foreach((bt) => {
            bt.setName(bt.getName().replace("payload_", ""))
            bt.setName(bt.getName().replace("valid", "tvalid"))
            bt.setName(bt.getName().replace("ready", "tready"))
            if (bt.getName().startsWith("io_")) bt.setName(bt.getName().replaceFirst("io_", ""))
          })
        }
    }

    if (Component.current == that.component)
      that.component.addPrePopTask(() => { doIt })
    else
      doIt
    that
  }
}

object NameProcess {
  def replaceAndMoveToEnd(pattern: Regex, input: String, delete: Boolean = false): String = {
    // 使用正则表达式查找并分离匹配的子字符串
    val (matches, modifiedString) =
      pattern.findAllIn(input).toList.foldLeft((List.empty[String], input)) { case ((m, s), sub) =>
        (m :+ sub, s.replace(sub, ""))
      }
    if (!delete)
      modifiedString + matches.mkString // 将匹配的子字符串追加到末尾
    else
      modifiedString
  }
}
