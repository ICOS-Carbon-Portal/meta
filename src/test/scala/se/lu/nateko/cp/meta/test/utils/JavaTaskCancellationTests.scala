package se.lu.nateko.cp.meta.test.utils

import org.scalatest.funsuite.AnyFunSuite
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ScheduledThreadPoolExecutor
import org.scalatest.BeforeAndAfterAll

class JavaTaskCancellationTests extends AnyFunSuite with BeforeAndAfterAll{

	private val exe = new ScheduledThreadPoolExecutor(2)
	exe.setMaximumPoolSize(10)

	override def afterAll(): Unit = {
		exe.shutdown()
	}

	test("Execution of a running task is cancelled when future is cancelled"){

		var executed = false

		val task: Callable[String] = () => {
			Thread.sleep(100)
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
