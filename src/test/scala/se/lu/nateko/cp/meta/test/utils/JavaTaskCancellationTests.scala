package se.lu.nateko.cp.meta.test.utils

import org.scalatest.FunSuite
import java.util.concurrent.Executors
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ScheduledThreadPoolExecutor

class JavaTaskCancellationTests extends FunSuite{

	test("Execution of a running task is cancelled when future is cancelled"){
		val exe = new ScheduledThreadPoolExecutor(2)
		exe.setMaximumPoolSize(10)

		var executed = false

		val task: Callable[String] = () => {
			Thread.sleep(1)
			executed = true
			"FROM TASK!"
		}

		val fut = exe.submit(task)
		fut.cancel(true)

		intercept[CancellationException]{
			fut.get
		}

		assert(!executed)
	}
}
