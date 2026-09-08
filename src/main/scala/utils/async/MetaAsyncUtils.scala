package se.lu.nateko.cp.meta.utils.async

import akka.Done

import scala.concurrent.{ExecutionContext, Future}

/** Sequential future combinators, used only by meta's upload workbench/test tooling. */

def traverseFut[T, R](on: Iterable[T])(thunk: T => Future[R])(using ExecutionContext): Future[Seq[R]] =
	on.foldLeft(Future.successful(Seq.empty[R])){(acc, next) =>
		for(
			accRes <- acc;
			nextRes <- thunk(next)
		) yield accRes :+ nextRes
	}

def executeSequentially[T](on: Iterable[T])(thunk: T => Future[Done])(using ExecutionContext): Future[Done] =
	traverseFut(on)(thunk).map(_ => Done)
