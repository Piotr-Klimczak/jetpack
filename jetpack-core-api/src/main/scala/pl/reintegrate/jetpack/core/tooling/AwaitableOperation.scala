package pl.reintegrate.jetpack.core.tooling

import scala.util.Try
import scala.util.Success
import scala.util.Failure

class AwaitableOperationTimeoutException(msg: String, ex: Throwable = null) extends Exception(msg, ex)

class AwaitableOperation[Req, Res](f: Req => Res, exceptionOk: Boolean, minTime: Long, maxTime: Long) {

    private def checkTimeout(block: => Res, time: Long, exception: Throwable = null): Res = {
        val time2 = time * 2
        if (time2 < maxTime) {
            Thread.sleep(time2)
            reTry(block, time2)
        } else if (exception != null) {
            throw new AwaitableOperationTimeoutException("Timeout with exception for function: " + f, exception)
        } else {
            throw new AwaitableOperationTimeoutException("Timeout for function: " + f)
        }
    }

    private def reTry(block: => Res, time: Long): Res = {
        Try(block) match {
            case Success(retVal) if retVal == null => checkTimeout(block, time)
            case Success(retVal) => retVal
            case Failure(e) if exceptionOk => checkTimeout(block, time, e)
            case Failure(e) => throw e
        }
    }

    def startWith(req: Req): Res = reTry(f(req), minTime)
}

object AwaitableOperation {
    val minTime = 10
    val maxTime = 10000
    def apply[Req, Res](f: Req => Res, exceptionOk: Boolean = true, minTime: Long = minTime, maxTime: Long = maxTime) =
        new AwaitableOperation[Req, Res](f, exceptionOk, minTime, maxTime)
}